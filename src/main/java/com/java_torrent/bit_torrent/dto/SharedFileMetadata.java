package com.java_torrent.bit_torrent.dto;

import java.util.List;

public class SharedFileMetadata {
    private String id;
    private String fileName;
    private long fileSize;
    private String infoHash;
    private int pieceCount;
    private long pieceLength;
    private List<String> pieceHashes;
    private String uploadedAt;
    private String storagePath;

    public SharedFileMetadata() {}

    public SharedFileMetadata(String id, String fileName, long fileSize, String infoHash,
                              int pieceCount, long pieceLength, List<String> pieceHashes,
                              String uploadedAt, String storagePath) {
        this.id = id;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.infoHash = infoHash;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.pieceHashes = pieceHashes;
        this.uploadedAt = uploadedAt;
        this.storagePath = storagePath;
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

    public List<String> getPieceHashes() { return pieceHashes; }
    public void setPieceHashes(List<String> pieceHashes) { this.pieceHashes = pieceHashes; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}
