'use client';

import { DIFFICULTIES, DIFFICULTY_LABELS } from '@/lib/constants';
import { DIFFICULTY_WEIGHTS } from '@/lib/dictation';
import type { Difficulty } from '@/lib/types';
import clsx from 'clsx';
import { useEffect, useRef, useState } from 'react';

const DESCRIPTIONS: Record<Difficulty, string> = {
  Easy: 'Điền sẵn khoảng một nửa số từ',
  Normal: 'Điền sẵn mỗi từ thứ 4',
  Hard: 'Gõ toàn bộ câu',
};

/** Settings popover with the Easy/Normal/Hard switch (DIC-06). Non-modal, closes on outside click/Esc. */
export function DifficultyPopover({
  value,
  onChange,
}: {
  value: Difficulty;
  onChange: (d: Difficulty) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    function onDown(e: MouseEvent | TouchEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onDown);
    document.addEventListener('touchstart', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('touchstart', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-haspopup="true"
        className="inline-flex h-11 items-center gap-1.5 rounded-full border border-slate-300 bg-white px-3 text-sm text-slate-700 hover:bg-slate-50"
        title="Cài đặt độ khó"
      >
        <span aria-hidden>⚙</span>
        <span>{DIFFICULTY_LABELS[value]}</span>
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-2 w-64 max-w-[calc(100vw-2rem)] rounded-xl border border-slate-200 bg-white p-2 shadow-lg">
          <p className="px-2 pb-1 pt-1 text-xs font-semibold uppercase tracking-wide text-slate-400">Độ khó</p>
          <div role="radiogroup" aria-label="Độ khó" className="flex flex-col">
            {DIFFICULTIES.map((d) => (
              <button
                key={d}
                type="button"
                role="radio"
                aria-checked={value === d}
                onClick={() => {
                  onChange(d);
                  setOpen(false);
                }}
                className={clsx(
                  'flex min-h-[44px] flex-col items-start rounded-lg px-3 py-2 text-left',
                  value === d ? 'bg-brand-50 text-brand-700' : 'text-slate-700 hover:bg-slate-50'
                )}
              >
                <span className="text-sm font-medium">
                  {DIFFICULTY_LABELS[d]} ({d}) · tối đa {Math.round(DIFFICULTY_WEIGHTS[d] * 100)} điểm
                </span>
                <span className="text-xs text-slate-500">{DESCRIPTIONS[d]}</span>
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
