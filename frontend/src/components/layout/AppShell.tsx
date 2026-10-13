'use client';

import { MOBILE_TAB_BAR_PADDING, MobileTabBar } from '@/components/layout/MobileTabBar';
import { PublicHeader } from '@/components/layout/PublicHeader';
import { Sidebar } from '@/components/layout/Sidebar';
import { Topbar } from '@/components/layout/Topbar';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useAuthStore } from '@/store/auth-store';
import clsx from 'clsx';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { isPracticeRoute } from './navItems';

/** True after the first client render; lets auth-dependent markup match the server HTML. */
export function useMounted(): boolean {
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  return mounted;
}

/**
 * Authenticated chrome: collapsible sidebar (≥ md), sticky topbar and the mobile tab bar (< md).
 * Shared by the (app) route group and the public /lessons and /pricing pages when signed in,
 * so moving between them never swaps the shell out.
 */
export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const showTabBar = !isPracticeRoute(pathname);

  return (
    <div className="flex min-h-screen">
      <Sidebar />
      {/* overflow-x-clip (not hidden) so the sticky Topbar keeps sticking to the viewport (NAV-06). */}
      <div className="flex min-w-0 flex-1 flex-col overflow-x-clip">
        <Topbar />
        <main
          className={clsx(
            'min-w-0 flex-1 bg-slate-50 px-4 pt-4 sm:px-6 md:p-8',
            showTabBar ? clsx(MOBILE_TAB_BAR_PADDING, 'md:pb-8') : 'pb-6'
          )}
        >
          {children}
        </main>
      </div>
      {showTabBar && <MobileTabBar />}
    </div>
  );
}

/** Guest chrome for public pages. */
export function PublicShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen overflow-x-clip bg-slate-50">
      <PublicHeader />
      <main className="px-4 py-8 sm:px-6 md:py-10">{children}</main>
    </div>
  );
}

/**
 * NAV-05: public routes (/lessons, /pricing) render the AppShell for signed-in users and the
 * PublicHeader for guests. Shows a spinner until the persisted auth state is known on the client
 * so neither shell flashes before the other.
 */
export function AuthAwareShell({ children }: { children: React.ReactNode }) {
  const mounted = useMounted();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);

  if (!mounted || !hasHydrated) return <FullPageSpinner />;
  if (token) return <AppShell>{children}</AppShell>;
  return <PublicShell>{children}</PublicShell>;
}
