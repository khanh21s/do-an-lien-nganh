'use client';

import { AppShell } from '@/components/layout/AppShell';
import { PublicHeader } from '@/components/layout/PublicHeader';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useAuthStore } from '@/store/auth-store';

// /lessons is public, but logged-in users reach it from the sidebar — keep them inside the
// app shell instead of dropping them onto the public header.
export default function LessonsLayout({ children }: { children: React.ReactNode }) {
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);

  if (!hasHydrated) {
    return <FullPageSpinner />;
  }

  if (token) {
    return <AppShell>{children}</AppShell>;
  }

  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="px-6 py-10">{children}</div>
    </main>
  );
}
