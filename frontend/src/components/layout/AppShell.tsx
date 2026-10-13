import { Sidebar } from '@/components/layout/Sidebar';
import { Topbar } from '@/components/layout/Topbar';

// Authenticated chrome (fixed sidebar + sticky topbar). Shared by the (app) route group and
// the public /lessons pages when a user is logged in, so navigating between them never
// swaps the sidebar out.
export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <Topbar />
        <main className="flex-1 bg-slate-50 p-4 md:p-8">{children}</main>
      </div>
    </div>
  );
}
