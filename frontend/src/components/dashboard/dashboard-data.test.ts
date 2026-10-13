import { describe, expect, it } from 'vitest';
import type { ActivityDay } from '@/lib/types';
import {
  activityLevel,
  addDays,
  buildHeatmap,
  buildWeeklyPoints,
  fillActivity,
  formatHours,
  formatPercent,
  formatShortDate,
  vnDateKey,
  weekdayMon0,
} from './dashboard-data';

function range(endKey: string, n: number, attempts: (i: number) => number = () => 0): ActivityDay[] {
  return Array.from({ length: n }, (_, i) => ({ date: addDays(endKey, i - n + 1), attempts: attempts(i), minutes: 0 }));
}

describe('date helpers', () => {
  it('vnDateKey uses Asia/Ho_Chi_Minh (23:30 VN on day D stays day D)', () => {
    // 2026-10-12T16:30Z = 2026-10-12 23:30 in Vietnam (UTC+7).
    expect(vnDateKey(new Date('2026-10-12T16:30:00Z'))).toBe('2026-10-12');
    // 2026-10-12T17:30Z = 2026-10-13 00:30 in Vietnam.
    expect(vnDateKey(new Date('2026-10-12T17:30:00Z'))).toBe('2026-10-13');
  });

  it('addDays crosses month and year boundaries', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
    expect(addDays('2025-12-31', 1)).toBe('2026-01-01');
    expect(addDays('2026-10-13', -89)).toBe('2026-07-16');
  });

  it('weekdayMon0 maps Monday → 0 and Sunday → 6', () => {
    expect(weekdayMon0('2024-01-01')).toBe(0); // Monday
    expect(weekdayMon0('2024-01-07')).toBe(6); // Sunday
  });

  it('formatShortDate', () => {
    expect(formatShortDate('2026-10-05')).toBe('05/10');
    expect(formatShortDate('W41')).toBe('W41');
  });
});

describe('fillActivity', () => {
  it('keeps a complete 90-day response unchanged', () => {
    const data = range('2026-10-13', 90, (i) => i % 3);
    expect(fillActivity(data, 90)).toEqual(data);
  });

  it('fills gaps with zero days and always returns exactly `days` items ending at the last date', () => {
    const data: ActivityDay[] = [
      { date: '2026-10-13', attempts: 2, minutes: 5 },
      { date: '2026-10-10', attempts: 1, minutes: 1 },
    ];
    const out = fillActivity(data, 90);
    expect(out).toHaveLength(90);
    expect(out[0].date).toBe('2026-07-16');
    expect(out[89]).toEqual({ date: '2026-10-13', attempts: 2, minutes: 5 });
    expect(out[86]).toEqual({ date: '2026-10-10', attempts: 1, minutes: 1 });
    expect(out[88]).toEqual({ date: '2026-10-12', attempts: 0, minutes: 0 });
  });

  it('uses the explicit end date for an empty response', () => {
    const out = fillActivity([], 7, '2026-10-13');
    expect(out.map((d) => d.date)).toEqual([
      '2026-10-07',
      '2026-10-08',
      '2026-10-09',
      '2026-10-10',
      '2026-10-11',
      '2026-10-12',
      '2026-10-13',
    ]);
    expect(out.every((d) => d.attempts === 0)).toBe(true);
  });
});

describe('activityLevel', () => {
  it('buckets attempts into 0–4 relative to the busiest day', () => {
    expect(activityLevel(0, 10)).toBe(0);
    expect(activityLevel(1, 10)).toBe(1);
    expect(activityLevel(3, 10)).toBe(2);
    expect(activityLevel(6, 10)).toBe(3);
    expect(activityLevel(10, 10)).toBe(4);
    expect(activityLevel(5, 0)).toBe(0);
  });
});

describe('buildHeatmap', () => {
  it('renders exactly 90 day cells in Monday-first week columns', () => {
    // 2024-01-01 is a Monday → no leading padding, 90 days = 12 full weeks + 6 days → 13 columns.
    const days = range(addDays('2024-01-01', 89), 90, (i) => (i === 10 ? 4 : i % 2));
    const hm = buildHeatmap(days);
    const cells = hm.columns.flat().filter((c) => c !== null);
    expect(cells).toHaveLength(90);
    expect(hm.dayCount).toBe(90);
    expect(hm.columns).toHaveLength(13);
    expect(hm.columns.every((col) => col.length === 7)).toBe(true);
    expect(hm.columns[0][0]?.date).toBe('2024-01-01');
    expect(hm.maxAttempts).toBe(4);
    expect(hm.columns[1][3]?.level).toBe(4); // day index 10
    expect(hm.totalAttempts).toBe(45 + 4); // 45 odd indices + 4 on (even) index 10
    expect(hm.activeDays).toBe(46);
  });

  it('pads the first column so each day sits on its weekday row (14 columns when needed)', () => {
    // 2024-01-07 is a Sunday → 6 leading null slots.
    const days = range(addDays('2024-01-07', 89), 90);
    const hm = buildHeatmap(days);
    expect(hm.columns[0].slice(0, 6)).toEqual([null, null, null, null, null, null]);
    expect(hm.columns[0][6]?.date).toBe('2024-01-07');
    expect(hm.columns).toHaveLength(14);
    expect(hm.columns.flat().filter(Boolean)).toHaveLength(90);
    for (const col of hm.columns) {
      col.forEach((cell, row) => {
        if (cell) expect(weekdayMon0(cell.date)).toBe(row);
      });
    }
  });

  it('labels the first column and every column containing the 1st of a month', () => {
    const hm = buildHeatmap(range('2026-10-13', 90));
    expect(hm.monthLabels.map((m) => m.label)).toEqual(['Th7', 'Th8', 'Th9', 'Th10']);
  });

  it('handles an empty input', () => {
    const hm = buildHeatmap([]);
    expect(hm.columns).toEqual([]);
    expect(hm.dayCount).toBe(0);
  });
});

describe('buildWeeklyPoints', () => {
  const g = { width: 320, height: 160, padLeft: 20, padRight: 20, padTop: 10, padBottom: 30 };

  it('emits one point per date, scaled on 0–100%', () => {
    const pts = buildWeeklyPoints([0, 50, 100, 75], ['2026-09-15', '2026-09-22', '2026-09-29', '2026-10-06'], g);
    expect(pts).toHaveLength(4);
    expect(pts[0]).toMatchObject({ x: 20, y: 130, value: 0, label: '15/09' });
    expect(pts[1].y).toBe(70);
    expect(pts[2]).toMatchObject({ x: 206.67, y: 10, value: 100 });
    expect(pts[3].x).toBe(300);
  });

  it('follows dates length when weekly is shorter, clamping out-of-range values', () => {
    const pts = buildWeeklyPoints([120], ['a', 'b', 'c'], g);
    expect(pts).toHaveLength(3);
    expect(pts[0].value).toBe(100);
    expect(pts[1].value).toBe(0);
  });

  it('centres a single point', () => {
    const [p] = buildWeeklyPoints([40], ['2026-10-06'], g);
    expect(p.x).toBe(160);
  });
});

describe('formatters', () => {
  it('formatHours', () => {
    expect(formatHours(0)).toBe('0 giờ');
    expect(formatHours(0.5)).toBe('30 phút');
    expect(formatHours(2)).toBe('2 giờ');
    expect(formatHours(2.345)).toBe('2.3 giờ');
  });

  it('formatPercent', () => {
    expect(formatPercent(87.46)).toBe('87.5%');
    expect(formatPercent(90)).toBe('90%');
    expect(formatPercent(null)).toBe('0%');
  });
});
