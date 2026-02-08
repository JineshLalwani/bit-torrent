package com.java_torrent.bit_torrent.dto;

public class MagnetUrlRequest {
    private String magnetUrl;

    public MagnetUrlRequest() {}

    public MagnetUrlRequest(String magnetUrl) {
        this.magnetUrl = magnetUrl;
    }

    public String getMagnetUrl() { return magnetUrl; }
    public void setMagnetUrl(String magnetUrl) { this.magnetUrl = magnetUrl; }
}