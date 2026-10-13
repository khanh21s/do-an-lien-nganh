import type { TrendingError } from '@/lib/types';
import { formatPercent } from './dashboard-data';

/** PRA-04: "Từ hay sai" – top 10 words from `GET /practice/stats/trending-errors`. */
export function TrendingErrors({ errors, limit = 10 }: { errors: readonly TrendingError[]; limit?: number }) {
  const top = [...errors].sort((a, b) => b.count - a.count).slice(0, limit);

  if (top.length === 0) {
    return <p className="py-6 text-center text-sm text-slate-500">Tuyệt vời! Chưa có từ nào bạn hay sai.</p>;
  }

  const maxCount = Math.max(...top.map((e) => e.count), 1);

  return (
    <ol className="flex flex-col gap-2" data-testid="trending-errors">
      {top.map((e, i) => (
        <li key={e.word} className="flex items-center gap-3 text-sm">
          <span className="w-5 shrink-0 text-right text-xs font-semibold text-slate-400">{i + 1}</span>
          <div className="min-w-0 flex-1">
            <div className="flex items-baseline justify-between gap-2">
              <span className="truncate font-medium text-slate-800">{e.word}</span>
              <span className="shrink-0 text-xs text-slate-500">
                {e.count} lần · {formatPercent(e.avgAccuracy)}
              </span>
            </div>
            <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-slate-100">
              <div className="h-full rounded-full bg-rose-400" style={{ width: `${(e.count / maxCount) * 100}%` }} />
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
