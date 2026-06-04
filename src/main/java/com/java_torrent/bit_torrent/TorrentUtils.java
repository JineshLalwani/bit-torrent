package com.java_torrent.bit_torrent;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TorrentUtils {
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
                map.put(keyValue[0], keyValue[1]);
            }
        }
        // Pick the first supported tracker: HTTP/HTTPS or UDP, skip wss://
        String selectedTracker = trackers.stream()
                .filter(t -> t.startsWith("http://") || t.startsWith("https://") || t.startsWith("udp://"))
                .findFirst()
                .orElse(trackers.isEmpty() ? null : trackers.get(0));
        if (selectedTracker != null) {
            map.put("tr", selectedTracker);
        }
        return map;
    }
}