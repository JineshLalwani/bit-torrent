package com.java_torrent.bit_torrent.dto;

public class FileUploadResponse {
    private String id;
    private String fileName;
    private long fileSize;
    private String infoHash;
    private int pieceCount;
    private long pieceLength;
    private String uploadedAt;
    private String error;

    public FileUploadResponse() {}

    public FileUploadResponse(String error) {
        this.error = error;
    }

    public FileUploadResponse(String id, String fileName, long fileSize, String infoHash,
                              int pieceCount, long pieceLength, String uploadedAt, String error) {
        this.id = id;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.infoHash = infoHash;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.uploadedAt = uploadedAt;
        this.error = error;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }

    public int getPieceCount() { return pieceCount; }
    public void setPieceCount(int pieceCount) { this.pieceCount = pieceCount; }

    public long getPieceLength() { return pieceLength; }
    public void setPieceLength(long pieceLength) { this.pieceLength = pieceLength; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
