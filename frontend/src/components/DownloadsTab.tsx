import React, { useCallback, useEffect, useState } from 'react';
import { torrentApi, DownloadStatusResponse } from '../services/api';
import { formatRelativeTime } from '../utils/format';
import { useToast } from './Toasts';

const ACTIVE_STATUSES = ['PENDING', 'FETCHING_METADATA', 'DOWNLOADING'];

const STATUS_LABELS: Record<string, string> = {
    PENDING: 'Queued',
    FETCHING_METADATA: 'Fetching metadata',
    DOWNLOADING: 'Downloading',
    COMPLETED: 'Completed',
    FAILED: 'Failed'
};

const DownloadsTab: React.FC = () => {
    const [jobs, setJobs] = useState<DownloadStatusResponse[] | null>(null);
    const [saving, setSaving] = useState<string | null>(null);
    const toast = useToast();

    const load = useCallback(async () => {
        try {
            setJobs(await torrentApi.listDownloads());
        } catch {
            // server unreachable — keep whatever we had
        }
    }, []);

    useEffect(() => {
        load();
        const timer = setInterval(load, 2500);
        return () => clearInterval(timer);
    }, [load]);

    const handleSave = async (job: DownloadStatusResponse) => {
        setSaving(job.downloadId);
        try {
            await torrentApi.fetchDownloadedFile(job.filePath);
            toast.success(`Saved ${job.fileName}`);
        } catch (e) {
            toast.error((e as Error).message);
        } finally {
            setSaving(null);
        }
    };

    const active = jobs?.filter(j => ACTIVE_STATUSES.includes(j.status)).length || 0;

    return (
        <div className="tab-content">
            <div className="card">
                <div className="card-header">
                    <h2>Downloads {active > 0 && <span className="count-badge">{active} active</span>}</h2>
                    <button onClick={load} className="btn-ghost">Refresh</button>
                </div>

                {jobs === null ? (
                    <div className="empty-state"><p>Loading…</p></div>
                ) : jobs.length === 0 ? (
                    <div className="empty-state">
                        <div className="empty-icon">⬇️</div>
                        <p>No downloads yet.</p>
                        <p className="muted">Start one from the Torrent or Magnet tab — it keeps running here even if you switch tabs.</p>
                    </div>
                ) : (
                    <div className="job-list">
                        {jobs.map(job => {
                            const failed = job.status === 'FAILED';
                            const completed = job.status === 'COMPLETED';
                            const running = ACTIVE_STATUSES.includes(job.status);
                            return (
                                <div key={job.downloadId} className="job-row">
                                    <div className="job-top">
                                        <span className="job-name">{job.fileName || 'Download'}</span>
                                        <span className="job-meta">
                                            <span className={`status-badge ${failed ? 'failed' : completed ? 'completed' : 'running'}`}>
                                                {STATUS_LABELS[job.status] || job.status}
                                            </span>
                                            <span className="job-time">{formatRelativeTime(job.createdAt)}</span>
                                        </span>
                                    </div>
                                    <div className="progress-track slim">
                                        <div
                                            className={`progress-fill ${failed ? 'failed' : ''} ${job.status === 'FETCHING_METADATA' ? 'indeterminate' : ''}`}
                                            style={{ width: `${completed ? 100 : Math.min(100, job.progress || 0)}%` }}
                                        />
                                    </div>
                                    <div className="job-bottom">
                                        <span className="muted">
                                            {job.totalPieces > 0
                                                ? `${job.completedPieces} / ${job.totalPieces} pieces`
                                                : running ? 'Starting…' : ''}
                                            {failed && job.error ? ` — ${job.error}` : ''}
                                        </span>
                                        {completed && job.filePath && (
                                            <button
                                                className="btn-download"
                                                disabled={saving === job.downloadId}
                                                onClick={() => handleSave(job)}
                                            >
                                                {saving === job.downloadId ? 'Saving…' : 'Save file'}
                                            </button>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};

export default DownloadsTab;
