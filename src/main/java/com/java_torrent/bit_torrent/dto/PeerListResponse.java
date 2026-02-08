package com.java_torrent.bit_torrent.dto;

import java.util.List;

public class PeerListResponse {
    private List<String> peers;
    private String error;

    public PeerListResponse() {}

    public PeerListResponse(String error) {
        this.error = error;
    }

    public PeerListResponse(List<String> peers, String error) {
        this.peers = peers;
        this.error = error;
    }

    public List<String> getPeers() { return peers; }
    public void setPeers(List<String> peers) { this.peers = peers; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}