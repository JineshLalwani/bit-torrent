import React from 'react';
import { DownloadStatusResponse } from '../services/api';

const STATUS_LABELS: Record<string, string> = {
    PENDING: 'Queued…',
    FETCHING_METADATA: 'Fetching metadata from peers…',
    DOWNLOADING: 'Downloading pieces…',
    COMPLETED: 'Completed — saved via your browser',
    FAILED: 'Failed'
};

const DownloadProgress: React.FC<{ status: DownloadStatusResponse }> = ({ status }) => {
    const percent = Math.max(0, Math.min(100, status.progress || 0));
    const failed = status.status === 'FAILED' || !!status.error;
    const completed = status.status === 'COMPLETED' && !status.error;

    return (
        <div className="card download-progress">
            <div className="progress-header">
                <span className="progress-name">{status.fileName || 'Download'}</span>
                <span className={`status-badge ${failed ? 'failed' : completed ? 'completed' : 'running'}`}>
                    {failed ? 'Failed' : STATUS_LABELS[status.status] || status.status}
                </span>
            </div>
            <div className="progress-track">
                <div
                    className={`progress-fill ${failed ? 'failed' : ''} ${status.status === 'FETCHING_METADATA' ? 'indeterminate' : ''}`}
                    style={{ width: `${completed ? 100 : percent}%` }}
                />
            </div>
            <div className="progress-meta">
                {status.totalPieces > 0 && (
                    <span>{status.completedPieces} / {status.totalPieces} pieces</span>
                )}
                <span>{completed ? 100 : percent}%</span>
            </div>
            {status.error && <pre className="error progress-error">{status.error}</pre>}
        </div>
    );
};

export default DownloadProgress;
