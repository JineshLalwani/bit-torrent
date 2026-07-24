import React, { useRef, useState } from 'react';
import { formatFileSize } from '../utils/format';

interface FileDropZoneProps {
    file: File | null;
    onFile: (file: File) => void;
    accept?: string;
    hint: string;
}

/** Click-or-drag file picker used by the Torrent and File Hub tabs. */
const FileDropZone: React.FC<FileDropZoneProps> = ({ file, onFile, accept, hint }) => {
    const inputRef = useRef<HTMLInputElement>(null);
    const [dragOver, setDragOver] = useState(false);

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            onFile(e.dataTransfer.files[0]);
        }
    };

    return (
        <div
            className={`dropzone ${dragOver ? 'dropzone-over' : ''} ${file ? 'dropzone-filled' : ''}`}
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={handleDrop}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') inputRef.current?.click(); }}
        >
            <input
                ref={inputRef}
                type="file"
                accept={accept}
                style={{ display: 'none' }}
                onChange={(e) => {
                    if (e.target.files && e.target.files[0]) onFile(e.target.files[0]);
                    e.target.value = '';
                }}
            />
            <div className="dropzone-icon">{file ? '📄' : '📥'}</div>
            {file ? (
                <>
                    <div className="dropzone-title">{file.name}</div>
                    <div className="dropzone-hint">{formatFileSize(file.size)} — click or drop to replace</div>
                </>
            ) : (
                <>
                    <div className="dropzone-title">Drop a file here or click to browse</div>
                    <div className="dropzone-hint">{hint}</div>
                </>
            )}
        </div>
    );
};

export default FileDropZone;
