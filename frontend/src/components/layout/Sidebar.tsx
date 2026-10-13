'use client';

import { authApi } from '@/lib/endpoints/auth';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const NAV_ITEMS = [
  { href: '/dashboard', label: 'Tổng quan', icon: '🏠' },
  { href: '/lessons', label: 'Bài học', icon: '📚' },
  { href: '/practice/history', label: 'Lịch sử luyện tập', icon: '📈' },
  { href: '/vocabulary', label: 'Bộ từ vựng', icon: '🗂️' },
  { href: '/vocabulary/review', label: 'Ôn tập từ vựng', icon: '🧠', badgeKey: 'dueToday' as const },
  { href: '/vocabulary/notebook', label: 'Sổ tay từ vựng', icon: '📓' },
  { href: '/subscription', label: 'Gói Premium', icon: '💎' },
  { href: '/profile', label: 'Hồ sơ', icon: '👤' },
];

const ADMIN_ITEMS = [
  { href: '/admin/lessons', label: 'Quản lý bài học', icon: '🛠️' },
  { href: '/admin/vocabulary', label: 'Quản lý từ vựng', icon: '🛠️' },
];

export function Sidebar() {
  const pathname = usePathname();
  // Cache key 'profile' is shared with the Profile page's own useQuery, so this doesn't
  // trigger an extra network call once that page (or this sidebar) has fetched it once.
  const { data: profile } = useQuery({ queryKey: ['profile'], queryFn: authApi.getProfile });
  const { data: vocabStats } = useQuery({ queryKey: ['vocab-stats'], queryFn: vocabularyApi.getStats });
  const { data: userSub } = useQuery({ queryKey: ['user-subscription'], queryFn: subscriptionApi.getUserSubscription });

  return (
    <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col border-r border-slate-100 bg-white md:flex">
      <div className="flex h-16 items-center gap-2 border-b border-slate-100 px-6">
        <span className="text-xl">🦜</span>
        <span className="text-xl font-bold text-brand-600">Parroto</span>
      </div>

      <nav className="scrollbar-thin flex-1 space-y-1 overflow-y-auto px-3 py-4">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.href}
            item={item}
            active={pathname === item.href || pathname.startsWith(item.href + '/')}
            badge={item.badgeKey && vocabStats ? vocabStats[item.badgeKey] : undefined}
          />
        ))}
        {profile?.isAdmin && (
          <div className="my-3 border-t border-slate-100 pt-3">
            <p className="px-3 pb-1 text-xs font-semibold uppercase text-slate-400">Quản trị</p>
            {ADMIN_ITEMS.map((item) => (
              <NavLink key={item.href} item={item} active={pathname.startsWith(item.href)} />
            ))}
          </div>
        )}
      </nav>

      {!userSub?.isPremium && (
        <div className="px-3 pb-3">
          <Link
            href="/subscription"
            className="flex items-center justify-center gap-2 rounded-xl bg-pro-gradient px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
          >
            👑 Mở khoá PRO
          </Link>
        </div>
      )}

      <Link
        href="/profile"
        className="flex items-center gap-3 border-t border-slate-100 px-4 py-3 transition-colors hover:bg-slate-50"
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700">
          {profile?.email?.[0]?.toUpperCase() ?? '?'}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-medium text-slate-800">{profile?.email ?? '...'}</span>
          <span className="block text-xs text-slate-400">Hồ sơ · {profile?.cefrLevel ?? ''}</span>
        </span>
      </Link>
    </aside>
  );
}

function NavLink({
  item,
  active,
  badge,
}: {
  item: { href: string; label: string; icon: string };
  active: boolean;
  badge?: number;
}) {
  return (
    <Link
      href={item.href}
      className={clsx(
        'flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors',
        active ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100'
      )}
    >
      <span
        className={clsx(
          'flex h-7 w-7 items-center justify-center rounded-lg text-base',
          active ? 'bg-white' : 'bg-slate-50'
        )}
      >
        {item.icon}
      </span>
      <span className="flex-1">{item.label}</span>
      {!!badge && (
        <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white">
          {badge}
        </span>
      )}
    </Link>
  );
}
