import { AuthAwareShell } from '@/components/layout/AppShell';

// NAV-05: /lessons is public; signed-in users stay inside the app shell, guests get PublicHeader.
export default function LessonsLayout({ children }: { children: React.ReactNode }) {
  return <AuthAwareShell>{children}</AuthAwareShell>;
}
