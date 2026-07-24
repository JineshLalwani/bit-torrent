const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8080/api/torrent';
const FILES_API_URL = process.env.REACT_APP_API_URL
    ? process.env.REACT_APP_API_URL.replace('/api/torrent', '/api/files')
    : 'http://localhost:8080/api/files';

export interface TorrentInfoResponse {
    trackerUrl: string;
    name?: string;
    length: number;
    infoHash: string;
    pieceCount: number;
    pieceLength: number;
    error?: string;
}

export interface PeerListResponse {
    peers: string[];
    error?: string;
}

export interface DownloadResponse {
    message: string;
    filePath: string;
    totalPieces: number;
    error?: string;
}

export type DownloadStatus = 'PENDING' | 'FETCHING_METADATA' | 'DOWNLOADING' | 'COMPLETED' | 'FAILED';

export interface DownloadStatusResponse {
    downloadId: string;
    fileName: string;
    filePath: string;
    status: DownloadStatus;
    totalPieces: number;
    completedPieces: number;
    progress: number;
    createdAt: number;
    error?: string;
}

export interface MagnetParseResponse {
    trackerUrl: string;
    infoHash: string;
    error?: string;
}

export interface DecodeResponse {
    decoded: string;
    error?: string;
}

/** Parses a JSON response, surfacing server errors as readable messages. */
async function asJson<T>(response: Response): Promise<T> {
    const text = await response.text();
    try {
        return JSON.parse(text) as T;
    } catch {
        throw new Error(response.ok
            ? 'Unexpected non-JSON response from server'
            : `Server error (HTTP ${response.status})`);
    }
}

/** Fetches a server file and triggers a browser download. */
async function saveBlob(response: Response, fileName: string): Promise<void> {
    if (!response.ok) {
        throw new Error(`File fetch failed (HTTP ${response.status})`);
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    a.remove();
}

export const torrentApi = {
    getTorrentInfo: async (file: File): Promise<TorrentInfoResponse> => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE_URL}/info`, { method: 'POST', body: formData });
        return asJson(response);
    },

    getPeers: async (file: File): Promise<PeerListResponse> => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE_URL}/peers`, { method: 'POST', body: formData });
        return asJson(response);
    },

    startTorrentDownload: async (file: File): Promise<DownloadStatusResponse> => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${API_BASE_URL}/download/start`, { method: 'POST', body: formData });
        return asJson(response);
    },

    startMagnetDownload: async (magnetUrl: string): Promise<DownloadStatusResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/download/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ magnetUrl })
        });
        return asJson(response);
    },

    getDownloadStatus: async (downloadId: string): Promise<DownloadStatusResponse> => {
        const response = await fetch(`${API_BASE_URL}/download/status/${downloadId}`);
        return asJson(response);
    },

    listDownloads: async (): Promise<DownloadStatusResponse[]> => {
        const response = await fetch(`${API_BASE_URL}/downloads`);
        return asJson(response);
    },

    fetchDownloadedFile: async (filePath: string): Promise<void> => {
        const response = await fetch(`${API_BASE_URL}/file?path=${encodeURIComponent(filePath)}`);
        await saveBlob(response, filePath.split('/').pop() || 'download');
    },

    parseMagnet: async (magnetUrl: string): Promise<MagnetParseResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/parse`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ magnetUrl })
        });
        return asJson(response);
    },

    getMagnetInfo: async (magnetUrl: string): Promise<TorrentInfoResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/info`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ magnetUrl })
        });
        return asJson(response);
    },

    decodeBencode: async (bencodedValue: string): Promise<DecodeResponse> => {
        const response = await fetch(`${API_BASE_URL}/decode`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ bencodedValue })
        });
        return asJson(response);
    }
};

export interface SharedFileInfo {
    id: string;
    fileName: string;
    fileSize: number;
    infoHash: string;
    pieceCount: number;
    pieceLength: number;
    uploadedAt: string;
    magnetLink?: string;
    error?: string;
}

export interface FileListResponse {
    files: SharedFileInfo[];
    error?: string;
}

export const fileShareApi = {
    uploadFile: async (file: File): Promise<SharedFileInfo> => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await fetch(`${FILES_API_URL}/upload`, { method: 'POST', body: formData });
        return asJson(response);
    },

    listFiles: async (): Promise<FileListResponse> => {
        const response = await fetch(`${FILES_API_URL}/list`);
        return asJson(response);
    },

    downloadFile: async (fileId: string, fileName: string): Promise<void> => {
        const response = await fetch(`${FILES_API_URL}/download/${fileId}`);
        await saveBlob(response, fileName);
    },

    downloadTorrentFile: async (fileId: string, fileName: string): Promise<void> => {
        const response = await fetch(`${FILES_API_URL}/torrent/${fileId}`);
        await saveBlob(response, `${fileName}.torrent`);
    }
};
