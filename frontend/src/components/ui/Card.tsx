import clsx from 'clsx';
import { HTMLAttributes } from 'react';

export function Card({ className, children, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={clsx('rounded-2xl border border-slate-200 bg-white p-5 shadow-sm', className)} {...rest}>
      {children}
    </div>
  );
}
