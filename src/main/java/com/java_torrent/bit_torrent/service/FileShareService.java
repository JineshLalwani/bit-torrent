package com.java_torrent.bit_torrent.service;

import com.dampcake.bencode.Bencode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.java_torrent.bit_torrent.Utils;
import com.java_torrent.bit_torrent.dto.FileListResponse;
import com.java_torrent.bit_torrent.dto.FileUploadResponse;
import com.java_torrent.bit_torrent.dto.SharedFileMetadata;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class FileShareService implements IFileShareService {

    private static final String SHARE_DIR = "shared-files/";
    private static final String METADATA_FILE = "shared-files/metadata.json";
    private static final long PIECE_LENGTH = 262144L; // 256 KB

    private final ConcurrentHashMap<String, SharedFileMetadata> fileStore = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    @PostConstruct
    public void init() {
        try {
            Path shareDir = Paths.get(SHARE_DIR);
            if (!Files.exists(shareDir)) {
                Files.createDirectories(shareDir);
            }

            Path metadataPath = Paths.get(METADATA_FILE);
            if (Files.exists(metadataPath)) {
                String json = Files.readString(metadataPath);
                List<SharedFileMetadata> entries = gson.fromJson(json,
                        new TypeToken<List<SharedFileMetadata>>() {}.getType());
                if (entries != null) {
                    for (SharedFileMetadata entry : entries) {
                        if (new File(entry.getStoragePath()).exists()) {
                            fileStore.put(entry.getId(), entry);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to load file metadata: " + e.getMessage());
        }
    }

    @Override
    public FileUploadResponse uploadFile(MultipartFile file) throws Exception {
        String id = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        byte[] fileBytes = file.getBytes();
        long fileSize = fileBytes.length;

        // Save file to disk
        String storagePath = SHARE_DIR + id + "_" + fileName;
        Files.write(Paths.get(storagePath), fileBytes);

        // Generate piece hashes
        List<String> pieceHashes = new ArrayList<>();
        int offset = 0;
        while (offset < fileBytes.length) {
            int end = (int) Math.min(offset + PIECE_LENGTH, fileBytes.length);
            byte[] piece = Arrays.copyOfRange(fileBytes, offset, end);
            pieceHashes.add(Utils.calculateSHA1(piece));
            offset = end;
        }

        // Build bencoded info dict and compute info hash
        byte[] rawPieceHashes = new byte[pieceHashes.size() * 20];
        for (int i = 0; i < pieceHashes.size(); i++) {
            byte[] hashBytes = Utils.hexStringToByteArray(pieceHashes.get(i));
            System.arraycopy(hashBytes, 0, rawPieceHashes, i * 20, 20);
        }

        Map<String, Object> infoDict = new TreeMap<>();
        infoDict.put("length", fileSize);
        infoDict.put("name", ByteBuffer.wrap(fileName.getBytes()));
        infoDict.put("piece length", PIECE_LENGTH);
        infoDict.put("pieces", ByteBuffer.wrap(rawPieceHashes));

        byte[] encodedInfo = new Bencode(true).encode(infoDict);
        String infoHash = Utils.calculateSHA1(encodedInfo);

        String uploadedAt = Instant.now().toString();

        SharedFileMetadata metadata = new SharedFileMetadata(
                id, fileName, fileSize, infoHash,
                pieceHashes.size(), PIECE_LENGTH, pieceHashes,
                uploadedAt, storagePath
        );

        fileStore.put(id, metadata);
        persistMetadata();

        return new FileUploadResponse(
                id, fileName, fileSize, infoHash,
                pieceHashes.size(), PIECE_LENGTH, uploadedAt, null
        );
    }

    @Override
    public FileListResponse listFiles() {
        List<FileUploadResponse> files = fileStore.values().stream()
                .sorted((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()))
                .map(m -> new FileUploadResponse(
                        m.getId(), m.getFileName(), m.getFileSize(), m.getInfoHash(),
                        m.getPieceCount(), m.getPieceLength(), m.getUploadedAt(), null
                ))
                .collect(Collectors.toList());
        return new FileListResponse(files, null);
    }

    @Override
    public SharedFileMetadata getFileMetadata(String fileId) {
        return fileStore.get(fileId);
    }

    private synchronized void persistMetadata() {
        try {
            String json = gson.toJson(new ArrayList<>(fileStore.values()));
            Files.writeString(Paths.get(METADATA_FILE), json);
        } catch (Exception e) {
            System.out.println("Failed to persist file metadata: " + e.getMessage());
        }
    }
}
