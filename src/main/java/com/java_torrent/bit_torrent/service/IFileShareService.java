package com.java_torrent.bit_torrent.service;

import com.java_torrent.bit_torrent.dto.FileListResponse;
import com.java_torrent.bit_torrent.dto.FileUploadResponse;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import org.springframework.web.multipart.MultipartFile;

public interface IFileShareService {
    FileUploadResponse uploadFile(MultipartFile file) throws Exception;
    FileListResponse listFiles();
    SharedFileMetadata getFileMetadata(String fileId);
}
