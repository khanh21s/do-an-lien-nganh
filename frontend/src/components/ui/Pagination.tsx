'use client';

import clsx from 'clsx';

/** Page numbers with ellipsis, e.g. [1, '…', 4, 5, 6, '…', 10]. Exported for reuse. */
export function pageItems(page: number, totalPages: number, siblings = 1): (number | '…')[] {
  if (totalPages <= 0) return [];
  const items: (number | '…')[] = [];
  const start = Math.max(2, page - siblings);
  const end = Math.min(totalPages - 1, page + siblings);
  items.push(1);
  if (start > 2) items.push('…');
  for (let p = start; p <= end; p++) items.push(p);
  if (end < totalPages - 1) items.push('…');
  if (totalPages > 1) items.push(totalPages);
  return items;
}

const btn =
  'inline-flex h-11 min-w-[44px] items-center justify-center rounded-lg px-3 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-40';

/** 1-based pagination (architecture §5.4). Renders nothing when there is a single page. */
export function Pagination({
  page,
  totalPages,
  onChange,
  className,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  className?: string;
}) {
  if (totalPages <= 1) return null;

  return (
    <nav aria-label="Phân trang" className={clsx('flex flex-wrap items-center justify-center gap-1', className)}>
      <button
        type="button"
        className={clsx(btn, 'text-slate-600 hover:bg-slate-100')}
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
        aria-label="Trang trước"
      >
        ‹
      </button>
      {/* Compact label on narrow screens, numbered buttons from sm up. */}
      <span className="px-2 text-sm text-slate-600 sm:hidden">
        Trang {page}/{totalPages}
      </span>
      <div className="hidden items-center gap-1 sm:flex">
        {pageItems(page, totalPages).map((item, i) =>
          item === '…' ? (
            <span key={`gap-${i}`} className="px-1 text-slate-400">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              onClick={() => onChange(item)}
              aria-current={item === page ? 'page' : undefined}
              className={clsx(
                btn,
                item === page ? 'bg-brand-600 text-white' : 'text-slate-700 hover:bg-slate-100'
              )}
            >
              {item}
            </button>
          )
        )}
      </div>
      <button
        type="button"
        className={clsx(btn, 'text-slate-600 hover:bg-slate-100')}
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
        aria-label="Trang sau"
      >
        ›
      </button>
    </nav>
  );
}
