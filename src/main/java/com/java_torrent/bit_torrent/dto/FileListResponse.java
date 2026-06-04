package com.java_torrent.bit_torrent.dto;

import java.util.List;

public class FileListResponse {
    private List<FileUploadResponse> files;
    private String error;

    public FileListResponse() {}

    public FileListResponse(String error) {
        this.error = error;
    }

    public FileListResponse(List<FileUploadResponse> files, String error) {
        this.files = files;
        this.error = error;
    }

    public List<FileUploadResponse> getFiles() { return files; }
    public void setFiles(List<FileUploadResponse> files) { this.files = files; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
