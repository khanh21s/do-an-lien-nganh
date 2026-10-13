'use client';

import { Button } from '@/components/ui/Button';
import Link from 'next/link';
import { useEffect } from 'react';

/** NAV-02: root error boundary; "Thử lại" re-renders the segment via `reset()`. */
export default function RootError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-brand-50 px-4 text-center">
      <div className="text-5xl" aria-hidden>
        ⚠️
      </div>
      <h1 className="text-2xl font-bold text-slate-900">Đã có lỗi xảy ra</h1>
      <p className="max-w-md text-sm text-slate-600">
        Trang gặp sự cố ngoài ý muốn. Bạn có thể thử lại hoặc quay về trang chủ.
      </p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Button type="button" onClick={() => reset()} className="min-h-[44px]">
          Thử lại
        </Button>
        <Link
          href="/"
          className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Về trang chủ
        </Link>
      </div>
    </main>
  );
}
