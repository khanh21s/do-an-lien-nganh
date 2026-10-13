'use client';

import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import { firstUndoneSentence, practiceSentencePath, sortSentences } from '@/lib/practice-nav';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import { useParams, useRouter } from 'next/navigation';
import { useEffect } from 'react';

/** `/lessons/[id]/shadowing` → first sentence not yet shadowed (or the first sentence). */
export default function ShadowingEntryPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const lessonQ = useQuery({ queryKey: qk.lesson(id), queryFn: () => contentApi.getLesson(id) });
  // Attempts are optional for picking the start sentence: ignore failures.
  const attemptsQ = useQuery({
    queryKey: qk.lessonAttempts(id),
    queryFn: () => practiceApi.getLessonAttempts(id),
    retry: false,
  });

  const lesson = lessonQ.data;
  const attemptsSettled = !attemptsQ.isLoading;
  const target = lesson
    ? (firstUndoneSentence(lesson.sentences, attemptsQ.data?.attempts ?? [], 'shadowing') ??
      sortSentences(lesson.sentences)[0] ??
      null)
    : null;
  const targetId = target?.id ?? null;

  useEffect(() => {
    if (targetId && attemptsSettled) router.replace(practiceSentencePath(id, 'shadowing', targetId));
  }, [targetId, attemptsSettled, id, router]);

  if (lessonQ.isError) {
    return <ErrorState error={lessonQ.error} onRetry={() => lessonQ.refetch()} backHref="/lessons" backLabel="Về danh sách bài học" />;
  }
  if (lesson && !lesson.sentences.length) {
    return (
      <ErrorState
        title="Bài học chưa có câu nào"
        message="Bài này chưa có câu để luyện Shadowing."
        backHref={`/lessons/${id}`}
        backLabel="Về bài học"
      />
    );
  }
  return <FullPageSpinner />;
}
