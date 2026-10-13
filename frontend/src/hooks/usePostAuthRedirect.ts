'use client';

import { postAuthPath } from '@/components/layout/roleRouting';
import { authApi } from '@/lib/endpoints/auth';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

/**
 * T14: once a token exists (fresh login/register, or an already signed-in visitor on /login),
 * loads the profile and replaces to the role's home: admin → `/admin`, user → `next` or
 * `/dashboard` (see `postAuthPath`). If the profile cannot be loaded the user is treated as a
 * learner; the admin guard still protects `/admin/**`.
 *
 * Returns `redirecting` so the page can show a spinner instead of the form meanwhile.
 */
export function usePostAuthRedirect(rawNext: string | null): { redirecting: boolean } {
  const router = useRouter();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);
  const enabled = hasHydrated && !!token;
  const { data: profile, isError } = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile, enabled });

  useEffect(() => {
    if (!enabled) return;
    if (profile) router.replace(postAuthPath(profile.isAdmin, rawNext));
    else if (isError) router.replace(postAuthPath(false, rawNext));
  }, [enabled, profile, isError, rawNext, router]);

  return { redirecting: enabled };
}
