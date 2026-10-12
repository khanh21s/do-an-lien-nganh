'use client';

import { FullPageSpinner } from '@/components/ui/Spinner';
import { useAuthStore } from '@/store/auth-store';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';

export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);

  useEffect(() => {
    if (hasHydrated && !token) {
      router.replace('/login');
    }
  }, [hasHydrated, token, router]);

  if (!hasHydrated || !token) {
    return <FullPageSpinner />;
  }

  return <>{children}</>;
}
