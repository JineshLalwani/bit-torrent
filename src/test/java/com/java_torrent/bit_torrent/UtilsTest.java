package com.java_torrent.bit_torrent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void hexRoundTrip() {
        byte[] bytes = new byte[]{0x00, 0x01, (byte) 0xab, (byte) 0xff, 0x7f, (byte) 0x80};
        String hex = Utils.byteToHexString(bytes);
        assertEquals("0001abff7f80", hex);
        assertArrayEquals(bytes, Utils.hexStringToByteArray(hex));
    }

    @Test
    void sha1KnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d",
                Utils.calculateSHA1("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void sanitizeFileNameStripsPathTraversal() {
        assertEquals("evil.sh", Utils.sanitizeFileName("../../evil.sh"));
        assertEquals("evil.sh", Utils.sanitizeFileName("..\\..\\evil.sh"));
        assertEquals("unnamed", Utils.sanitizeFileName(".."));
        assertEquals("unnamed", Utils.sanitizeFileName(null));
        assertEquals("unnamed", Utils.sanitizeFileName("  "));
        assertEquals("report (final) [v2].pdf", Utils.sanitizeFileName("report (final) [v2].pdf"));
    }

    @Test
    void headerSafeRemovesInjectionCharacters() {
        assertEquals("a_b_c_", Utils.headerSafe("a\"b\rc\n"));
    }
}
