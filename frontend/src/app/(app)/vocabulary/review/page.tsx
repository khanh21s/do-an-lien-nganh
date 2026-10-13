'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { DailyLimitInput } from '@/components/vocab/DailyLimitInput';
import { ReviewSummary } from '@/components/vocab/ReviewSummary';
import { StudyCard } from '@/components/vocab/StudyCard';
import { StudyToolbar } from '@/components/vocab/StudyToolbar';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { DEFAULT_DAILY_LIMIT, clampDailyLimit, earliestDate, formatInterval } from '@/lib/srs';
import { storageKeys } from '@/lib/storageKeys';
import type { ReviewQueueItem } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

interface SessionResult {
  quality: number;
  interval: number;
  nextReviewAt: string;
}

interface Session {
  cards: ReviewQueueItem[];
  /** Due cards at the start of the session (before the daily limit cut). */
  totalDue: number;
}

export default function VocabReviewPage() {
  const queryClient = useQueryClient();
  const [storedLimit, setStoredLimit, limitHydrated] = useLocalStorage<number>(
    storageKeys.reviewDailyLimit,
    DEFAULT_DAILY_LIMIT
  );
  const dailyLimit = clampDailyLimit(storedLimit);
  const [typingMode, setTypingMode] = useState(false);
  // The queue is snapshotted when a session starts, so refetches caused by invalidation
  // (after each rating) don't shift the cards under the learner.
  const [session, setSession] = useState<Session | null>(null);
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<SessionResult[]>([]);

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: qk.reviewQueue,
    queryFn: vocabularyApi.getReviewQueue,
  });

  useEffect(() => {
    if (session || !data || !limitHydrated) return;
    setSession({ cards: data.queue.slice(0, dailyLimit), totalDue: data.queue.length });
    setIndex(0);
    setResults([]);
  }, [session, data, limitHydrated, dailyLimit]);

  const reviewMutation = useMutation({
    mutationFn: ({ item, quality }: { item: ReviewQueueItem; quality: number }) =>
      vocabularyApi.submitReview({ userVocabId: item.id, quality }),
    onSuccess: (res, { quality }) => {
      setResults((r) => [...r, { quality, interval: res.interval, nextReviewAt: res.nextReviewAt }]);
      setIndex((i) => i + 1);
      queryClient.invalidateQueries({ queryKey: qk.vocabStats });
      queryClient.invalidateQueries({ queryKey: qk.reviewQueue });
      queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể lưu kết quả ôn tập.')),
  });

  function changeLimit(next: number) {
    setStoredLimit(next);
    // Nothing rated yet → apply the new limit to the current session right away.
    if (results.length === 0 && data) {
      setSession({ cards: data.queue.slice(0, next), totalDue: data.queue.length });
      setIndex(0);
    }
  }

  async function startNextSession() {
    await refetch();
    setSession(null);
  }

  if (isLoading || (data && !session)) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data || !session) return <ErrorState notFound />;

  const toolbar = (
    <StudyToolbar typingMode={typingMode} onTypingModeChange={setTypingMode}>
      <DailyLimitInput value={dailyLimit} onChange={changeLimit} />
    </StudyToolbar>
  );

  if (session.cards.length === 0) {
    return <NothingDue />;
  }

  const current = session.cards[index];
  if (!current) {
    const remainingDue = Math.max(0, session.totalDue - session.cards.length);
    return (
      <ReviewSummary
        qualities={results.map((r) => r.quality)}
        nextReviewAt={earliestDate(results.map((r) => r.nextReviewAt))}
        remainingDue={remainingDue}
      >
        {remainingDue > 0 && (
          <Button className="min-h-[44px]" loading={isFetching} onClick={startNextSession}>
            Ôn tiếp
          </Button>
        )}
        <Link
          href="/dashboard"
          className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Về trang tổng quan
        </Link>
      </ReviewSummary>
    );
  }

  const total = session.cards.length;
  const last = results[results.length - 1];

  return (
    <div className="mx-auto max-w-xl">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-xl font-bold text-slate-900">Ôn tập từ vựng</h1>
        {toolbar}
      </div>
      <div className="mt-2 flex items-center justify-between text-sm text-slate-500">
        <span>
          Đã ôn {index}/{total} thẻ
          {session.totalDue > total && <span className="text-slate-400"> · {session.totalDue} thẻ đến hạn</span>}
        </span>
        <span className="font-semibold text-slate-700">Thẻ {index + 1}</span>
      </div>
      <div
        className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={index}
      >
        <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${(index / total) * 100}%` }} />
      </div>
      {last && (
        <p className="mt-2 text-xs text-slate-500" role="status">
          Thẻ trước: ôn lại sau {formatInterval(last.interval)}.
        </p>
      )}
      <div className="mt-4">
        <StudyCard
          key={current.id}
          card={current}
          srsState={{ repetitions: current.repetitions, intervalDays: current.intervalDays, easeFactor: current.easeFactor }}
          onRate={(quality) => reviewMutation.mutate({ item: current, quality })}
          busy={reviewMutation.isPending}
          typingMode={typingMode}
        />
      </div>
    </div>
  );
}

function NothingDue() {
  const { data: stats } = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });
  return (
    <Card className="mx-auto max-w-md text-center">
      <p className="text-4xl" aria-hidden>
        ✅
      </p>
      <p className="mt-2 text-lg font-semibold text-slate-900">Không còn thẻ đến hạn</p>
      <p className="mt-2 text-sm text-slate-500">
        {stats && stats.dueThisWeek > 0
          ? `Có ${stats.dueThisWeek} thẻ sẽ đến hạn trong 7 ngày tới – quay lại sau nhé.`
          : stats && stats.totalLearned === 0
            ? 'Sổ tay đang trống. Học một bộ từ để bắt đầu ôn tập.'
            : 'Quay lại sau hoặc học thêm từ mới.'}
      </p>
      <div className="mt-4 flex flex-wrap justify-center gap-2">
        <Link
          href="/vocabulary"
          className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
        >
          Khám phá bộ từ vựng
        </Link>
        <Link
          href="/vocabulary/notebook"
          className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Sổ tay
        </Link>
      </div>
    </Card>
  );
}
