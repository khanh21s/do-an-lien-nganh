'use client';

import { AdminShell } from '@/components/admin/AdminShell';
import { roleRedirect } from '@/components/layout/roleRouting';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { authApi } from '@/lib/endpoints/auth';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useRef } from 'react';
import toast from 'react-hot-toast';

/**
 * ADM-01 / T14: client-side admin guard + admin console shell. Waits for the profile (shared
 * `qk.profile` cache) and sends non-admins to /dashboard without ever rendering admin UI. The BE
 * still enforces 403 on every admin endpoint; this guard only avoids showing pages that cannot work.
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const notified = useRef(false);
  const { data: profile, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.profile,
    queryFn: authApi.getProfile,
  });

  const target = profile ? roleRedirect('admin', profile.isAdmin) : null;

  useEffect(() => {
    if (!target) return;
    if (!notified.current) {
      notified.current = true;
      toast.error('Không có quyền truy cập trang quản trị.');
    }
    router.replace(target);
  }, [target, router]);

  if (isLoading) return <FullPageSpinner />;
  if (isError)
    return (
      <div className="px-4">
        <ErrorState error={error} onRetry={refetch} backHref="/dashboard" backLabel="Về Dashboard" className="mt-10" />
      </div>
    );
  if (!profile || target) return <FullPageSpinner />;

  return <AdminShell email={profile.email}>{children}</AdminShell>;
}
