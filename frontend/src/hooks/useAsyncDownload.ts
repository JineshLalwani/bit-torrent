import { useCallback, useEffect, useRef, useState } from 'react';
import { DownloadStatusResponse, torrentApi } from '../services/api';

/**
 * Drives an asynchronous server-side download: starts it, polls progress,
 * and saves the finished file through the browser when it completes.
 */
export function useAsyncDownload() {
    const [status, setStatus] = useState<DownloadStatusResponse | null>(null);
    const [active, setActive] = useState(false);
    const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const savedRef = useRef(false);
    const mountedRef = useRef(true);

    useEffect(() => {
        mountedRef.current = true;
        return () => {
            mountedRef.current = false;
            if (timerRef.current) clearTimeout(timerRef.current);
        };
    }, []);

    const poll = useCallback(async (downloadId: string) => {
        if (!mountedRef.current) return;
        try {
            const s = await torrentApi.getDownloadStatus(downloadId);
            if (!mountedRef.current) return;
            setStatus(s);
            if (s.status === 'COMPLETED') {
                setActive(false);
                if (!savedRef.current && s.filePath) {
                    savedRef.current = true;
                    try {
                        await torrentApi.fetchDownloadedFile(s.filePath);
                    } catch (e) {
                        setStatus({ ...s, error: `Downloaded on server but saving failed: ${(e as Error).message}` });
                    }
                }
                return;
            }
            if (s.status === 'FAILED') {
                setActive(false);
                return;
            }
            timerRef.current = setTimeout(() => poll(downloadId), 1500);
        } catch (e) {
            if (!mountedRef.current) return;
            setActive(false);
            setStatus(prev => prev
                ? { ...prev, status: 'FAILED', error: (e as Error).message }
                : { status: 'FAILED', error: (e as Error).message } as DownloadStatusResponse);
        }
    }, []);

    const start = useCallback(async (starter: () => Promise<DownloadStatusResponse>) => {
        setActive(true);
        savedRef.current = false;
        setStatus(null);
        try {
            const s = await starter();
            if (!mountedRef.current) return;
            setStatus(s);
            if (s.error || !s.downloadId) {
                setActive(false);
                return;
            }
            timerRef.current = setTimeout(() => poll(s.downloadId), 1000);
        } catch (e) {
            if (!mountedRef.current) return;
            setActive(false);
            setStatus({ status: 'FAILED', error: (e as Error).message } as DownloadStatusResponse);
        }
    }, [poll]);

    return { status, active, start };
}
