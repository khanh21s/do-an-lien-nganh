'use client';

import clsx from 'clsx';
import { useState } from 'react';
import type { ShadowingWordScore } from '@/lib/types';

export type PronunciationLevel = 'good' | 'close' | 'retry';

/** Thresholds shared with the backend `status` (≥ 80 green, 60–79 yellow, < 60 red). */
export function pronunciationLevel(accuracy: number): PronunciationLevel {
  if (accuracy >= 80) return 'good';
  if (accuracy >= 60) return 'close';
  return 'retry';
}

export const LEVEL_LABELS: Record<PronunciationLevel, string> = {
  good: 'Đúng',
  close: 'Gần đúng',
  retry: 'Thử lại',
};

const LEVEL_CLASSES: Record<PronunciationLevel, string> = {
  good: 'border-green-200 bg-green-50 text-green-800',
  close: 'border-amber-200 bg-amber-50 text-amber-800',
  retry: 'border-red-200 bg-red-50 text-red-800',
};

const LEVEL_DOT: Record<PronunciationLevel, string> = {
  good: 'bg-green-500',
  close: 'bg-amber-500',
  retry: 'bg-red-500',
};

function levelOf(w: ShadowingWordScore): PronunciationLevel {
  if (Number.isFinite(w.wordAccuracy)) return pronunciationLevel(w.wordAccuracy);
  return w.status === 'green' ? 'good' : w.status === 'yellow' ? 'close' : 'retry';
}

function pct(n: number): string {
  return Number.isFinite(n) ? `${Math.round(n)}%` : '–';
}

/**
 * Word-by-word pronunciation scores (SHA-01 result, PRA-06 history modal).
 * Click/tap a word to see its phoneme breakdown. `words` is the only required prop (used by T11).
 */
export function PhonemeScoreView({
  words,
  showLegend = true,
  className,
}: {
  words: ShadowingWordScore[];
  showLegend?: boolean;
  className?: string;
}) {
  const [selected, setSelected] = useState<number | null>(null);
  if (!words?.length) return null;
  const active = selected !== null ? words[selected] : null;

  return (
    <div className={clsx('flex flex-col gap-3', className)}>
      <div className="flex flex-wrap gap-2">
        {words.map((w, i) => {
          const level = levelOf(w);
          return (
            <button
              type="button"
              key={`${w.word}-${i}`}
              onClick={() => setSelected(selected === i ? null : i)}
              aria-pressed={selected === i}
              aria-label={`${w.word}: ${pct(w.wordAccuracy)} – ${LEVEL_LABELS[level]}`}
              className={clsx(
                'min-h-[44px] min-w-[44px] rounded-lg border px-3 py-1.5 text-center transition-shadow',
                LEVEL_CLASSES[level],
                selected === i && 'ring-2 ring-brand-400'
              )}
            >
              <div className="font-medium">{w.word}</div>
              <div className="text-xs opacity-80">{pct(w.wordAccuracy)}</div>
            </button>
          );
        })}
      </div>

      {active && (
        <div className="rounded-lg border border-slate-200 bg-white p-3 text-sm">
          <p className="font-medium text-slate-900">
            “{active.word}” · {pct(active.wordAccuracy)} · {LEVEL_LABELS[levelOf(active)]}
          </p>
          {active.phonemes?.length ? (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {active.phonemes.map((p, j) => {
                const level = pronunciationLevel(p.accuracy);
                return (
                  <span
                    key={`${p.phoneme}-${j}`}
                    className={clsx('rounded-md border px-2 py-0.5 font-mono text-xs', LEVEL_CLASSES[level])}
                  >
                    /{p.phoneme}/ {pct(p.accuracy)}
                  </span>
                );
              })}
            </div>
          ) : (
            <p className="mt-1 text-slate-500">Không có dữ liệu âm vị cho từ này.</p>
          )}
        </div>
      )}

      {showLegend && (
        <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
          <li className="flex items-center gap-1.5">
            <span className={clsx('h-2.5 w-2.5 rounded-full', LEVEL_DOT.good)} /> Đúng ≥ 80
          </li>
          <li className="flex items-center gap-1.5">
            <span className={clsx('h-2.5 w-2.5 rounded-full', LEVEL_DOT.close)} /> Gần đúng 60–79
          </li>
          <li className="flex items-center gap-1.5">
            <span className={clsx('h-2.5 w-2.5 rounded-full', LEVEL_DOT.retry)} /> Thử lại &lt; 60
          </li>
          <li className="text-slate-400">Chạm vào từ để xem âm vị</li>
        </ul>
      )}
    </div>
  );
}
