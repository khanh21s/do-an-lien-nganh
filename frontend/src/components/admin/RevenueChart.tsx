import { formatVnd } from '@/components/subscription/format';
import type { RevenueDay } from '@/lib/types';
import { buildRevenueChart, formatCompactVnd, shortDate } from './console';

const GEOMETRY = { width: 640, height: 220, padLeft: 48, padRight: 8, padTop: 12, padBottom: 28 };

/** T14: 30-day revenue as a plain SVG bar chart (no chart library); native `<title>` tooltips. */
export function RevenueChart({ days }: { days: readonly RevenueDay[] }) {
  if (days.length === 0) {
    return <p className="py-10 text-center text-sm text-slate-500">Chưa có dữ liệu doanh thu.</p>;
  }

  const { width, height, padLeft, padRight } = GEOMETRY;
  const chart = buildRevenueChart(days, GEOMETRY);
  // Thin out x labels so they never overlap on narrow screens.
  const labelEvery = Math.max(1, Math.ceil(chart.bars.length / 6));

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="h-auto w-full"
      role="img"
      aria-label={`Doanh thu ${days.length} ngày gần nhất, tổng ${formatVnd(chart.total)}`}
    >
      {chart.ticks.map((t) => (
        <g key={t.value}>
          <line x1={padLeft} x2={width - padRight} y1={t.y} y2={t.y} className="stroke-slate-200" strokeDasharray="3 3" />
          <text x={padLeft - 6} y={t.y + 4} textAnchor="end" className="fill-slate-400 text-[10px]">
            {formatCompactVnd(t.value)}
          </text>
        </g>
      ))}
      {chart.bars.map((b, i) => (
        <g key={`${b.date}-${i}`}>
          {/* Full-height transparent hit area so empty days still show a tooltip. */}
          <rect x={b.x} y={GEOMETRY.padTop} width={b.width} height={height - GEOMETRY.padTop - GEOMETRY.padBottom} fill="transparent">
            <title>{`${shortDate(b.date)}: ${formatVnd(b.amount)}`}</title>
          </rect>
          {b.height > 0 && (
            <rect x={b.x} y={b.y} width={b.width} height={b.height} rx={2} className="fill-brand-500">
              <title>{`${shortDate(b.date)}: ${formatVnd(b.amount)}`}</title>
            </rect>
          )}
          {(i % labelEvery === 0 || i === chart.bars.length - 1) && (
            <text x={b.x + b.width / 2} y={height - 8} textAnchor="middle" className="fill-slate-500 text-[10px]">
              {shortDate(b.date)}
            </text>
          )}
        </g>
      ))}
    </svg>
  );
}
