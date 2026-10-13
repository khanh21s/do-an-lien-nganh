'use client';

import { LessonNotFound } from '@/components/lessons/LessonNotFound';
import { DictationPractice } from '@/components/practice/dictation/DictationPractice';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { apiErrorStatus } from '@/lib/api';
import { isDifficulty } from '@/lib/dictation';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import { storageKeys } from '@/lib/storageKeys';
import type { Difficulty } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';

export default function DictationPage() {
  const params = useParams<{ id: string; sentenceId: string }>();
  const lessonQuery = useQuery({
    queryKey: qk.lesson(params.id),
    queryFn: () => contentApi.getLesson(params.id),
  });
  // DIC-06: remembered difficulty (sanitised: anything unknown → Normal).
  const [storedDifficulty, setDifficulty, hydrated] = useLocalStorage<Difficulty>(
    storageKeys.dictationDifficulty,
    'Normal'
  );
  const difficulty: Difficulty = isDifficulty(storedDifficulty) ? storedDifficulty : 'Normal';

  // DIC-01: never an endless spinner.
  if (lessonQuery.isLoading || !hydrated) return <FullPageSpinner />;
  if (lessonQuery.isError) {
    const status = apiErrorStatus(lessonQuery.error);
    if (status === 404 || status === 400) return <LessonNotFound />;
    return <ErrorState error={lessonQuery.error} onRetry={() => lessonQuery.refetch()} backHref="/lessons" backLabel="Về danh sách bài học" />;
  }
  const lesson = lessonQuery.data;
  if (!lesson) return <LessonNotFound />;

  const sentence = lesson.sentences.find((s) => s.id === params.sentenceId);
  if (!sentence) {
    return (
      <ErrorState
        notFound
        title="Không tìm thấy câu"
        message="Câu này không thuộc bài học hoặc đã bị xoá."
        backHref={`/lessons/${lesson.id}`}
        backLabel="Về bài học"
        className="mt-10"
      />
    );
  }

  return (
    <DictationPractice
      key={sentence.id}
      lesson={lesson}
      sentence={sentence}
      difficulty={difficulty}
      onDifficultyChange={setDifficulty}
    />
  );
}
