const API_BASE_URL = 'http://localhost:8080/api/torrent';

export interface TorrentInfoResponse {
    trackerURL: string;
    length: number;
    infoHash: string;
    numberOfPieces: number;
    pieceLength: number;
    error?: string;
}

export interface PeerListResponse {
    peers: string[];
    error?: string;
}

export interface DownloadResponse {
    message: string;
    outputPath: string;
    numberOfPieces: number;
    error?: string;
}

export interface MagnetParseResponse {
    trackerURL: string;
    infoHash: string;
    error?: string;
}

export interface DecodeResponse {
    decoded: string;
    error?: string;
}

export const torrentApi = {
    getTorrentInfo: async (file: File): Promise<TorrentInfoResponse> => {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${API_BASE_URL}/info`, {
            method: 'POST',
            body: formData
        });
        return response.json();
    },

    getPeers: async (file: File): Promise<PeerListResponse> => {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${API_BASE_URL}/peers`, {
            method: 'POST',
            body: formData
        });
        return response.json();
    },

    downloadTorrent: async (file: File): Promise<DownloadResponse> => {
        const formData = new FormData();
        formData.append('file', file);

        const response = await fetch(`${API_BASE_URL}/download`, {
            method: 'POST',
            body: formData
        });
        return response.json();
    },

    parseMagnet: async (magnetUrl: string): Promise<MagnetParseResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/parse`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        return response.json();
    },

    getMagnetInfo: async (magnetUrl: string): Promise<TorrentInfoResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/info`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        return response.json();
    },

    downloadMagnet: async (magnetUrl: string): Promise<DownloadResponse> => {
        const response = await fetch(`${API_BASE_URL}/magnet/download`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        return response.json();
    },

    decodeBencode: async (bencodedValue: string): Promise<DecodeResponse> => {
        const response = await fetch(`${API_BASE_URL}/decode`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ bencodedValue })
        });
        return response.json();
    }
};
