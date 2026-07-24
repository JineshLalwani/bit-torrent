package com.java_torrent.bit_torrent.service;

import com.dampcake.bencode.Bencode;
import com.java_torrent.bit_torrent.*;
import com.java_torrent.bit_torrent.dto.*;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TorrentService implements ITorrentService {

    private static final String DOWNLOAD_DIR = "downloads/";

    private final DownloadManager downloadManager;

    public TorrentService(DownloadManager downloadManager) {
        this.downloadManager = downloadManager;
    }

    @Override
    public TorrentInfoResponse getTorrentInfo(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);
        return toInfoResponse(torrent);
    }

    private static TorrentInfoResponse toInfoResponse(Torrent torrent) {
        return new TorrentInfoResponse(
                torrent.getTrackerURL(),
                torrent.getName(),
                torrent.getLength(),
                torrent.getInfoHash(),
                torrent.getPieces().size(),
                torrent.getPieceLength(),
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

    private static Path ensureDownloadDir() throws IOException {
        Path downloadPath = Paths.get(DOWNLOAD_DIR);
        if (!Files.exists(downloadPath)) {
            Files.createDirectories(downloadPath);
        }
        return downloadPath;
    }

    private static String deriveFileName(Torrent torrent, String fallback) {
        String name = torrent.getName();
        if (name == null || name.isBlank()) {
            name = fallback;
        }
        return Utils.sanitizeFileName(name);
    }

    @Override
    public DownloadResponse downloadTorrent(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);
        ensureDownloadDir();

        String uploadedName = file.getOriginalFilename();
        String fileName = deriveFileName(torrent,
                uploadedName != null ? uploadedName.replace(".torrent", "") : "download");
        String outputPath = DOWNLOAD_DIR + fileName;

        TorrentDownloader.downloadTorrent(torrent, outputPath, false);

        return new DownloadResponse(
                "Download completed for: " + fileName,
                outputPath,
                torrent.getPieces().size(),
                null
        );
    }

    @Override
    public DownloadStatusResponse startTorrentDownload(MultipartFile file) throws Exception {
        byte[] fileBytes = file.getBytes();
        Torrent torrent = TorrentUtils.getTorrentFromBytes(fileBytes);
        ensureDownloadDir();

        String uploadedName = file.getOriginalFilename();
        String fileName = deriveFileName(torrent,
                uploadedName != null ? uploadedName.replace(".torrent", "") : "download");
        String outputPath = DOWNLOAD_DIR + fileName;

        DownloadManager.DownloadJob job = downloadManager.submit(fileName, j -> {
            j.setStatus(DownloadManager.Status.DOWNLOADING);
            j.setTotalPieces(torrent.getPieces().size());
            TorrentDownloader.downloadTorrent(torrent, outputPath, false,
                    (done, total) -> j.setCompletedPieces(done), null);
            j.setFilePath(outputPath);
        });
        return toStatusResponse(job);
    }

    @Override
    public DownloadStatusResponse startMagnetDownload(String magnetUrl) {
        // Validate the URL up front so obvious errors fail synchronously
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnetUrl);
        TorrentUtils.getInfoHashFromMagnetParams(params);
        String displayName = params.getOrDefault("dn", "magnet-download");

        DownloadManager.DownloadJob job = downloadManager.submit(Utils.sanitizeFileName(displayName), j -> {
            try {
                ensureDownloadDir();
                j.setStatus(DownloadManager.Status.FETCHING_METADATA);
                Pair<Torrent, TCPService> result = resolveMagnet(params);
                Torrent torrent = result.getLeft();
                closeQuietly(result.getRight());

                String fileName = deriveFileName(torrent, j.getFileName());
                String outputPath = DOWNLOAD_DIR + fileName;
                j.setFileName(fileName);
                j.setTotalPieces(torrent.getPieces().size());
                j.setStatus(DownloadManager.Status.DOWNLOADING);

                TorrentDownloader.downloadTorrent(torrent, outputPath, true,
                        (done, total) -> j.setCompletedPieces(done),
                        TorrentUtils.getDirectPeers(params));
                j.setFilePath(outputPath);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        return toStatusResponse(job);
    }

    @Override
    public DownloadStatusResponse getDownloadStatus(String downloadId) {
        DownloadManager.DownloadJob job = downloadManager.getJob(downloadId);
        if (job == null) {
            return null;
        }
        return toStatusResponse(job);
    }

    private static DownloadStatusResponse toStatusResponse(DownloadManager.DownloadJob job) {
        double progress = job.getTotalPieces() > 0
                ? (100.0 * job.getCompletedPieces()) / job.getTotalPieces()
                : 0.0;
        if (job.getStatus() == DownloadManager.Status.COMPLETED) {
            progress = 100.0;
        }
        DownloadStatusResponse response = new DownloadStatusResponse(
                job.getId(),
                job.getFileName(),
                job.getFilePath(),
                job.getStatus().name(),
                job.getTotalPieces(),
                job.getCompletedPieces(),
                Math.round(progress * 10.0) / 10.0,
                job.getError()
        );
        response.setCreatedAt(job.getCreatedAt());
        return response;
    }

    @Override
    public List<DownloadStatusResponse> listDownloads() {
        return downloadManager.listJobs().stream()
                .map(TorrentService::toStatusResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public MagnetParseResponse parseMagnetUrl(String magnetUrl) {
        try {
            Map<String, String> magnetInfo = TorrentUtils.getParamsFromMagnetURL(magnetUrl);
            String trackerUrl = magnetInfo.get("tr");
            String infoHash = TorrentUtils.getInfoHashFromMagnetParams(magnetInfo);

            return new MagnetParseResponse(trackerUrl, infoHash, null);
        } catch (Exception e) {
            return new MagnetParseResponse(null, null, e.getMessage());
        }
    }

    @Override
    public TorrentInfoResponse getMagnetInfo(String magnetUrl) throws Exception {
        Pair<Torrent, TCPService> result = getTorrentFromMagnetURL(magnetUrl);
        closeQuietly(result.getRight());
        return toInfoResponse(result.getLeft());
    }

    @Override
    public DownloadResponse downloadMagnet(String magnetUrl) throws Exception {
        Map<String, String> params = TorrentUtils.getParamsFromMagnetURL(magnetUrl);
        Pair<Torrent, TCPService> pair = resolveMagnet(params);
        Torrent torrent = pair.getLeft();
        closeQuietly(pair.getRight());

        ensureDownloadDir();
        String fileName = deriveFileName(torrent,
                params.getOrDefault("dn", "magnet_" + System.currentTimeMillis()));
        String outputPath = DOWNLOAD_DIR + fileName;

        TorrentDownloader.downloadTorrent(torrent, outputPath, true, null,
                TorrentUtils.getDirectPeers(params));

        return new DownloadResponse(
                "Download completed for magnet link",
                outputPath,
                torrent.getPieces().size(),
                null
        );
    }

    private static void closeQuietly(TCPService tcpService) {
        if (tcpService != null) {
            try { tcpService.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public DecodeResponse decodeBencode(String bencodedValue) {
        try {
            return new DecodeResponse(Codec.decodeToJson(bencodedValue), null);
        } catch (Exception e) {
            return new DecodeResponse(null, e.getMessage());
        }
    }

    @Override
    public Pair<Torrent, TCPService> getTorrentFromMagnetURL(String magnetURL) {
        return resolveMagnet(TorrentUtils.getParamsFromMagnetURL(magnetURL));
    }

    /**
     * Resolves torrent metadata for a magnet link: first via the xs (exact
     * source) URL if present, otherwise via the peer metadata extension.
     */
    private Pair<Torrent, TCPService> resolveMagnet(Map<String, String> params) {
        String infoHash = TorrentUtils.getInfoHashFromMagnetParams(params);
        String trackerURL = params.get("tr");

        // Try xs (exact source) URL first — fast HTTPS download of .torrent file
        String xs = params.get("xs");
        if (xs != null) {
            try {
                HttpClientService httpClientService = new HttpClientService();
                HttpResponse<byte[]> response = httpClientService.sendGetRequest(xs);
                Torrent torrent = Torrent.fromBytes(response.body());
                if (!torrent.getInfoHash().equalsIgnoreCase(infoHash)) {
                    throw new RuntimeException("xs torrent info hash does not match magnet link");
                }
                return Pair.of(torrent, null);
            } catch (Exception e) {
                System.out.println("xs fallback failed: " + e.getMessage());
            }
        }

        // Fall back to fetching metadata from a peer via the extension protocol
        Pair<TCPService, Long> handshakeResult = TorrentDownloader.performMagnetHandshakeWithParams(params);

        if (handshakeResult != null && handshakeResult.getLeft() != null) {
            TCPService tcpService = handshakeResult.getLeft();
            long extensionId = handshakeResult.getRight();

            byte[] metadataRequestMessage = TorrentDownloader.createMetadataRequestMessage(0, 0, extensionId);
            tcpService.sendMessage(metadataRequestMessage);
            byte[] metadataResponse = TorrentDownloader.waitForExtendedMessage(tcpService);
            Map<String, Object> metadataPieceDict = TorrentDownloader.getMetadataFromMessage(metadataResponse);
            String calculatedInfoHash = Utils.calculateSHA1(new Bencode(true).encode(metadataPieceDict));

            if (!calculatedInfoHash.equalsIgnoreCase(infoHash)) {
                closeQuietly(tcpService);
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
                    .setName(Torrent.bufToString(metadataPieceDict.get("name")))
                    .build(), tcpService);
        }

        throw new RuntimeException("Failed to get torrent metadata: no xs URL available and could not connect to any peers");
    }
}
