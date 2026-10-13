'use client';

import { authApi } from '@/lib/endpoints/auth';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { MoreDrawer } from './MoreDrawer';
import { MOBILE_TABS, allNavHrefs, badgeCount, findActiveHref } from './navItems';

/** Height reserved under page content while the tab bar is visible (h-16 + safe area). */
export const MOBILE_TAB_BAR_PADDING = 'pb-[calc(4rem+env(safe-area-inset-bottom)+1rem)]';

/**
 * NAV-01: bottom tab bar for < md (Dashboard, Bài học, Ôn tập, Lịch sử, Thêm).
 * AppShell does not render it on dictation/shadowing screens.
 */
export function MobileTabBar() {
  const pathname = usePathname();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const { data: profile } = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const { data: vocabStats } = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });

  const isAdmin = !!profile?.isAdmin;
  const activeHref = findActiveHref(pathname, allNavHrefs(isAdmin));
  const tabActive = MOBILE_TABS.some((t) => t.href === activeHref);

  const closeDrawer = useCallback(() => setDrawerOpen(false), []);
  useEffect(() => setDrawerOpen(false), [pathname]);

  return (
    <>
      <nav
        className="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
        aria-label="Điều hướng di động"
      >
        <ul className="grid h-16 grid-cols-5">
          {MOBILE_TABS.map((tab) => {
            const active = activeHref === tab.href;
            const badge = badgeCount(tab.badge, vocabStats);
            return (
              <li key={tab.href} className="min-w-0">
                <Link
                  href={tab.href}
                  aria-current={active ? 'page' : undefined}
                  className={clsx(
                    'relative flex h-full flex-col items-center justify-center gap-0.5 px-1 text-[11px] font-medium',
                    active ? 'text-brand-700' : 'text-slate-500'
                  )}
                >
                  <span className="text-xl leading-none" aria-hidden>
                    {tab.icon}
                  </span>
                  <span className="w-full truncate text-center">{tab.shortLabel ?? tab.label}</span>
                  {!!badge && (
                    <span className="absolute left-1/2 top-1.5 ml-2 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-semibold text-white">
                      {badge > 99 ? '99+' : badge}
                    </span>
                  )}
                </Link>
              </li>
            );
          })}
          <li className="min-w-0">
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              aria-haspopup="dialog"
              aria-expanded={drawerOpen}
              className={clsx(
                'flex h-full w-full flex-col items-center justify-center gap-0.5 px-1 text-[11px] font-medium',
                !tabActive && activeHref ? 'text-brand-700' : 'text-slate-500'
              )}
            >
              <span className="text-xl leading-none" aria-hidden>
                ☰
              </span>
              <span>Thêm</span>
            </button>
          </li>
        </ul>
      </nav>
      <MoreDrawer
        open={drawerOpen}
        onClose={closeDrawer}
        isAdmin={isAdmin}
        activeHref={activeHref}
        vocabStats={vocabStats}
      />
    </>
  );
}
