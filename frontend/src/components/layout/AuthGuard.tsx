'use client';

import { FullPageSpinner } from '@/components/ui/Spinner';
import { currentPathForNext, loginUrlWithNext } from '@/lib/api';
import { useAuthStore } from '@/store/auth-store';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

/** AUTH-05: unauthenticated visitors go to `/login?next=<path+search>`. */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);

  useEffect(() => {
    if (hasHydrated && !token) {
      router.replace(loginUrlWithNext(currentPathForNext()));
    }
  }, [hasHydrated, token, router]);

  if (!hasHydrated || !token) {
    return <FullPageSpinner />;
  }

  return <>{children}</>;
}
