import React, { useEffect, useState } from 'react';
import TorrentTab from './components/TorrentTab';
import MagnetTab from './components/MagnetTab';
import DecodeTab from './components/DecodeTab';
import FileHubTab from './components/FileHubTab';
import DownloadsTab from './components/DownloadsTab';
import { ToastProvider } from './components/Toasts';
import './App.css';

type TabType = 'torrent' | 'magnet' | 'filehub' | 'downloads' | 'decode';

const TABS: { id: TabType; icon: string; label: string }[] = [
    { id: 'torrent', icon: '📄', label: 'Torrent' },
    { id: 'magnet', icon: '🧲', label: 'Magnet' },
    { id: 'filehub', icon: '🌐', label: 'File Hub' },
    { id: 'downloads', icon: '⬇️', label: 'Downloads' },
    { id: 'decode', icon: '🔍', label: 'Decode' }
];

type Theme = 'light' | 'dark';

const initialTheme = (): Theme => {
    const saved = localStorage.getItem('theme');
    if (saved === 'light' || saved === 'dark') return saved;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark' : 'light';
};

function App() {
    const [activeTab, setActiveTab] = useState<TabType>('torrent');
    const [theme, setTheme] = useState<Theme>(initialTheme);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        localStorage.setItem('theme', theme);
    }, [theme]);

    return (
        <ToastProvider>
            <div className="App">
                <div className="container">
                    <header>
                        <button
                            className="theme-toggle"
                            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
                            title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                            aria-label="Toggle color theme"
                        >
                            {theme === 'dark' ? '☀️' : '🌙'}
                        </button>
                        <h1><span className="logo-mark">⚡</span> BitTorrent Hub</h1>
                        <p className="subtitle">Download torrents & magnet links — share and seed your own files</p>
                    </header>

                    <nav className="tabs" role="tablist">
                        {TABS.map(tab => (
                            <button
                                key={tab.id}
                                role="tab"
                                aria-selected={activeTab === tab.id}
                                className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
                                onClick={() => setActiveTab(tab.id)}
                            >
                                <span className="tab-icon">{tab.icon}</span>
                                <span className="tab-label">{tab.label}</span>
                            </button>
                        ))}
                    </nav>

                    {activeTab === 'torrent' && <TorrentTab />}
                    {activeTab === 'magnet' && <MagnetTab />}
                    {activeTab === 'filehub' && <FileHubTab />}
                    {activeTab === 'downloads' && <DownloadsTab />}
                    {activeTab === 'decode' && <DecodeTab />}

                    <footer className="footer">
                        A BitTorrent client, tracker & seeder built from scratch in Java + React
                    </footer>
                </div>
            </div>
        </ToastProvider>
    );
}

export default App;
