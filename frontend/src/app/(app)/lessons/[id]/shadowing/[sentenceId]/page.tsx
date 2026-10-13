'use client';

import { ShadowingPractice } from '@/components/practice/shadowing/ShadowingPractice';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';

/** Shadowing practice for one sentence (SHA-01..03, SHA-05, SHA-06, SHA-08, NAV-06). */
export default function ShadowingSentencePage() {
  const params = useParams<{ id: string; sentenceId: string }>();
  const { data: lesson, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.lesson(params.id),
    queryFn: () => contentApi.getLesson(params.id),
  });

  if (isLoading) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={() => refetch()} backHref="/lessons" backLabel="Về danh sách bài học" />;
  if (!lesson) return <ErrorState notFound backHref="/lessons" backLabel="Về danh sách bài học" />;

  const sentence = lesson.sentences.find((s) => s.id === params.sentenceId);
  if (!sentence) {
    return (
      <ErrorState
        notFound
        title="Không tìm thấy câu"
        message="Câu này không thuộc bài học hoặc đã bị xoá."
        backHref={`/lessons/${lesson.id}`}
        backLabel="Về bài học"
      />
    );
  }

  // Remount per sentence so the recording, result and timers reset cleanly.
  return <ShadowingPractice key={sentence.id} lesson={lesson} sentence={sentence} />;
}
