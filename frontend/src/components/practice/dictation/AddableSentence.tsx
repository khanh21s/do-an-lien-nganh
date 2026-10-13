'use client';

import { clickableWords } from '@/components/vocab/addWordForm';
import { useMemo } from 'react';

/**
 * T13: the answer sentence with every word as a button that opens `AddWordModal`
 * (`onPick` receives the word without surrounding punctuation). Buttons are focusable, and the
 * dictation Enter shortcut ignores focused buttons (`isNativeActivation`), so Enter/Space click them.
 */
export function AddableSentence({ text, onPick }: { text: string; onPick: (word: string) => void }) {
  const chunks = useMemo(() => clickableWords(text), [text]);
  return (
    <p className="font-medium leading-relaxed text-slate-900">
      {chunks.map((c, i) => (
        <span key={i}>
          {i > 0 && ' '}
          {c.word ? (
            <button
              type="button"
              onClick={() => onPick(c.word)}
              aria-label={`Thêm '${c.word}' vào sổ tay`}
              title={`Thêm '${c.word}' vào sổ tay`}
              className="cursor-pointer rounded px-0.5 underline decoration-slate-300 decoration-dotted underline-offset-4 transition-colors hover:bg-brand-100 hover:text-brand-700 hover:decoration-brand-500 focus-visible:bg-brand-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
            >
              {c.text}
            </button>
          ) : (
            c.text
          )}
        </span>
      ))}
    </p>
  );
}
