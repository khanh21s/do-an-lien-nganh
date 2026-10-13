'use client';

import { Kbd } from '@/components/ui/Kbd';
import { primaryLabel } from '@/lib/shortcuts';
import { useShortcutKeys } from '@/store/shortcut-store';
import { SRS_RATINGS, formatInterval, previewInterval, type SrsRatingName, type SrsState } from '@/lib/srs';
import clsx from 'clsx';

const COLORS: Record<SrsRatingName, string> = {
  again: 'bg-red-500 hover:bg-red-600',
  hard: 'bg-amber-500 hover:bg-amber-600',
  good: 'bg-green-500 hover:bg-green-600',
  easy: 'bg-brand-500 hover:bg-brand-600',
};

/**
 * VOC-02: the 4 SM-2 rating buttons (Again/Hard/Good/Easy → q1/q3/q4/q5, default keys 1–4, customisable).
 * `state` = the card's current SM-2 state for the interval labels; `null` = new card;
 * `undefined` = unknown (labels hidden rather than guessed).
 */
export function RatingButtons({
  state,
  onRate,
  disabled,
  suggestedQuality,
}: {
  state: SrsState | null | undefined;
  onRate: (quality: number) => void;
  disabled?: boolean;
  suggestedQuality?: number | null;
}) {
  const keys = useShortcutKeys();
  return (
    <div>
      <p className="pb-2 pt-4 text-center text-sm text-slate-500">Bạn nhớ từ này ở mức nào?</p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {SRS_RATINGS.map((r) => {
          const suggested = suggestedQuality === r.quality;
          const keyLabel = primaryLabel(keys(`flashcard.${r.name}`));
          return (
            <button
              key={r.key}
              type="button"
              disabled={disabled}
              onClick={() => onRate(r.quality)}
              aria-keyshortcuts={keys(`flashcard.${r.name}`)[0]}
              className={clsx(
                'flex min-h-[64px] flex-col items-center justify-center gap-0.5 rounded-xl px-3 py-2.5 text-sm font-semibold text-white transition-colors disabled:opacity-60',
                COLORS[r.name],
                suggested && 'ring-4 ring-offset-2 ring-slate-800/70'
              )}
            >
              <span className="flex items-center gap-1.5">
                <Kbd className="hidden border-white/40 bg-white/20 text-white shadow-none sm:inline-flex">{keyLabel}</Kbd>
                {r.label}
              </span>
              {state !== undefined && (
                <span className="text-xs font-normal opacity-90">{formatInterval(previewInterval(state, r.quality))}</span>
              )}
              {suggested && <span className="text-[11px] font-normal opacity-90">Gợi ý</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
