'use client';

import { TopbarGamification } from '@/components/gamification/TopbarGamification';
import { DictionarySearch } from '@/components/layout/DictionarySearch';
import { authApi } from '@/lib/endpoints/auth';
import { practiceApi } from '@/lib/endpoints/practice';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useCallback, useEffect, useRef, useState } from 'react';

/** Logout shared by the Topbar avatar menu and the mobile "Thêm" drawer. */
export function useLogout() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [loggingOut, setLoggingOut] = useState(false);

  const logout = useCallback(async () => {
    setLoggingOut(true);
    try {
      // Send the stored refresh token so the server revokes it as well.
      await authApi.logout(useAuthStore.getState().refreshToken);
    } catch {
      // ignore: local state is cleared regardless
    } finally {
      clearAuth();
      // Drop the previous user's cached data (profile, stats, …) before anyone else signs in.
      queryClient.clear();
      router.replace('/login');
    }
  }, [clearAuth, queryClient, router]);

  return { logout, loggingOut };
}

export function Topbar() {
  const user = useAuthStore((s) => s.user);

  // Cache keys shared with Sidebar/Dashboard. T08/T09 invalidate qk.streak after a submit,
  // T10 invalidates qk.vocabStats after grading a card, so these chips update live (PRA-07).
  const { data: profile } = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const { data: streak } = useQuery({ queryKey: qk.streak, queryFn: practiceApi.getStreak });
  const { data: vocabStats } = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });
  const { data: userSub } = useQuery({
    queryKey: qk.userSubscription,
    queryFn: subscriptionApi.getUserSubscription,
  });

  const email = profile?.email ?? user?.email ?? '';
  const due = vocabStats?.dueToday ?? 0;
  const streakDays = streak?.currentStreak ?? 0;

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-slate-100 bg-white px-3 sm:px-4 md:h-16 md:px-6">
      <Link href="/dashboard" className="flex items-center gap-1.5 md:hidden" aria-label="Parroto – Dashboard">
        <span className="text-xl" aria-hidden>
          🦜
        </span>
        <span className="hidden text-lg font-bold text-brand-600 min-[400px]:inline">Parroto</span>
      </Link>

      <DictionarySearch className="mx-1 min-w-0 flex-1 sm:mx-3 sm:max-w-sm" />

      <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-3">
        <span
          className="flex h-9 items-center gap-1 rounded-full bg-amber-50 px-2.5 text-sm font-semibold text-amber-700"
          title="Chuỗi ngày học liên tiếp"
          aria-label={`Chuỗi ${streakDays} ngày học liên tiếp`}
        >
          <span aria-hidden>🔥</span> {streakDays}
        </span>
        {/* --- P2-B --- level + diamonds (also toasts XP gains) */}
        <TopbarGamification />
        <Link
          href="/vocabulary/review"
          className={clsx(
            'flex h-9 items-center gap-1 rounded-full px-2.5 text-sm font-semibold',
            due > 0 ? 'bg-red-50 text-red-600' : 'bg-brand-50 text-brand-700'
          )}
          title="Thẻ cần ôn hôm nay"
          aria-label={`${due} thẻ cần ôn hôm nay`}
        >
          <span aria-hidden>🧠</span> {due}
        </Link>
        {userSub?.isPremium ? (
          <span
            className="flex h-9 items-center rounded-full bg-pro-gradient px-3 text-xs font-bold tracking-wide text-white"
            title="Bạn đang dùng gói Premium"
          >
            PRO
          </span>
        ) : (
          <Link
            href="/subscription"
            className="flex h-9 items-center gap-1 whitespace-nowrap rounded-full bg-pro-gradient px-3 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90"
          >
            <span aria-hidden>👑</span>
            <span className="hidden sm:inline">Nâng cấp</span>
            <span className="sr-only sm:hidden">Nâng cấp</span>
          </Link>
        )}
        <AvatarMenu email={email} cefrLevel={profile?.cefrLevel ?? user?.cefrLevel} />
      </div>
    </header>
  );
}

function AvatarMenu({ email, cefrLevel }: { email: string; cefrLevel?: string | null }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const { logout, loggingOut } = useLogout();

  // Close on navigation, outside click and Escape.
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return;
    function onPointer(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="flex h-11 w-11 items-center justify-center rounded-full hover:bg-slate-100"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Menu tài khoản"
      >
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700">
          {email[0]?.toUpperCase() ?? '?'}
        </span>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 top-full z-40 mt-1 w-60 max-w-[calc(100vw-1.5rem)] overflow-hidden rounded-xl border border-slate-100 bg-white py-1 shadow-lg"
        >
          <div className="border-b border-slate-100 px-4 py-2.5">
            <p className="truncate text-sm font-medium text-slate-800">{email || '…'}</p>
            {cefrLevel && <p className="text-xs text-slate-400">Trình độ {cefrLevel}</p>}
          </div>
          <Link
            href="/profile"
            role="menuitem"
            className="flex h-11 items-center gap-2 px-4 text-sm text-slate-700 hover:bg-slate-50"
          >
            <span aria-hidden>👤</span> Hồ sơ
          </Link>
          <button
            type="button"
            role="menuitem"
            onClick={logout}
            disabled={loggingOut}
            className="flex h-11 w-full items-center gap-2 px-4 text-left text-sm text-red-600 hover:bg-red-50 disabled:opacity-60"
          >
            <span aria-hidden>↩</span> {loggingOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
          </button>
        </div>
      )}
    </div>
  );
}
