package com.java_torrent.bit_torrent.dto;

public class TorrentInfoResponse {
    private String trackerUrl;
    private String name;
    private Long length;
    private String infoHash;
    private Integer pieceCount;
    private Long pieceLength;
    private String error;

    public TorrentInfoResponse() {}

    public TorrentInfoResponse(String error) {
        this.error = error;
    }

    public TorrentInfoResponse(String trackerUrl, String name, Long length, String infoHash,
                               Integer pieceCount, Long pieceLength, String error) {
        this.trackerUrl = trackerUrl;
        this.name = name;
        this.length = length;
        this.infoHash = infoHash;
        this.pieceCount = pieceCount;
        this.pieceLength = pieceLength;
        this.error = error;
    }

    public String getTrackerUrl() { return trackerUrl; }
    public void setTrackerUrl(String trackerUrl) { this.trackerUrl = trackerUrl; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getLength() { return length; }
    public void setLength(Long length) { this.length = length; }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }

    public Integer getPieceCount() { return pieceCount; }
    public void setPieceCount(Integer pieceCount) { this.pieceCount = pieceCount; }

    public Long getPieceLength() { return pieceLength; }
    public void setPieceLength(Long pieceLength) { this.pieceLength = pieceLength; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
