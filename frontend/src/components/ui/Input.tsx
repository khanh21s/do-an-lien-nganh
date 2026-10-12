import clsx from 'clsx';
import { forwardRef, InputHTMLAttributes, LabelHTMLAttributes, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';

interface FieldWrapperProps {
  label?: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}

export function FieldWrapper({ label, error, hint, children }: FieldWrapperProps) {
  return (
    <div className="flex flex-col gap-1">
      {label && <label className="text-sm font-medium text-slate-700">{label}</label>}
      {children}
      {hint && !error && <span className="text-xs text-slate-500">{hint}</span>}
      {error && <span className="text-xs text-red-600">{error}</span>}
    </div>
  );
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(({ label, error, hint, className, ...rest }, ref) => (
  <FieldWrapper label={label} error={error} hint={hint}>
    <input
      ref={ref}
      className={clsx(
        'rounded-lg border px-3 py-2 text-sm outline-none transition-colors focus:border-brand-500 focus:ring-1 focus:ring-brand-500',
        error ? 'border-red-400' : 'border-slate-300',
        className
      )}
      {...rest}
    />
  </FieldWrapper>
));
Input.displayName = 'Input';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(
  ({ label, error, hint, className, ...rest }, ref) => (
    <FieldWrapper label={label} error={error} hint={hint}>
      <textarea
        ref={ref}
        className={clsx(
          'rounded-lg border px-3 py-2 text-sm outline-none transition-colors focus:border-brand-500 focus:ring-1 focus:ring-brand-500',
          error ? 'border-red-400' : 'border-slate-300',
          className
        )}
        {...rest}
      />
    </FieldWrapper>
  )
);
Textarea.displayName = 'Textarea';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  hint?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, error, hint, className, children, ...rest }, ref) => (
    <FieldWrapper label={label} error={error} hint={hint}>
      <select
        ref={ref}
        className={clsx(
          'rounded-lg border bg-white px-3 py-2 text-sm outline-none transition-colors focus:border-brand-500 focus:ring-1 focus:ring-brand-500',
          error ? 'border-red-400' : 'border-slate-300',
          className
        )}
        {...rest}
      >
        {children}
      </select>
    </FieldWrapper>
  )
);
Select.displayName = 'Select';
