'use client';

import { useLogout } from '@/components/layout/Topbar';
import type { VocabUserStats } from '@/lib/types';
import clsx from 'clsx';
import Link from 'next/link';
import { useEffect, useId, useRef } from 'react';
import { badgeCount, moreDrawerEntries, type NavLeaf } from './navItems';

/**
 * NAV-01: bottom sheet behind the "Thêm" tab with every destination that is not a tab.
 * Renders `role="dialog" aria-modal="true"` so page hotkeys pause while it is open.
 */
export function MoreDrawer({
  open,
  onClose,
  activeHref,
  vocabStats,
}: {
  open: boolean;
  onClose: () => void;
  activeHref: string | null;
  vocabStats?: VocabUserStats;
}) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const { logout, loggingOut } = useLogout();

  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const previousFocus = document.activeElement as HTMLElement | null;
    panelRef.current?.focus();
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  const entries = moreDrawerEntries();

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="absolute inset-x-0 bottom-0 max-h-[85vh] overflow-y-auto rounded-t-2xl bg-white pb-[env(safe-area-inset-bottom)] shadow-xl outline-none"
      >
        <div className="sticky top-0 flex items-center justify-between border-b border-slate-100 bg-white px-4 py-2">
          <h2 id={titleId} className="text-base font-semibold text-slate-900">
            Thêm
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-600"
            aria-label="Đóng"
          >
            ✕
          </button>
        </div>

        <nav className="space-y-1 px-3 py-3" aria-label="Điều hướng khác">
          {entries.map((entry) =>
            entry.kind === 'leaf' ? (
              <DrawerLink
                key={entry.href}
                item={entry}
                active={activeHref === entry.href}
                badge={badgeCount(entry.badge, vocabStats)}
                onNavigate={onClose}
              />
            ) : (
              <div key={entry.key} role="group" aria-label={entry.label} className="pt-2">
                <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-slate-400">{entry.label}</p>
                {entry.children.map((child) => (
                  <DrawerLink
                    key={child.href}
                    item={child}
                    active={activeHref === child.href}
                    badge={badgeCount(child.badge, vocabStats)}
                    onNavigate={onClose}
                  />
                ))}
              </div>
            )
          )}
          <div className="mt-2 border-t border-slate-100 pt-2">
            <button
              type="button"
              onClick={logout}
              disabled={loggingOut}
              className="flex min-h-[44px] w-full items-center gap-3 rounded-xl px-3 text-left text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-60"
            >
              <span className="flex h-7 w-7 items-center justify-center" aria-hidden>
                ↩
              </span>
              {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
            </button>
          </div>
        </nav>
      </div>
    </div>
  );
}

function DrawerLink({
  item,
  active,
  badge,
  onNavigate,
}: {
  item: NavLeaf;
  active: boolean;
  badge?: number;
  onNavigate: () => void;
}) {
  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={clsx(
        'flex min-h-[44px] items-center gap-3 rounded-xl px-3 text-sm font-medium',
        active ? 'bg-brand-50 text-brand-700' : 'text-slate-700 hover:bg-slate-100'
      )}
    >
      <span className="flex h-7 w-7 items-center justify-center text-base" aria-hidden>
        {item.icon}
      </span>
      <span className="min-w-0 flex-1 truncate">{item.label}</span>
      {!!badge && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white">
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </Link>
  );
}
