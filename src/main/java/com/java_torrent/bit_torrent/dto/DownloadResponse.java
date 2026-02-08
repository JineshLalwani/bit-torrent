package com.java_torrent.bit_torrent.dto;

public class DownloadResponse {
    private String message;
    private String filePath;
    private Integer totalPieces;
    private String error;

    public DownloadResponse() {}

    public DownloadResponse(String error) {
        this.error = error;
    }

    public DownloadResponse(String message, String filePath, Integer totalPieces, String error) {
        this.message = message;
        this.filePath = filePath;
        this.totalPieces = totalPieces;
        this.error = error;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getTotalPieces() { return totalPieces; }
    public void setTotalPieces(Integer totalPieces) { this.totalPieces = totalPieces; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}