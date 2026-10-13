'use client';

import { KbdCombo } from '@/components/ui/Kbd';
import { Modal } from '@/components/ui/Modal';
import { comboToLabels, useHotkeys } from '@/hooks/useHotkeys';
import clsx from 'clsx';
import { useState } from 'react';

export interface ShortcutItem {
  /** One or more alternative combos in `useHotkeys` syntax, e.g. `['Tab', 'Ctrl+Space']`. */
  combos: string[];
  description: string;
}

/** Dictation shortcuts (spec §3.3). */
export const DICTATION_SHORTCUTS: ShortcutItem[] = [
  { combos: ['Tab', 'Ctrl+Space'], description: 'Phát / tạm dừng' },
  { combos: ['`', 'Ctrl+R'], description: 'Nghe lại câu' },
  { combos: ['Enter'], description: 'Nộp bài / sang câu kế nếu đã đúng' },
  { combos: ['Shift+ArrowRight', 'Mod+ArrowRight'], description: 'Câu kế' },
  { combos: ['Shift+ArrowLeft', 'Mod+ArrowLeft'], description: 'Câu trước' },
  { combos: ['Ctrl+H'], description: 'Gợi ý 1 từ' },
];

/** Shadowing shortcuts (architecture §6.5). */
export const SHADOWING_SHORTCUTS: ShortcutItem[] = [
  { combos: ['Shift+`'], description: 'Ghi âm / dừng' },
  { combos: ['Space'], description: 'Nghe lại bản ghi' },
  { combos: ['Shift+ArrowRight'], description: 'Câu kế' },
  { combos: ['Shift+ArrowLeft'], description: 'Câu trước' },
];

/** Flashcard shortcuts (architecture §6.5). */
export const FLASHCARD_SHORTCUTS: ShortcutItem[] = [
  { combos: ['Space', 'Enter'], description: 'Lật thẻ' },
  { combos: ['1'], description: 'Học lại (Again)' },
  { combos: ['2'], description: 'Khó (Hard)' },
  { combos: ['3'], description: 'Tốt (Good)' },
  { combos: ['4'], description: 'Dễ (Easy)' },
];

/** "?" button (and `?` key) that lists the shortcuts of the current screen. */
export function ShortcutHelp({
  shortcuts,
  title = 'Phím tắt',
  className,
}: {
  shortcuts: ShortcutItem[];
  title?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);

  useHotkeys([{ combo: '?', handler: () => setOpen(true) }]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Xem phím tắt"
        title="Phím tắt (?)"
        className={clsx(
          'flex h-11 w-11 items-center justify-center rounded-full border border-slate-300 bg-white text-base font-semibold text-slate-600 hover:bg-slate-50',
          className
        )}
      >
        ?
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title={title}>
        <ul className="divide-y divide-slate-100">
          {shortcuts.map((s) => (
            <li key={s.description} className="flex items-center justify-between gap-4 py-2.5">
              <span className="text-sm text-slate-700">{s.description}</span>
              <span className="flex flex-wrap items-center justify-end gap-2">
                {s.combos.map((c, i) => (
                  <span key={c} className="inline-flex items-center gap-2">
                    {i > 0 && <span className="text-xs text-slate-400">hoặc</span>}
                    <KbdCombo keys={comboToLabels(c)} />
                  </span>
                ))}
              </span>
            </li>
          ))}
        </ul>
      </Modal>
    </>
  );
}
