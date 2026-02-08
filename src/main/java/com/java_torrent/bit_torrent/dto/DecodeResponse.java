package com.java_torrent.bit_torrent.dto;

public class DecodeResponse {
    private String decoded;
    private String error;

    public DecodeResponse() {}

    public DecodeResponse(String decoded, String error) {
        this.decoded = decoded;
        this.error = error;
    }

    public String getDecoded() { return decoded; }
    public void setDecoded(String decoded) { this.decoded = decoded; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}