import clsx from 'clsx';

/** Toggle chip for the CEFR / topic filters (LES-02/03). */
export function FilterChip({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={clsx(
        'inline-flex min-h-[36px] items-center gap-1 rounded-full border px-3 text-sm transition-colors',
        active
          ? 'border-brand-600 bg-brand-600 text-white'
          : 'border-slate-300 bg-white text-slate-700 hover:border-brand-400 hover:text-brand-700'
      )}
    >
      {children}
    </button>
  );
}
