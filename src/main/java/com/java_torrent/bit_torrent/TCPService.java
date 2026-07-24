package com.java_torrent.bit_torrent;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TCPService implements Closeable {

    // Upper bound on a single peer message; largest legitimate message is a
    // piece block (16 KiB) or a bitfield/metadata piece, all well under 1 MiB.
    private static final int MAX_MESSAGE_LENGTH = 1024 * 1024;

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public TCPService(Socket socket) {
        try {
            this.socket = socket;
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads the next non-keep-alive peer message and returns its payload
     * (message id at index 0). Keep-alive messages (length 0) are skipped.
     */
    public byte[] waitForMessage() {
        try {
            while (true) {
                byte[] lengthBuffer = readFully(4);
                int messageLength = ByteBuffer.wrap(lengthBuffer).getInt();
                if (messageLength == 0) {
                    continue; // keep-alive
                }
                if (messageLength < 0 || messageLength > MAX_MESSAGE_LENGTH) {
                    throw new IOException("Invalid message length from peer: " + messageLength);
                }
                return readFully(messageLength);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int bytesRead = in.readNBytes(buffer, 0, length);
        if (bytesRead != length) {
            throw new IOException("Connection closed: expected " + length + " bytes, got " + bytesRead);
        }
        return buffer;
    }

    public static byte[] createRequestPayload(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return buffer.array();
    }

    public void sendMessage(byte messageId, byte[] payload) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 1 + payload.length);
        buffer.putInt(1 + payload.length);
        buffer.put(messageId);
        buffer.put(payload);
        out.write(buffer.array());
        out.flush();
    }

    public void sendMessage(byte[] message) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] waitForHandshakeResponse() {
        try {
            return readFully(68);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
