package com.java_torrent.bit_torrent;

import com.java_torrent.bit_torrent.service.ITorrentService;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class BitTorrentApplication {

    @Autowired
    private static ITorrentService torrentService;

    public static void main(String[] args) {
        // If no arguments provided, start the web server
        if (args.length == 0) {
            SpringApplication.run(BitTorrentApplication.class, args);
            return;
        }

        // Otherwise, run CLI mode
        String command = args[0];
        String torrentFilePath;
        Torrent torrent;
        List<String> peerList;
        String peerIPAndPort;
        String magnetURL;
        String pieceStoragePath;
        switch(command) {
            case "decode" -> {
                String bencodedValue = args[1];
                Codec.decodeAndPrintBencodedString(bencodedValue);
            }
            case "info" -> {
                torrentFilePath = args[1];
                torrent = TorrentUtils.getTorrentFromPath(torrentFilePath);
                torrent.printInfo();
            }
            case "peers" -> {
                torrentFilePath = args[1];
                torrent = TorrentUtils.getTorrentFromPath(torrentFilePath);
                try {
                    peerList = TorrentDownloader.getPeerList(torrent);
                    for (String peer : peerList) {
                        System.out.println(peer);
                    }
                } catch (Exception e) {
                    System.out.println("Failed to get peer list: " + e.getMessage());
                }
            }
            case "handshake" -> {
                torrentFilePath = args[1];
                torrent = TorrentUtils.getTorrentFromPath(torrentFilePath);
                peerIPAndPort = args[2];
                String peerIP = peerIPAndPort.split(":")[0];
                int peerPort = Integer.parseInt(peerIPAndPort.split(":")[1]);
                try (Socket socket = new Socket(peerIP, peerPort)){
                    TCPService tcpService = new TCPService(socket);
                    TorrentDownloader.performHandshake(torrent.getInfoHash(), tcpService, false);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            case "magnet_parse" -> {
                String magnetURI = args[1];
                Map<String,String> magnetInfo = TorrentUtils.getParamsFromMagnetURL(magnetURI);
                System.out.println("Tracker URL: " + magnetInfo.get("tr"));
                System.out.println("Info Hash: " + magnetInfo.get("xt").split(":")[2]);
            }
            case "magnet_handshake" -> {
                magnetURL = args[1];
                TorrentDownloader.performMagnetHandshake(magnetURL);
            }
            case "magnet_info" -> {
                magnetURL = args[1];
                torrent = torrentService.getTorrentFromMagnetURL(magnetURL).getLeft();
                torrent.printInfo();
            }
            case "download_piece" -> {
                pieceStoragePath = args[2];
                torrentFilePath = args[3];
                torrent = TorrentUtils.getTorrentFromPath(torrentFilePath);
                int pieceIndex = Integer.parseInt(args[4]);
                byte[] piece = TorrentDownloader.downloadPiece(torrent, pieceIndex, false);
                Utils.writePieceToFile(pieceStoragePath, piece);
            }
            case "magnet_download_piece" -> {
                pieceStoragePath = args[2];
                magnetURL = args[3];
                int pieceIndex = Integer.parseInt(args[4]);
                Pair<Torrent, TCPService> pair = torrentService.getTorrentFromMagnetURL(magnetURL);
                torrent = pair.getLeft();
                torrent.printInfo();
                TCPService tcpService = pair.getRight();
                try {
                    byte[] piece = TorrentDownloader.downloadPieceHelper(tcpService, (int) torrent.getPieceLength(pieceIndex), pieceIndex);
                    Utils.writePieceToFile(pieceStoragePath, piece);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            case "download" -> {
                String storageFilePath = args[2];
                torrentFilePath = args[3];
                torrent = TorrentUtils.getTorrentFromPath(torrentFilePath);
                // sout number of pieces
                System.out.println("Number of pieces: " + torrent.getPieces().size());
                TorrentDownloader.downloadTorrent(torrent, storageFilePath, false);
            }
            case "magnet_download" -> {
                String storageFilePath = args[2];
                magnetURL = args[3];
                Pair<Torrent, TCPService> pair = torrentService.getTorrentFromMagnetURL(magnetURL);
                torrent = pair.getLeft();
                TCPService tcpService = pair.getRight();
                try {
                    tcpService.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("downloadTorrent");
                TorrentDownloader.downloadTorrent(torrent, storageFilePath, true);
            }
            default -> System.out.println("Unknown command: " + command);
        }
    }
}