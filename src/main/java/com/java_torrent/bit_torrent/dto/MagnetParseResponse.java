package com.java_torrent.bit_torrent.dto;

public class MagnetParseResponse {
    private String trackerUrl;
    private String infoHash;
    private String error;

    public MagnetParseResponse() {}

    public MagnetParseResponse(String error) {
        this.error = error;
    }

    public MagnetParseResponse(String trackerUrl, String infoHash, String error) {
        this.trackerUrl = trackerUrl;
        this.infoHash = infoHash;
        this.error = error;
    }

    public String getTrackerUrl() { return trackerUrl; }
    public void setTrackerUrl(String trackerUrl) { this.trackerUrl = trackerUrl; }

    public String getInfoHash() { return infoHash; }
    public void setInfoHash(String infoHash) { this.infoHash = infoHash; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}