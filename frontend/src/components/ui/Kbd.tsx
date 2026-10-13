import clsx from 'clsx';

/** A single key cap, e.g. <Kbd>Ctrl</Kbd>. */
export function Kbd({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <kbd
      className={clsx(
        'inline-flex min-w-[1.5rem] items-center justify-center rounded border border-slate-300 bg-slate-50 px-1.5 py-0.5 font-mono text-xs font-medium text-slate-700 shadow-[0_1px_0_rgb(203,213,225)]',
        className
      )}
    >
      {children}
    </kbd>
  );
}

/** A key combination, e.g. <KbdCombo keys={['Shift', '→']} />. */
export function KbdCombo({ keys, className }: { keys: string[]; className?: string }) {
  return (
    <span className={clsx('inline-flex items-center gap-1', className)}>
      {keys.map((k, i) => (
        <span key={`${k}-${i}`} className="inline-flex items-center gap-1">
          {i > 0 && <span className="text-xs text-slate-400">+</span>}
          <Kbd>{k}</Kbd>
        </span>
      ))}
    </span>
  );
}
