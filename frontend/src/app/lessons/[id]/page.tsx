'use client';

import { AudioPlayer } from '@/components/audio/AudioPlayer';
import { lessonMinutes } from '@/components/lessons/LessonCard';
import { LessonNotFound } from '@/components/lessons/LessonNotFound';
import { LessonTranscript } from '@/components/lessons/LessonTranscript';
import { formatNumber, progressBySentence } from '@/components/lessons/lesson-progress';
import { Badge } from '@/components/ui/Badge';
import { ErrorState } from '@/components/ui/ErrorState';
import { Spinner, FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorStatus, loginUrlWithNext } from '@/lib/api';
import { topicIcon, topicLabel } from '@/lib/constants';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import { firstUndoneSentence, practiceSentencePath, sortSentences } from '@/lib/practice-nav';
import { qk } from '@/lib/queryKeys';
import type { PracticeMode } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMemo } from 'react';

export default function LessonDetailPage() {
  const params = useParams<{ id: string }>();
  const lessonId = params.id;
  const token = useAuthStore((s) => s.token);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const signedIn = hasHydrated && !!token;

  const lessonQuery = useQuery({
    queryKey: qk.lesson(lessonId),
    queryFn: () => contentApi.getLesson(lessonId),
  });

  // Attempts only exist for signed-in users (LES-04/LES-05).
  const attemptsQuery = useQuery({
    queryKey: qk.lessonAttempts(lessonId),
    queryFn: () => practiceApi.getLessonAttempts(lessonId),
    enabled: signedIn && lessonQuery.isSuccess,
  });

  const progress = useMemo(() => {
    if (!signedIn || !attemptsQuery.data) return null;
    const attempts = attemptsQuery.data.attempts;
    return {
      dictation: progressBySentence(attempts, 'dictation'),
      shadowing: progressBySentence(attempts, 'shadowing'),
    };
  }, [signedIn, attemptsQuery.data]);

  if (lessonQuery.isLoading) return <FullPageSpinner />;
  if (lessonQuery.isError) {
    const status = apiErrorStatus(lessonQuery.error);
    // A malformed id is answered with 400 by the BE: treat it as "not found" too.
    if (status === 404 || status === 400) return <LessonNotFound />;
    return <ErrorState error={lessonQuery.error} onRetry={() => lessonQuery.refetch()} backHref="/lessons" backLabel="Về danh sách bài học" />;
  }
  const lesson = lessonQuery.data;
  if (!lesson) return <LessonNotFound />;

  const lessonPage = `/lessons/${lesson.id}`;
  const practiceHref = (mode: PracticeMode, sentenceId: string) =>
    signedIn ? practiceSentencePath(lesson.id, mode, sentenceId) : loginUrlWithNext(practiceSentencePath(lesson.id, mode, sentenceId));

  const sorted = sortSentences(lesson.sentences);
  const attemptsReady = !signedIn || attemptsQuery.isSuccess || attemptsQuery.isError;

  /** LES-04: first sentence without an attempt in `mode` (all done → sentence 1); guests → login. */
  function startHref(mode: PracticeMode): string | null {
    if (sorted.length === 0) return null;
    if (!signedIn) return loginUrlWithNext(lessonPage);
    const target = firstUndoneSentence(sorted, attemptsQuery.data?.attempts ?? [], mode) ?? sorted[0];
    return practiceSentencePath(lessonId, mode, target.id);
  }

  const minutes = lessonMinutes({ duration: lesson.duration, durationMinutes: null });
  const doneD = progress?.dictation.size ?? 0;
  const doneS = progress?.shadowing.size ?? 0;

  return (
    <div className="mx-auto max-w-3xl">
      <Link href="/lessons" className="inline-flex min-h-[44px] items-center text-sm text-brand-600 hover:underline">
        ← Danh sách bài học
      </Link>

      <header className="mt-1">
        <h1 className="break-words text-2xl font-bold text-slate-900">{lesson.title}</h1>
        <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-slate-500">
          <Badge tone="brand">{lesson.cefrLevel}</Badge>
          {lesson.topicTag && (
            <span>
              {topicIcon(lesson.topicTag)} {topicLabel(lesson.topicTag)}
            </span>
          )}
          {minutes != null && <span>⏱ {formatNumber(minutes)} phút</span>}
          <span>{sorted.length} câu</span>
        </div>
        {lesson.description && <p className="mt-3 text-slate-600">{lesson.description}</p>}
      </header>

      {lesson.audioUrl && (
        <div className="mt-4">
          <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">Audio cả bài</p>
          <AudioPlayer src={contentApi.lessonAudioUrl(lesson.id)} preload="metadata" />
        </div>
      )}

      <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
        {(['dictation', 'shadowing'] as const).map((mode) => {
          const href = startHref(mode);
          const label = mode === 'dictation' ? '✍️ Luyện Dictation' : '🗣️ Luyện Shadowing';
          const done = mode === 'dictation' ? doneD : doneS;
          const cls = clsx(
            'flex min-h-[52px] flex-col items-center justify-center rounded-xl px-4 py-2 text-center font-semibold transition-colors',
            mode === 'dictation'
              ? 'bg-brand-600 text-white hover:bg-brand-700'
              : 'border border-brand-200 bg-white text-brand-700 hover:bg-brand-50'
          );
          if (!href || !attemptsReady) {
            return (
              <span key={mode} className={clsx(cls, 'cursor-not-allowed opacity-60')} aria-disabled>
                {!attemptsReady ? <Spinner className="h-5 w-5" /> : label}
              </span>
            );
          }
          return (
            <Link key={mode} href={href} className={cls}>
              {label}
              {signedIn && (
                <span className="text-xs font-normal opacity-80">
                  Đã làm {done}/{sorted.length} câu
                </span>
              )}
              {!signedIn && <span className="text-xs font-normal opacity-80">Đăng nhập để luyện tập</span>}
            </Link>
          );
        })}
      </div>

      <LessonTranscript lesson={lesson} progress={progress} practiceHref={practiceHref} />
    </div>
  );
}
