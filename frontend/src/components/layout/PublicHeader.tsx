'use client';

import { useAuthStore } from '@/store/auth-store';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { matchesRoute } from './navItems';

const PUBLIC_LINKS = [
  { href: '/lessons', label: 'Bài học' },
  { href: '/pricing', label: 'Bảng giá' },
];

/** Guest header (landing, /lessons, /pricing). Fits 360px without horizontal scroll (NAV-06). */
export function PublicHeader() {
  const pathname = usePathname();
  const token = useAuthStore((s) => s.token);
  // Persisted auth is only known on the client: render the guest variant on the server and on the
  // first client render, then switch, so the markup never mismatches during hydration.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const signedIn = mounted && !!token;

  const loginHref =
    pathname && pathname !== '/' && !pathname.startsWith('/login')
      ? `/login?next=${encodeURIComponent(pathname)}`
      : '/login';

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4 sm:h-16 sm:gap-4 sm:px-6">
        <Link href="/" className="flex shrink-0 items-center gap-1.5 text-xl font-bold text-brand-600">
          <span aria-hidden>🦜</span>
          <span className="hidden min-[400px]:inline">Parroto</span>
          <span className="sr-only min-[400px]:hidden">Parroto</span>
        </Link>
        <nav className="ml-auto flex min-w-0 items-center gap-1 sm:gap-2" aria-label="Điều hướng công khai">
          {PUBLIC_LINKS.map((link) => {
            const active = !!pathname && matchesRoute(pathname, link.href);
            return (
              <Link
                key={link.href}
                href={link.href}
                aria-current={active ? 'page' : undefined}
                className={clsx(
                  'flex h-11 items-center whitespace-nowrap rounded-lg px-2 text-sm font-medium sm:px-3',
                  active ? 'text-brand-700' : 'text-slate-600 hover:text-slate-900'
                )}
              >
                {link.label}
              </Link>
            );
          })}
          {signedIn ? (
            <Link
              href="/dashboard"
              className="flex h-9 items-center whitespace-nowrap rounded-lg bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700"
            >
              Vào học
            </Link>
          ) : (
            <>
              <Link
                href={loginHref}
                className="flex h-9 items-center whitespace-nowrap rounded-lg bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700 sm:border sm:border-slate-300 sm:bg-white sm:text-slate-700 sm:hover:bg-slate-50"
              >
                Đăng nhập
              </Link>
              <Link
                href="/register"
                className="hidden h-9 items-center whitespace-nowrap rounded-lg bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700 sm:flex"
              >
                Bắt đầu miễn phí
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
