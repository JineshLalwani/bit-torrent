import React, { createContext, useCallback, useContext, useRef, useState } from 'react';

type ToastKind = 'success' | 'error' | 'info';

interface Toast {
    id: number;
    kind: ToastKind;
    message: string;
    leaving?: boolean;
}

interface ToastApi {
    success: (message: string) => void;
    error: (message: string) => void;
    info: (message: string) => void;
}

const ToastContext = createContext<ToastApi>({
    success: () => {},
    error: () => {},
    info: () => {}
});

export const useToast = () => useContext(ToastContext);

const ICONS: Record<ToastKind, string> = { success: '✓', error: '✕', info: 'ℹ' };
const AUTO_DISMISS_MS = 4000;

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [toasts, setToasts] = useState<Toast[]>([]);
    const nextId = useRef(1);

    const dismiss = useCallback((id: number) => {
        setToasts(prev => prev.map(t => (t.id === id ? { ...t, leaving: true } : t)));
        setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 250);
    }, []);

    const push = useCallback((kind: ToastKind, message: string) => {
        const id = nextId.current++;
        setToasts(prev => [...prev.slice(-4), { id, kind, message }]);
        setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    }, [dismiss]);

    const api = useRef<ToastApi>({
        success: (m: string) => push('success', m),
        error: (m: string) => push('error', m),
        info: (m: string) => push('info', m)
    });
    // Keep the stable api object pointing at the latest push
    api.current.success = (m: string) => push('success', m);
    api.current.error = (m: string) => push('error', m);
    api.current.info = (m: string) => push('info', m);

    return (
        <ToastContext.Provider value={api.current}>
            {children}
            <div className="toast-stack" role="status" aria-live="polite">
                {toasts.map(t => (
                    <div key={t.id} className={`toast toast-${t.kind} ${t.leaving ? 'toast-leaving' : ''}`}
                         onClick={() => dismiss(t.id)}>
                        <span className="toast-icon">{ICONS[t.kind]}</span>
                        <span className="toast-message">{t.message}</span>
                    </div>
                ))}
            </div>
        </ToastContext.Provider>
    );
};
