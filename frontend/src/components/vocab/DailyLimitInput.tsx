'use client';

import { DAILY_LIMIT_MAX, DAILY_LIMIT_MIN, clampDailyLimit } from '@/lib/srs';
import { useEffect, useState } from 'react';

/** VOC-10: cards-per-day limit (1–1000), committed on blur/Enter. */
export function DailyLimitInput({ value, onChange }: { value: number; onChange: (value: number) => void }) {
  const [draft, setDraft] = useState(String(value));

  useEffect(() => setDraft(String(value)), [value]);

  function commit() {
    const next = clampDailyLimit(draft);
    setDraft(String(next));
    if (next !== value) onChange(next);
  }

  return (
    <label className="inline-flex min-h-[44px] items-center gap-2 text-sm text-slate-600">
      Thẻ/ngày
      <input
        type="number"
        inputMode="numeric"
        min={DAILY_LIMIT_MIN}
        max={DAILY_LIMIT_MAX}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault();
            (e.target as HTMLInputElement).blur();
          }
        }}
        className="w-20 rounded-lg border border-slate-300 px-2 py-1.5 text-sm outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
        aria-label="Giới hạn số thẻ ôn mỗi ngày"
      />
    </label>
  );
}
