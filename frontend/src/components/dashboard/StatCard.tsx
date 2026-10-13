import { Card } from '@/components/ui/Card';
import clsx from 'clsx';
import Link from 'next/link';

/** Single KPI tile on the dashboard (PRA-01). With `href` the whole tile is a link. */
export function StatCard({
  icon,
  tint,
  label,
  value,
  hint,
  href,
}: {
  icon: string;
  tint: string;
  label: string;
  value: React.ReactNode;
  hint?: React.ReactNode;
  href?: string;
}) {
  const body = (
    <Card className={clsx('h-full', href && 'transition-shadow hover:shadow-md')}>
      <span className={clsx('flex h-9 w-9 items-center justify-center rounded-lg text-lg', tint)} aria-hidden>
        {icon}
      </span>
      <p className="mt-3 text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </Card>
  );
  return href ? (
    <Link href={href} className="block h-full rounded-2xl focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
      {body}
    </Link>
  ) : (
    body
  );
}

/** Compact inline error for a single widget (the page-level ErrorState is for the core stats). */
export function WidgetError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center text-sm text-slate-500">
      <span>⚠️ {message}</span>
      <button
        type="button"
        onClick={onRetry}
        className="min-h-[44px] rounded-lg px-3 font-medium text-brand-600 hover:bg-brand-50"
      >
        Thử lại
      </button>
    </div>
  );
}
