import { Card } from '@/components/ui/Card';
import { formatDateTime } from '@/components/vocab/format';
import { SRS_RATINGS, ratingDistribution, type SrsRatingName } from '@/lib/srs';
import clsx from 'clsx';

const BAR: Record<SrsRatingName, string> = {
  again: 'bg-red-500',
  hard: 'bg-amber-500',
  good: 'bg-green-500',
  easy: 'bg-brand-500',
};

/** VOC-06: end-of-session summary – card count and the Again/Hard/Good/Easy distribution. */
export function ReviewSummary({
  qualities,
  nextReviewAt,
  remainingDue,
  children,
}: {
  qualities: number[];
  /** Earliest next review among the cards rated in this session. */
  nextReviewAt: string | null;
  /** Due cards left out of this session (daily limit). */
  remainingDue: number;
  children?: React.ReactNode;
}) {
  const dist = ratingDistribution(qualities);
  const total = qualities.length;

  return (
    <Card className="mx-auto max-w-lg text-center">
      <p className="text-4xl" aria-hidden>
        🎉
      </p>
      <h2 className="mt-2 text-lg font-semibold text-slate-900">Hoàn thành phiên ôn tập!</h2>
      <p className="mt-1 text-sm text-slate-500">Bạn đã ôn {total} thẻ.</p>

      <ul className="mt-5 space-y-2 text-left">
        {SRS_RATINGS.map((r) => {
          const count = dist[r.name];
          const pct = total ? Math.round((count / total) * 100) : 0;
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

      {remainingDue > 0 ? (
        <p className="mt-5 rounded-xl bg-amber-50 px-4 py-2 text-sm text-amber-800">
          Còn {remainingDue} thẻ đến hạn ngoài giới hạn của phiên này.
        </p>
      ) : (
        <p className="mt-5 rounded-xl bg-green-50 px-4 py-2 text-sm text-green-800">
          Không còn thẻ đến hạn
          {nextReviewAt ? <> – lần ôn kế tiếp lúc {formatDateTime(nextReviewAt)}</> : null}.
        </p>
      )}
      <div className="mt-5 flex flex-wrap justify-center gap-2">{children}</div>
    </Card>
  );
}
