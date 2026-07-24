package com.java_torrent.bit_torrent;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class Codec {

    private static final Gson gson = new Gson();

    public static String decodeToJson(String bencodedString) throws Exception {
        Object decoded = decodeBencodedBytes(bencodedString.getBytes(StandardCharsets.ISO_8859_1));
        return gson.toJson(decoded);
    }

    public static void decodeAndPrintBencodedString(String bencodedString) {
        try {
            System.out.println(decodeToJson(bencodedString));
        } catch (Exception e) {
            System.out.println("Problem encountered during decoding: " + e.getMessage());
        }
    }

    public static Object decodeBencodedBytes(byte[] bencodedBytes) throws Exception {
        if (bencodedBytes == null || bencodedBytes.length == 0) {
            throw new Exception("Empty bencoded value");
        }
        Bencode bencode = new Bencode();
        if (Character.isDigit((char) bencodedBytes[0])) {
            String decodedString = bencode.decode(bencodedBytes, Type.STRING);
            return decodedString;
        } else if (bencodedBytes[0] == 'i') {
            Long decodedInt = bencode.decode(bencodedBytes, Type.NUMBER);
            return decodedInt;
        } else if (bencodedBytes[0] == 'l') {
            List<Object> decodedList = bencode.decode(bencodedBytes, Type.LIST);
            return decodedList;
        } else if (bencodedBytes[0] == 'd') {
            Map<String, Object> decodedDict = bencode.decode(bencodedBytes, Type.DICTIONARY);
            return decodedDict;
        } else {
            throw new Exception("Unsupported bencode type: " + (char) bencodedBytes[0]);
        }
    }
}
