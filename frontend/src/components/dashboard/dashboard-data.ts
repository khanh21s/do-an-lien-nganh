import type { ActivityDay } from '@/lib/types';

// Pure data-shaping helpers for the dashboard widgets (PRA-01..03). No React, unit-tested.

/** Practice days, streaks and the heatmap are all computed in Vietnam time (PRA-07). */
export const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

const DAY_MS = 86_400_000;
const DATE_KEY_RE = /^(\d{4})-(\d{2})-(\d{2})/;

/** 'YYYY-MM-DD' of `date` as seen in Asia/Ho_Chi_Minh. */
export function vnDateKey(date: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: VN_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

/** Calendar date key → UTC midnight timestamp (pure calendar arithmetic, timezone-free). */
function keyToUtc(key: string): number {
  const m = DATE_KEY_RE.exec(key);
  if (!m) return NaN;
  return Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
}

function utcToKey(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10);
}

/** Adds `n` calendar days to a 'YYYY-MM-DD' key. */
export function addDays(key: string, n: number): string {
  return utcToKey(keyToUtc(key) + n * DAY_MS);
}

/** Weekday of a 'YYYY-MM-DD' key with Monday = 0 … Sunday = 6. */
export function weekdayMon0(key: string): number {
  return (new Date(keyToUtc(key)).getUTCDay() + 6) % 7;
}

/** 'YYYY-MM-DD…' → 'dd/MM'; anything else is returned unchanged. */
export function formatShortDate(key: string): string {
  const m = DATE_KEY_RE.exec(key);
  return m ? `${m[3]}/${m[2]}` : key;
}

/** 'YYYY-MM-DD…' → 'dd/MM/yyyy'; anything else is returned unchanged. */
export function formatLongDate(key: string): string {
  const m = DATE_KEY_RE.exec(key);
  return m ? `${m[3]}/${m[2]}/${m[1]}` : key;
}

/**
 * Normalises `GET /practice/stats/activity` into exactly `days` consecutive days ending at
 * `endKey` (the last returned date, or today in VN time). The BE already returns exactly `days`
 * items; this only guards against gaps/duplicates so the grid always has `days` cells.
 */
export function fillActivity(data: readonly ActivityDay[], days: number, endKey?: string): ActivityDay[] {
  const byDate = new Map<string, ActivityDay>();
  for (const d of data) {
    const key = d.date.slice(0, 10);
    const prev = byDate.get(key);
    byDate.set(key, {
      date: key,
      attempts: (prev?.attempts ?? 0) + (d.attempts || 0),
      minutes: (prev?.minutes ?? 0) + (d.minutes || 0),
    });
  }
  const sortedKeys = Array.from(byDate.keys()).sort();
  const end = endKey ?? sortedKeys[sortedKeys.length - 1] ?? vnDateKey();
  const out: ActivityDay[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const key = addDays(end, -i);
    out.push(byDate.get(key) ?? { date: key, attempts: 0, minutes: 0 });
  }
  return out;
}

/** Colour bucket 0–4: 0 = no activity, 1–4 = quartiles of the busiest day. */
export function activityLevel(attempts: number, max: number): 0 | 1 | 2 | 3 | 4 {
  if (attempts <= 0 || max <= 0) return 0;
  const level = Math.ceil((attempts / max) * 4);
  return Math.min(4, Math.max(1, level)) as 1 | 2 | 3 | 4;
}

export interface HeatCell extends ActivityDay {
  level: 0 | 1 | 2 | 3 | 4;
}

export interface Heatmap {
  /** Week columns, each with 7 slots Monday → Sunday; `null` = padding outside the range. */
  columns: (HeatCell | null)[][];
  /** Number of real day cells (= input length). */
  dayCount: number;
  maxAttempts: number;
  totalAttempts: number;
  activeDays: number;
  /** Column index → month label ('Th1'…'Th12') for the column containing the 1st of a month. */
  monthLabels: { column: number; label: string }[];
}

/**
 * Lays consecutive days out GitHub-style: one column per week, rows Monday → Sunday. 90 days
 * span 13 columns (14 when the range starts late in the week); padding slots are `null`.
 */
export function buildHeatmap(days: readonly ActivityDay[]): Heatmap {
  const maxAttempts = days.reduce((m, d) => Math.max(m, d.attempts), 0);
  const columns: (HeatCell | null)[][] = [];
  const monthLabels: { column: number; label: string }[] = [];
  let totalAttempts = 0;
  let activeDays = 0;

  if (days.length > 0) {
    const offset = weekdayMon0(days[0].date);
    const slots = offset + days.length;
    const colCount = Math.ceil(slots / 7);
    for (let c = 0; c < colCount; c++) columns.push(Array<HeatCell | null>(7).fill(null));

    days.forEach((d, i) => {
      const slot = offset + i;
      const col = Math.floor(slot / 7);
      columns[col][slot % 7] = { ...d, level: activityLevel(d.attempts, maxAttempts) };
      totalAttempts += d.attempts;
      if (d.attempts > 0) activeDays++;
      const dayOfMonth = Number(d.date.slice(8, 10));
      if (i === 0 || dayOfMonth === 1) {
        const label = `Th${Number(d.date.slice(5, 7))}`;
        const last = monthLabels[monthLabels.length - 1];
        // Skip a label that would collide with the previous one (e.g. range starts on the 30th).
        if (!last || col - last.column >= 2) monthLabels.push({ column: col, label });
        else if (dayOfMonth === 1) monthLabels[monthLabels.length - 1] = { column: col, label };
      }
    });
  }

  return { columns, dayCount: days.length, maxAttempts, totalAttempts, activeDays, monthLabels };
}

export interface ChartPoint {
  x: number;
  y: number;
  value: number;
  label: string;
}

export interface ChartGeometry {
  width: number;
  height: number;
  padLeft: number;
  padRight: number;
  padTop: number;
  padBottom: number;
}

/**
 * PRA-02: one point per entry of `accuracy.dates` (value = `weekly[i]`, missing → 0), y scaled
 * on 0–100 %. A single point is centred horizontally.
 */
export function buildWeeklyPoints(
  weekly: readonly number[],
  dates: readonly string[],
  g: ChartGeometry
): ChartPoint[] {
  const n = dates.length;
  const innerW = g.width - g.padLeft - g.padRight;
  const innerH = g.height - g.padTop - g.padBottom;
  return dates.map((date, i) => {
    const raw = Number(weekly[i] ?? 0);
    const value = Number.isFinite(raw) ? Math.min(100, Math.max(0, raw)) : 0;
    const x = n === 1 ? g.padLeft + innerW / 2 : g.padLeft + (innerW * i) / (n - 1);
    const y = g.padTop + innerH * (1 - value / 100);
    return { x: round2(x), y: round2(y), value, label: formatShortDate(date) };
  });
}

function round2(v: number): number {
  return Math.round(v * 100) / 100;
}

/** "1.5 giờ" / "45 phút" for the total-hours card. */
export function formatHours(hours: number | null | undefined): string {
  const h = Number(hours ?? 0);
  if (!Number.isFinite(h) || h <= 0) return '0 giờ';
  if (h < 1) return `${Math.round(h * 60)} phút`;
  return `${Number.isInteger(h) ? h : h.toFixed(1)} giờ`;
}

/** Percent with at most one decimal, e.g. 87.5 → "87.5%", 90 → "90%". */
export function formatPercent(value: number | null | undefined): string {
  const v = Number(value ?? 0);
  if (!Number.isFinite(v)) return '0%';
  return `${Math.round(v * 10) / 10}%`;
}
