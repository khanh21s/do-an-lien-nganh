import { AuthAwareShell } from '@/components/layout/AppShell';

// NAV-05: /pricing follows the same rule as /lessons (AppShell when signed in, PublicHeader for guests).
export default function PricingLayout({ children }: { children: React.ReactNode }) {
  return <AuthAwareShell>{children}</AuthAwareShell>;
}
