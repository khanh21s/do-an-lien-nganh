'use client';

import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { authApi } from '@/lib/endpoints/auth';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useRef } from 'react';
import toast from 'react-hot-toast';

/**
 * ADM-01: client-side admin guard. Waits for the profile (shared `qk.profile` cache), and sends
 * non-admins to /dashboard without ever rendering admin UI. The BE still enforces 403 on every
 * admin endpoint; this guard only avoids showing pages that cannot work.
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const notified = useRef(false);
  const { data: profile, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.profile,
    queryFn: authApi.getProfile,
  });

  const denied = !!profile && !profile.isAdmin;

  useEffect(() => {
    if (!denied) return;
    if (!notified.current) {
      notified.current = true;
      toast.error('Không có quyền truy cập trang quản trị.');
    }
    router.replace('/dashboard');
  }, [denied, router]);

  if (isLoading) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={refetch} backHref="/dashboard" backLabel="Về Dashboard" />;
  if (!profile || denied) return <FullPageSpinner />;

  return <>{children}</>;
}
