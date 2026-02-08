import React, { useState } from 'react';
import { torrentApi, MagnetParseResponse, TorrentInfoResponse, DownloadResponse } from '../services/api';

const MagnetTab: React.FC = () => {
    const [magnetUrl, setMagnetUrl] = useState('');
    const [result, setResult] = useState<MagnetParseResponse | TorrentInfoResponse | DownloadResponse | null>(null);
    const [loading, setLoading] = useState(false);

    const handleParse = async () => {
        if (!magnetUrl.trim()) {
            alert('Please enter a magnet URL');
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.parseMagnet(magnetUrl);
            setResult(response);
        } catch (error) {
            setResult({ error: (error as Error).message } as any);
        } finally {
            setLoading(false);
        }
    };

    const handleGetInfo = async () => {
        if (!magnetUrl.trim()) {
            alert('Please enter a magnet URL');
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.getMagnetInfo(magnetUrl);
            setResult(response);
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

        if (!window.confirm('Start downloading from this magnet link?')) {
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.downloadMagnet(magnetUrl);
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
                    <button onClick={handleParse} className="btn btn-info" disabled={loading}>
                        Parse
                    </button>
                    <button onClick={handleGetInfo} className="btn btn-info" disabled={loading}>
                        Get Info
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

export default MagnetTab;
