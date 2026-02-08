package com.java_torrent.bit_torrent.dto;

public class TorrentInfoResponse {
    private String trackerUrl;
    private Long length;
    private String infoHash;
    private Integer pieceCount;
    private Integer pieceLength;
    private String error;

    public TorrentInfoResponse() {}

    public TorrentInfoResponse(String error) {
        this.error = error;
    }

    public TorrentInfoResponse(String trackerUrl, Long length, String infoHash,
                               Integer pieceCount, Integer pieceLength, String error) {
        this.trackerUrl = trackerUrl;
        this.length = length;
        this.infoHash = infoHash;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.error = error;
    }

    // Getters and Setters
    public String getTrackerUrl() { return trackerUrl; }
    public void setTrackerUrl(String trackerUrl) { this.trackerUrl = trackerUrl; }

    public Long getLength() { return length; }
    public void setLength(Long length) { this.length = length; }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }

    public Integer getPieceCount() { return pieceCount; }
    public void setPieceCount(Integer pieceCount) { this.pieceCount = pieceCount; }

    public Integer getPieceLength() { return pieceLength; }
    public void setPieceLength(Integer pieceLength) { this.pieceLength = pieceLength; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}