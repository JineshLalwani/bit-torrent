package com.java_torrent.bit_torrent.controller;

import com.dampcake.bencode.Bencode;
import com.java_torrent.bit_torrent.Utils;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import com.java_torrent.bit_torrent.service.FileShareService;
import com.java_torrent.bit_torrent.service.SeederService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal HTTP tracker for files shared through the File Hub. It always
 * answers with a single peer: this server's own seeder. That makes the
 * generated .torrent files and magnet links downloadable by any BitTorrent
 * client that can reach this host.
 */
@RestController
@RequestMapping("/api/tracker")
@CrossOrigin(origins = "*")
public class TrackerController {

    private final FileShareService fileShareService;
    private final SeederService seederService;
    private final Bencode bencode = new Bencode(true);

    public TrackerController(FileShareService fileShareService, SeederService seederService) {
        this.fileShareService = fileShareService;
        this.seederService = seederService;
    }

    @GetMapping(value = "/announce", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> announce(HttpServletRequest request) {
        // info_hash is raw binary percent-encoded; the servlet container would
        // decode it as UTF-8 and corrupt it, so parse the query string manually
        String infoHashHex = extractInfoHashHex(request.getQueryString());
        if (infoHashHex == null) {
            return ResponseEntity.ok(failure("missing info_hash"));
        }

        SharedFileMetadata metadata = fileShareService.getFileByInfoHash(infoHashHex);
        if (metadata == null) {
            return ResponseEntity.ok(failure("unknown info_hash"));
        }

        byte[] compactPeer = buildCompactPeer(request);
        if (compactPeer == null) {
            return ResponseEntity.ok(failure("tracker has no IPv4 address for the seeder"));
        }

        Map<String, Object> response = new TreeMap<>();
        response.put("complete", 1L);
        response.put("incomplete", 0L);
        response.put("interval", 1800L);
        response.put("peers", ByteBuffer.wrap(compactPeer));
        return ResponseEntity.ok(bencode.encode(response));
    }

    private static String extractInfoHashHex(String queryString) {
        if (queryString == null) {
            return null;
        }
        for (String param : queryString.split("&")) {
            if (param.startsWith("info_hash=")) {
                String encoded = param.substring("info_hash=".length());
                String decoded = URLDecoder.decode(encoded, StandardCharsets.ISO_8859_1);
                byte[] bytes = decoded.getBytes(StandardCharsets.ISO_8859_1);
                if (bytes.length == 20) {
                    return Utils.byteToHexString(bytes);
                }
            }
        }
        return null;
    }

    /** 6-byte compact peer entry pointing at this server's seeder. */
    private byte[] buildCompactPeer(HttpServletRequest request) {
        try {
            InetAddress address = InetAddress.getByName(request.getLocalAddr());
            byte[] ip = address.getAddress();
            if (ip.length != 4) {
                // IPv6 request (e.g. ::1) — fall back to IPv4 loopback, which is
                // correct for the local-demo case where this can happen
                ip = new byte[]{127, 0, 0, 1};
            }
            int port = seederService.getPort();
            return new byte[]{ip[0], ip[1], ip[2], ip[3], (byte) ((port >> 8) & 0xFF), (byte) (port & 0xFF)};
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] failure(String reason) {
        Map<String, Object> response = new TreeMap<>();
        response.put("failure reason", ByteBuffer.wrap(reason.getBytes(StandardCharsets.UTF_8)));
        return bencode.encode(response);
    }
}
