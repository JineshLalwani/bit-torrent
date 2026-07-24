package com.java_torrent.bit_torrent.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Tracks asynchronous download jobs so the API can start a download, return
 * immediately, and let clients poll for progress.
 */
@Service
public class DownloadManager {

    public enum Status { PENDING, FETCHING_METADATA, DOWNLOADING, COMPLETED, FAILED }

    public static class DownloadJob {
        private final String id;
        private final long createdAt = System.currentTimeMillis();
        private volatile String fileName;
        private volatile String filePath;
        private volatile Status status = Status.PENDING;
        private volatile int totalPieces;
        private volatile int completedPieces;
        private volatile String error;

        DownloadJob(String id, String fileName) {
            this.id = id;
            this.fileName = fileName;
        }

        public String getId() { return id; }
        public long getCreatedAt() { return createdAt; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public Status getStatus() { return status; }
        public void setStatus(Status status) { this.status = status; }
        public int getTotalPieces() { return totalPieces; }
        public void setTotalPieces(int totalPieces) { this.totalPieces = totalPieces; }
        public int getCompletedPieces() { return completedPieces; }
        public void setCompletedPieces(int completedPieces) { this.completedPieces = completedPieces; }
        public String getError() { return error; }
    }

    private final Map<String, DownloadJob> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "download-worker");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Registers a new job and runs {@code task} asynchronously. The task is
     * responsible for updating progress; completion/failure status is set here.
     */
    public DownloadJob submit(String initialFileName, Consumer<DownloadJob> task) {
        String id = UUID.randomUUID().toString();
        DownloadJob job = new DownloadJob(id, initialFileName);
        jobs.put(id, job);
        executor.submit(() -> {
            try {
                task.accept(job);
                job.status = Status.COMPLETED;
                job.completedPieces = job.totalPieces;
            } catch (Exception e) {
                job.status = Status.FAILED;
                job.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        });
        return job;
    }

    public DownloadJob getJob(String id) {
        return jobs.get(id);
    }

    /** All jobs, newest first. */
    public List<DownloadJob> listJobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparingLong(DownloadJob::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
