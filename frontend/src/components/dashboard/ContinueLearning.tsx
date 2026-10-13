'use client';

import { Card } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import { practiceSentencePath } from '@/lib/practice-nav';
import { qk } from '@/lib/queryKeys';
import { formatAttemptDateTime, isPracticeMode, modeLabel } from '@/components/history/history-data';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { WidgetError } from './StatCard';

const LESSON_STALE_MS = 5 * 60_000;

/** PRA-01 "Tiếp tục học": the lesson of the most recent attempt (`listAttempts({limit: 1})`). */
export function ContinueLearning() {
  const latest = useQuery({
    queryKey: qk.attempts({ page: 1, limit: 1 }),
    queryFn: () => practiceApi.listAttempts({ page: 1, limit: 1 }),
  });
  const attempt = latest.data?.data[0];
  const lesson = useQuery({
    queryKey: qk.lesson(attempt?.lessonId ?? ''),
    queryFn: () => contentApi.getLesson(attempt!.lessonId),
    enabled: !!attempt?.lessonId,
    staleTime: LESSON_STALE_MS,
  });

  return (
    <Card className="flex h-full flex-col">
      <h2 className="font-semibold text-slate-900">Tiếp tục học</h2>
      {latest.isLoading ? (
        <div className="flex flex-1 items-center justify-center py-6">
          <Spinner />
        </div>
      ) : latest.isError ? (
        <WidgetError message="Không tải được bài gần nhất." onRetry={() => latest.refetch()} />
      ) : !attempt ? (
        <div className="mt-3 flex flex-1 flex-col justify-between gap-3">
          <p className="text-sm text-slate-500">Bạn chưa luyện bài nào. Hãy chọn một bài để bắt đầu!</p>
          <Link
            href="/lessons"
            className="inline-flex min-h-[44px] w-fit items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            Khám phá bài học
          </Link>
        </div>
      ) : (
        <div className="mt-3 flex flex-1 flex-col justify-between gap-3">
          <div>
            <p className="line-clamp-2 font-medium text-slate-800">
              {lesson.data?.title ?? (lesson.isLoading ? 'Đang tải…' : 'Bài học')}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {modeLabel(attempt.mode)} · {formatAttemptDateTime(attempt.createdAt)}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {isPracticeMode(attempt.mode) && (
              <Link
                href={practiceSentencePath(attempt.lessonId, attempt.mode, attempt.sentenceId)}
                className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
              >
                Tiếp tục
              </Link>
            )}
            <Link
              href={`/lessons/${attempt.lessonId}`}
              className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Xem bài
            </Link>
          </div>
        </div>
      )}
    </Card>
  );
}
