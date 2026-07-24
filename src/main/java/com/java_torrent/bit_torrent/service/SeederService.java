package com.java_torrent.bit_torrent.service;

import com.java_torrent.bit_torrent.TCPService;
import com.java_torrent.bit_torrent.Utils;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Seeds files uploaded to the File Hub over the BitTorrent peer wire protocol.
 * Any client that knows this node's address (via the built-in tracker or an
 * x.pe magnet param) can handshake with a shared file's info hash and download
 * its pieces.
 */
@Service
public class SeederService {

    private static final byte UNCHOKE_MESSAGE_ID = 1;
    private static final byte INTERESTED_MESSAGE_ID = 2;
    private static final byte BITFIELD_MESSAGE_ID = 5;
    private static final byte REQUEST_MESSAGE_ID = 6;
    private static final byte PIECE_MESSAGE_ID = 7;
    private static final int MAX_BLOCK_LENGTH = 128 * 1024;
    private static final int IDLE_TIMEOUT_MS = 120_000;
    private static final int MAX_CONCURRENT_PEERS = 50;

    private final FileShareService fileShareService;
    private final int seederPort;

    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final ExecutorService peerPool = Executors.newFixedThreadPool(MAX_CONCURRENT_PEERS, runnable -> {
        Thread thread = new Thread(runnable, "seeder-peer");
        thread.setDaemon(true);
        return thread;
    });

    public SeederService(FileShareService fileShareService,
                         @Value("${seeder.port:6881}") int seederPort) {
        this.fileShareService = fileShareService;
        this.seederPort = seederPort;
    }

    public int getPort() {
        return seederPort;
    }

    @PostConstruct
    public void start() {
        Thread acceptThread = new Thread(this::acceptLoop, "seeder-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        try (ServerSocket server = new ServerSocket(seederPort)) {
            this.serverSocket = server;
            this.running = true;
            System.out.println("Seeder listening on port " + seederPort);
            while (running) {
                Socket peerSocket = server.accept();
                peerPool.submit(() -> servePeer(peerSocket));
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Seeder stopped: " + e.getMessage());
            }
        }
        running = false;
    }

    private void servePeer(Socket socket) {
        String remote = socket.getRemoteSocketAddress().toString();
        try (socket) {
            socket.setSoTimeout(IDLE_TIMEOUT_MS);
            TCPService tcpService = new TCPService(socket);

            // Incoming handshake: 68 bytes, info hash at offset 28
            byte[] handshake = tcpService.waitForHandshakeResponse();
            byte[] infoHashBytes = Arrays.copyOfRange(handshake, 28, 48);
            String infoHashHex = Utils.byteToHexString(infoHashBytes);

            SharedFileMetadata metadata = fileShareService.getFileByInfoHash(infoHashHex);
            if (metadata == null) {
                System.out.println("Seeder: unknown info hash from " + remote + ": " + infoHashHex);
                return;
            }

            tcpService.sendMessage(buildHandshakeResponse(infoHashBytes));
            tcpService.sendMessage(BITFIELD_MESSAGE_ID, buildFullBitfield(metadata.getPieceCount()));

            try (RandomAccessFile file = new RandomAccessFile(metadata.getStoragePath(), "r")) {
                messageLoop(tcpService, metadata, file, remote);
            }
        } catch (Exception e) {
            System.out.println("Seeder: peer " + remote + " disconnected: " + e.getMessage());
        }
    }

    private void messageLoop(TCPService tcpService, SharedFileMetadata metadata,
                             RandomAccessFile file, String remote) throws IOException {
        while (true) {
            byte[] message = tcpService.waitForMessage();
            switch (message[0]) {
                case INTERESTED_MESSAGE_ID -> tcpService.sendMessage(new byte[]{0, 0, 0, 1, UNCHOKE_MESSAGE_ID});
                case REQUEST_MESSAGE_ID -> handleBlockRequest(tcpService, metadata, file, message, remote);
                default -> { /* choke/unchoke/have/cancel etc. — nothing to do */ }
            }
        }
    }

    private void handleBlockRequest(TCPService tcpService, SharedFileMetadata metadata,
                                    RandomAccessFile file, byte[] message, String remote) throws IOException {
        if (message.length < 13) {
            throw new IOException("Malformed request message");
        }
        ByteBuffer payload = ByteBuffer.wrap(message, 1, 12);
        int index = payload.getInt();
        int begin = payload.getInt();
        int length = payload.getInt();

        long pieceOffset = (long) index * metadata.getPieceLength();
        long blockStart = pieceOffset + begin;
        if (index < 0 || index >= metadata.getPieceCount()
                || begin < 0 || length <= 0 || length > MAX_BLOCK_LENGTH
                || blockStart + length > metadata.getFileSize()) {
            throw new IOException("Invalid block request: piece " + index + " begin " + begin + " length " + length);
        }

        byte[] block = new byte[length];
        synchronized (file) {
            file.seek(blockStart);
            file.readFully(block);
        }

        ByteBuffer response = ByteBuffer.allocate(8 + length);
        response.putInt(index);
        response.putInt(begin);
        response.put(block);
        tcpService.sendMessage(PIECE_MESSAGE_ID, response.array());
        System.out.println("Seeder: served piece " + index + " block @" + begin + " (" + length + "B) to " + remote);
    }

    private static byte[] buildHandshakeResponse(byte[] infoHashBytes) throws IOException {
        ByteArrayOutputStream handshake = new ByteArrayOutputStream();
        handshake.write(19);
        handshake.write("BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1));
        handshake.write(new byte[8]);
        handshake.write(infoHashBytes);
        byte[] peerId = new byte[20];
        byte[] prefix = "-BT0001-".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, peerId, 0, prefix.length);
        System.arraycopy(Utils.getRandomBytes(20 - prefix.length), 0, peerId, prefix.length, 20 - prefix.length);
        handshake.write(peerId);
        return handshake.toByteArray();
    }

    /** Bitfield with every piece bit set (MSB-first within each byte). */
    private static byte[] buildFullBitfield(int pieceCount) {
        byte[] bitfield = new byte[(pieceCount + 7) / 8];
        for (int i = 0; i < pieceCount; i++) {
            bitfield[i / 8] |= (byte) (1 << (7 - (i % 8)));
        }
        return bitfield;
    }

    @PreDestroy
    public void stop() {
        running = false;
        ServerSocket server = this.serverSocket;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) {}
        }
        peerPool.shutdownNow();
    }
}
