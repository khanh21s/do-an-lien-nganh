'use client';

import { useLocalStorage } from '@/hooks/useLocalStorage';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { storageKeys } from '@/lib/storageKeys';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { MAIN_NAV, allNavHrefs, badgeCount, findActiveHref, type NavLeaf } from './navItems';

/** Desktop sidebar (≥ md). Collapsible to icons only (NAV-04), state kept in localStorage. */
export function Sidebar() {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useLocalStorage<boolean>(storageKeys.sidebarCollapsed, false);

  // Shared cache keys: Topbar / pages reuse these without extra requests; T10 invalidates qk.vocabStats.
  const { data: vocabStats } = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });
  const { data: userSub } = useQuery({
    queryKey: qk.userSubscription,
    queryFn: subscriptionApi.getUserSubscription,
  });

  const activeHref = findActiveHref(pathname, allNavHrefs());

  return (
    <aside
      className={clsx(
        'sticky top-0 hidden h-screen shrink-0 flex-col border-r border-slate-100 bg-white transition-[width] duration-200 md:flex',
        collapsed ? 'w-[72px]' : 'w-64'
      )}
      aria-label="Điều hướng chính"
    >
      <Link
        href="/dashboard"
        className={clsx('flex h-16 items-center gap-2 border-b border-slate-100', collapsed ? 'justify-center' : 'px-6')}
      >
        <span className="text-xl" aria-hidden>
          🦜
        </span>
        {!collapsed && <span className="text-xl font-bold text-brand-600">Parroto</span>}
        {collapsed && <span className="sr-only">Parroto</span>}
      </Link>

      <nav className="scrollbar-thin flex-1 space-y-1 overflow-y-auto overflow-x-hidden px-3 py-4">
        {MAIN_NAV.map((entry) =>
          entry.kind === 'leaf' ? (
            <SidebarLink
              key={entry.href}
              item={entry}
              active={activeHref === entry.href}
              collapsed={collapsed}
              badge={badgeCount(entry.badge, vocabStats)}
            />
          ) : (
            <div key={entry.key} role="group" aria-label={entry.label}>
              {!collapsed ? (
                <p
                  className={clsx(
                    'flex items-center gap-3 px-3 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide',
                    entry.children.some((c) => c.href === activeHref) ? 'text-brand-700' : 'text-slate-400'
                  )}
                >
                  {entry.label}
                </p>
              ) : (
                <div className="mx-3 my-2 border-t border-slate-100" aria-hidden />
              )}
              <div className={clsx('space-y-1', !collapsed && 'pl-2')}>
                {entry.children.map((child) => (
                  <SidebarLink
                    key={child.href}
                    item={child}
                    active={activeHref === child.href}
                    collapsed={collapsed}
                    badge={badgeCount(child.badge, vocabStats)}
                    groupLabel={entry.label}
                  />
                ))}
              </div>
            </div>
          )
        )}
      </nav>

      {!userSub?.isPremium && !collapsed && (
        <div className="px-3 pb-3">
          <Link
            href="/subscription"
            className="flex items-center justify-center gap-2 rounded-xl bg-pro-gradient px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
          >
            👑 Mở khoá PRO
          </Link>
        </div>
      )}

      <button
        type="button"
        onClick={() => setCollapsed((c) => !c)}
        className={clsx(
          'flex h-11 items-center gap-3 border-t border-slate-100 text-sm font-medium text-slate-500 transition-colors hover:bg-slate-50 hover:text-slate-800',
          collapsed ? 'justify-center' : 'px-6'
        )}
        aria-label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'}
        aria-expanded={!collapsed}
        title={collapsed ? 'Mở rộng' : 'Thu gọn'}
      >
        <span aria-hidden className="text-base">
          {collapsed ? '»' : '«'}
        </span>
        {!collapsed && <span>Thu gọn</span>}
      </button>
    </aside>
  );
}

function SidebarLink({
  item,
  active,
  collapsed,
  badge,
  groupLabel,
}: {
  item: NavLeaf;
  active: boolean;
  collapsed: boolean;
  badge?: number;
  groupLabel?: string;
}) {
  const fullLabel = groupLabel ? `${groupLabel} › ${item.label}` : item.label;
  return (
    <Link
      href={item.href}
      aria-current={active ? 'page' : undefined}
      title={collapsed ? fullLabel : undefined}
      className={clsx(
        'relative flex items-center gap-3 rounded-xl py-2 text-sm font-medium transition-colors',
        collapsed ? 'justify-center px-0' : 'px-3',
        active ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
      )}
    >
      <span
        className={clsx(
          'flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-base',
          active ? 'bg-white' : 'bg-slate-50'
        )}
        aria-hidden
      >
        {item.icon}
      </span>
      {collapsed ? (
        <span className="sr-only">{fullLabel}</span>
      ) : (
        <span className="min-w-0 flex-1 truncate">{item.label}</span>
      )}
      {!!badge && (
        <span
          className={clsx(
            'flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white',
            collapsed && 'absolute right-1 top-0.5'
          )}
          aria-label={`${badge} thẻ cần ôn`}
        >
          {badge > 99 ? '99+' : badge}
        </span>
      )}
    </Link>
  );
}
