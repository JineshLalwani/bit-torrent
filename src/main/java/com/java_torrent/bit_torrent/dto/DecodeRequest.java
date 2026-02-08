package com.java_torrent.bit_torrent.dto;

public class DecodeRequest {
    private String bencodedValue;

    public DecodeRequest() {}

    public DecodeRequest(String bencodedValue) {
        this.bencodedValue = bencodedValue;
    }

    public String getBencodedValue() { return bencodedValue; }
    public void setBencodedValue(String bencodedValue) { this.bencodedValue = bencodedValue; }
}