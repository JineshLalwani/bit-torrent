package com.java_torrent.bit_torrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TorrentUtilsTest {

    private static final String HEX_HASH = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void parsesMagnetWithHexInfoHashAndTrackers() {
        String magnet = "magnet:?xt=urn:btih:" + HEX_HASH
                + "&dn=test%20file"
                + "&tr=http%3A%2F%2Ftracker.example.com%2Fannounce"
                + "&tr=udp%3A%2F%2Ftracker2.example.com%3A1337%2Fannounce";
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnet);

        assertEquals(HEX_HASH, TorrentUtils.getInfoHashFromMagnetParams(params));
        assertEquals("test file", params.get("dn"));
        // HTTP tracker should be preferred first
        assertEquals("http://tracker.example.com/announce", params.get("tr"));
        assertEquals(List.of("http://tracker.example.com/announce", "udp://tracker2.example.com:1337/announce"),
                TorrentUtils.getTrackerList(params));
    }

    @Test
    void parsesDirectPeersFromMagnet() {
        String magnet = "magnet:?xt=urn:btih:" + HEX_HASH
                + "&x.pe=127.0.0.1%3A6881&x.pe=10.0.0.2%3A51413";
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnet);
        assertEquals(List.of("127.0.0.1:6881", "10.0.0.2:51413"), TorrentUtils.getDirectPeers(params));
        // Direct peers present -> no fallback trackers forced in
        assertEquals(List.of(), TorrentUtils.getTrackerList(params));
    }

    @Test
    void fallsBackToPublicTrackersWhenNoneProvided() {
        String magnet = "magnet:?xt=urn:btih:" + HEX_HASH;
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnet);
        assertFalse(TorrentUtils.getTrackerList(params).isEmpty());
    }

    @Test
    void decodesBase32InfoHash() {
        // 32 base32 chars = exactly 160 bits = a 20-byte info hash
        String base32 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL("magnet:?xt=urn:btih:" + base32);
        assertEquals("0".repeat(40), TorrentUtils.getInfoHashFromMagnetParams(params));
    }

    @Test
    void base32ToHexKnownVector() {
        // base32("abc") == "MFRGG"
        assertEquals("616263", TorrentUtils.base32ToHex("MFRGG"));
    }

    @Test
    void rejectsInvalidMagnetUrl() {
        assertThrows(RuntimeException.class, () -> TorrentUtils.getParamsFromMagnetURL("http://not-a-magnet"));
        assertThrows(RuntimeException.class,
                () -> TorrentUtils.getInfoHashFromMagnetParams(Map.of("dn", "no-xt-here")));
    }

    @Test
    void splitsPieceHashes() {
        byte[] pieces = new byte[40];
        pieces[0] = 0x01;
        pieces[20] = 0x02;
        List<String> hashes = TorrentUtils.splitPieceHashes(pieces, 20, new java.util.ArrayList<>());
        assertEquals(2, hashes.size());
        assertTrue(hashes.get(0).startsWith("01"));
        assertTrue(hashes.get(1).startsWith("02"));
        assertEquals(40, hashes.get(0).length());
    }
}
