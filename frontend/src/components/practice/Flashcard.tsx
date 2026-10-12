'use client';

import clsx from 'clsx';
import { useState } from 'react';

export function Flashcard({
  word,
  meaning,
  example,
  ipa,
  flipped: flippedProp,
  onFlip,
}: {
  word: string;
  meaning: string;
  example?: string | null;
  ipa?: string | null;
  /** Controlled flip state. Omit to let the card manage its own flip state internally. */
  flipped?: boolean;
  onFlip?: (flipped: boolean) => void;
}) {
  const [internalFlipped, setInternalFlipped] = useState(false);
  const flipped = flippedProp ?? internalFlipped;

  function toggle() {
    const next = !flipped;
    if (onFlip) onFlip(next);
    else setInternalFlipped(next);
  }

  return (
    <div className="[perspective:1200px]">
      <div
        onClick={toggle}
        className={clsx(
          'relative min-h-[220px] cursor-pointer select-none transition-transform duration-500 ease-out [transform-style:preserve-3d]',
          flipped && '[transform:rotateY(180deg)]'
        )}
      >
        {/* Front face */}
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-white p-5 text-center shadow-sm [backface-visibility:hidden]">
          <h3 className="text-3xl font-bold text-slate-900">{word}</h3>
          {ipa && <p className="text-slate-500">/{ipa}/</p>}
          <p className="mt-4 text-xs text-slate-400">👆 Nhấn để lật thẻ và xem nghĩa</p>
        </div>

        {/* Back face */}
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-brand-50 p-5 text-center shadow-sm [backface-visibility:hidden] [transform:rotateY(180deg)]">
          <p className="text-xl font-semibold text-brand-700">{meaning}</p>
          {example && <p className="mt-2 max-w-sm text-sm italic text-slate-600">&ldquo;{example}&rdquo;</p>}
          <p className="mt-4 text-xs text-slate-400">👆 Nhấn để lật lại</p>
        </div>
      </div>
    </div>
  );
}
