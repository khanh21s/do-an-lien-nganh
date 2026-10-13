'use client';

import { FLASHCARD_SHORTCUTS, ShortcutHelp } from '@/components/practice/ShortcutHelp';
import clsx from 'clsx';

/** Typing-mode switch (VOC-09) + shortcut help, shown above the study card. */
export function StudyToolbar({
  typingMode,
  onTypingModeChange,
  children,
}: {
  typingMode: boolean;
  onTypingModeChange: (value: boolean) => void;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-end gap-2">
      {children}
      <button
        type="button"
        role="switch"
        aria-checked={typingMode}
        onClick={() => onTypingModeChange(!typingMode)}
        className="inline-flex min-h-[44px] items-center gap-2 rounded-lg px-2 text-sm text-slate-600 hover:bg-slate-100"
      >
        <span
          className={clsx(
            'relative inline-block h-5 w-9 rounded-full transition-colors',
            typingMode ? 'bg-brand-600' : 'bg-slate-300'
          )}
        >
          <span
            className={clsx(
              'absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all',
              typingMode ? 'left-[18px]' : 'left-0.5'
            )}
          />
        </span>
        Chế độ gõ từ
      </button>
      <ShortcutHelp shortcuts={FLASHCARD_SHORTCUTS} />
    </div>
  );
}
