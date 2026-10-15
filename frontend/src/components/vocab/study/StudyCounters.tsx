import { COUNTER_ORDER } from '@/lib/srs';
import type { StudyCounts, StudyQueue } from '@/lib/types';
import clsx from 'clsx';

const COLOR: Record<StudyQueue, string> = {
  new: 'text-blue-600',
  learning: 'text-red-600',
  review: 'text-green-600',
};

/** Anki's three counters: Mới (blue), Đang học (red), Ôn (green); the current card's counter is underlined. */
export function StudyCounters({ counts, active }: { counts: StudyCounts; active?: StudyQueue | null }) {
  return (
    <ul className="flex items-center justify-center gap-5 text-sm" aria-label="Số thẻ còn lại">
      {COUNTER_ORDER.map(({ queue, label }) => (
        <li key={queue} className="flex flex-col items-center" data-testid={`count-${queue}`}>
          <span
            className={clsx(
              'text-xl font-bold tabular-nums',
              COLOR[queue],
              active === queue && 'underline decoration-2 underline-offset-4'
            )}
            aria-label={`${label}: ${counts[queue]}`}
          >
            {counts[queue]}
          </span>
          <span className="text-xs text-slate-500">{label}</span>
        </li>
      ))}
    </ul>
  );
}
