import clsx from 'clsx';

export function Spinner({ className }: { className?: string }) {
  return (
    <div
      className={clsx('h-6 w-6 animate-spin rounded-full border-2 border-slate-300 border-t-brand-600', className)}
    />
  );
}

export function FullPageSpinner() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center">
      <Spinner className="h-8 w-8" />
    </div>
  );
}
