'use client';

import { apiErrorMessage } from '@/lib/api';
import { gamificationApi } from '@/lib/endpoints/gamification';
import { GAMIFICATION_REFRESH_DELAY_MS, gainMessages } from '@/lib/gamification';
import { qk } from '@/lib/queryKeys';
import type { GamificationMe } from '@/lib/types';
import { type QueryClient, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import toast from 'react-hot-toast';

/** Shared `/vocabs/gamification/me` query (topbar chips + dashboard card use the same cache). */
export function useGamificationMe() {
  return useQuery({ queryKey: qk.gamification, queryFn: gamificationApi.me });
}

/** Toasts "+5 XP" / "Lên cấp" / "+30 💎" whenever a refetch shows a gain. Mount once (Topbar). */
export function useGainToasts(me: GamificationMe | undefined) {
  const prev = useRef<GamificationMe | null>(null);
  useEffect(() => {
    if (!me) return;
    for (const msg of gainMessages(prev.current, me)) toast.success(msg, { id: `gain-${msg}`, duration: 2500 });
    prev.current = me;
  }, [me]);
}

/**
 * Refresh XP after an activity. Practice attempts are reported to vocabulary-service in the
 * background, so the numbers are fetched again after a short delay too.
 */
export function refreshGamification(queryClient: QueryClient, { delayed = false }: { delayed?: boolean } = {}) {
  void queryClient.invalidateQueries({ queryKey: qk.gamification });
  void queryClient.invalidateQueries({ queryKey: qk.xpLeaderboard('week') });
  if (delayed && typeof window !== 'undefined') {
    window.setTimeout(() => {
      void queryClient.invalidateQueries({ queryKey: qk.gamification });
      void queryClient.invalidateQueries({ queryKey: qk.xpLeaderboard('week') });
    }, GAMIFICATION_REFRESH_DELAY_MS);
  }
}

export function useCheckIn() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: gamificationApi.checkIn,
    onSuccess: (res) => {
      if (res.alreadyCheckedIn) toast('Hôm nay bạn đã điểm danh rồi.');
      else if (res.bonusDiamonds > 0) toast.success(`Chuỗi ${res.streak} ngày! Thưởng thêm ${res.bonusDiamonds} 💎`);
      // the "+N 💎" toast itself comes from useGainToasts when the numbers refresh
      void queryClient.invalidateQueries({ queryKey: qk.gamification });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không điểm danh được, vui lòng thử lại.')),
  });
}
