import { AppShell } from '@/components/layout/AppShell';
import { AuthGuard } from '@/components/layout/AuthGuard';
import { ReviewReminder } from '@/components/layout/ReviewReminder';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <AppShell>{children}</AppShell>
      <ReviewReminder />
    </AuthGuard>
  );
}
