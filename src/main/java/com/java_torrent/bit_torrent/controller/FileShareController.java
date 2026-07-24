package com.java_torrent.bit_torrent.controller;

import com.java_torrent.bit_torrent.Utils;
import com.java_torrent.bit_torrent.dto.FileListResponse;
import com.java_torrent.bit_torrent.dto.FileUploadResponse;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import com.java_torrent.bit_torrent.service.FileShareService;
import com.java_torrent.bit_torrent.service.SeederService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileShareController {

    @Autowired
    private FileShareService fileShareService;

    @Autowired
    private SeederService seederService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file,
                                                         HttpServletRequest request) {
        try {
            FileUploadResponse response = fileShareService.uploadFile(file);
            response.setMagnetLink(buildMagnetLink(response.getId(), response.getFileName(),
                    response.getInfoHash(), request));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<FileListResponse> listFiles(HttpServletRequest request) {
        try {
            FileListResponse response = fileShareService.listFiles();
            if (response.getFiles() != null) {
                for (FileUploadResponse f : response.getFiles()) {
                    f.setMagnetLink(buildMagnetLink(f.getId(), f.getFileName(), f.getInfoHash(), request));
                }
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileListResponse(e.getMessage()));
        }
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) {
        SharedFileMetadata metadata = fileShareService.getFileMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(metadata.getStoragePath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + Utils.headerSafe(metadata.getFileName()) + "\"")
                .contentLength(metadata.getFileSize())
                .body(resource);
    }

    /** Serves a generated .torrent (metainfo) file for a shared file. */
    @GetMapping("/torrent/{fileId}")
    public ResponseEntity<byte[]> downloadTorrentFile(@PathVariable String fileId, HttpServletRequest request) {
        SharedFileMetadata metadata = fileShareService.getFileMetadata(fileId);
        if (metadata == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] torrentBytes = fileShareService.generateTorrentFile(fileId, announceUrl(request));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + Utils.headerSafe(metadata.getFileName()) + ".torrent\"")
                .body(torrentBytes);
    }

    private static String baseUrl(HttpServletRequest request) {
        String host = request.getServerName();
        int port = request.getServerPort();
        String scheme = request.getScheme();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String announceUrl(HttpServletRequest request) {
        return baseUrl(request) + "/api/tracker/announce";
    }

    /**
     * Builds a magnet link that works end-to-end with this app: xs points at
     * the generated .torrent (metadata), tr at the built-in tracker and x.pe
     * directly at the seeder.
     */
    private String buildMagnetLink(String fileId, String fileName, String infoHash, HttpServletRequest request) {
        String base = baseUrl(request);
        String xs = base + "/api/files/torrent/" + fileId;
        String tr = announceUrl(request);
        return "magnet:?xt=urn:btih:" + infoHash
                + "&dn=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                + "&xs=" + URLEncoder.encode(xs, StandardCharsets.UTF_8)
                + "&tr=" + URLEncoder.encode(tr, StandardCharsets.UTF_8)
                + "&x.pe=" + URLEncoder.encode(request.getServerName() + ":" + seederService.getPort(), StandardCharsets.UTF_8);
    }
}
