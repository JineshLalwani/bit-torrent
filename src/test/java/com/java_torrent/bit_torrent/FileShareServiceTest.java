package com.java_torrent.bit_torrent;

import com.java_torrent.bit_torrent.dto.FileUploadResponse;
import com.java_torrent.bit_torrent.service.FileShareService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileShareServiceTest {

    private final List<Path> createdFiles = new ArrayList<>();

    @AfterEach
    void cleanup() throws Exception {
        for (Path path : createdFiles) {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void uploadedFileTorrentRoundTripsWithSameInfoHash() throws Exception {
        FileShareService service = new FileShareService();
        service.init();

        byte[] content = new byte[300_000]; // spans two 256 KB pieces
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        MockMultipartFile upload = new MockMultipartFile("file", "../sneaky/e2e test.bin",
                "application/octet-stream", content);

        FileUploadResponse response = service.uploadFile(upload);
        createdFiles.add(Paths.get(service.getFileMetadata(response.getId()).getStoragePath()));
        createdFiles.add(Paths.get("shared-files/metadata.json"));

        assertNull(response.getError());
        // Path traversal in the client-supplied name must be neutralized
        assertEquals("e2e test.bin", response.getFileName());
        assertEquals(2, response.getPieceCount());
        assertEquals(content.length, response.getFileSize());
        assertEquals(40, response.getInfoHash().length());

        // The generated .torrent must parse back to the identical info hash
        byte[] torrentBytes = service.generateTorrentFile(response.getId(), "http://localhost:8080/api/tracker/announce");
        Torrent torrent = Torrent.fromBytes(torrentBytes);
        assertEquals(response.getInfoHash(), torrent.getInfoHash());
        assertEquals("e2e test.bin", torrent.getName());
        assertEquals(content.length, torrent.getLength());
        assertEquals("http://localhost:8080/api/tracker/announce", torrent.getTrackerURL());

        // Seeder lookup by info hash resolves to the same file
        assertNotNull(service.getFileByInfoHash(response.getInfoHash()));
        assertNull(service.getFileByInfoHash("0".repeat(40)));
    }
}
