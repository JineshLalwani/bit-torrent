package com.java_torrent.bit_torrent;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UdpTrackerService {

    private static final long CONNECT_MAGIC = 0x41727101980L;
    private static final int ACTION_CONNECT = 0;
    private static final int ACTION_ANNOUNCE = 1;
    private static final int TIMEOUT_MS = 3000;

    /**
     * Fetches a peer list from a UDP tracker.
     *
     * @param trackerUrl  UDP tracker URL (e.g. udp://tracker.opentrackr.org:1337)
     * @param infoHash    Hex-encoded info hash
     * @param peerId      20-byte peer ID
     * @param left        Bytes remaining to download
     * @return List of "ip:port" peer strings
     */
    public static List<String> getPeerList(String trackerUrl, byte[] infoHash, byte[] peerId, long left) throws Exception {
        // Parse host and port from udp://host:port/announce
        URI uri = new URI(trackerUrl);
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) port = 80;

        InetAddress address = InetAddress.getByName(host);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);

            // Step 1: Connect
            long connectionId = sendConnect(socket, address, port);

            // Step 2: Announce
            return sendAnnounce(socket, address, port, connectionId, infoHash, peerId, left);
        }
    }

    private static long sendConnect(DatagramSocket socket, InetAddress address, int port) throws Exception {
        Random random = new Random();
        int transactionId = random.nextInt();

        // Connect request: 16 bytes
        // 8 bytes: connection_id = 0x41727101980
        // 4 bytes: action = 0 (connect)
        // 4 bytes: transaction_id
        ByteBuffer sendBuf = ByteBuffer.allocate(16);
        sendBuf.putLong(CONNECT_MAGIC);
        sendBuf.putInt(ACTION_CONNECT);
        sendBuf.putInt(transactionId);

        byte[] sendData = sendBuf.array();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);

        socket.send(sendPacket);

        byte[] recvData = new byte[16];
        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

        try {
            socket.receive(recvPacket);
        } catch (SocketTimeoutException e) {
            throw new RuntimeException("UDP tracker connect timed out (UDP may be blocked)");
        }

        if (recvPacket.getLength() < 16) {
            throw new RuntimeException("UDP tracker connect failed: short response (" + recvPacket.getLength() + " bytes)");
        }

        ByteBuffer recvBuf = ByteBuffer.wrap(recvData);
        int action = recvBuf.getInt();
        int recvTransactionId = recvBuf.getInt();
        long connectionId = recvBuf.getLong();

        if (action != ACTION_CONNECT) {
            throw new RuntimeException("UDP tracker connect failed: unexpected action " + action);
        }
        if (recvTransactionId != transactionId) {
            throw new RuntimeException("UDP tracker connect failed: transaction ID mismatch");
        }

        return connectionId;
    }

    private static List<String> sendAnnounce(DatagramSocket socket, InetAddress address, int port,
                                              long connectionId, byte[] infoHash, byte[] peerId, long left) throws Exception {
        Random random = new Random();
        int transactionId = random.nextInt();

        // Announce request: 98 bytes
        ByteBuffer sendBuf = ByteBuffer.allocate(98);
        sendBuf.putLong(connectionId);          // 8: connection_id
        sendBuf.putInt(ACTION_ANNOUNCE);         // 4: action = 1 (announce)
        sendBuf.putInt(transactionId);           // 4: transaction_id
        sendBuf.put(infoHash);                   // 20: info_hash
        sendBuf.put(peerId);                     // 20: peer_id
        sendBuf.putLong(0);                      // 8: downloaded
        sendBuf.putLong(left);                   // 8: left
        sendBuf.putLong(0);                      // 8: uploaded
        sendBuf.putInt(0);                       // 4: event (0 = none)
        sendBuf.putInt(0);                       // 4: IP address (0 = default)
        sendBuf.putInt(random.nextInt());         // 4: key (random)
        sendBuf.putInt(-1);                      // 4: num_want (-1 = default)
        sendBuf.putShort((short) 6881);          // 2: port

        byte[] sendData = sendBuf.array();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);

        socket.send(sendPacket);

        byte[] recvData = new byte[65535];
        DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);

        try {
            socket.receive(recvPacket);
        } catch (SocketTimeoutException e) {
            throw new RuntimeException("UDP tracker announce timed out (UDP may be blocked)");
        }

        ByteBuffer recvBuf = ByteBuffer.wrap(recvData, 0, recvPacket.getLength());
        int action = recvBuf.getInt();
        int recvTransactionId = recvBuf.getInt();

        if (action == 3) {
            byte[] msgBytes = new byte[recvPacket.getLength() - 8];
            recvBuf.get(msgBytes);
            throw new RuntimeException("UDP tracker error: " + new String(msgBytes));
        }

        if (action != ACTION_ANNOUNCE) {
            throw new RuntimeException("UDP tracker announce failed: unexpected action " + action);
        }
        if (recvTransactionId != transactionId) {
            throw new RuntimeException("UDP tracker announce failed: transaction ID mismatch");
        }

        int interval = recvBuf.getInt();
        int leechers = recvBuf.getInt();
        int seeders = recvBuf.getInt();

        List<String> peers = new ArrayList<>();
        while (recvBuf.remaining() >= 6) {
            int ip1 = recvBuf.get() & 0xFF;
            int ip2 = recvBuf.get() & 0xFF;
            int ip3 = recvBuf.get() & 0xFF;
            int ip4 = recvBuf.get() & 0xFF;
            int peerPort = recvBuf.getShort() & 0xFFFF;
            peers.add(ip1 + "." + ip2 + "." + ip3 + "." + ip4 + ":" + peerPort);
        }

        return peers;
    }
}
