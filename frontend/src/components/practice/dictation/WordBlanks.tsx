'use client';

import type { DictationToken, WordCell } from '@/lib/dictation';
import clsx from 'clsx';
import { type MutableRefObject } from 'react';

export const DICTATION_INPUT_ATTR = 'data-dictation-input';

/**
 * Easy/Normal fill-in-the-blanks (DIC-06): one input per hidden word, punctuation kept outside.
 * `Space` → next blank, `Backspace` in an empty blank → previous blank. Revealed words (DIC-08) are
 * shown in amber and are no longer editable.
 */
export function WordBlanks({
  tokens,
  mask,
  typed,
  revealed,
  correctness,
  inputRefs,
  onType,
  onFocusWord,
}: {
  tokens: readonly DictationToken[];
  mask: readonly WordCell[];
  typed: readonly string[];
  revealed: ReadonlySet<number>;
  /** Per-word result after grading (`null` = unknown / not graded). */
  correctness: readonly (boolean | null)[] | null;
  inputRefs: MutableRefObject<(HTMLInputElement | null)[]>;
  onType: (wordIndex: number, value: string) => void;
  onFocusWord: (wordIndex: number | null) => void;
}) {
  const editable = (i: number) => mask[i] === 'blank' && !revealed.has(i);

  function focusFrom(start: number, step: 1 | -1) {
    for (let i = start + step; i >= 0 && i < mask.length; i += step) {
      if (editable(i)) {
        const el = inputRefs.current[i];
        if (el) {
          el.focus();
          if (step === -1) {
            const len = el.value.length;
            el.setSelectionRange(len, len);
          }
        }
        return true;
      }
    }
    return false;
  }

  return (
    <div className="flex flex-wrap items-center gap-x-1.5 gap-y-3 text-lg leading-relaxed">
      {tokens.map((t, k) => {
        if (t.wordIndex < 0) {
          return (
            <span key={k} className="text-slate-500">
              {t.prefix}
            </span>
          );
        }
        const i = t.wordIndex;
        const graded = correctness?.[i] ?? null;
        let body: React.ReactNode;
        if (mask[i] === 'prefilled') {
          body = <span className="text-slate-800">{t.word}</span>;
        } else if (revealed.has(i)) {
          body = (
            <span
              className="rounded-md border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-amber-800"
              title="Từ đã được gợi ý (tính là sai)"
            >
              {t.word}
            </span>
          );
        } else {
          body = (
            <input
              ref={(el) => {
                inputRefs.current[i] = el;
              }}
              {...{ [DICTATION_INPUT_ATTR]: '' }}
              type="text"
              inputMode="text"
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              aria-label={`Từ thứ ${i + 1}`}
              value={typed[i] ?? ''}
              onChange={(e) => onType(i, e.target.value.replace(/\s+/g, ''))}
              onFocus={() => onFocusWord(i)}
              onKeyDown={(e) => {
                if (e.key === ' ' && !e.ctrlKey && !e.metaKey) {
                  e.preventDefault();
                  focusFrom(i, 1);
                } else if (e.key === 'Backspace' && !e.currentTarget.value) {
                  e.preventDefault();
                  focusFrom(i, -1);
                }
              }}
              style={{ width: `${Math.max(2, t.word.length) + 1.5}ch` }}
              className={clsx(
                'h-11 min-w-[2.75rem] max-w-full rounded-md border-b-2 bg-slate-50 px-1 text-center font-medium outline-none transition-colors focus:bg-brand-50',
                graded === true && 'border-green-500 text-green-800',
                graded === false && 'border-red-500 text-red-700',
                graded === null && 'border-slate-400 text-slate-900 focus:border-brand-500'
              )}
            />
          );
        }
        return (
          <span key={k} className="inline-flex items-center">
            {t.prefix && <span className="text-slate-500">{t.prefix}</span>}
            {body}
            {t.suffix && <span className="text-slate-500">{t.suffix}</span>}
          </span>
        );
      })}
    </div>
  );
}
