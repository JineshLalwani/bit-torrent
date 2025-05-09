package com.java_torrent.bit_torrent;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TorrentDownloader {

    private static final int PORT = 6881;

    private static final byte UNCHOKE_MESSAGE_ID = 1;
    private static final byte INTERESTED_MESSAGE_ID = 2;
    private static final byte BITFIELD_MESSAGE_ID = 5;
    private static final byte REQUEST_MESSAGE_ID = 6;
    private static final byte PIECE_MESSAGE_ID = 7;
    private static final int BLOCK_SIZE = 16384;

    private static Queue<Integer> pieceQueue = new ConcurrentLinkedQueue<>();
    private static Map<Integer, byte[]> bufferMap = new ConcurrentHashMap<>();
    private static Lock bufferLock = new ReentrantLock();

    public static byte[] downloadPieceFromPeer(Torrent torrent, String peer, int index, boolean isMagnetHandshake) {
        try (Socket socket = new Socket(peer.split(":")[0], Integer.parseInt(peer.split(":")[1]))) {
            TCPService tcpService = new TCPService(socket);
            int pieceLength = (int) torrent.getPieceLength(index);
            if (isMagnetHandshake) {
                performMagnetHandshakeOnPeer(tcpService, torrent.getInfoHash());
                return downloadPieceHelper(tcpService, pieceLength, index);
            } else {
                performHandshake(torrent.getInfoHash(), tcpService, false);
                return downloadPieceHelper(pieceLength, tcpService, index);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error downloading piece from peer: " + e.getMessage());
        }
    }
    public static byte[] downloadPiece(Torrent torrent, int index, boolean isMagnetHandshake) {
        List<String> peerList = null;
        try {
            peerList = getPeerList(torrent);
        } catch (Exception e) {
            throw new RuntimeException("Error getting peer list: " + e.getMessage());
        }

        if (peerList == null || peerList.size() == 0) {
            throw new RuntimeException("No peers available to download from");
        }
        byte piece[] = null;
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

    public static byte[] downloadPieceHelper(int pieceLength, TCPService tcpService, int index) throws Exception {
        byte[] bitfieldMessage = tcpService.waitForMessage();
        if (bitfieldMessage[0] != BITFIELD_MESSAGE_ID) {
            throw new RuntimeException("Expected bitfield message (5) from peer, but received different message: " + bitfieldMessage[0]);
        }
        System.out.println("Received bitfield message");
        byte[] piece = downloadPieceHelper(tcpService, pieceLength, index);
        return piece;
    }
    public static byte[] downloadPieceHelper(TCPService tcpService, int pieceLength, int index) throws Exception {
        // send an interested message to the peer
        byte[] interestedMessage = new byte[]{0, 0, 0, 1, INTERESTED_MESSAGE_ID};
        tcpService.sendMessage(interestedMessage);
        byte[] unchokeMessage = tcpService.waitForMessage();
        if (unchokeMessage[0] != UNCHOKE_MESSAGE_ID) {
            throw new RuntimeException("Expected unchoke message (1) from peer, but received different message: " + unchokeMessage[0]);
        }
        System.out.println("Received unchoke message");
        int blocks = (int) Math.ceil((double) pieceLength / BLOCK_SIZE);
        int offset = 0;
        byte[] piece = new byte[pieceLength];
        for (int blockIndex = 0; blockIndex < blocks; blockIndex++) {
            int blockLength = Math.min(BLOCK_SIZE, pieceLength - offset);
            byte[] requestPayload = TCPService.createRequestPayload(index, offset, blockLength);
            tcpService.sendMessage(REQUEST_MESSAGE_ID, requestPayload);
            byte[] pieceMessage = tcpService.waitForMessage();
            if (pieceMessage[0] != PIECE_MESSAGE_ID) {
                throw new RuntimeException("Expected piece message (7) from peer, but received different message: " + pieceMessage[0]);
            }
            System.out.println("Received piece message for block: " + blockIndex + " out of " + blocks);
            System.arraycopy(pieceMessage, 9, piece, offset, blockLength);
            offset += blockLength;
        }
        return piece;
    }

    private static List<String> getPeerListFromHTTPResponse(HttpResponse<byte[]> response) {
        Bencode bencode = new Bencode(true);
        Map<String, Object> decodedResponse = bencode.decode(response.body(), Type.DICTIONARY);
        byte[] peersBytes = ((ByteBuffer) decodedResponse.get("peers")).array();

        List<String> peerList = new ArrayList<>();
        for (int i = 0; i < peersBytes.length; i += 6) {
            String ip = String.format("%d.%d.%d.%d", peersBytes[i] & 0xff, peersBytes[i + 1] & 0xff,
                    peersBytes[i + 2] & 0xff, peersBytes[i + 3] & 0xff);
            int port = ((peersBytes[i + 4] & 0xff) << 8) | (peersBytes[i + 5] & 0xff);
            peerList.add(ip + ":" + port);
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
            if (response[25] != 16) {
                throw new RuntimeException("Invalid reserved byte: " + response[25]);
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
        // create a handshake message to send to the peer
        ByteArrayOutputStream handshakeMessage = new ByteArrayOutputStream();
        try {
            handshakeMessage.write(19);
            handshakeMessage.write("BitTorrent protocol".getBytes());
            byte[] reservedBytes = new byte[] {0,0,0,0,0,0,0,0};
            if (isMagnetHandshake) {
                reservedBytes[5] = 16;
            }
            handshakeMessage.write(reservedBytes);
            handshakeMessage.write(Utils.hexStringToByteArray(infoHash));
            handshakeMessage.write("ABCDEFGHIJKLMNOPQRST".getBytes());
            byte[] handshakeMessageBytes = handshakeMessage.toByteArray();
            return handshakeMessageBytes;
        } catch (Exception e) {
            throw new RuntimeException("Error creating handshake message: " + e.getMessage());
        }
    }

    public static void downloadTorrent(Torrent torrent, String storageFilePath, boolean isMagnetDownload) {
        int numPieces = torrent.getPieces().size();

        // create a queue of pieces to download
        // add all the pieces to the queue
        for (int i = 0; i < numPieces; i++) {
            pieceQueue.add(i);
        }
        // create a connection pool to each peer
        List<String> peerList;
        try {
            peerList = getPeerList(torrent);
            int numPeers = peerList.size();
            ExecutorService executorService = Executors.newFixedThreadPool(numPeers);
            for (String peer : peerList) {
                executorService.submit(() -> worker(torrent, peer, isMagnetDownload));
            }
            executorService.shutdown();
            try {
                executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                System.out.println("Error waiting for executor service to terminate: " + e.getMessage());
            }
            // write the pieces to the file
            for (int i = 0; i < numPieces; i++) {
                bufferLock.lock();
                try {
                    Utils.writePieceToFile(storageFilePath, bufferMap.get(i));
                } finally {
                    bufferLock.unlock();
                }
            }
        } catch (Exception e) {
            System.out.println("Error getting peer list: " + e.getMessage());
        }
    }
    private static void worker(Torrent torrent, String peer, boolean isMagnetDownload) {
        while (true) {
            Integer pieceIndex = pieceQueue.poll();
            if (pieceIndex == null) {
                break;
            }
            // calculate the piece length based on the piece index
            try {
                byte[] piece = downloadPieceFromPeer(torrent, peer, pieceIndex, isMagnetDownload);
                bufferLock.lock();
                try {
                    bufferMap.put(pieceIndex, piece);
                    System.out.println("Downloaded piece: " + pieceIndex);
                } finally {
                    bufferLock.unlock();
                }
            } catch (Exception e) {
                System.out.println("Error downloading piece: " + e.getMessage());
                pieceQueue.add(pieceIndex);
            }
        }
    }

    static List<String> getPeerList(Torrent torrent) throws URISyntaxException, IOException, InterruptedException {
        String url = torrent.getTrackerURL();
        String infoHash = new String(Utils.hexStringToByteArray(torrent.getInfoHash()),
                StandardCharsets.ISO_8859_1);
        byte[] peerIdBytes = Utils.getRandomBytes(10);
        String peerId = Utils.byteToHexString(peerIdBytes);

        HttpClientService httpClientService = new HttpClientService();
        String requestURL = httpClientService.newRequestURLBuilder(torrent.getTrackerURL())
                .addParam("info_hash", infoHash)
                .addParam("peer_id", peerId)
                .addParam("port", String.valueOf(PORT))
                .addParam("uploaded", "0")
                .addParam("downloaded", "0")
                .addParam("left", String.valueOf(torrent.getLength()))
                .addParam("compact", "1")
                .build();

        HttpResponse<byte[]> response = httpClientService.sendGetRequest(requestURL);
        return getPeerListFromHTTPResponse(response);
    }

    public static List<String> getPeerListFromMagnetInfo(Map<String, String> magnetInfoMap) {
        // parse the magnet URL to extract the xt, dn, and tr parameters
        // perform a GET request to the tracker URL
        String infoHash = new String(Utils.hexStringToByteArray(magnetInfoMap.get("xt").split(":")[2]),
                StandardCharsets.ISO_8859_1);
        byte[] peerIdBytes = Utils.getRandomBytes(10);
        String peerId = Utils.byteToHexString(peerIdBytes);

        HttpClientService httpClientService = new HttpClientService();
        String requestURL = httpClientService.newRequestURLBuilder(magnetInfoMap.get("tr"))
                .addParam("info_hash", infoHash)
                .addParam("dn", magnetInfoMap.get("dn"))
                .addParam("port", String.valueOf(PORT))
                .addParam("downloaded", "0")
                .addParam("uploaded", "0")
                .addParam("left", "1")
                .addParam("compact", "1")
                .addParam("peer_id", peerId)
                .build();
        try {
            HttpResponse<byte[]> response = httpClientService.sendGetRequest(requestURL);
            return getPeerListFromHTTPResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Error getting peer list from tracker: " + e.getMessage());
        }
    }

    public static byte[] createExtensionHandshakeMessage(List<String> extensionList) {
        Map<String, Map<String, Integer>> extensionDict = new HashMap<>();
        Map<String, Integer> m = new HashMap<>();
        for (String extension : extensionList) {
            m.put(extension, 1);
        }
        extensionDict.put("m", m);
        byte[] extensionDictBytes = new Bencode(true).encode(extensionDict);
        // create byte array for the extension handshake message with a 4 byte length prefix, 1 byte message ID, 1 byte extension messageid, and the extension dictionary
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + extensionDictBytes.length);
        buffer.putInt(1 + 1 + extensionDictBytes.length);
        buffer.put((byte) 20);
        buffer.put((byte) 0);
        buffer.put(extensionDictBytes);
        System.out.println("Extension handshake message created");
        return buffer.array();
    }

    public static Map<String, Object> parseExtensionHandshakeResponse(byte[] extensionHandshakeResponse) {
        byte[] extensionDictBytes = Arrays.copyOfRange(extensionHandshakeResponse, 2, extensionHandshakeResponse.length);
        Map<String, Object> extensionDict = new Bencode(false).decode(extensionDictBytes, Type.DICTIONARY);
        Map<String, Object> m = (Map<String, Object>) extensionDict.get("m");
        return m;
    }

    public static byte[] createMetadataRequestMessage(int messageType, int pieceIndex, long extensionId) {
        Map<String, Integer> metadataRequestDict = new HashMap<>();
        metadataRequestDict.put("msg_type", messageType);
        metadataRequestDict.put("piece", pieceIndex);
        byte[] metadataRequestDictBytes = new Bencode(true).encode(metadataRequestDict);
        // create byte array for the metadata request message with a 4 byte length prefix, 1 byte message ID, and the metadata request dictionary
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + 1 + metadataRequestDictBytes.length);
        buffer.putInt(2 + metadataRequestDictBytes.length);
        buffer.put((byte) 20);
        buffer.put((byte) extensionId);
        buffer.put(metadataRequestDictBytes);
        System.out.println("Metadata request message created");
        return buffer.array();
    }

    public static Pair<TCPService, Long> performMagnetHandshakeOnPeer(TCPService tcpService, String infohash) {
        TorrentDownloader.performHandshake(infohash, tcpService, true);
        // wait for bitfield message
        byte[] bitfieldMessage = tcpService.waitForMessage();
        if (bitfieldMessage[0] != 5) {
            System.out.println("Expected bitfield message, received different message type: " + bitfieldMessage[4]);
        }
        System.out.println("Received bitfield message");
        // send extension handshake
        List<String> extensionList = new ArrayList<>();
        extensionList.add("ut_metadata");
        extensionList.add("ut_pex");
        byte[] extensionHandshakeMessage = TorrentDownloader.createExtensionHandshakeMessage(extensionList);
        tcpService.sendMessage(extensionHandshakeMessage);
        byte[] extensionHandshakeResponse = tcpService.waitForMessage();
        Map<String, Object> metaDataIDMap = TorrentDownloader.parseExtensionHandshakeResponse(extensionHandshakeResponse);
        System.out.println("Peer Metadata Extension ID: " + metaDataIDMap.get("ut_metadata"));
        return Pair.of(tcpService, (long) metaDataIDMap.get("ut_metadata"));
    }
    public static Pair<TCPService, Long> performMagnetHandshakeOnPeer(Map<String, String> magnetInfo, String peerIP, int peerPort) {
        TCPService tcpService = null;
        try {
            Socket socket = new Socket(peerIP, peerPort);
            tcpService = new TCPService(socket);
            return performMagnetHandshakeOnPeer(tcpService, magnetInfo.get("xt").split(":")[2]);
        } catch (Exception e) {
            System.out.println("Failed to connect to peer: " + peerIP + ":" + peerPort + " - " + e.getMessage());
        }
        return null;
    }

    public static Pair<TCPService, Long> performMagnetHandshake(String magnetURL) {
        Map<String, String> magnetInfo = TorrentUtils.getParamsFromMagnetURL(magnetURL);
        List<String> peerList = TorrentDownloader.getPeerListFromMagnetInfo(magnetInfo);
        for (String peer : peerList) {
            String peerIP = peer.split(":")[0];
            int peerPort = Integer.parseInt(peer.split(":")[1]);
            Pair<TCPService, Long> handshakeResult = TorrentDownloader.performMagnetHandshakeOnPeer(magnetInfo, peerIP, peerPort);
            return handshakeResult;
        }
        return null;
    }

    public static Map<String, Object> getMetadataFromMessage(byte[] metadataResponse) {
        byte[] payloadBytes = Arrays.copyOfRange(metadataResponse, 2, metadataResponse.length);
        Map<String, Object> metadataDict = new Bencode(false).decode(payloadBytes, Type.DICTIONARY);
        int metadataPieceLength = ((Number) metadataDict.get("total_size")).intValue();
        byte[] metadataPieceBytes = Arrays.copyOfRange(payloadBytes, payloadBytes.length - metadataPieceLength, payloadBytes.length);
        Map<String, Object> metadataPieceDict = new Bencode(true).decode(metadataPieceBytes, Type.DICTIONARY);
        return metadataPieceDict;
    }
}