'use client';

import { findActiveHref, type NavEntry, type NavLeaf } from '@/components/layout/navItems';
import { useLogout } from '@/components/layout/Topbar';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { ADMIN_CONSOLE_NAV, adminNavHrefs } from './adminNav';

/**
 * T14: chrome of the admin console. Sidebar (≥ md), topbar with the admin email + "Đăng xuất",
 * and a slide-in drawer on mobile. No learner widgets (streak, review chip, upgrade, reminder).
 */
export function AdminShell({ email, children }: { email: string; children: React.ReactNode }) {
  const pathname = usePathname();
  const activeHref = findActiveHref(pathname, adminNavHrefs());
  const [drawerOpen, setDrawerOpen] = useState(false);
  const closeDrawer = useCallback(() => setDrawerOpen(false), []);
  useEffect(() => setDrawerOpen(false), [pathname]);

  return (
    <div className="flex min-h-screen">
      <aside
        className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-slate-200 bg-white md:flex"
        aria-label="Điều hướng quản trị"
      >
        <AdminBrand />
        <nav className="scrollbar-thin flex-1 overflow-y-auto px-3 py-4">
          <AdminNavList entries={ADMIN_CONSOLE_NAV} activeHref={activeHref} />
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col overflow-x-clip">
        <AdminTopbar email={email} onOpenMenu={() => setDrawerOpen(true)} menuOpen={drawerOpen} />
        <main className="min-w-0 flex-1 bg-slate-50 px-4 pb-10 pt-4 sm:px-6 md:p-8">{children}</main>
      </div>

      <AdminDrawer open={drawerOpen} onClose={closeDrawer} activeHref={activeHref} />
    </div>
  );
}

function AdminBrand() {
  return (
    <Link href="/admin" className="flex h-16 shrink-0 items-center gap-2 border-b border-slate-100 px-6">
      <span className="text-xl" aria-hidden>
        🦜
      </span>
      <span className="text-xl font-bold text-brand-600">Parroto</span>
      <span className="rounded-md bg-ink-800 px-1.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide text-white">
        Admin
      </span>
    </Link>
  );
}

function AdminTopbar({ email, onOpenMenu, menuOpen }: { email: string; onOpenMenu: () => void; menuOpen: boolean }) {
  const { logout, loggingOut } = useLogout();

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-slate-200 bg-white px-2 sm:px-4 md:h-16 md:px-6">
      <button
        type="button"
        onClick={onOpenMenu}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-xl text-slate-600 hover:bg-slate-100 md:hidden"
        aria-label="Mở menu quản trị"
        aria-haspopup="dialog"
        aria-expanded={menuOpen}
      >
        ☰
      </button>
      <Link href="/admin" className="flex shrink-0 items-center gap-1.5 md:hidden" aria-label="Parroto Admin – Tổng quan">
        <span className="text-xl" aria-hidden>
          🦜
        </span>
        <span className="rounded-md bg-ink-800 px-1.5 py-0.5 text-[11px] font-semibold uppercase text-white">Admin</span>
      </Link>

      <div className="ml-auto flex min-w-0 items-center gap-2 sm:gap-3">
        <span className="hidden h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700 sm:flex" aria-hidden>
          {email[0]?.toUpperCase() ?? '?'}
        </span>
        <span className="min-w-0 truncate text-sm font-medium text-slate-700" title={email}>
          {email || '…'}
        </span>
        <button
          type="button"
          onClick={logout}
          disabled={loggingOut}
          className="flex h-11 shrink-0 items-center gap-1 whitespace-nowrap rounded-lg px-2 text-sm font-medium text-red-600 hover:bg-red-50 disabled:opacity-60 sm:px-3"
        >
          <span aria-hidden>↩</span>
          {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
        </button>
      </div>
    </header>
  );
}

/** Left slide-in menu (< md). `role="dialog" aria-modal` so page hotkeys pause while open. */
function AdminDrawer({ open, onClose, activeHref }: { open: boolean; onClose: () => void; activeHref: string | null }) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

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

  return (
    <div className="fixed inset-0 z-50 md:hidden">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col overflow-y-auto bg-white shadow-xl outline-none"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2">
          <h2 id={titleId} className="text-base font-semibold text-slate-900">
            Quản trị
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
        <nav className="px-3 py-3" aria-label="Điều hướng quản trị">
          <AdminNavList entries={ADMIN_CONSOLE_NAV} activeHref={activeHref} onNavigate={onClose} />
        </nav>
      </div>
    </div>
  );
}

function AdminNavList({
  entries,
  activeHref,
  onNavigate,
}: {
  entries: NavEntry[];
  activeHref: string | null;
  onNavigate?: () => void;
}) {
  return (
    <div className="space-y-1">
      {entries.map((entry) =>
        entry.kind === 'leaf' ? (
          <AdminNavLink key={entry.href} item={entry} active={activeHref === entry.href} onNavigate={onNavigate} />
        ) : (
          <div key={entry.key} role="group" aria-label={entry.label}>
            <p
              className={clsx(
                'flex items-center gap-2 px-3 pb-1 pt-3 text-xs font-semibold uppercase tracking-wide',
                entry.children.some((c) => c.href === activeHref) ? 'text-brand-700' : 'text-slate-400'
              )}
            >
              <span aria-hidden>{entry.icon}</span> {entry.label}
            </p>
            <div className="space-y-1 pl-2">
              {entry.children.map((child) => (
                <AdminNavLink
                  key={child.href}
                  item={child}
                  active={activeHref === child.href}
                  onNavigate={onNavigate}
                />
              ))}
            </div>
          </div>
        )
      )}
    </div>
  );
}

function AdminNavLink({ item, active, onNavigate }: { item: NavLeaf; active: boolean; onNavigate?: () => void }) {
  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={clsx(
        'flex min-h-[44px] items-center gap-3 rounded-xl px-3 text-sm font-medium transition-colors',
        active ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
      )}
    >
      <span
        className={clsx('flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-base', active ? 'bg-white' : 'bg-slate-50')}
        aria-hidden
      >
        {item.icon}
      </span>
      <span className="min-w-0 flex-1 truncate">{item.label}</span>
    </Link>
  );
}
