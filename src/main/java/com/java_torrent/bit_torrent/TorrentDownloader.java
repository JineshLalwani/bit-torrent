package com.java_torrent.bit_torrent;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class TorrentDownloader {

    private static final int PORT = 6881;

    private static final byte CHOKE_MESSAGE_ID = 0;
    private static final byte UNCHOKE_MESSAGE_ID = 1;
    private static final byte INTERESTED_MESSAGE_ID = 2;
    private static final byte BITFIELD_MESSAGE_ID = 5;
    private static final byte REQUEST_MESSAGE_ID = 6;
    private static final byte PIECE_MESSAGE_ID = 7;
    private static final byte EXTENSION_MESSAGE_ID = 20;

    private static final int BLOCK_SIZE = 16384;
    private static final int PIPELINE_DEPTH = 8;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 15000;
    private static final int MAX_PEER_WORKERS = 12;
    private static final int MAX_PIECE_ATTEMPTS = 4;
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final int MAX_SKIPPED_MESSAGES = 1024;

    /** Callback for piece-level download progress. */
    public interface ProgressListener {
        void onProgress(int completedPieces, int totalPieces);
    }

    /** Opens a socket to a peer with connect/read timeouts applied. */
    private static TCPService connectToPeer(String peer) throws IOException {
        String[] hostPort = peer.split(":");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        return new TCPService(socket);
    }

    /**
     * Performs the base handshake, expresses interest and waits until the peer
     * unchokes us. Bitfield/have/keep-alive messages received in between are
     * tolerated (peers send them in varying order).
     */
    private static TCPService openPeerConnection(Torrent torrent, String peer) throws IOException {
        TCPService tcpService = connectToPeer(peer);
        boolean ready = false;
        try {
            performHandshake(torrent.getInfoHash(), tcpService, false);
            tcpService.sendMessage(new byte[]{0, 0, 0, 1, INTERESTED_MESSAGE_ID});
            awaitUnchoke(tcpService);
            ready = true;
            return tcpService;
        } finally {
            if (!ready) {
                try { tcpService.close(); } catch (IOException ignored) {}
            }
        }
    }

    private static void awaitUnchoke(TCPService tcpService) {
        for (int i = 0; i < MAX_SKIPPED_MESSAGES; i++) {
            byte[] message = tcpService.waitForMessage();
            if (message[0] == UNCHOKE_MESSAGE_ID) {
                return;
            }
            // choke/bitfield/have/extension — keep waiting for unchoke
        }
        throw new RuntimeException("Peer never unchoked us");
    }

    public static byte[] downloadPieceFromPeer(Torrent torrent, String peer, int index, boolean isMagnetHandshake) {
        try (TCPService tcpService = connectToPeer(peer)) {
            int pieceLength = (int) torrent.getPieceLength(index);
            if (isMagnetHandshake) {
                performMagnetHandshakeOnPeer(tcpService, torrent.getInfoHash());
            } else {
                performHandshake(torrent.getInfoHash(), tcpService, false);
            }
            return downloadPieceHelper(tcpService, pieceLength, index);
        } catch (Exception e) {
            throw new RuntimeException("Error downloading piece from peer: " + e.getMessage());
        }
    }

    public static byte[] downloadPiece(Torrent torrent, int index, boolean isMagnetHandshake) {
        List<String> peerList;
        try {
            peerList = getPeerList(torrent);
        } catch (Exception e) {
            throw new RuntimeException("Error getting peer list: " + e.getMessage());
        }

        if (peerList == null || peerList.isEmpty()) {
            throw new RuntimeException("No peers available to download from");
        }
        byte[] piece = null;
        for (String peer : peerList) {
            try {
                System.out.println("Downloading piece from peer: " + peer);
                piece = downloadPieceFromPeer(torrent, peer, index, isMagnetHandshake);
                break;
            } catch (Exception e) {
                System.out.println("Error downloading piece from peer: " + peer + ", " + e.getMessage());
            }
        }
        if (piece == null) {
            throw new RuntimeException("Failed to download piece: " + index);
        }
        if (!validatePieceHash(torrent.getPieces().get(index), piece)) {
            throw new RuntimeException("Piece hash validation failed: " + index);
        }
        return piece;
    }

    private static boolean validatePieceHash(String expectedPieceHash, byte[] piece) {
        String actualPieceHash = Utils.calculateSHA1(piece);
        if (!expectedPieceHash.equals(actualPieceHash)) {
            System.out.println("Hash validation failed. Expected hash: " + expectedPieceHash + ", Actual hash: " + actualPieceHash);
        }
        return expectedPieceHash.equals(actualPieceHash);
    }

    /**
     * Downloads one piece on an already-handshaken connection: sends interested,
     * waits for unchoke, then requests blocks with pipelining.
     */
    public static byte[] downloadPieceHelper(TCPService tcpService, int pieceLength, int index) throws Exception {
        tcpService.sendMessage(new byte[]{0, 0, 0, 1, INTERESTED_MESSAGE_ID});
        awaitUnchoke(tcpService);
        return downloadPieceOverConnection(tcpService, pieceLength, index);
    }

    /**
     * Requests all blocks of a piece over an unchoked connection, keeping up to
     * {@link #PIPELINE_DEPTH} requests in flight. Blocks may arrive out of order
     * and are placed by their begin offset.
     */
    private static byte[] downloadPieceOverConnection(TCPService tcpService, int pieceLength, int index) throws IOException {
        int blocks = (int) Math.ceil((double) pieceLength / BLOCK_SIZE);
        byte[] piece = new byte[pieceLength];
        int nextBlock = 0;
        int blocksReceived = 0;
        int outstanding = 0;
        int skipped = 0;
        while (blocksReceived < blocks) {
            while (nextBlock < blocks && outstanding < PIPELINE_DEPTH) {
                int offset = nextBlock * BLOCK_SIZE;
                int blockLength = Math.min(BLOCK_SIZE, pieceLength - offset);
                tcpService.sendMessage(REQUEST_MESSAGE_ID, TCPService.createRequestPayload(index, offset, blockLength));
                nextBlock++;
                outstanding++;
            }
            byte[] message = tcpService.waitForMessage();
            if (message[0] == PIECE_MESSAGE_ID) {
                if (message.length < 9) {
                    throw new IOException("Malformed piece message");
                }
                ByteBuffer header = ByteBuffer.wrap(message, 1, 8);
                int pieceIndex = header.getInt();
                int begin = header.getInt();
                int blockLength = message.length - 9;
                if (pieceIndex != index || begin < 0 || begin + blockLength > pieceLength) {
                    throw new IOException("Unexpected block: piece " + pieceIndex + " offset " + begin);
                }
                System.arraycopy(message, 9, piece, begin, blockLength);
                blocksReceived++;
                outstanding--;
            } else if (message[0] == CHOKE_MESSAGE_ID) {
                throw new IOException("Peer choked us mid-piece");
            } else {
                // have/bitfield/extension etc. — ignore, but bound the tolerance
                if (++skipped > MAX_SKIPPED_MESSAGES) {
                    throw new IOException("Peer flooded us with non-piece messages");
                }
            }
        }
        return piece;
    }

    private static List<String> getPeerListFromHTTPResponse(HttpResponse<byte[]> response) {
        Bencode bencode = new Bencode(true);
        Map<String, Object> decodedResponse = bencode.decode(response.body(), Type.DICTIONARY);

        Object failure = decodedResponse.get("failure reason");
        if (failure != null) {
            throw new RuntimeException("Tracker error: " + Torrent.bufToString(failure));
        }

        Object peersObj = decodedResponse.get("peers");
        List<String> peerList = new ArrayList<>();
        if (peersObj instanceof ByteBuffer) {
            // Compact model: 6 bytes per peer (4 IP + 2 port)
            byte[] peersBytes = ((ByteBuffer) peersObj).array();
            for (int i = 0; i + 6 <= peersBytes.length; i += 6) {
                String ip = String.format("%d.%d.%d.%d", peersBytes[i] & 0xff, peersBytes[i + 1] & 0xff,
                        peersBytes[i + 2] & 0xff, peersBytes[i + 3] & 0xff);
                int port = ((peersBytes[i + 4] & 0xff) << 8) | (peersBytes[i + 5] & 0xff);
                peerList.add(ip + ":" + port);
            }
        } else if (peersObj instanceof List) {
            // Dictionary model: list of {ip, port}
            for (Object entry : (List<?>) peersObj) {
                if (entry instanceof Map) {
                    Map<?, ?> peerDict = (Map<?, ?>) entry;
                    String ip = Torrent.bufToString(peerDict.get("ip"));
                    Object port = peerDict.get("port");
                    if (ip != null && port instanceof Number) {
                        peerList.add(ip + ":" + ((Number) port).intValue());
                    }
                }
            }
        }
        return peerList;
    }

    private static void validateHandshakeResponse(byte[] response,
                                                  byte[] expectedInfoHash, boolean isMagnetHandshake) {
        if (response[0] != 19) {
            throw new RuntimeException("Invalid protocol length: " + response[0]);
        }
        byte[] protocolBytes = Arrays.copyOfRange(response, 1, 20);
        String protocol = new String(protocolBytes, StandardCharsets.ISO_8859_1);
        if (!"BitTorrent protocol".equals(protocol)) {
            throw new RuntimeException("Invalid protocol: " + protocol);
        }
        if (isMagnetHandshake) {
            // Extension protocol support is bit 0x10 of reserved byte 5; peers may set other bits too
            if ((response[25] & 0x10) == 0) {
                throw new RuntimeException("Peer does not support the extension protocol");
            }
        }
        byte[] receivedInfoHash = Arrays.copyOfRange(response, 28, 48);
        if (!Arrays.equals(expectedInfoHash, receivedInfoHash)) {
            throw new RuntimeException("Info hash mismatch");
        }
    }

    static void performHandshake(String infoHash, TCPService tcpService, boolean isMagnetHandshake) {
        byte[] handshakeMessage = createHandshakeMessage(infoHash, isMagnetHandshake);
        tcpService.sendMessage(handshakeMessage);
        byte[] handshakeResponse = tcpService.waitForHandshakeResponse();
        validateHandshakeResponse(handshakeResponse, Utils.hexStringToByteArray(infoHash), isMagnetHandshake);
        byte[] peerIdBytes = Arrays.copyOfRange(handshakeResponse, handshakeResponse.length - 20, handshakeResponse.length);
        String peerId = Utils.byteToHexString(peerIdBytes);
        System.out.println("Peer ID: " + peerId);
    }

    static byte[] createHandshakeMessage(String infoHash, boolean isMagnetHandshake) {
        ByteArrayOutputStream handshakeMessage = new ByteArrayOutputStream();
        try {
            handshakeMessage.write(19);
            handshakeMessage.write("BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1));
            byte[] reservedBytes = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
            if (isMagnetHandshake) {
                reservedBytes[5] = 16;
            }
            handshakeMessage.write(reservedBytes);
            handshakeMessage.write(Utils.hexStringToByteArray(infoHash));
            handshakeMessage.write(generatePeerId());
            return handshakeMessage.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error creating handshake message: " + e.getMessage());
        }
    }

    public static void downloadTorrent(Torrent torrent, String storageFilePath, boolean isMagnetDownload) {
        downloadTorrent(torrent, storageFilePath, isMagnetDownload, null, null);
    }

    /**
     * Downloads all pieces of a torrent concurrently and writes the assembled
     * file to {@code storageFilePath}.
     *
     * @param listener   optional progress callback
     * @param extraPeers optional known peer addresses tried in addition to tracker peers
     */
    public static void downloadTorrent(Torrent torrent, String storageFilePath, boolean isMagnetDownload,
                                       ProgressListener listener, List<String> extraPeers) {
        int numPieces = torrent.getPieces().size();

        Queue<Integer> pieceQueue = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < numPieces; i++) {
            pieceQueue.add(i);
        }
        Map<Integer, byte[]> bufferMap = new ConcurrentHashMap<>();
        Map<Integer, AtomicInteger> attempts = new ConcurrentHashMap<>();
        AtomicInteger completed = new AtomicInteger();

        // Direct peers first (e.g. from x.pe magnet params), then tracker peers
        LinkedHashSet<String> peers = new LinkedHashSet<>();
        if (extraPeers != null) {
            peers.addAll(extraPeers);
        }
        try {
            peers.addAll(getPeerList(torrent));
        } catch (Exception e) {
            if (peers.isEmpty()) {
                throw new RuntimeException("Error getting peer list: " + e.getMessage(), e);
            }
            System.out.println("Tracker lookup failed, continuing with direct peers: " + e.getMessage());
        }
        if (peers.isEmpty()) {
            throw new RuntimeException("No peers available to download from");
        }

        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(peers.size(), MAX_PEER_WORKERS));
        for (String peer : peers) {
            executorService.submit(() -> worker(torrent, peer, pieceQueue, bufferMap, attempts, completed, listener));
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Download interrupted");
        }

        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < numPieces; i++) {
            if (!bufferMap.containsKey(i)) {
                missing.add(i);
            }
        }
        if (!missing.isEmpty()) {
            throw new RuntimeException("Download incomplete: " + missing.size() + " of " + numPieces
                    + " pieces could not be downloaded from any peer");
        }

        try {
            Files.deleteIfExists(Paths.get(storageFilePath));
        } catch (IOException e) {
            throw new RuntimeException("Could not replace existing file: " + e.getMessage());
        }
        for (int i = 0; i < numPieces; i++) {
            Utils.writePieceToFile(storageFilePath, bufferMap.get(i));
        }
    }

    /**
     * Peer worker: keeps a single handshaken connection open and downloads
     * pieces from the shared queue until it drains or the peer fails repeatedly.
     */
    private static void worker(Torrent torrent, String peer, Queue<Integer> pieceQueue,
                               Map<Integer, byte[]> bufferMap, Map<Integer, AtomicInteger> attempts,
                               AtomicInteger completed, ProgressListener listener) {
        int totalPieces = torrent.getPieces().size();
        TCPService tcpService;
        try {
            tcpService = openPeerConnection(torrent, peer);
        } catch (Exception e) {
            System.out.println("Could not connect to peer " + peer + ": " + e.getMessage());
            return;
        }

        int consecutiveFailures = 0;
        try {
            Integer pieceIndex;
            while ((pieceIndex = pieceQueue.poll()) != null) {
                try {
                    byte[] piece = downloadPieceOverConnection(tcpService, (int) torrent.getPieceLength(pieceIndex), pieceIndex);
                    if (!validatePieceHash(torrent.getPieces().get(pieceIndex), piece)) {
                        throw new IOException("Hash mismatch for piece " + pieceIndex);
                    }
                    bufferMap.put(pieceIndex, piece);
                    consecutiveFailures = 0;
                    int done = completed.incrementAndGet();
                    System.out.println("Downloaded piece " + pieceIndex + " from " + peer + " (" + done + "/" + totalPieces + ")");
                    if (listener != null) {
                        listener.onProgress(done, totalPieces);
                    }
                } catch (Exception e) {
                    System.out.println("Error downloading piece " + pieceIndex + " from " + peer + ": " + e.getMessage());
                    // Give other peers a chance at this piece, but don't retry forever
                    if (attempts.computeIfAbsent(pieceIndex, k -> new AtomicInteger()).incrementAndGet() < MAX_PIECE_ATTEMPTS) {
                        pieceQueue.add(pieceIndex);
                    }
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        break;
                    }
                    // The stream may be desynchronized after an error — reconnect
                    try { tcpService.close(); } catch (IOException ignored) {}
                    tcpService = openPeerConnection(torrent, peer);
                }
            }
        } catch (Exception e) {
            System.out.println("Peer " + peer + " dropped: " + e.getMessage());
        } finally {
            try { tcpService.close(); } catch (IOException ignored) {}
        }
    }

    public static List<String> getPeerList(Torrent torrent) throws Exception {
        List<String> trackerURLs = torrent.getTrackerURLs();
        if (trackerURLs == null || trackerURLs.isEmpty()) {
            trackerURLs = new ArrayList<>();
            if (torrent.getTrackerURL() != null) {
                trackerURLs.add(torrent.getTrackerURL());
            }
        }
        if (trackerURLs.isEmpty()) {
            throw new RuntimeException("Torrent has no tracker URLs");
        }

        byte[] infoHashBytes = Utils.hexStringToByteArray(torrent.getInfoHash());
        byte[] peerIdBytes = generatePeerId();
        List<String> errors = new ArrayList<>();

        for (String url : trackerURLs) {
            try {
                List<String> peers = announceToTracker(url, infoHashBytes, peerIdBytes, torrent.getLength(), null);
                if (!peers.isEmpty()) return peers;
            } catch (Exception e) {
                errors.add(url + ": " + e.getMessage());
                System.out.println("Tracker " + url + " failed: " + e.getMessage());
            }
        }

        throw new RuntimeException("All trackers failed: " + String.join("; ", errors));
    }

    private static List<String> announceToTracker(String url, byte[] infoHashBytes, byte[] peerIdBytes,
                                                  long left, String displayName) throws Exception {
        if (url.startsWith("udp://")) {
            return UdpTrackerService.getPeerList(url, infoHashBytes, peerIdBytes, left);
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            String infoHash = new String(infoHashBytes, StandardCharsets.ISO_8859_1);
            String peerId = new String(peerIdBytes, StandardCharsets.ISO_8859_1);

            HttpClientService httpClientService = new HttpClientService();
            HttpClientService.RequestURLBuilder builder = httpClientService.newRequestURLBuilder(url)
                    .addParam("info_hash", infoHash)
                    .addParam("peer_id", peerId)
                    .addParam("port", String.valueOf(PORT))
                    .addParam("uploaded", "0")
                    .addParam("downloaded", "0")
                    .addParam("left", String.valueOf(left))
                    .addParam("compact", "1");
            if (displayName != null && !displayName.isEmpty()) {
                builder.addParam("dn", displayName);
            }
            HttpResponse<byte[]> response = httpClientService.sendGetRequest(builder.build());
            return getPeerListFromHTTPResponse(response);
        }
        throw new RuntimeException("Unsupported tracker protocol: " + url);
    }

    public static List<String> getPeerListFromMagnetInfo(Map<String, String> magnetInfoMap) {
        String infoHashHex = TorrentUtils.getInfoHashFromMagnetParams(magnetInfoMap);
        byte[] infoHashBytes = Utils.hexStringToByteArray(infoHashHex);
        byte[] peerIdBytes = generatePeerId();
        List<String> errors = new ArrayList<>();

        // Direct peers from x.pe params don't need a tracker round trip
        List<String> peers = new ArrayList<>(TorrentUtils.getDirectPeers(magnetInfoMap));

        for (String trackerUrl : TorrentUtils.getTrackerList(magnetInfoMap)) {
            try {
                List<String> trackerPeers = announceToTracker(trackerUrl, infoHashBytes, peerIdBytes, 1,
                        magnetInfoMap.getOrDefault("dn", ""));
                for (String p : trackerPeers) {
                    if (!peers.contains(p)) peers.add(p);
                }
                if (!peers.isEmpty()) return peers;
            } catch (Exception e) {
                errors.add(trackerUrl + ": " + e.getMessage());
                System.out.println("Tracker " + trackerUrl + " failed: " + e.getMessage());
            }
        }
        if (!peers.isEmpty()) {
            return peers;
        }

        // Fallback: try xs (exact source) URL to download .torrent file directly
        String xs = magnetInfoMap.get("xs");
        if (xs != null) {
            try {
                HttpClientService httpClientService = new HttpClientService();
                HttpResponse<byte[]> response = httpClientService.sendGetRequest(xs);
                Torrent torrent = Torrent.fromBytes(response.body());
                return getPeerList(torrent);
            } catch (Exception e) {
                errors.add("xs fallback: " + e.getMessage());
            }
        }

        throw new RuntimeException("All trackers failed: " + String.join("; ", errors));
    }

    private static byte[] generatePeerId() {
        byte[] peerId = new byte[20];
        byte[] prefix = "-BT0001-".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, peerId, 0, prefix.length);
        byte[] random = Utils.getRandomBytes(20 - prefix.length);
        System.arraycopy(random, 0, peerId, prefix.length, random.length);
        return peerId;
    }

    public static byte[] createExtensionHandshakeMessage(List<String> extensionList) {
        Map<String, Map<String, Integer>> extensionDict = new HashMap<>();
        Map<String, Integer> m = new HashMap<>();
        for (String extension : extensionList) {
            m.put(extension, 1);
        }
        extensionDict.put("m", m);
        byte[] extensionDictBytes = new Bencode(true).encode(extensionDict);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + extensionDictBytes.length);
        buffer.putInt(1 + 1 + extensionDictBytes.length);
        buffer.put(EXTENSION_MESSAGE_ID);
        buffer.put((byte) 0);
        buffer.put(extensionDictBytes);
        return buffer.array();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseExtensionHandshakeResponse(byte[] extensionHandshakeResponse) {
        byte[] extensionDictBytes = Arrays.copyOfRange(extensionHandshakeResponse, 2, extensionHandshakeResponse.length);
        Map<String, Object> extensionDict = new Bencode(false).decode(extensionDictBytes, Type.DICTIONARY);
        return (Map<String, Object>) extensionDict.get("m");
    }

    public static byte[] createMetadataRequestMessage(int messageType, int pieceIndex, long extensionId) {
        Map<String, Integer> metadataRequestDict = new HashMap<>();
        metadataRequestDict.put("msg_type", messageType);
        metadataRequestDict.put("piece", pieceIndex);
        byte[] metadataRequestDictBytes = new Bencode(true).encode(metadataRequestDict);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + metadataRequestDictBytes.length);
        buffer.putInt(2 + metadataRequestDictBytes.length);
        buffer.put(EXTENSION_MESSAGE_ID);
        buffer.put((byte) extensionId);
        buffer.put(metadataRequestDictBytes);
        return buffer.array();
    }

    /** Reads messages until an extended-protocol message (id 20) arrives. */
    public static byte[] waitForExtendedMessage(TCPService tcpService) {
        for (int i = 0; i < MAX_SKIPPED_MESSAGES; i++) {
            byte[] message = tcpService.waitForMessage();
            if (message[0] == EXTENSION_MESSAGE_ID) {
                return message;
            }
        }
        throw new RuntimeException("Peer never sent an extension message");
    }

    public static Pair<TCPService, Long> performMagnetHandshakeOnPeer(TCPService tcpService, String infohash) {
        performHandshake(infohash, tcpService, true);
        // Send our extension handshake; the peer's bitfield/have messages may
        // arrive before its extension handshake, so skip until we see it
        byte[] extensionHandshakeMessage = createExtensionHandshakeMessage(List.of("ut_metadata", "ut_pex"));
        tcpService.sendMessage(extensionHandshakeMessage);
        byte[] extensionHandshakeResponse = waitForExtendedMessage(tcpService);
        Map<String, Object> metaDataIDMap = parseExtensionHandshakeResponse(extensionHandshakeResponse);
        Object utMetadata = metaDataIDMap != null ? metaDataIDMap.get("ut_metadata") : null;
        if (!(utMetadata instanceof Number)) {
            throw new RuntimeException("Peer does not support ut_metadata");
        }
        System.out.println("Peer Metadata Extension ID: " + utMetadata);
        return Pair.of(tcpService, ((Number) utMetadata).longValue());
    }

    public static Pair<TCPService, Long> performMagnetHandshakeOnPeer(Map<String, String> magnetInfo, String peerIP, int peerPort) {
        TCPService tcpService = null;
        try {
            tcpService = connectToPeer(peerIP + ":" + peerPort);
            return performMagnetHandshakeOnPeer(tcpService, TorrentUtils.getInfoHashFromMagnetParams(magnetInfo));
        } catch (Exception e) {
            System.out.println("Failed magnet handshake with peer: " + peerIP + ":" + peerPort + " - " + e.getMessage());
            if (tcpService != null) {
                try { tcpService.close(); } catch (IOException ignored) {}
            }
        }
        return null;
    }

    public static Pair<TCPService, Long> performMagnetHandshake(String magnetURL) {
        Map<String, String> magnetInfo = TorrentUtils.getParamsFromMagnetURL(magnetURL);
        return performMagnetHandshakeWithParams(magnetInfo);
    }

    public static Pair<TCPService, Long> performMagnetHandshakeWithParams(Map<String, String> magnetInfo) {
        List<String> peerList = getPeerListFromMagnetInfo(magnetInfo);
        for (String peer : peerList) {
            String peerIP = peer.split(":")[0];
            int peerPort = Integer.parseInt(peer.split(":")[1]);
            Pair<TCPService, Long> handshakeResult = performMagnetHandshakeOnPeer(magnetInfo, peerIP, peerPort);
            if (handshakeResult != null && handshakeResult.getLeft() != null) {
                return handshakeResult;
            }
        }
        return null;
    }

    public static Map<String, Object> getMetadataFromMessage(byte[] metadataResponse) {
        byte[] payloadBytes = Arrays.copyOfRange(metadataResponse, 2, metadataResponse.length);
        Map<String, Object> metadataDict = new Bencode(false).decode(payloadBytes, Type.DICTIONARY);
        int metadataPieceLength = ((Number) metadataDict.get("total_size")).intValue();
        byte[] metadataPieceBytes = Arrays.copyOfRange(payloadBytes, payloadBytes.length - metadataPieceLength, payloadBytes.length);
        return new Bencode(true).decode(metadataPieceBytes, Type.DICTIONARY);
    }
}
