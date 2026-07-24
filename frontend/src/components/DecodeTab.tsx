import React, { useState } from 'react';
import { torrentApi } from '../services/api';
import { copyToClipboard } from '../utils/format';
import { ErrorBanner } from './ResultViews';
import { useToast } from './Toasts';

const EXAMPLES: { label: string; value: string }[] = [
    { label: 'String', value: '5:hello' },
    { label: 'Integer', value: 'i52e' },
    { label: 'List', value: 'l5:helloi52ee' },
    { label: 'Dictionary', value: 'd3:foo3:bar5:helloi52ee' }
];

const DecodeTab: React.FC = () => {
    const [bencodedValue, setBencodedValue] = useState('');
    const [decoded, setDecoded] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const toast = useToast();

    const handleDecode = async (value?: string) => {
        const input = (value ?? bencodedValue).trim();
        if (!input) {
            toast.info('Enter a bencoded value first');
            return;
        }
        setLoading(true);
        setDecoded(null);
        setError(null);
        try {
            const response = await torrentApi.decodeBencode(input);
            if (response.error) {
                setError(response.error);
            } else {
                // Pretty-print if it parses as JSON, otherwise show raw
                try {
                    setDecoded(JSON.stringify(JSON.parse(response.decoded), null, 2));
                } catch {
                    setDecoded(response.decoded);
                }
            }
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="tab-content">
            <div className="card">
                <h2>Bencode Decoder</h2>
                <p className="card-sub">Bencode is the encoding torrent files use. Paste any bencoded value to see it as JSON.</p>
                <div className="input-group">
                    <label htmlFor="bencode-input">Bencoded Value</label>
                    <textarea
                        id="bencode-input"
                        rows={4}
                        value={bencodedValue}
                        onChange={(e) => setBencodedValue(e.target.value)}
                        placeholder="d3:foo3:bar5:helloi52ee"
                        className="text-input mono bencode-textarea"
                        spellCheck={false}
                    />
                </div>

                <div className="example-chips">
                    <span className="muted">Try:</span>
                    {EXAMPLES.map(example => (
                        <button
                            key={example.label}
                            className="chip"
                            onClick={() => { setBencodedValue(example.value); handleDecode(example.value); }}
                        >
                            {example.label}
                        </button>
                    ))}
                </div>

                <div className="button-group">
                    <button onClick={() => handleDecode()} className="btn btn-primary" disabled={loading}>
                        {loading ? 'Decoding…' : 'Decode'}
                    </button>
                </div>
            </div>

            {error && <ErrorBanner message={error} />}

            {decoded !== null && (
                <div className="card result-card">
                    <div className="card-header">
                        <h3>Decoded JSON</h3>
                        <button
                            className="btn-ghost"
                            onClick={async () => {
                                if (await copyToClipboard(decoded)) toast.success('JSON copied');
                            }}
                        >
                            Copy
                        </button>
                    </div>
                    <pre className="code-block">{decoded}</pre>
                </div>
            )}
        </div>
    );
};

export default DecodeTab;
