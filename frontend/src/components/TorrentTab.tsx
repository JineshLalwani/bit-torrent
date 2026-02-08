import React, { useState } from 'react';
import { torrentApi, TorrentInfoResponse, PeerListResponse, DownloadResponse } from '../services/api';

const TorrentTab: React.FC = () => {
    const [file, setFile] = useState<File | null>(null);
    const [result, setResult] = useState<TorrentInfoResponse | PeerListResponse | DownloadResponse | null>(null);
    const [loading, setLoading] = useState(false);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    const handleGetInfo = async () => {
        if (!file) {
            alert('Please select a torrent file first');
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.getTorrentInfo(file);
            setResult(response);
        } catch (error) {
            setResult({ error: (error as Error).message } as any);
        } finally {
            setLoading(false);
        }
    };

    const handleGetPeers = async () => {
        if (!file) {
            alert('Please select a torrent file first');
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.getPeers(file);
            setResult(response);
        } catch (error) {
            setResult({ error: (error as Error).message } as any);
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async () => {
        if (!file) {
            alert('Please select a torrent file first');
            return;
        }

        if (!window.confirm('Start downloading this torrent?')) {
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.downloadTorrent(file);
            setResult(response);
        } catch (error) {
            setResult({ error: (error as Error).message } as any);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Upload Torrent File</h2>
                <div className="upload-area">
                    <input
                        type="file"
                        accept=".torrent"
                        onChange={handleFileChange}
                        className="file-input"
                    />
                    {file && <p className="file-name">Selected: {file.name}</p>}
                </div>

                <div className="button-group">
                    <button onClick={handleGetInfo} className="btn btn-info" disabled={loading}>
                        Get Info
                    </button>
                    <button onClick={handleGetPeers} className="btn btn-info" disabled={loading}>
                        Get Peers
                    </button>
                    <button onClick={handleDownload} className="btn btn-primary" disabled={loading}>
                        Download
                    </button>
                </div>
            </div>

            {loading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Processing...</p>
                </div>
            )}

            {result && (
                <div className="card result-card">
                    <h3>Result</h3>
                    <pre className={result.error ? 'error' : ''}>
                        {JSON.stringify(result, null, 2)}
                    </pre>
                </div>
            )}
        </div>
    );
};

export default TorrentTab;
