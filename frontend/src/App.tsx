import React, { useState } from 'react';
import TorrentTab from './components/TorrentTab';
import MagnetTab from './components/MagnetTab';
import DecodeTab from './components/DecodeTab';
import './App.css';

type TabType = 'torrent' | 'magnet' | 'decode';

function App() {
  const [activeTab, setActiveTab] = useState<TabType>('torrent');

  return (
    <div className="App">
      <div className="container">
        <header>
          <h1>🌐 BitTorrent P2P File Transfer</h1>
          <p className="subtitle">Download files using torrents and magnet links</p>
        </header>

        <div className="tabs">
          <button
            className={`tab-btn ${activeTab === 'torrent' ? 'active' : ''}`}
            onClick={() => setActiveTab('torrent')}
          >
            Torrent File
          </button>
          <button
            className={`tab-btn ${activeTab === 'magnet' ? 'active' : ''}`}
            onClick={() => setActiveTab('magnet')}
          >
            Magnet Link
          </button>
          <button
            className={`tab-btn ${activeTab === 'decode' ? 'active' : ''}`}
            onClick={() => setActiveTab('decode')}
          >
            Decode
          </button>
        </div>

        {activeTab === 'torrent' && <TorrentTab />}
        {activeTab === 'magnet' && <MagnetTab />}
        {activeTab === 'decode' && <DecodeTab />}
      </div>
    </div>
  );
}

export default App;
