'use client';

import { AttemptDetailModal } from '@/components/history/AttemptDetailModal';
import { HistoryList, type LessonLookup } from '@/components/history/HistoryList';
import { MODE_FILTERS, type ModeFilter, uniqueLessonIds } from '@/components/history/history-data';
import { ErrorState } from '@/components/ui/ErrorState';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import { qk } from '@/lib/queryKeys';
import type { AttemptSummary, ListAttemptsParams } from '@/lib/types';
import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useMemo, useState } from 'react';

const PAGE_SIZE = 20;
/** Lesson metadata rarely changes; share `qk.lesson(id)` with the lesson pages. */
const LESSON_STALE_MS = 5 * 60_000;

export default function PracticeHistoryPage() {
  const [page, setPage] = useState(1);
  const [mode, setMode] = useState<ModeFilter>('all');
  const [selected, setSelected] = useState<AttemptSummary | null>(null);

  const params: ListAttemptsParams = { page, limit: PAGE_SIZE, ...(mode !== 'all' ? { mode } : {}) };
  const attemptsQuery = useQuery({
    queryKey: qk.attempts(params),
    queryFn: () => practiceApi.listAttempts(params),
    placeholderData: keepPreviousData,
  });

  const attempts = useMemo(() => attemptsQuery.data?.data ?? [], [attemptsQuery.data]);
  // PRA-05: one lesson request per distinct lessonId (20 rows / 3 lessons → ≤ 3 requests), cached.
  const lessonIds = useMemo(() => uniqueLessonIds(attempts), [attempts]);
  const lessonResults = useQueries({
    queries: lessonIds.map((id) => ({
      queryKey: qk.lesson(id),
      queryFn: () => contentApi.getLesson(id),
      staleTime: LESSON_STALE_MS,
    })),
  });
  const lessons = new Map<string, LessonLookup>(
    lessonIds.map((id, i) => [
      id,
      { data: lessonResults[i]?.data, isLoading: lessonResults[i]?.isLoading ?? true, error: lessonResults[i]?.error },
    ])
  );

  function changeMode(next: ModeFilter) {
    setMode(next);
    setPage(1);
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Lịch sử luyện tập</h1>
          {attemptsQuery.data && (
            <p className="mt-1 text-sm text-slate-500">{attemptsQuery.data.pagination.total} lượt luyện tập</p>
          )}
        </div>
        <div role="tablist" aria-label="Lọc theo chế độ" className="flex rounded-xl bg-slate-100 p-1">
          {MODE_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              role="tab"
              aria-selected={mode === f.value}
              onClick={() => changeMode(f.value)}
              className={clsx(
                'min-h-[44px] rounded-lg px-3 text-sm font-medium transition-colors sm:px-4',
                mode === f.value ? 'bg-white text-brand-700 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {attemptsQuery.isLoading ? (
        <FullPageSpinner />
      ) : attemptsQuery.isError ? (
        <ErrorState error={attemptsQuery.error} onRetry={() => attemptsQuery.refetch()} />
      ) : attempts.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
          <p className="text-slate-600">
            {mode === 'all' ? 'Bạn chưa có lượt luyện tập nào.' : 'Chưa có lượt luyện tập nào ở chế độ này.'}
          </p>
          <Link
            href="/lessons"
            className="mt-4 inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            Bắt đầu luyện tập
          </Link>
        </div>
      ) : (
        <div className={clsx('flex flex-col gap-5', attemptsQuery.isPlaceholderData && 'opacity-60')}>
          <HistoryList attempts={attempts} lessons={lessons} onSelect={setSelected} />
          <Pagination
            page={page}
            totalPages={attemptsQuery.data?.pagination.totalPages ?? 1}
            onChange={(p) => {
              setPage(p);
              window.scrollTo({ top: 0, behavior: 'smooth' });
            }}
          />
        </div>
      )}

      <AttemptDetailModal
        attemptId={selected?.attemptId ?? null}
        lesson={selected ? lessons.get(selected.lessonId)?.data : undefined}
        onClose={() => setSelected(null)}
      />
    </div>
  );
}
