'use client';

import { Button } from '@/components/ui/Button';
import Link from 'next/link';
import { useEffect } from 'react';

/** NAV-02: error boundary inside the app shell (sidebar/topbar stay visible). */
export default function AppError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div
      role="alert"
      className="mx-auto mt-10 flex max-w-md flex-col items-center gap-3 rounded-2xl border border-slate-200 bg-white px-6 py-10 text-center"
    >
      <div className="text-4xl" aria-hidden>
        ⚠️
      </div>
      <h2 className="text-lg font-semibold text-slate-900">Đã có lỗi xảy ra</h2>
      <p className="text-sm text-slate-600">Không hiển thị được nội dung này. Vui lòng thử lại.</p>
      <div className="mt-2 flex flex-wrap justify-center gap-2">
        <Button type="button" onClick={() => reset()} className="min-h-[44px]">
          Thử lại
        </Button>
        <Link
          href="/dashboard"
          className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Về bảng điều khiển
        </Link>
      </div>
    </div>
  );
}
