import { AuthGuard } from '@/components/layout/AuthGuard';

// T14: the admin console is its own route group, outside the learner AppShell.
export default function AdminGroupLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
