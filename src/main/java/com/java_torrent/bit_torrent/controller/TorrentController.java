package com.java_torrent.bit_torrent.controller;

import com.java_torrent.bit_torrent.Utils;
import com.java_torrent.bit_torrent.dto.*;
import com.java_torrent.bit_torrent.service.TorrentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/api/torrent")
@CrossOrigin(origins = "*")
public class TorrentController {

    @Autowired
    private TorrentService torrentService;

    @PostMapping("/info")
    public ResponseEntity<TorrentInfoResponse> getTorrentInfo(@RequestParam("file") MultipartFile file) {
        try {
            TorrentInfoResponse response = torrentService.getTorrentInfo(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TorrentInfoResponse(e.getMessage()));
        }
    }

    @PostMapping("/peers")
    public ResponseEntity<PeerListResponse> getPeers(@RequestParam("file") MultipartFile file) {
        try {
            PeerListResponse response = torrentService.getPeers(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new PeerListResponse(e.getMessage()));
        }
    }

    @PostMapping("/download")
    public ResponseEntity<DownloadResponse> downloadTorrent(@RequestParam("file") MultipartFile file) {
        try {
            DownloadResponse response = torrentService.downloadTorrent(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DownloadResponse(e.getMessage()));
        }
    }

    @PostMapping("/download/start")
    public ResponseEntity<DownloadStatusResponse> startTorrentDownload(@RequestParam("file") MultipartFile file) {
        try {
            DownloadStatusResponse response = torrentService.startTorrentDownload(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DownloadStatusResponse(e.getMessage()));
        }
    }

    @PostMapping("/magnet/download/start")
    public ResponseEntity<DownloadStatusResponse> startMagnetDownload(@RequestBody MagnetUrlRequest request) {
        try {
            DownloadStatusResponse response = torrentService.startMagnetDownload(request.getMagnetUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DownloadStatusResponse(e.getMessage()));
        }
    }

    @GetMapping("/downloads")
    public ResponseEntity<java.util.List<DownloadStatusResponse>> listDownloads() {
        return ResponseEntity.ok(torrentService.listDownloads());
    }

    @GetMapping("/download/status/{downloadId}")
    public ResponseEntity<DownloadStatusResponse> getDownloadStatus(@PathVariable String downloadId) {
        DownloadStatusResponse response = torrentService.getDownloadStatus(downloadId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/magnet/parse")
    public ResponseEntity<MagnetParseResponse> parseMagnetUrl(@RequestBody MagnetUrlRequest request) {
        try {
            MagnetParseResponse response = torrentService.parseMagnetUrl(request.getMagnetUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MagnetParseResponse(e.getMessage()));
        }
    }

    @PostMapping("/magnet/info")
    public ResponseEntity<TorrentInfoResponse> getMagnetInfo(@RequestBody MagnetUrlRequest request) {
        try {
            TorrentInfoResponse response = torrentService.getMagnetInfo(request.getMagnetUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new TorrentInfoResponse(e.getMessage()));
        }
    }

    @PostMapping("/magnet/download")
    public ResponseEntity<DownloadResponse> downloadMagnet(@RequestBody MagnetUrlRequest request) {
        try {
            DownloadResponse response = torrentService.downloadMagnet(request.getMagnetUrl());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DownloadResponse(e.getMessage()));
        }
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> getDownloadedFile(@RequestParam("path") String filePath) {
        try {
            // Only serve files from inside the downloads directory
            File downloadsDir = new File("downloads").getCanonicalFile();
            File requested = new File(filePath).getCanonicalFile();
            if (!requested.toPath().startsWith(downloadsDir.toPath())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!requested.exists() || !requested.isFile()) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(requested);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + Utils.headerSafe(requested.getName()) + "\"")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/decode")
    public ResponseEntity<DecodeResponse> decodeBencode(@RequestBody DecodeRequest request) {
        try {
            DecodeResponse response = torrentService.decodeBencode(request.getBencodedValue());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DecodeResponse(request.getBencodedValue(), e.getMessage()));
        }
    }
}
