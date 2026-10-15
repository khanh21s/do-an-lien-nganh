'use client';

import { authApi } from '@/lib/endpoints/auth';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { isLessonLocked } from '@/lib/pro-lesson';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';

/**
 * P2-A1: is practising this lesson locked for the signed-in user? Reuses the shared
 * `userSubscription` / `profile` caches (Topbar/Sidebar load them anyway). `ready` is false while
 * the premium status of a Pro lesson is still loading, so pages can wait instead of flashing.
 */
export function useLessonAccess(lesson: { isPro?: boolean | null } | null | undefined) {
  const token = useAuthStore((s) => s.token);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const enabled = hasHydrated && !!token && !!lesson?.isPro;

  const subQuery = useQuery({
    queryKey: qk.userSubscription,
    queryFn: subscriptionApi.getUserSubscription,
    enabled,
  });
  const profileQuery = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile, enabled });

  const locked = isLessonLocked(lesson, {
    isPremium: subQuery.data ? subQuery.data.isPremium : subQuery.isError ? null : undefined,
    isAdmin: profileQuery.data?.isAdmin,
  });
  const ready = !enabled || !subQuery.isLoading;
  return { locked, ready };
}
