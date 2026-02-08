const API_BASE_URL = '/api/torrent';

// Tab switching
function showTab(tabName) {
    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    
    // Remove active class from all buttons
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    
    // Show selected tab
    document.getElementById(`${tabName}-tab`).classList.add('active');
    event.target.classList.add('active');
}

// File upload handling
const fileInput = document.getElementById('torrent-file');
const uploadArea = document.getElementById('upload-area');
const fileNameDisplay = document.getElementById('file-name');

fileInput.addEventListener('change', (e) => {
    if (e.target.files.length > 0) {
        fileNameDisplay.textContent = `Selected: ${e.target.files[0].name}`;
    }
});

// Drag and drop
uploadArea.addEventListener('dragover', (e) => {
    e.preventDefault();
    uploadArea.style.background = '#f8f9ff';
});

uploadArea.addEventListener('dragleave', () => {
    uploadArea.style.background = '';
});

uploadArea.addEventListener('drop', (e) => {
    e.preventDefault();
    uploadArea.style.background = '';
    
    if (e.dataTransfer.files.length > 0) {
        fileInput.files = e.dataTransfer.files;
        fileNameDisplay.textContent = `Selected: ${e.dataTransfer.files[0].name}`;
    }
});

// Show/hide loading
function showLoading() {
    document.getElementById('loading').style.display = 'flex';
}

function hideLoading() {
    document.getElementById('loading').style.display = 'none';
}

// Display result
function displayResult(elementId, contentId, data) {
    const resultElement = document.getElementById(elementId);
    const contentElement = document.getElementById(contentId);
    
    if (data.error) {
        contentElement.textContent = `Error: ${data.error}`;
        contentElement.style.color = '#e74c3c';
    } else {
        contentElement.textContent = JSON.stringify(data, null, 2);
        contentElement.style.color = '#333';
    }
    
    resultElement.style.display = 'block';
    resultElement.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

// Torrent File Functions
async function getTorrentInfo() {
    const file = fileInput.files[0];
    if (!file) {
        alert('Please select a torrent file first');
        return;
    }
    
    showLoading();
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        const response = await fetch(`${API_BASE_URL}/info`, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        displayResult('torrent-result', 'torrent-result-content', data);
    } catch (error) {
        displayResult('torrent-result', 'torrent-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

async function getPeers() {
    const file = fileInput.files[0];
    if (!file) {
        alert('Please select a torrent file first');
        return;
    }
    
    showLoading();
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        const response = await fetch(`${API_BASE_URL}/peers`, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        displayResult('torrent-result', 'torrent-result-content', data);
    } catch (error) {
        displayResult('torrent-result', 'torrent-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

async function downloadTorrent() {
    const file = fileInput.files[0];
    if (!file) {
        alert('Please select a torrent file first');
        return;
    }
    
    if (!confirm('Start downloading this torrent?')) {
        return;
    }
    
    showLoading();
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        const response = await fetch(`${API_BASE_URL}/download`, {
            method: 'POST',
            body: formData
        });
        const data = await response.json();
        displayResult('torrent-result', 'torrent-result-content', data);
    } catch (error) {
        displayResult('torrent-result', 'torrent-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

// Magnet Link Functions
async function parseMagnet() {
    const magnetUrl = document.getElementById('magnet-url').value.trim();
    if (!magnetUrl) {
        alert('Please enter a magnet URL');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/magnet/parse`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        const data = await response.json();
        displayResult('magnet-result', 'magnet-result-content', data);
    } catch (error) {
        displayResult('magnet-result', 'magnet-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

async function getMagnetInfo() {
    const magnetUrl = document.getElementById('magnet-url').value.trim();
    if (!magnetUrl) {
        alert('Please enter a magnet URL');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/magnet/info`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        const data = await response.json();
        displayResult('magnet-result', 'magnet-result-content', data);
    } catch (error) {
        displayResult('magnet-result', 'magnet-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

async function downloadMagnet() {
    const magnetUrl = document.getElementById('magnet-url').value.trim();
    if (!magnetUrl) {
        alert('Please enter a magnet URL');
        return;
    }
    
    if (!confirm('Start downloading from this magnet link?')) {
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/magnet/download`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ magnetUrl })
        });
        const data = await response.json();
        displayResult('magnet-result', 'magnet-result-content', data);
    } catch (error) {
        displayResult('magnet-result', 'magnet-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}

// Decode Function
async function decodeBencode() {
    const bencodedValue = document.getElementById('bencode-input').value.trim();
    if (!bencodedValue) {
        alert('Please enter a bencoded value');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/decode`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ bencodedValue })
        });
        const data = await response.json();
        displayResult('decode-result', 'decode-result-content', data);
    } catch (error) {
        displayResult('decode-result', 'decode-result-content', 
            { error: error.message });
    } finally {
        hideLoading();
    }
}
