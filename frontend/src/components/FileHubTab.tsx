import React, { useState, useEffect } from 'react';
import { fileShareApi, SharedFileInfo } from '../services/api';

const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
};

const truncateHash = (hash: string): string => {
    if (hash.length <= 16) return hash;
    return hash.substring(0, 8) + '...' + hash.substring(hash.length - 8);
};

const FileHubTab: React.FC = () => {
    const [file, setFile] = useState<File | null>(null);
    const [uploading, setUploading] = useState(false);
    const [uploadResult, setUploadResult] = useState<SharedFileInfo | null>(null);
    const [files, setFiles] = useState<SharedFileInfo[]>([]);
    const [loadingFiles, setLoadingFiles] = useState(false);
    const [downloading, setDownloading] = useState<string | null>(null);

    const loadFiles = async () => {
        setLoadingFiles(true);
        try {
            const response = await fileShareApi.listFiles();
            if (response.files) {
                setFiles(response.files);
            }
        } catch (error) {
            console.error('Failed to load files:', error);
        } finally {
            setLoadingFiles(false);
        }
    };

    useEffect(() => {
        loadFiles();
    }, []);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
            setUploadResult(null);
        }
    };

    const handleUpload = async () => {
        if (!file) {
            alert('Please select a file first');
            return;
        }
        setUploading(true);
        setUploadResult(null);
        try {
            const response = await fileShareApi.uploadFile(file);
            setUploadResult(response);
            if (!response.error) {
                setFile(null);
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
            alert('Download failed: ' + (error as Error).message);
        } finally {
            setDownloading(null);
        }
    };

    return (
        <div className="tab-content">
            <div className="ephemeral-warning">
                Files are stored on a free-tier server and may be cleared periodically.
            </div>

            <div className="card">
                <h2>Share a File</h2>
                <div className="upload-area">
                    <input
                        type="file"
                        onChange={handleFileChange}
                        className="file-input"
                    />
                    {file && <p className="file-name">Selected: {file.name} ({formatFileSize(file.size)})</p>}
                </div>
                <div className="button-group">
                    <button onClick={handleUpload} className="btn btn-primary" disabled={uploading || !file}>
                        {uploading ? 'Uploading...' : 'Upload & Generate Torrent Info'}
                    </button>
                </div>

                {uploadResult && !uploadResult.error && (
                    <div className="upload-success">
                        <p><span className="label">File: </span><strong>{uploadResult.fileName}</strong></p>
                        <p><span className="label">Size: </span>{formatFileSize(uploadResult.fileSize)}</p>
                        <p><span className="label">Info Hash: </span><span className="value">{uploadResult.infoHash}</span></p>
                        <p><span className="label">Pieces: </span>{uploadResult.pieceCount} x {formatFileSize(uploadResult.pieceLength)}</p>
                    </div>
                )}

                {uploadResult && uploadResult.error && (
                    <div className="card result-card" style={{ marginTop: '1rem' }}>
                        <pre className="error">{uploadResult.error}</pre>
                    </div>
                )}
            </div>

            {uploading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Uploading and generating torrent metadata...</p>
                </div>
            )}

            <div className="card">
                <div className="card-header">
                    <h2>Shared Files</h2>
                    <button onClick={loadFiles} className="btn-refresh" disabled={loadingFiles}>
                        {loadingFiles ? 'Loading...' : 'Refresh'}
                    </button>
                </div>

                {files.length === 0 ? (
                    <div className="empty-state">
                        <p>No files shared yet. Be the first to upload!</p>
                    </div>
                ) : (
                    <table className="file-table">
                        <thead>
                            <tr>
                                <th>File</th>
                                <th>Size</th>
                                <th>Info Hash</th>
                                <th>Uploaded</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            {files.map((f) => (
                                <tr key={f.id}>
                                    <td><strong>{f.fileName}</strong></td>
                                    <td>{formatFileSize(f.fileSize)}</td>
                                    <td>
                                        <span className="info-hash" title={f.infoHash}>
                                            {truncateHash(f.infoHash)}
                                        </span>
                                    </td>
                                    <td>{new Date(f.uploadedAt).toLocaleDateString()}</td>
                                    <td>
                                        <button
                                            onClick={() => handleDownload(f.id, f.fileName)}
                                            className="btn-download"
                                            disabled={downloading === f.id}
                                        >
                                            {downloading === f.id ? 'Downloading...' : 'Download'}
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>
        </div>
    );
};

export default FileHubTab;
