'use client';

import { Button } from '@/components/ui/Button';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import clsx from 'clsx';
import Link from 'next/link';

export interface ErrorStateProps {
  error?: unknown;
  /** Force the "not found" variant (also chosen automatically for axios 404 errors). */
  notFound?: boolean;
  title?: string;
  message?: string;
  onRetry?: () => void;
  backHref?: string;
  backLabel?: string;
  className?: string;
}

/**
 * Standard error/not-found block (architecture §6.3, LES-01):
 * `if (isError) return <ErrorState error={error} onRetry={refetch} />`.
 */
export function ErrorState({
  error,
  notFound,
  title,
  message,
  onRetry,
  backHref,
  backLabel,
  className,
}: ErrorStateProps) {
  const status = apiErrorStatus(error);
  const isNotFound = notFound || status === 404;

  const heading = title ?? (isNotFound ? 'Không tìm thấy nội dung' : 'Đã có lỗi xảy ra');
  const body =
    message ??
    (isNotFound
      ? 'Nội dung bạn tìm không tồn tại hoặc đã bị xoá.'
      : apiErrorMessage(error, 'Không tải được dữ liệu, vui lòng thử lại.'));

  return (
    <div
      role="alert"
      className={clsx(
        'mx-auto flex max-w-md flex-col items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 py-10 text-center',
        className
      )}
    >
      <div className="text-4xl" aria-hidden>
        {isNotFound ? '🔍' : '⚠️'}
      </div>
      <h2 className="text-lg font-semibold text-slate-900">{heading}</h2>
      <p className="text-sm text-slate-600">{body}</p>
      <div className="mt-2 flex flex-wrap justify-center gap-2">
        {onRetry && !isNotFound && (
          <Button type="button" onClick={() => onRetry()} className="min-h-[44px]">
            Thử lại
          </Button>
        )}
        {backHref && (
          <Link
            href={backHref}
            className="inline-flex min-h-[44px] items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            {backLabel ?? 'Quay lại'}
          </Link>
        )}
      </div>
    </div>
  );
}
