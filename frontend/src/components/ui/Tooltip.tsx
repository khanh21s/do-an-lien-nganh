import clsx from 'clsx';

/**
 * CSS-only tooltip shown on hover/focus of the wrapped element (no portal, no dependency).
 * Keep content short; it is also exposed to assistive tech via `role="tooltip"`.
 */
export function Tooltip({
  content,
  children,
  side = 'top',
  className,
}: {
  content: React.ReactNode;
  children: React.ReactNode;
  side?: 'top' | 'bottom';
  className?: string;
}) {
  return (
    <span className={clsx('group relative inline-flex', className)}>
      {children}
      <span
        role="tooltip"
        className={clsx(
          'pointer-events-none absolute left-1/2 z-40 -translate-x-1/2 whitespace-nowrap rounded-md bg-ink-900 px-2 py-1 text-xs text-white opacity-0 shadow transition-opacity group-focus-within:opacity-100 group-hover:opacity-100',
          side === 'top' ? 'bottom-full mb-2' : 'top-full mt-2'
        )}
      >
        {content}
      </span>
    </span>
  );
}
