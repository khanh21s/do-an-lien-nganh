'use client';

import { Kbd } from '@/components/ui/Kbd';
import { primaryLabel } from '@/lib/shortcuts';
import { useShortcutKeys } from '@/store/shortcut-store';
import { SRS_RATINGS, previewLabel, type SrsRatingName } from '@/lib/srs';
import type { RatingPreview } from '@/lib/types';
import clsx from 'clsx';

const COLORS: Record<SrsRatingName, string> = {
  again: 'bg-red-500 hover:bg-red-600',
  hard: 'bg-amber-500 hover:bg-amber-600',
  good: 'bg-green-500 hover:bg-green-600',
  easy: 'bg-brand-500 hover:bg-brand-600',
};

/**
 * VOC-02 / P2-anki: the 4 rating buttons Học lại / Khó / Tốt / Dễ (Anki ratings 1–4, default keys
 * 1–4, customisable). `previews` = the server's labels for this card ("<1 phút", "10 phút",
 * "4 ngày"…); without them the labels are hidden rather than guessed.
 */
export function RatingButtons({
  previews,
  onRate,
  disabled,
  suggestedRating,
}: {
  previews: readonly RatingPreview[] | undefined;
  onRate: (rating: number) => void;
  disabled?: boolean;
  suggestedRating?: number | null;
}) {
  const keys = useShortcutKeys();
  return (
    <div>
      <p className="pb-2 pt-4 text-center text-sm text-slate-500">Bạn nhớ từ này ở mức nào?</p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {SRS_RATINGS.map((r) => {
          const suggested = suggestedRating === r.rating;
          const label = previewLabel(previews, r.rating);
          const keyLabel = primaryLabel(keys(`flashcard.${r.name}`));
          return (
            <button
              key={r.key}
              type="button"
              disabled={disabled}
              onClick={() => onRate(r.rating)}
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
              {label && (
                <span className="text-xs font-normal opacity-90" data-testid={`preview-${r.name}`}>
                  {label}
                </span>
              )}
              {suggested && <span className="text-[11px] font-normal opacity-90">Gợi ý</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}
