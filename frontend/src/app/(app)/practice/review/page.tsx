'use client';

import { SentenceReviewSession } from '@/components/practice/review/SentenceReviewSession';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import { qk } from '@/lib/queryKeys';
import { formatNextReview } from '@/lib/sentence-review';
import type { SentenceReviewItem } from '@/lib/types';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';

/** Sentences per session (backend max 100). */
const SESSION_LIMIT = 50;

/** P2-A2 "Ôn câu sai": walks the due sentences with the Dictation / Shadowing screens. */
export default function SentenceReviewPage() {
  const queryClient = useQueryClient();
  // Snapshot per session so refetches after each attempt don't shift the list under the learner.
  const [session, setSession] = useState<SentenceReviewItem[] | null>(null);
  const [sessionNo, setSessionNo] = useState(0);

  const queueQuery = useQuery({
    queryKey: qk.sentenceReviewQueue,
    queryFn: () => practiceApi.getReviewQueue(SESSION_LIMIT),
    refetchOnWindowFocus: false,
  });
  const statsQuery = useQuery({ queryKey: qk.sentenceReviewStats, queryFn: practiceApi.getReviewStats });

  useEffect(() => {
    if (!session && queueQuery.data && !queueQuery.isFetching) setSession(queueQuery.data);
  }, [session, queueQuery.data, queueQuery.isFetching]);

  function restart() {
    setSession(null);
    setSessionNo((n) => n + 1);
    void queryClient.invalidateQueries({ queryKey: qk.sentenceReviewQueue });
  }

  if (queueQuery.isLoading) return <FullPageSpinner />;
  if (queueQuery.isError) {
    return <ErrorState error={queueQuery.error} onRetry={() => queueQuery.refetch()} backHref="/dashboard" backLabel="Về Dashboard" />;
  }
  if (!session) return <FullPageSpinner />;

  if (session.length === 0) {
    const stats = statsQuery.data;
    return (
      <div className="mx-auto w-full max-w-2xl">
        <h1 className="text-2xl font-bold text-slate-900">Ôn câu sai</h1>
        <Card className="mt-4 flex flex-col items-center gap-3 text-center">
          <div className="text-4xl" aria-hidden>
            🎉
          </div>
          <p className="font-semibold text-slate-900">Không có câu nào cần ôn lúc này</p>
          <p className="text-sm text-slate-600">
            Câu Dictation chưa đúng 100% hoặc câu Shadowing dưới 70 điểm sẽ được đưa vào đây để luyện lại theo lịch
            lặp lại ngắt quãng.
          </p>
          {stats && stats.total > 0 && (
            <p className="text-sm text-slate-500">
              {stats.total} câu trong hàng ôn
              {stats.nextDueAt ? ` · câu tiếp theo đến hạn ${formatNextReview(stats.nextDueAt)}` : ''}.
            </p>
          )}
          <div className="flex flex-wrap justify-center gap-2">
            <Link
              href="/lessons"
              className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-semibold text-white hover:bg-brand-700"
            >
              Luyện bài mới
            </Link>
            <Button variant="secondary" className="min-h-[44px]" onClick={restart}>
              Tải lại
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return <SentenceReviewSession key={sessionNo} items={session} onRestart={restart} />;
}
