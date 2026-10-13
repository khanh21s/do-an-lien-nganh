import { buildWeeklyPoints, formatPercent } from './dashboard-data';

const GEOMETRY = { width: 560, height: 200, padLeft: 36, padRight: 16, padTop: 14, padBottom: 30 };
const GRID = [0, 25, 50, 75, 100];

/**
 * PRA-02: weekly accuracy trend as a plain SVG line chart (no chart library).
 * Renders exactly `dates.length` points; each point has a native `<title>` tooltip.
 */
export function WeeklyAccuracyChart({ weekly, dates }: { weekly: readonly number[]; dates: readonly string[] }) {
  if (dates.length === 0) {
    return (
      <p className="py-10 text-center text-sm text-slate-500">
        Chưa có dữ liệu. Hoàn thành vài câu luyện tập để thấy xu hướng độ chính xác.
      </p>
    );
  }

  const { width, height, padLeft, padRight, padTop, padBottom } = GEOMETRY;
  const points = buildWeeklyPoints(weekly, dates, GEOMETRY);
  const innerH = height - padTop - padBottom;
  const baseY = padTop + innerH;
  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ');
  const area =
    points.length > 1 ? `${line} L${points[points.length - 1].x},${baseY} L${points[0].x},${baseY} Z` : '';
  // Thin out x labels so they never overlap on narrow screens.
  const labelEvery = Math.max(1, Math.ceil(points.length / 8));

  return (
    <svg
      viewBox={`0 0 ${width} ${height}`}
      className="h-auto w-full"
      role="img"
      aria-label={`Độ chính xác theo tuần, ${points.length} tuần`}
      data-testid="weekly-accuracy-chart"
    >
      {GRID.map((g) => {
        const y = padTop + innerH * (1 - g / 100);
        return (
          <g key={g}>
            <line x1={padLeft} x2={width - padRight} y1={y} y2={y} className="stroke-slate-200" strokeDasharray="3 3" />
            <text x={padLeft - 6} y={y + 4} textAnchor="end" className="fill-slate-400 text-[10px]">
              {g}%
            </text>
          </g>
        );
      })}
      {area && <path d={area} className="fill-brand-100/60" />}
      {points.length > 1 && (
        <path d={line} fill="none" className="stroke-brand-600" strokeWidth={2.5} strokeLinejoin="round" strokeLinecap="round" />
      )}
      {points.map((p, i) => (
        <g key={`${p.label}-${i}`} data-point>
          <circle cx={p.x} cy={p.y} r={4.5} className="fill-white stroke-brand-600" strokeWidth={2}>
            <title>{`Tuần ${p.label}: ${formatPercent(p.value)}`}</title>
          </circle>
          {i % labelEvery === 0 || i === points.length - 1 ? (
            <text x={p.x} y={height - 10} textAnchor="middle" className="fill-slate-500 text-[10px]">
              {p.label}
            </text>
          ) : null}
        </g>
      ))}
    </svg>
  );
}
