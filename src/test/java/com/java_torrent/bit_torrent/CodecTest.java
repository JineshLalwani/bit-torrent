package com.java_torrent.bit_torrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodecTest {

    @Test
    void decodesString() throws Exception {
        assertEquals("\"hello\"", Codec.decodeToJson("5:hello"));
    }

    @Test
    void decodesInteger() throws Exception {
        assertEquals("52", Codec.decodeToJson("i52e"));
        assertEquals("-52", Codec.decodeToJson("i-52e"));
    }

    @Test
    void decodesList() throws Exception {
        assertEquals("[\"hello\",52]", Codec.decodeToJson("l5:helloi52ee"));
    }

    @Test
    void decodesDictionary() throws Exception {
        assertEquals("{\"foo\":\"bar\",\"hello\":52}", Codec.decodeToJson("d3:foo3:bar5:helloi52ee"));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(Exception.class, () -> Codec.decodeToJson("x123"));
        assertThrows(Exception.class, () -> Codec.decodeToJson(""));
    }
}
