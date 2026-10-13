'use client';

import clsx from 'clsx';
import { useEffect, useId, useRef } from 'react';

/**
 * Renders `role="dialog" aria-modal="true"`: `useHotkeys` relies on that to pause page shortcuts
 * while any modal is open (DIC-05).
 */
export function Modal({
  open,
  onClose,
  title,
  children,
  className,
}: {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: React.ReactNode;
  className?: string;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    if (open) document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  // Move focus into the dialog and restore it on close.
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();
    return () => previous?.focus?.();
  }, [open]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        tabIndex={-1}
        className={clsx(
          'max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-xl bg-white p-6 shadow-xl outline-none',
          className
        )}
      >
        <div className="mb-4 flex items-center justify-between">
          {title && (
            <h2 id={titleId} className="text-lg font-semibold text-slate-900">
              {title}
            </h2>
          )}
          <button
            type="button"
            onClick={onClose}
            className="-mr-2 ml-auto flex h-11 w-11 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            aria-label="Đóng"
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
