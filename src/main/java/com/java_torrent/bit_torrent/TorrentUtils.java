package com.java_torrent.bit_torrent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TorrentUtils {

    // Public HTTP trackers used as fallback when a magnet link has no trackers
    private static final List<String> FALLBACK_TRACKERS = List.of(
            "http://bt1.archive.org:6969/announce",
            "http://bt2.archive.org:6969/announce",
            "https://torrent.ubuntu.com/announce",
            "http://tracker.opentrackr.org:1337/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce"
    );

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public static List<String> splitPieceHashes(byte[] pieces, int pieceLength, List<String> pieceHashes) {
        for (int i = 0; i < pieces.length; i += pieceLength) {
            String pieceHashString = Utils.byteToHexString(Arrays.copyOfRange(pieces, i, i + pieceLength));
            pieceHashes.add(pieceHashString);
        }
        return pieceHashes;
    }

    static Torrent getTorrentFromPath(String torrentFilePath) {
        byte[] torrentFileBytes = Utils.readTorrentFile(torrentFilePath);
        Torrent torrent = Torrent.fromBytes(torrentFileBytes);
        return torrent;
    }

    public static Torrent getTorrentFromBytes(byte[] fileBytes) {
        return Torrent.fromBytes(fileBytes);
    }

    /**
     * Extracts the hex-encoded info hash from parsed magnet params.
     * Supports both hex (40 chars) and base32 (32 chars) btih encodings.
     */
    public static String getInfoHashFromMagnetParams(Map<String, String> params) {
        String xt = params.get("xt");
        if (xt == null) {
            throw new RuntimeException("Magnet URL has no xt (exact topic) parameter");
        }
        // Expected form: urn:btih:<hash>
        String[] segments = xt.split(":");
        String hash = segments[segments.length - 1];
        if (hash.length() == 40 && hash.matches("[0-9a-fA-F]+")) {
            return hash.toLowerCase();
        }
        if (hash.length() == 32) {
            return base32ToHex(hash);
        }
        throw new RuntimeException("Unsupported info hash format in magnet URL: " + hash);
    }

    /** Decodes a base32 string (RFC 4648, no padding) to lowercase hex. */
    public static String base32ToHex(String base32) {
        String upper = base32.toUpperCase(Locale.ROOT);
        int bitBuffer = 0;
        int bitCount = 0;
        StringBuilder hex = new StringBuilder();
        for (char c : upper.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                throw new RuntimeException("Invalid base32 character: " + c);
            }
            bitBuffer = (bitBuffer << 5) | val;
            bitCount += 5;
            if (bitCount >= 8) {
                bitCount -= 8;
                int b = (bitBuffer >> bitCount) & 0xFF;
                hex.append(String.format("%02x", b));
            }
        }
        return hex.toString();
    }

    public static Map<String, String> getParamsFromMagnetURL(String magnetURL) {
        Map<String, String> map = new HashMap<>();
        List<String> trackers = new ArrayList<>();
        List<String> directPeers = new ArrayList<>();
        String[] parts = magnetURL.split("\\?", 2);
        if (parts.length != 2 || !parts[0].startsWith("magnet:")) {
            throw new RuntimeException("Invalid magnet URL: " + magnetURL);
        }
        String[] params = parts[1].split("&");
        for (String param : params) {
            if (param.isEmpty()) {
                continue;
            }
            String[] keyValue = param.split("=", 2);
            if (keyValue.length != 2) {
                continue; // tolerate valueless params instead of failing the whole parse
            }
            String key = keyValue[0];
            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            if (key.equals("tr")) {
                trackers.add(value);
            } else if (key.equals("x.pe")) {
                directPeers.add(value);
            } else {
                map.put(key, value);
            }
        }
        // Store all supported trackers (HTTP/HTTPS first, then UDP, skip wss://)
        List<String> supported = new ArrayList<>();
        // HTTP/HTTPS trackers first (work on all hosting platforms)
        trackers.stream()
                .filter(t -> t.startsWith("http://") || t.startsWith("https://"))
                .forEach(supported::add);
        // Then UDP trackers
        trackers.stream()
                .filter(t -> t.startsWith("udp://"))
                .forEach(supported::add);

        // If no trackers and no direct peers provided, add fallback public trackers
        if (supported.isEmpty() && directPeers.isEmpty()) {
            FALLBACK_TRACKERS.stream()
                    .filter(t -> t.startsWith("http://") || t.startsWith("https://"))
                    .forEach(supported::add);
            FALLBACK_TRACKERS.stream()
                    .filter(t -> t.startsWith("udp://"))
                    .forEach(supported::add);
        }

        if (!supported.isEmpty()) {
            map.put("tr", supported.get(0));
        }
        map.put("trackers", String.join(",", supported));
        if (!directPeers.isEmpty()) {
            map.put("x.pe", String.join(",", directPeers));
        }
        return map;
    }

    public static List<String> getTrackerList(Map<String, String> magnetInfoMap) {
        String trackers = magnetInfoMap.get("trackers");
        if (trackers == null || trackers.isEmpty()) {
            String single = magnetInfoMap.get("tr");
            return single != null ? List.of(single) : List.of();
        }
        return List.of(trackers.split(","));
    }

    /** Direct peer addresses (ip:port) from x.pe magnet params, if any. */
    public static List<String> getDirectPeers(Map<String, String> magnetInfoMap) {
        String peers = magnetInfoMap.get("x.pe");
        if (peers == null || peers.isEmpty()) {
            return List.of();
        }
        return List.of(peers.split(","));
    }
}
