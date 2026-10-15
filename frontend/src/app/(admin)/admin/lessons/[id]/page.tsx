'use client';

import { LessonAudioUpload } from '@/components/admin/LessonAudioUpload';
import { LessonInfoForm } from '@/components/admin/LessonInfoForm';
import { SentenceManager } from '@/components/admin/SentenceManager';
import { ProBadge } from '@/components/lessons/ProLessonLock';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';

/** ADM-02/03/04/09: edit lesson info, audio and sentences. */
export default function AdminLessonDetailPage() {
  const params = useParams<{ id: string }>();
  const lessonId = params.id;

  const { data: lesson, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.lesson(lessonId),
    queryFn: () => contentApi.getLesson(lessonId),
  });

  if (isLoading) return <FullPageSpinner />;
  if (isError)
    return <ErrorState error={error} onRetry={refetch} backHref="/admin/lessons" backLabel="Về danh sách bài học" />;
  if (!lesson) return <ErrorState notFound backHref="/admin/lessons" backLabel="Về danh sách bài học" />;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <div>
        <Link href="/admin/lessons" className="text-sm text-brand-600 hover:underline">
          ← Quay lại danh sách bài học
        </Link>
        <div className="mt-2 flex flex-wrap items-baseline justify-between gap-2">
          <h1 className="break-words text-2xl font-bold text-slate-900">
            {lesson.title} {lesson.isPro && <ProBadge className="align-middle" />}
          </h1>
          <Link href={`/lessons/${lesson.id}`} className="text-sm text-brand-600 hover:underline">
            Xem trang học →
          </Link>
        </div>
      </div>
      {/* key: re-initialise the form state when the saved lesson changes. */}
      <LessonInfoForm key={lesson.updatedAt} lesson={lesson} />
      <LessonAudioUpload lesson={lesson} />
      <SentenceManager lesson={lesson} />
    </div>
  );
}
