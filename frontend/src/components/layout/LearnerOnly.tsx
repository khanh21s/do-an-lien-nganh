'use client';

import { FullPageSpinner } from '@/components/ui/Spinner';
import { authApi } from '@/lib/endpoints/auth';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { roleRedirect } from './roleRouting';

/**
 * T14: the learning UI is for learners only. Must be rendered for signed-in users (inside
 * AuthGuard / the token branch of AuthAwareShell). Shows a spinner until the profile is known so
 * an admin never sees the learner shell flash, then replaces admins to `/admin`.
 * If the profile cannot be loaded the learner UI is rendered (pages show their own errors).
 */
export function LearnerOnly({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { data: profile, isLoading } = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const target = profile ? roleRedirect('learner', profile.isAdmin) : null;

  useEffect(() => {
    if (target) router.replace(target);
  }, [target, router]);

  if (isLoading || target) return <FullPageSpinner />;
  return <>{children}</>;
}
