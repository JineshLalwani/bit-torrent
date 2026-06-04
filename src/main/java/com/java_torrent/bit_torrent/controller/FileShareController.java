package com.java_torrent.bit_torrent.controller;

import com.java_torrent.bit_torrent.dto.FileListResponse;
import com.java_torrent.bit_torrent.dto.FileUploadResponse;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import com.java_torrent.bit_torrent.service.FileShareService;
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
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileShareController {

    @Autowired
    private FileShareService fileShareService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            FileUploadResponse response = fileShareService.uploadFile(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new FileUploadResponse(e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<FileListResponse> listFiles() {
        try {
            FileListResponse response = fileShareService.listFiles();
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
                        "attachment; filename=\"" + metadata.getFileName() + "\"")
                .contentLength(metadata.getFileSize())
                .body(resource);
    }
}
