package com.java_torrent.bit_torrent.controller;


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
        File file = new File(filePath);
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(resource);
    }

    @PostMapping("/decode")
    public ResponseEntity<DecodeResponse> decodeBencode(@RequestBody DecodeRequest request) {
        try {
            DecodeResponse response = torrentService.decodeBencode(request.getBencodedValue());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DecodeResponse(request.getBencodedValue(),e.getMessage()));
        }
    }
}
