package com.java_torrent.bit_torrent.dto;

public class DownloadStatusResponse {
    private String downloadId;
    private String fileName;
    private String filePath;
    private String status;
    private int totalPieces;
    private int completedPieces;
    private double progress;
    private String error;

    public DownloadStatusResponse() {}

    public DownloadStatusResponse(String error) {
        this.error = error;
        this.status = "FAILED";
    }

    public DownloadStatusResponse(String downloadId, String fileName, String filePath, String status,
                                  int totalPieces, int completedPieces, double progress, String error) {
        this.downloadId = downloadId;
        this.fileName = fileName;
        this.filePath = filePath;
        this.status = status;
        this.totalPieces = totalPieces;
        this.completedPieces = completedPieces;
        this.progress = progress;
        this.error = error;
    }

    public String getDownloadId() { return downloadId; }
    public void setDownloadId(String downloadId) { this.downloadId = downloadId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalPieces() { return totalPieces; }
    public void setTotalPieces(int totalPieces) { this.totalPieces = totalPieces; }

    public int getCompletedPieces() { return completedPieces; }
    public void setCompletedPieces(int completedPieces) { this.completedPieces = completedPieces; }

    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = progress; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
