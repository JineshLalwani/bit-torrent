import React, { useState } from 'react';
import { torrentApi, TorrentInfoResponse, PeerListResponse } from '../services/api';
import { useAsyncDownload } from '../hooks/useAsyncDownload';
import DownloadProgress from './DownloadProgress';

const TorrentTab: React.FC = () => {
    const [file, setFile] = useState<File | null>(null);
    const [result, setResult] = useState<TorrentInfoResponse | PeerListResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const { status: downloadStatus, active: downloading, start: startDownload } = useAsyncDownload();

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            setFile(e.target.files[0]);
        }
    };

    const runAction = async (action: () => Promise<TorrentInfoResponse | PeerListResponse>) => {
        if (!file) {
            alert('Please select a torrent file first');
            return;
        }
        setLoading(true);
        try {
            setResult(await action());
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
        setResult(null);
        await startDownload(() => torrentApi.startTorrentDownload(file));
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
                    <button onClick={() => runAction(() => torrentApi.getTorrentInfo(file!))} className="btn btn-info" disabled={loading || downloading}>
                        Get Info
                    </button>
                    <button onClick={() => runAction(() => torrentApi.getPeers(file!))} className="btn btn-info" disabled={loading || downloading}>
                        Get Peers
                    </button>
                    <button onClick={handleDownload} className="btn btn-primary" disabled={loading || downloading}>
                        {downloading ? 'Downloading…' : 'Download'}
                    </button>
                </div>
            </div>

            {loading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Processing...</p>
                </div>
            )}

            {downloadStatus && <DownloadProgress status={downloadStatus} />}

            {result && (
                <div className="card result-card">
                    <h3>Result</h3>
                    <pre className={(result as any).error ? 'error' : ''}>
                        {JSON.stringify(result, null, 2)}
                    </pre>
                </div>
            )}
        </div>
    );
};

export default TorrentTab;
