import React, { useState } from 'react';
import { torrentApi, TorrentInfoResponse, PeerListResponse } from '../services/api';
import { useAsyncDownload } from '../hooks/useAsyncDownload';
import DownloadProgress from './DownloadProgress';
import FileDropZone from './FileDropZone';
import { ErrorBanner, TorrentInfoView, PeerListView } from './ResultViews';
import { useToast } from './Toasts';

type Result =
    | { kind: 'info'; data: TorrentInfoResponse }
    | { kind: 'peers'; data: PeerListResponse }
    | { kind: 'error'; message: string };

const TorrentTab: React.FC = () => {
    const [file, setFile] = useState<File | null>(null);
    const [result, setResult] = useState<Result | null>(null);
    const [loading, setLoading] = useState(false);
    const { status: downloadStatus, active: downloading, start: startDownload } = useAsyncDownload();
    const toast = useToast();

    const requireFile = (): boolean => {
        if (!file) {
            toast.info('Select a .torrent file first');
            return false;
        }
        return true;
    };

    const handleGetInfo = async () => {
        if (!requireFile()) return;
        setLoading(true);
        try {
            const data = await torrentApi.getTorrentInfo(file!);
            setResult(data.error ? { kind: 'error', message: data.error } : { kind: 'info', data });
        } catch (error) {
            setResult({ kind: 'error', message: (error as Error).message });
        } finally {
            setLoading(false);
        }
    };

    const handleGetPeers = async () => {
        if (!requireFile()) return;
        setLoading(true);
        try {
            const data = await torrentApi.getPeers(file!);
            setResult(data.error ? { kind: 'error', message: data.error } : { kind: 'peers', data });
        } catch (error) {
            setResult({ kind: 'error', message: (error as Error).message });
        } finally {
            setLoading(false);
        }
    };

    const handleDownload = async () => {
        if (!requireFile()) return;
        setResult(null);
        await startDownload(() => torrentApi.startTorrentDownload(file!));
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Torrent File</h2>
                <p className="card-sub">Inspect a .torrent file, list its peers, or download its content.</p>
                <FileDropZone
                    file={file}
                    onFile={(f) => { setFile(f); setResult(null); }}
                    accept=".torrent"
                    hint="Only .torrent files"
                />
                <div className="button-group">
                    <button onClick={handleGetInfo} className="btn btn-info" disabled={loading || downloading}>
                        Get Info
                    </button>
                    <button onClick={handleGetPeers} className="btn btn-info" disabled={loading || downloading}>
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
                    <p>Talking to trackers…</p>
                </div>
            )}

            {downloadStatus && <DownloadProgress status={downloadStatus} />}

            {result?.kind === 'error' && <ErrorBanner message={result.message} />}
            {result?.kind === 'info' && <TorrentInfoView info={result.data} />}
            {result?.kind === 'peers' && <PeerListView peers={result.data.peers || []} />}
        </div>
    );
};

export default TorrentTab;
