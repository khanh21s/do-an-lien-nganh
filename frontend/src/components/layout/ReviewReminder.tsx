'use client';

import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { storageKeys } from '@/lib/storageKeys';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { localDateKey, shouldShowReviewReminder } from './navItems';

/**
 * NAV-07: "Đã đến lúc ôn tập: N từ" popup on /dashboard when cards are due, at most once per
 * local day (`parroto:review-popup-date`). The day is recorded when the popup is shown, so
 * dismissing it also counts.
 */
export function ReviewReminder() {
  const pathname = usePathname();
  const [lastShown, setLastShown, hydrated] = useLocalStorage<string | null>(storageKeys.reviewPopupDate, null);
  const [open, setOpen] = useState(false);
  const [count, setCount] = useState(0);
  const { data: vocabStats } = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });
  const due = vocabStats?.dueToday;
  const close = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!hydrated || open) return;
    const today = localDateKey();
    if (shouldShowReviewReminder({ pathname, dueCount: due, lastShownDate: lastShown, today })) {
      setCount(due ?? 0);
      setOpen(true);
      setLastShown(today);
    }
  }, [hydrated, open, pathname, due, lastShown, setLastShown]);

  useEffect(() => {
    if (pathname !== '/dashboard') setOpen(false);
  }, [pathname]);

  return (
    <Modal open={open} onClose={close} title="Đã đến lúc ôn tập!" className="max-w-sm">
      <div className="text-center">
        <p className="text-5xl" aria-hidden>
          🧠
        </p>
        <p className="mt-3 text-slate-700">
          Đã đến lúc ôn tập: <strong className="text-brand-700">{count} từ</strong> đang chờ bạn.
        </p>
        <p className="mt-1 text-sm text-slate-500">Ôn đúng hạn giúp bạn nhớ từ lâu hơn.</p>
        <div className="mt-6 flex flex-col gap-2 sm:flex-row-reverse">
          <Link
            href="/vocabulary/review"
            onClick={close}
            className="inline-flex min-h-[44px] items-center justify-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700 sm:flex-1"
          >
            Ôn tập ngay
          </Link>
          <Button variant="secondary" className="min-h-[44px] sm:flex-1" onClick={close}>
            Để sau
          </Button>
        </div>
      </div>
    </Modal>
  );
}
