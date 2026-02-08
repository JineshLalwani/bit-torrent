package com.java_torrent.bit_torrent.service;

import com.java_torrent.bit_torrent.TCPService;
import com.java_torrent.bit_torrent.Torrent;
import com.java_torrent.bit_torrent.dto.*;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.multipart.MultipartFile;

public interface ITorrentService {
    TorrentInfoResponse getTorrentInfo(MultipartFile file) throws Exception;

    PeerListResponse getPeers(MultipartFile file) throws Exception;

    DownloadResponse downloadTorrent(MultipartFile file) throws Exception;

    MagnetParseResponse parseMagnetUrl(String magnetUrl);

    TorrentInfoResponse getMagnetInfo(String magnetUrl) throws Exception;

    DownloadResponse downloadMagnet(String magnetUrl) throws Exception;

    DecodeResponse decodeBencode(String bencodedValue);

    Pair<Torrent, TCPService> getTorrentFromMagnetURL(String magnetURL);
}
