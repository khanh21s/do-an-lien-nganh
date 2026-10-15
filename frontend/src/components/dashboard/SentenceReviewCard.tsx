'use client';

import { WidgetError } from '@/components/dashboard/StatCard';
import { Card } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import { qk } from '@/lib/queryKeys';
import { formatNextReview } from '@/lib/sentence-review';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';

/** P2-A2: due sentences of the "Ôn câu sai" queue on the dashboard. */
export function SentenceReviewCard() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: qk.sentenceReviewStats,
    queryFn: practiceApi.getReviewStats,
  });

  let body: React.ReactNode;
  if (isLoading) {
    body = <Spinner className="h-5 w-5" />;
  } else if (isError || !data) {
    body = <WidgetError message="Không tải được hàng ôn câu sai." onRetry={() => refetch()} />;
  } else if (data.dueCount > 0) {
    body = (
      <p className="text-sm text-slate-600">
        <strong className="text-2xl text-slate-900">{data.dueCount}</strong> câu đến hạn ôn
        {data.total > data.dueCount ? ` · ${data.total} câu trong hàng ôn` : ''}
      </p>
    );
  } else if (data.total > 0) {
    body = (
      <p className="text-sm text-slate-600">
        Đã ôn hết câu đến hạn. {data.total} câu trong hàng ôn
        {data.nextDueAt ? `, câu tiếp theo ${formatNextReview(data.nextDueAt)}` : ''}.
      </p>
    );
  } else {
    body = (
      <p className="text-sm text-slate-600">
        Câu Dictation chưa đúng 100% hoặc Shadowing dưới 70 điểm sẽ vào đây để luyện lại.
      </p>
    );
  }
  const due = data?.dueCount ?? 0;

  return (
    <Card className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-start gap-3">
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-orange-100 text-xl" aria-hidden>
          🔁
        </span>
        <div className="min-w-0">
          <h2 className="font-semibold text-slate-900">Ôn câu sai</h2>
          <div className="mt-1">{body}</div>
        </div>
      </div>
      <Link
        href="/practice/review"
        className={clsx(
          'inline-flex min-h-[44px] shrink-0 items-center justify-center rounded-lg px-4 text-sm font-semibold',
          due > 0 ? 'bg-brand-600 text-white hover:bg-brand-700' : 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
        )}
      >
        {due > 0 ? 'Ôn ngay →' : 'Xem hàng ôn'}
      </Link>
    </Card>
  );
}
