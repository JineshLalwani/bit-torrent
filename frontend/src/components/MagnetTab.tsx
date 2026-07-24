import React, { useState } from 'react';
import { torrentApi, MagnetParseResponse, TorrentInfoResponse } from '../services/api';
import { useAsyncDownload } from '../hooks/useAsyncDownload';
import DownloadProgress from './DownloadProgress';

const MagnetTab: React.FC = () => {
    const [magnetUrl, setMagnetUrl] = useState('');
    const [result, setResult] = useState<MagnetParseResponse | TorrentInfoResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const { status: downloadStatus, active: downloading, start: startDownload } = useAsyncDownload();

    const runAction = async (action: () => Promise<MagnetParseResponse | TorrentInfoResponse>) => {
        if (!magnetUrl.trim()) {
            alert('Please enter a magnet URL');
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
        if (!magnetUrl.trim()) {
            alert('Please enter a magnet URL');
            return;
        }
        setResult(null);
        await startDownload(() => torrentApi.startMagnetDownload(magnetUrl));
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Magnet Link</h2>
                <div className="input-group">
                    <label htmlFor="magnet-url">Magnet URL</label>
                    <input
                        type="text"
                        id="magnet-url"
                        value={magnetUrl}
                        onChange={(e) => setMagnetUrl(e.target.value)}
                        placeholder="magnet:?xt=urn:btih:..."
                        className="magnet-input"
                    />
                </div>

                <div className="button-group">
                    <button onClick={() => runAction(() => torrentApi.parseMagnet(magnetUrl))} className="btn btn-info" disabled={loading || downloading}>
                        Parse
                    </button>
                    <button onClick={() => runAction(() => torrentApi.getMagnetInfo(magnetUrl))} className="btn btn-info" disabled={loading || downloading}>
                        Get Info
                    </button>
                    <button onClick={handleDownload} className="btn btn-primary" disabled={loading || downloading}>
                        {downloading ? 'Downloading…' : 'Download'}
                    </button>
                </div>
            </div>

            {loading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Processing... (magnet metadata can take a little while)</p>
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

export default MagnetTab;
