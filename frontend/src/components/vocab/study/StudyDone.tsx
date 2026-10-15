import { SRS_RATINGS, formatNextDue, ratingDistribution, type SrsRatingName } from '@/lib/srs';
import clsx from 'clsx';

const BAR: Record<SrsRatingName, string> = {
  again: 'bg-red-500',
  hard: 'bg-amber-500',
  good: 'bg-green-500',
  easy: 'bg-brand-500',
};

/** "Đã xong hôm nay": answers of this session (Again/Hard/Good/Easy) and when the next card falls due. */
export function StudyDone({
  ratings,
  nextDueAt,
  hint,
  children,
}: {
  ratings: number[];
  nextDueAt: string | null;
  /** Extra line under the title (e.g. an empty notebook). */
  hint?: React.ReactNode;
  children?: React.ReactNode;
}) {
  const dist = ratingDistribution(ratings);
  const total = ratings.length;
  const next = formatNextDue(nextDueAt);

  return (
    <div className="mx-auto max-w-lg text-center" data-testid="study-done">
      <p className="text-4xl" aria-hidden>
        🎉
      </p>
      <h2 className="mt-2 text-lg font-semibold text-slate-900">Đã xong hôm nay!</h2>
      <p className="mt-1 text-sm text-slate-500">
        {total > 0 ? `Bạn đã trả lời ${total} lượt trong phiên này.` : 'Không còn thẻ nào cần học lúc này.'}
      </p>
      {hint && <p className="mt-1 text-sm text-slate-500">{hint}</p>}

      {total > 0 && (
        <ul className="mt-5 space-y-2 text-left">
          {SRS_RATINGS.map((r) => {
            const count = dist[r.name];
            const pct = Math.round((count / total) * 100);
            return (
              <li key={r.name} className="flex items-center gap-3 text-sm">
                <span className="w-24 shrink-0 text-slate-600">
                  {r.label} <span className="text-slate-400">({r.labelEn})</span>
                </span>
                <span className="h-2.5 flex-1 overflow-hidden rounded-full bg-slate-100">
                  <span className={clsx('block h-full rounded-full', BAR[r.name])} style={{ width: `${pct}%` }} />
                </span>
                <span className="w-14 shrink-0 text-right font-medium text-slate-700">
                  {count} <span className="text-xs font-normal text-slate-400">({pct}%)</span>
                </span>
              </li>
            );
          })}
        </ul>
      )}

      <p className="mt-5 rounded-xl bg-green-50 px-4 py-2 text-sm text-green-800" data-testid="next-due">
        {next ? <>Thẻ tiếp theo đến hạn {next}.</> : <>Chưa có thẻ nào được lên lịch ôn.</>}
      </p>
      <div className="mt-5 flex flex-wrap justify-center gap-2">{children}</div>
    </div>
  );
}
