import React from 'react';
import { TorrentInfoResponse } from '../services/api';
import { copyToClipboard, formatFileSize } from '../utils/format';
import { useToast } from './Toasts';

export const ErrorBanner: React.FC<{ message: string }> = ({ message }) => (
    <div className="error-banner">
        <span className="error-banner-icon">⚠</span>
        <span>{message}</span>
    </div>
);

/** Monospace value with a copy button (info hashes, magnet links, peers). */
export const CopyableValue: React.FC<{ value: string; label?: string }> = ({ value, label }) => {
    const toast = useToast();
    return (
        <span className="copyable">
            <span className="copyable-text">{value}</span>
            <button
                className="btn-icon"
                title={`Copy ${label || 'value'}`}
                onClick={async () => {
                    if (await copyToClipboard(value)) toast.success(`${label || 'Value'} copied`);
                }}
            >
                ⧉
            </button>
        </span>
    );
};

export const TorrentInfoView: React.FC<{ info: TorrentInfoResponse; title?: string }> = ({ info, title }) => (
    <div className="card result-card">
        <h3>{title || 'Torrent Info'}</h3>
        <div className="info-grid">
            {info.name && (
                <div className="info-row">
                    <span className="info-label">Name</span>
                    <span className="info-value">{info.name}</span>
                </div>
            )}
            <div className="info-row">
                <span className="info-label">Size</span>
                <span className="info-value">{formatFileSize(info.length)} <span className="info-sub">({info.length?.toLocaleString()} bytes)</span></span>
            </div>
            <div className="info-row">
                <span className="info-label">Info Hash</span>
                <span className="info-value mono"><CopyableValue value={info.infoHash} label="Info hash" /></span>
            </div>
            <div className="info-row">
                <span className="info-label">Pieces</span>
                <span className="info-value">{info.pieceCount} × {formatFileSize(info.pieceLength)}</span>
            </div>
            {info.trackerUrl && (
                <div className="info-row">
                    <span className="info-label">Tracker</span>
                    <span className="info-value mono">{info.trackerUrl}</span>
                </div>
            )}
        </div>
    </div>
);

export const PeerListView: React.FC<{ peers: string[] }> = ({ peers }) => (
    <div className="card result-card">
        <h3>Peers <span className="count-badge">{peers.length}</span></h3>
        {peers.length === 0 ? (
            <p className="muted">The tracker returned no peers for this torrent.</p>
        ) : (
            <div className="peer-chips">
                {peers.map((peer, i) => (
                    <span key={i} className="peer-chip">{peer}</span>
                ))}
            </div>
        )}
    </div>
);
