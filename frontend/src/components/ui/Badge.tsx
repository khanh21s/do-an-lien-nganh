import clsx from 'clsx';

type Tone = 'brand' | 'green' | 'amber' | 'red' | 'slate' | 'purple' | 'navy';

const toneClasses: Record<Tone, string> = {
  brand: 'bg-brand-100 text-brand-700',
  green: 'bg-green-100 text-green-700',
  amber: 'bg-amber-100 text-amber-700',
  red: 'bg-red-100 text-red-700',
  slate: 'bg-slate-100 text-slate-600',
  purple: 'bg-violet-100 text-violet-700',
  navy: 'bg-ink-800 text-white',
};

export function Badge({
  tone = 'slate',
  className,
  children,
}: {
  tone?: Tone;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium',
        toneClasses[tone],
        className
      )}
    >
      {children}
    </span>
  );
}
