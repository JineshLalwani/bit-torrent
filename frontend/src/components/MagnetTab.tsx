import React, { useState } from 'react';
import { torrentApi, MagnetParseResponse, TorrentInfoResponse } from '../services/api';
import { useAsyncDownload } from '../hooks/useAsyncDownload';
import DownloadProgress from './DownloadProgress';
import { CopyableValue, ErrorBanner, TorrentInfoView } from './ResultViews';
import { useToast } from './Toasts';

type Result =
    | { kind: 'parse'; data: MagnetParseResponse }
    | { kind: 'info'; data: TorrentInfoResponse }
    | { kind: 'error'; message: string };

const MagnetTab: React.FC = () => {
    const [magnetUrl, setMagnetUrl] = useState('');
    const [result, setResult] = useState<Result | null>(null);
    const [loading, setLoading] = useState(false);
    const [loadingLabel, setLoadingLabel] = useState('Processing…');
    const { status: downloadStatus, active: downloading, start: startDownload } = useAsyncDownload();
    const toast = useToast();

    const requireUrl = (): boolean => {
        if (!magnetUrl.trim()) {
            toast.info('Paste a magnet link first');
            return false;
        }
        if (!magnetUrl.trim().startsWith('magnet:')) {
            toast.error('That does not look like a magnet link (must start with magnet:)');
            return false;
        }
        return true;
    };

    const handleParse = async () => {
        if (!requireUrl()) return;
        setLoading(true);
        setLoadingLabel('Parsing magnet link…');
        try {
            const data = await torrentApi.parseMagnet(magnetUrl.trim());
            setResult(data.error ? { kind: 'error', message: data.error } : { kind: 'parse', data });
        } catch (error) {
            setResult({ kind: 'error', message: (error as Error).message });
        } finally {
            setLoading(false);
        }
    };

    const handleGetInfo = async () => {
        if (!requireUrl()) return;
        setLoading(true);
        setLoadingLabel('Fetching metadata from peers — this can take a little while…');
        try {
            const data = await torrentApi.getMagnetInfo(magnetUrl.trim());
            setResult(data.error ? { kind: 'error', message: data.error } : { kind: 'info', data });
        } catch (error) {
            setResult({ kind: 'error', message: (error as Error).message });
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async () => {
        if (!requireUrl()) return;
        setResult(null);
        await startDownload(() => torrentApi.startMagnetDownload(magnetUrl.trim()));
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Magnet Link</h2>
                <p className="card-sub">Paste any magnet link — or one copied from the File Hub.</p>
                <div className="input-group">
                    <label htmlFor="magnet-url">Magnet URL</label>
                    <input
                        type="text"
                        id="magnet-url"
                        value={magnetUrl}
                        onChange={(e) => setMagnetUrl(e.target.value)}
                        placeholder="magnet:?xt=urn:btih:…"
                        className="text-input mono"
                        spellCheck={false}
                    />
                </div>

                <div className="button-group">
                    <button onClick={handleParse} className="btn btn-info" disabled={loading || downloading}>
                        Parse
                    </button>
                    <button onClick={handleGetInfo} className="btn btn-info" disabled={loading || downloading}>
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
                    <p>{loadingLabel}</p>
                </div>
            )}

            {downloadStatus && <DownloadProgress status={downloadStatus} />}

            {result?.kind === 'error' && <ErrorBanner message={result.message} />}
            {result?.kind === 'info' && <TorrentInfoView info={result.data} title="Magnet Metadata" />}
            {result?.kind === 'parse' && (
                <div className="card result-card">
                    <h3>Parsed Magnet</h3>
                    <div className="info-grid">
                        <div className="info-row">
                            <span className="info-label">Info Hash</span>
                            <span className="info-value mono"><CopyableValue value={result.data.infoHash} label="Info hash" /></span>
                        </div>
                        {result.data.trackerUrl && (
                            <div className="info-row">
                                <span className="info-label">Tracker</span>
                                <span className="info-value mono">{result.data.trackerUrl}</span>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
};

export default MagnetTab;
