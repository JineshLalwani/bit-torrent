package com.java_torrent.bit_torrent.service;

import com.dampcake.bencode.Bencode;
import com.java_torrent.bit_torrent.*;
import com.java_torrent.bit_torrent.dto.*;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TorrentService implements ITorrentService{

    private static final String DOWNLOAD_DIR = "downloads/";

    @Override
    public TorrentInfoResponse getTorrentInfo(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);

        return new TorrentInfoResponse(
                torrent.getTrackerURL(),
                torrent.getLength(),
                torrent.getInfoHash(),
                torrent.getPieces().size(),
                torrent.getPieces().get(0).length() * torrent.getPieces().size(),
                null
        );
    }

    @Override
    public PeerListResponse getPeers(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);

        List<String> peerList = TorrentDownloader.getPeerList(torrent);
        return new PeerListResponse(peerList, null);
    }

    @Override
    public DownloadResponse downloadTorrent(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);

        // Create downloads directory if it doesn't exist
        Path downloadPath = Paths.get(DOWNLOAD_DIR);
        if (!Files.exists(downloadPath)) {
            Files.createDirectories(downloadPath);
        }

        String fileName = file.getOriginalFilename().replace(".torrent", "");
        String outputPath = DOWNLOAD_DIR + fileName;

        // Start download in a separate thread
        new Thread(() -> {
            try {
                TorrentDownloader.downloadTorrent(torrent, outputPath, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return new DownloadResponse(
                "Download started for: " + fileName,
                outputPath,
                torrent.getPieces().size(),
                null
        );
    }

    @Override
    public MagnetParseResponse parseMagnetUrl(String magnetUrl) {
        try {
            Map<String, String> magnetInfo = TorrentUtils.getParamsFromMagnetURL(magnetUrl);
            String trackerUrl = magnetInfo.get("tr");
            String infoHash = magnetInfo.get("xt").split(":")[2];

            return new MagnetParseResponse(trackerUrl, infoHash, null);
        } catch (Exception e) {
            return new MagnetParseResponse(null, null, e.getMessage());
        }
    }

    @Override
    public TorrentInfoResponse getMagnetInfo(String magnetUrl) throws Exception {
        Torrent torrent = getTorrentFromMagnetURL(magnetUrl).getLeft();

        return new TorrentInfoResponse(
                torrent.getTrackerURL(),
                torrent.getLength(),
                torrent.getInfoHash(),
                torrent.getPieces().size(),
                torrent.getPieces().get(0).length() * torrent.getPieces().size(),
                null
        );
    }

    @Override
    public DownloadResponse downloadMagnet(String magnetUrl) throws Exception {
        Pair<Torrent, TCPService> pair = getTorrentFromMagnetURL(magnetUrl);
        Torrent torrent = pair.getLeft();
        TCPService tcpService = pair.getRight();

        try {
            tcpService.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Create downloads directory if it doesn't exist
        Path downloadPath = Paths.get(DOWNLOAD_DIR);
        if (!Files.exists(downloadPath)) {
            Files.createDirectories(downloadPath);
        }

        String fileName = "magnet_" + System.currentTimeMillis();
        String outputPath = DOWNLOAD_DIR + fileName;

        // Start download in a separate thread
        new Thread(() -> {
            try {
                TorrentDownloader.downloadTorrent(torrent, outputPath, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return new DownloadResponse(
                "Download started for magnet link",
                outputPath,
                torrent.getPieces().size(),
                null
        );
    }

    @Override
    public DecodeResponse decodeBencode(String bencodedValue) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            PrintStream old = System.out;
            System.setOut(ps);

            Codec.decodeAndPrintBencodedString(bencodedValue);

            System.out.flush();
            System.setOut(old);

            String output = baos.toString();
            return new DecodeResponse(output, null);
        } catch (Exception e) {
            return new DecodeResponse(null, e.getMessage());
        }
    }

    @Override
    public Pair<Torrent, TCPService> getTorrentFromMagnetURL(String magnetURL) {
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnetURL);
        String infoHash = params.get("xt").split(":")[2];
        String trackerURL = params.get("tr");
        Pair<TCPService, Long> handshakeResult = TorrentDownloader.performMagnetHandshake(magnetURL);
        TCPService tcpService = handshakeResult.getLeft();
        long extensionId = handshakeResult.getRight();

        if (tcpService == null) {
            throw new RuntimeException("Failed to connect to any peers");
        }

        byte[] metadataRequestMessage = TorrentDownloader.createMetadataRequestMessage(0, 0, extensionId);
        tcpService.sendMessage(metadataRequestMessage);
        byte[] metadataResponse = tcpService.waitForMessage();
        Map<String, Object> metadataPieceDict = TorrentDownloader.getMetadataFromMessage(metadataResponse);
        String calculatedInfoHash = Utils.calculateSHA1(new Bencode(true).encode(metadataPieceDict));

        if (!calculatedInfoHash.equals(infoHash)) {
            throw new RuntimeException("Info hash mismatch, expected " + infoHash + " but got " + calculatedInfoHash);
        }

        byte[] pieceHashBytes = ((ByteBuffer) metadataPieceDict.get("pieces")).array();
        List<String> pieceHashes = TorrentUtils.splitPieceHashes(pieceHashBytes, 20, new ArrayList<>());

        return Pair.of(new Torrent.Builder()
                .setTrackerURL(trackerURL)
                .setLength(((Number) metadataPieceDict.get("length")).longValue())
                .setInfoHash(infoHash)
                .setPieceLength(((Number) metadataPieceDict.get("piece length")).longValue())
                .setPieces(pieceHashes)
                .build(), tcpService);
    }
}
