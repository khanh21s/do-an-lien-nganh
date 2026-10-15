'use client';

import { UNGROUPED, type VocabGroup } from '@/lib/types';
import clsx from 'clsx';

/** P2-C: "Tất cả / <group> / Chưa phân nhóm" chips; `value` null = all cards. */
export function GroupFilterChips({
  groups,
  total,
  ungroupedCount,
  value,
  onChange,
  className,
}: {
  groups: VocabGroup[];
  total: number;
  ungroupedCount: number;
  value: string | null;
  onChange: (groupId: string | null) => void;
  className?: string;
}) {
  if (groups.length === 0) return null;
  const chips: { id: string | null; label: string; count: number }[] = [
    { id: null, label: 'Tất cả', count: total },
    ...groups.map((g) => ({ id: g.id, label: g.name, count: g.cardCount })),
  ];
  if (ungroupedCount > 0) chips.push({ id: UNGROUPED, label: 'Chưa phân nhóm', count: ungroupedCount });
  return (
    <div className={clsx('-mx-1 flex gap-2 overflow-x-auto px-1 pb-1', className)} role="group" aria-label="Lọc theo nhóm">
      {chips.map((c) => {
        const active = value === c.id;
        return (
          <button
            key={c.id ?? 'all'}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(c.id)}
            className={clsx(
              'inline-flex min-h-[40px] shrink-0 items-center gap-1.5 rounded-full border px-3 text-sm',
              active ? 'border-brand-500 bg-brand-50 font-medium text-brand-700' : 'border-slate-300 bg-white text-slate-600'
            )}
          >
            <span className="max-w-[12rem] truncate">{c.label}</span>
            <span className="text-xs text-slate-400">{c.count}</span>
          </button>
        );
      })}
    </div>
  );
}
