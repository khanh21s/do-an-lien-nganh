'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { apiErrorStatus } from '@/lib/api';
import { scoreLabel } from '@/lib/dictation';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import { firstUndoneSentence, practiceSentencePath, sortSentences } from '@/lib/practice-nav';
import { clearSession, readSession, summarizeSession, type SessionSummary } from '@/lib/practice-session';
import { qk } from '@/lib/queryKeys';
import type { PracticeMode, ShadowingWordScore } from '@/lib/types';
import { useQueries, useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { LessonCard } from './LessonCard';
import { LessonNotFound } from './LessonNotFound';
import { formatDuration, formatNumber, progressBySentence, summarizeLessonResult, weakestWords } from './lesson-progress';

const MODE_LABEL: Record<PracticeMode, string> = { dictation: 'Dictation', shadowing: 'Shadowing' };

function Stat({ label, value, hint }: { label: string; value: React.ReactNode; hint?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-3">
      <p className="text-xs text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold text-slate-900">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-slate-400">{hint}</p>}
    </div>
  );
}

/** DIC-10 / SHA-07: end-of-lesson statistics for `mode`. */
export function LessonResult({ lessonId, mode }: { lessonId: string; mode: PracticeMode }) {
  const router = useRouter();
  const otherMode: PracticeMode = mode === 'dictation' ? 'shadowing' : 'dictation';

  const lessonQuery = useQuery({ queryKey: qk.lesson(lessonId), queryFn: () => contentApi.getLesson(lessonId) });
  const attemptsQuery = useQuery({
    queryKey: qk.lessonAttempts(lessonId),
    queryFn: () => practiceApi.getLessonAttempts(lessonId),
    enabled: lessonQuery.isSuccess,
  });
  const statsQuery = useQuery({
    queryKey: qk.lessonStats(lessonId),
    queryFn: () => practiceApi.getLessonStats(lessonId),
    enabled: lessonQuery.isSuccess,
  });
  const cefrLevel = lessonQuery.data?.cefrLevel;
  const similarQuery = useQuery({
    queryKey: qk.lessons({ page: 1, limit: 4, cefrLevel }),
    queryFn: () => contentApi.listLessons({ page: 1, limit: 4, cefrLevel }),
    enabled: !!cefrLevel,
  });

  // Session counters live in sessionStorage → read after mount (no SSR mismatch).
  const [session, setSession] = useState<SessionSummary | null>(null);
  useEffect(() => {
    const s = readSession(lessonId, mode);
    setSession(s && Object.keys(s.sentences).length > 0 ? summarizeSession(s) : null);
  }, [lessonId, mode]);

  const sentences = useMemo(() => sortSentences(lessonQuery.data?.sentences ?? []), [lessonQuery.data]);
  const attempts = useMemo(() => attemptsQuery.data?.attempts ?? [], [attemptsQuery.data]);
  const summary = useMemo(() => summarizeLessonResult(sentences, attempts, mode), [sentences, attempts, mode]);

  // SHA-07: word scores of the latest shadowing attempt per sentence.
  const latestIds = useMemo(
    () =>
      mode === 'shadowing'
        ? Array.from(progressBySentence(attempts, 'shadowing').values()).map((p) => p.latestAttemptId)
        : [],
    [attempts, mode]
  );
  const detailQueries = useQueries({
    queries: latestIds.map((id) => ({ queryKey: qk.attempt(id), queryFn: () => practiceApi.getAttempt(id) })),
  });
  const detailsLoading = detailQueries.some((q) => q.isLoading);
  const weakWords =
    mode === 'shadowing' && !detailsLoading
      ? weakestWords(detailQueries.map((q) => (q.data?.wordScores as ShadowingWordScore[] | null | undefined) ?? null))
      : [];

  if (lessonQuery.isLoading) return <FullPageSpinner />;
  if (lessonQuery.isError) {
    const status = apiErrorStatus(lessonQuery.error);
    if (status === 404 || status === 400) return <LessonNotFound />;
    return <ErrorState error={lessonQuery.error} onRetry={() => lessonQuery.refetch()} />;
  }
  const lesson = lessonQuery.data;
  if (!lesson) return <LessonNotFound />;
  if (attemptsQuery.isLoading) return <FullPageSpinner />;
  if (attemptsQuery.isError) return <ErrorState error={attemptsQuery.error} onRetry={() => attemptsQuery.refetch()} />;

  const label = scoreLabel(summary.finalScore);
  const first = sentences[0];

  function restart() {
    clearSession(lessonId, mode);
    if (first) router.push(practiceSentencePath(lessonId, mode, first.id));
  }
  const otherTarget = firstUndoneSentence(sentences, attempts, otherMode) ?? first;
  const similar = (similarQuery.data?.data ?? []).filter((l) => l.id !== lessonId).slice(0, 3);

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-5">
      <div>
        <Link
          href={`/lessons/${lessonId}`}
          className="inline-flex min-h-[44px] items-center text-sm text-brand-600 hover:underline"
        >
          ← {lesson.title}
        </Link>
        <h1 className="text-2xl font-bold text-slate-900">Kết quả {MODE_LABEL[mode]}</h1>
      </div>

      <Card className="flex flex-col items-center gap-2 py-8 text-center">
        <p className="text-sm text-slate-500">
          {mode === 'dictation' ? 'Điểm cuối bài (TB điểm tốt nhất mỗi câu)' : 'Điểm phát âm trung bình'}
        </p>
        <p className="text-5xl font-bold text-brand-600">{formatNumber(summary.finalScore)}</p>
        {summary.done > 0 ? (
          <Badge tone={label.tone} className="text-sm">
            {label.label}
          </Badge>
        ) : (
          <p className="text-sm text-slate-500">Bạn chưa làm câu nào ở chế độ này.</p>
        )}
        <p className="mt-1 text-sm text-slate-600">
          Đã {mode === 'dictation' ? 'làm' : 'shadow'} <strong>{summary.done}</strong>/{summary.total} câu · độ chính xác TB{' '}
          {formatNumber(summary.avgAccuracy)}%
        </p>
        {statsQuery.data && (
          <p className="text-xs text-slate-400">
            Tổng {statsQuery.data.attempts} lượt luyện bài này (mọi chế độ) · điểm cao nhất{' '}
            {formatNumber(statsQuery.data.bestScore)}
          </p>
        )}
      </Card>

      {session && (
        <section>
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">Phiên luyện tập này</h2>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Stat label={mode === 'dictation' ? 'TB lần nộp/câu' : 'TB lần ghi/câu'} value={formatNumber(session.avgSubmits)} />
            <Stat label="TB nghe lại/câu" value={formatNumber(session.avgReplays)} />
            {mode === 'dictation' && <Stat label="TB từ lộ/câu" value={formatNumber(session.avgHints)} />}
            <Stat label="Tổng thời gian" value={formatDuration(session.totalMs)} hint={`${session.sentencesTouched} câu đã mở`} />
          </div>
        </section>
      )}

      {mode === 'dictation' && summary.mostMistakes.length > 0 && (
        <Card>
          <h2 className="font-semibold text-slate-900">Câu sai nhiều nhất</h2>
          <ul className="mt-3 divide-y divide-slate-100">
            {summary.mostMistakes.map((m) => (
              <li key={m.sentence.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                <span className="min-w-0 flex-1 break-words text-sm text-slate-700">{m.sentence.text}</span>
                <span className="flex items-center gap-2">
                  <Badge tone="red">{m.wrongAttempts} lần sai</Badge>
                  <Link
                    href={practiceSentencePath(lessonId, 'dictation', m.sentence.id)}
                    className="inline-flex min-h-[44px] items-center text-sm text-brand-600 hover:underline"
                  >
                    Luyện lại
                  </Link>
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}

      {mode === 'shadowing' && summary.done > 0 && (
        <Card>
          <h2 className="font-semibold text-slate-900">Từ phát âm chưa tốt</h2>
          {detailsLoading ? (
            <div className="mt-3 flex justify-center">
              <Spinner />
            </div>
          ) : weakWords.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Tuyệt vời! Không có từ nào dưới 80%.</p>
          ) : (
            <ul className="mt-3 flex flex-wrap gap-2">
              {weakWords.map((w) => (
                <li
                  key={w.word}
                  className={clsx(
                    'rounded-lg px-3 py-1.5 text-center',
                    w.avgAccuracy < 60 ? 'bg-red-100 text-red-800' : 'bg-amber-100 text-amber-800'
                  )}
                >
                  <span className="font-medium">{w.word}</span>
                  <span className="ml-1.5 text-xs opacity-80">{Math.round(w.avgAccuracy)}%</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}

      <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
        <Button onClick={restart} disabled={!first} className="min-h-[44px]">
          ↻ Làm lại bài
        </Button>
        {otherTarget && (
          <Link
            href={practiceSentencePath(lessonId, otherMode, otherTarget.id)}
            className="inline-flex min-h-[44px] items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Chuyển sang {MODE_LABEL[otherMode]}
          </Link>
        )}
        <Link
          href={`/lessons?level=${lesson.cefrLevel}`}
          className="inline-flex min-h-[44px] items-center justify-center rounded-lg px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100"
        >
          Bài khác ({lesson.cefrLevel}) →
        </Link>
      </div>

      {similar.length > 0 && (
        <section>
          <h2 className="mb-3 font-semibold text-slate-900">Có thể bạn thích ({lesson.cefrLevel})</h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            {similar.map((l) => (
              <LessonCard key={l.id} lesson={l} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
