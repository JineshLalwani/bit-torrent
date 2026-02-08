import React, { useState } from 'react';
import { torrentApi, DecodeResponse } from '../services/api';

const DecodeTab: React.FC = () => {
    const [bencodedValue, setBencodedValue] = useState('');
    const [result, setResult] = useState<DecodeResponse | null>(null);
    const [loading, setLoading] = useState(false);

    const handleDecode = async () => {
        if (!bencodedValue.trim()) {
            alert('Please enter a bencoded value');
            return;
        }

        setLoading(true);
        try {
            const response = await torrentApi.decodeBencode(bencodedValue);
            setResult(response);
        } catch (error) {
            setResult({ decoded: '', error: (error as Error).message });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Decode Bencoded String</h2>
                <div className="input-group">
                    <label htmlFor="bencode-input">Bencoded Value</label>
                    <textarea
                        id="bencode-input"
                        rows={4}
                        value={bencodedValue}
                        onChange={(e) => setBencodedValue(e.target.value)}
                        placeholder="d3:foo3:bar5:helloi52ee"
                        className="bencode-textarea"
                    />
                </div>

                <div className="button-group">
                    <button onClick={handleDecode} className="btn btn-primary" disabled={loading}>
                        Decode
                    </button>
                </div>
            </div>

            {loading && (
                <div className="loading">
                    <div className="spinner"></div>
                    <p>Processing...</p>
                </div>
            )}

            {result && (
                <div className="card result-card">
                    <h3>Decoded Result</h3>
                    <pre className={result.error ? 'error' : ''}>
                        {result.error || result.decoded}
                    </pre>
                </div>
            )}
        </div>
    );
};

export default DecodeTab;
