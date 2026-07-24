import React, { useState, useEffect, useMemo } from 'react';
import { fileShareApi, SharedFileInfo } from '../services/api';
import { formatFileSize } from '../utils/format';
import { copyToClipboard } from '../utils/format';
import FileDropZone from './FileDropZone';
import { CopyableValue, ErrorBanner } from './ResultViews';
import { useToast } from './Toasts';

const MAX_UPLOAD_BYTES = 100 * 1024 * 1024; // matches server multipart limit

const FileHubTab: React.FC = () => {
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [uploadResult, setUploadResult] = useState<SharedFileInfo | null>(null);
    const [files, setFiles] = useState<SharedFileInfo[]>([]);
    const [loadingFiles, setLoadingFiles] = useState(false);
    const [downloading, setDownloading] = useState<string | null>(null);
    const [expandedFile, setExpandedFile] = useState<string | null>(null);
    const [search, setSearch] = useState('');
    const toast = useToast();

    const loadFiles = async () => {
        setLoadingFiles(true);
        try {
            const response = await fileShareApi.listFiles();
            if (response.files) {
                setFiles(response.files);
            }
        } catch (error) {
            toast.error('Could not load shared files: ' + (error as Error).message);
        } finally {
            setLoadingFiles(false);
        }
    };

    useEffect(() => {
        loadFiles();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const visibleFiles = useMemo(() => {
        const q = search.trim().toLowerCase();
        if (!q) return files;
        return files.filter(f =>
            f.fileName.toLowerCase().includes(q) || f.infoHash.toLowerCase().includes(q));
    }, [files, search]);

    const handleUpload = async () => {
        if (!file) {
            toast.info('Select a file first');
            return;
        }
        if (file.size > MAX_UPLOAD_BYTES) {
            toast.error('File is too large — the server accepts uploads up to 100 MB');
            return;
        }
        setUploading(true);
        setUploadResult(null);
        try {
            const response = await fileShareApi.uploadFile(file);
            setUploadResult(response);
            if (!response.error) {
                setFile(null);
                toast.success(`${response.fileName} is now being seeded`);
                loadFiles();
            }
        } catch (error) {
            setUploadResult({ error: (error as Error).message } as SharedFileInfo);
        } finally {
            setUploading(false);
        }
    };

    const handleDownload = async (fileId: string, fileName: string) => {
        setDownloading(fileId);
        try {
            await fileShareApi.downloadFile(fileId, fileName);
        } catch (error) {
            toast.error('Download failed: ' + (error as Error).message);
        } finally {
            setDownloading(null);
        }
    };

    const handleTorrentDownload = async (fileId: string, fileName: string) => {
        try {
            await fileShareApi.downloadTorrentFile(fileId, fileName);
            toast.success('.torrent file saved');
        } catch (error) {
            toast.error('.torrent download failed: ' + (error as Error).message);
        }
    };

    const handleCopyMagnet = async (magnetLink?: string) => {
        if (!magnetLink) return;
        if (await copyToClipboard(magnetLink)) {
            toast.success('Magnet link copied — paste it in the Magnet tab');
        }
    };

    const toggleExpand = (fileId: string) => {
        setExpandedFile(expandedFile === fileId ? null : fileId);
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Share a File</h2>
                <p className="card-sub">
                    Uploaded files are seeded by this server over the BitTorrent protocol —
                    anyone with the magnet link can download them.
                </p>
                <FileDropZone
                    file={file}
                    onFile={(f) => { setFile(f); setUploadResult(null); }}
                    hint="Any file up to 100 MB"
                />
                <div className="button-group">
                    <button onClick={handleUpload} className="btn btn-primary" disabled={uploading || !file}>
                        {uploading ? 'Uploading…' : 'Upload & Start Seeding'}
                    </button>
                </div>

                {uploadResult && !uploadResult.error && (
                    <div className="upload-success">
                        <p><span className="label">File: </span><strong>{uploadResult.fileName}</strong> ({formatFileSize(uploadResult.fileSize)})</p>
                        <p><span className="label">Info Hash: </span><span className="value">{uploadResult.infoHash}</span></p>
                        <p><span className="label">Pieces: </span>{uploadResult.pieceCount} × {formatFileSize(uploadResult.pieceLength)}</p>
                        {uploadResult.magnetLink && (
                            <div className="magnet-row">
                                <span className="magnet-link">{uploadResult.magnetLink}</span>
                                <button className="btn-ghost" onClick={() => handleCopyMagnet(uploadResult.magnetLink)}>
                                    Copy Magnet
                                </button>
                            </div>
                        )}
                    </div>
                )}

                {uploadResult?.error && <ErrorBanner message={uploadResult.error} />}
            </div>

            {uploading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Uploading and generating torrent metadata…</p>
                </div>
            )}

            <div className="card">
                <div className="card-header">
                    <h2>Shared Files {files.length > 0 && <span className="count-badge">{files.length}</span>}</h2>
                    <button onClick={loadFiles} className="btn-ghost" disabled={loadingFiles}>
                        {loadingFiles ? 'Loading…' : 'Refresh'}
                    </button>
                </div>

                {files.length > 3 && (
                    <div className="input-group">
                        <input
                            type="search"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            placeholder="Search by name or info hash…"
                            className="text-input"
                        />
                    </div>
                )}

                {files.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-icon">🌱</div>
                        <p>No files shared yet. Be the first to upload!</p>
                    </div>
                ) : visibleFiles.length === 0 ? (
                    <div className="empty-state">
                        <p>No files match “{search}”.</p>
                    </div>
                ) : (
                    <div className="file-list">
                        {visibleFiles.map((f) => (
                            <div key={f.id} className="file-card">
                                <div className="file-card-header" onClick={() => toggleExpand(f.id)}>
                                    <div className="file-card-info">
                                        <span className="file-card-name">{f.fileName}</span>
                                        <span className="file-card-size">{formatFileSize(f.fileSize)} · {f.pieceCount} pieces</span>
                                    </div>
                                    <div className="file-card-actions">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleCopyMagnet(f.magnetLink); }}
                                            className="btn-ghost"
                                            disabled={!f.magnetLink}
                                            title="Copy magnet link"
                                        >
                                            🧲 Magnet
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleTorrentDownload(f.id, f.fileName); }}
                                            className="btn-ghost"
                                            title="Download .torrent file"
                                        >
                                            .torrent
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); handleDownload(f.id, f.fileName); }}
                                            className="btn-download"
                                            disabled={downloading === f.id}
                                        >
                                            {downloading === f.id ? 'Downloading…' : 'Download'}
                                        </button>
                                        <span className="expand-icon">{expandedFile === f.id ? '▲' : '▼'}</span>
                                    </div>
                                </div>

                                {expandedFile === f.id && (
                                    <div className="file-card-details">
                                        <div className="info-grid">
                                            <div className="info-row">
                                                <span className="info-label">File Size</span>
                                                <span className="info-value">{formatFileSize(f.fileSize)} <span className="info-sub">({f.fileSize.toLocaleString()} bytes)</span></span>
                                            </div>
                                            <div className="info-row">
                                                <span className="info-label">Info Hash</span>
                                                <span className="info-value mono"><CopyableValue value={f.infoHash} label="Info hash" /></span>
                                            </div>
                                            <div className="info-row">
                                                <span className="info-label">Pieces</span>
                                                <span className="info-value">{f.pieceCount} × {formatFileSize(f.pieceLength)}</span>
                                            </div>
                                            <div className="info-row">
                                                <span className="info-label">Uploaded</span>
                                                <span className="info-value">{new Date(f.uploadedAt).toLocaleString()}</span>
                                            </div>
                                            {f.magnetLink && (
                                                <div className="info-row">
                                                    <span className="info-label">Magnet</span>
                                                    <span className="info-value mono"><CopyableValue value={f.magnetLink} label="Magnet link" /></span>
                                                </div>
                                            )}
                                        </div>
                                        <p className="peers-summary">
                                            Seeded by this server. Paste the magnet link into the Magnet tab
                                            (or the .torrent into the Torrent tab) to download it over BitTorrent.
                                        </p>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default FileHubTab;
