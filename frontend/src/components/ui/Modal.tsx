'use client';

import { useEffect } from 'react';

export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
}) {
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    if (open) document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          {title && <h2 className="text-lg font-semibold text-slate-900">{title}</h2>}
          <button onClick={onClose} className="ml-auto text-slate-400 hover:text-slate-600" aria-label="Đóng">
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
