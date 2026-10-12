import clsx from 'clsx';
import { ButtonHTMLAttributes, forwardRef } from 'react';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'pro' | 'warning' | 'success';
type Size = 'sm' | 'md' | 'lg';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
}

const variantClasses: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-300',
  secondary: 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 disabled:text-slate-400',
  danger: 'bg-red-500 text-white hover:bg-red-600 disabled:bg-red-300',
  ghost: 'bg-transparent text-slate-600 hover:bg-slate-100 disabled:text-slate-300',
  pro: 'bg-pro-gradient text-white shadow-sm hover:opacity-90 disabled:opacity-50',
  warning: 'bg-amber-500 text-white hover:bg-amber-600 disabled:bg-amber-300',
  success: 'bg-green-500 text-white hover:bg-green-600 disabled:bg-green-300',
};

const sizeClasses: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-5 py-2.5 text-base',
};

export const Button = forwardRef<HTMLButtonElement, Props>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, ...rest }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={clsx(
          'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors disabled:cursor-not-allowed',
          variantClasses[variant],
          sizeClasses[size],
          className
        )}
        {...rest}
      >
        {loading && (
          <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
        )}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
