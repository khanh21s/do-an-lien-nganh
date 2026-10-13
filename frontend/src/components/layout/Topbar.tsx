'use client';

import { Button } from '@/components/ui/Button';
import { authApi } from '@/lib/endpoints/auth';
import { practiceApi } from '@/lib/endpoints/practice';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

export function Topbar() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [loggingOut, setLoggingOut] = useState(false);

  // Cache keys shared with Dashboard/Sidebar queries — no extra network calls once warm.
  const { data: vocabStats } = useQuery({ queryKey: ['vocab-stats'], queryFn: vocabularyApi.getStats });
  const { data: practiceStats } = useQuery({ queryKey: ['practice-stats'], queryFn: practiceApi.getStats });
  const { data: userSub } = useQuery({ queryKey: ['user-subscription'], queryFn: subscriptionApi.getUserSubscription });

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      // ignore — we clear local state regardless
    } finally {
      clearAuth();
      router.replace('/login');
    }
  }

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-slate-100 bg-white px-4 md:px-6">
      <span className="text-lg font-semibold text-brand-600 md:hidden">Parroto</span>
      <div className="ml-auto flex items-center gap-2 sm:gap-3">
        <Link
          href="/vocabulary/review"
          className="flex items-center gap-1.5 rounded-full bg-brand-50 px-3 py-1.5 text-sm font-semibold text-brand-700"
          title="Từ cần ôn hôm nay"
        >
          💎 {vocabStats?.dueToday ?? 0}
        </Link>
        <span
          className="flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1.5 text-sm font-semibold text-amber-700"
          title="Chuỗi ngày học liên tiếp"
        >
          🔥 {practiceStats?.currentStreak ?? 0}
        </span>
        {!userSub?.isPremium && (
          <Link
            href="/subscription"
            className="hidden items-center gap-1.5 rounded-full bg-pro-gradient px-3 py-1.5 text-sm font-semibold text-white shadow-sm transition-opacity hover:opacity-90 sm:flex"
          >
            👑 PRO
          </Link>
        )}
        {user && <span className="hidden text-sm text-slate-600 md:inline">{user.email}</span>}
        <Button variant="secondary" size="sm" loading={loggingOut} onClick={handleLogout}>
          Đăng xuất
        </Button>
      </div>
    </header>
  );
}
