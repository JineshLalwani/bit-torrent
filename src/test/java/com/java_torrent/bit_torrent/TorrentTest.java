package com.java_torrent.bit_torrent;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TorrentTest {

    @Test
    void parsesSampleTorrent() throws Exception {
        byte[] bytes;
        try (InputStream in = getClass().getResourceAsStream("/sample.torrent")) {
            assertNotNull(in, "sample.torrent test resource missing");
            bytes = in.readAllBytes();
        }
        Torrent torrent = Torrent.fromBytes(bytes);

        assertNotNull(torrent.getTrackerURL());
        assertTrue(torrent.getTrackerURL().startsWith("http"));
        assertTrue(torrent.getLength() > 0);
        assertEquals(40, torrent.getInfoHash().length());
        assertTrue(torrent.getPieceLength() > 0);
        assertFalse(torrent.getPieces().isEmpty());
        assertNotNull(torrent.getName());

        // Every piece hash is 20 bytes hex-encoded
        for (String piece : torrent.getPieces()) {
            assertEquals(40, piece.length());
        }

        // Last piece length never exceeds the nominal piece length and covers the file
        long total = 0;
        for (int i = 0; i < torrent.getPieces().size(); i++) {
            long len = torrent.getPieceLength(i);
            assertTrue(len > 0 && len <= torrent.getPieceLength());
            total += len;
        }
        assertEquals(torrent.getLength(), total);
    }
}
