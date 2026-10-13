import { AppShell } from '@/components/layout/AppShell';
import { AuthGuard } from '@/components/layout/AuthGuard';
import { LearnerOnly } from '@/components/layout/LearnerOnly';
import { ReviewReminder } from '@/components/layout/ReviewReminder';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      {/* T14: admins are sent to the admin console before any learner UI renders. */}
      <LearnerOnly>
        <AppShell>{children}</AppShell>
        <ReviewReminder />
      </LearnerOnly>
    </AuthGuard>
  );
}
