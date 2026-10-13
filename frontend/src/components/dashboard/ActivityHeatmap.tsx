import clsx from 'clsx';
import { Fragment } from 'react';
import type { ActivityDay } from '@/lib/types';
import { buildHeatmap, fillActivity, formatLongDate } from './dashboard-data';

const LEVEL_CLASSES = ['bg-slate-100', 'bg-brand-200', 'bg-brand-400', 'bg-brand-600', 'bg-brand-800'] as const;
const WEEKDAY_LABELS = ['T2', '', 'T4', '', 'T6', '', 'CN'];

/**
 * PRA-03: last `days` days of practice (from `GET /practice/stats/activity`), one column per week,
 * rows Monday → Sunday, colour by number of attempts. Plain divs + Tailwind, `title` tooltips.
 */
export function ActivityHeatmap({ data, days = 90 }: { data: readonly ActivityDay[]; days?: number }) {
  const heatmap = buildHeatmap(fillActivity(data, days));
  const colCount = heatmap.columns.length;
  const monthByColumn = new Map(heatmap.monthLabels.map((m) => [m.column, m.label]));

  return (
    <div>
      <p className="text-sm text-slate-500">
        {heatmap.activeDays} ngày có luyện tập · {heatmap.totalAttempts} lượt trong {heatmap.dayCount} ngày qua
      </p>
      {/* Row-major grid: header row of month labels, then one row per weekday (label + week cells). */}
      <div
        className="mt-3 grid max-w-md items-center gap-[3px] text-[10px] leading-none text-slate-400"
        style={{ gridTemplateColumns: `1.5rem repeat(${colCount}, minmax(0, 1fr))` }}
        data-testid="activity-heatmap"
      >
        <span />
        {heatmap.columns.map((_, c) => (
          <span key={`m-${c}`} className="h-3 overflow-visible whitespace-nowrap">
            {monthByColumn.get(c) ?? ''}
          </span>
        ))}
        {WEEKDAY_LABELS.map((label, r) => (
          <Fragment key={`row-${r}`}>
            <span>{label}</span>
            {heatmap.columns.map((col, c) => {
              const cell = col[r];
              return cell ? (
                <div
                  key={cell.date}
                  role="img"
                  data-day={cell.date}
                  title={`${formatLongDate(cell.date)}: ${cell.attempts} lượt${cell.minutes > 0 ? ` · ${cell.minutes} phút` : ''}`}
                  aria-label={`${formatLongDate(cell.date)}: ${cell.attempts} lượt`}
                  className={clsx('aspect-square rounded-[3px]', LEVEL_CLASSES[cell.level])}
                />
              ) : (
                <div key={`pad-${c}-${r}`} aria-hidden className="aspect-square" />
              );
            })}
          </Fragment>
        ))}
      </div>
      <div className="mt-3 flex items-center justify-end gap-1 text-[11px] text-slate-500">
        <span>Ít</span>
        {LEVEL_CLASSES.map((cls) => (
          <span key={cls} className={clsx('h-3 w-3 rounded-[3px]', cls)} />
        ))}
        <span>Nhiều</span>
      </div>
    </div>
  );
}
