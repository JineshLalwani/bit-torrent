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

    public static Map<String, String> getParamsFromMagnetURL(String magnetURL) {
        Map<String, String> map = new HashMap<>();
        List<String> trackers = new ArrayList<>();
        String[] parts = magnetURL.split("\\?");
        if (parts.length != 2) {
            throw new RuntimeException("Invalid magnet URL: " + magnetURL);
        }
        String[] params = parts[1].split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length != 2) {
                throw new RuntimeException("Invalid parameter: " + param);
            }
            if (keyValue[0].equals("tr")) {
                trackers.add(URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
            } else {
                String key = keyValue[0];
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
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

        // If no trackers provided, add fallback public trackers
        if (supported.isEmpty()) {
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
}