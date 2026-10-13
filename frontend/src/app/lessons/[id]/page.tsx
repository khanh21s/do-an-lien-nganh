'use client';

import { AudioPlayer } from '@/components/audio/AudioPlayer';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';

export default function LessonDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: lesson, isLoading } = useQuery({
    queryKey: ['lesson', params.id],
    queryFn: () => contentApi.getLesson(params.id),
  });

  if (isLoading || !lesson) {
    return <FullPageSpinner />;
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{lesson.title}</h1>
          <div className="mt-2 flex items-center gap-2 text-sm text-slate-500">
            <Badge tone="brand">{lesson.cefrLevel}</Badge>
            {lesson.topicTag && <span>#{lesson.topicTag}</span>}
          </div>
        </div>
      </div>
      {lesson.description && <p className="mt-4 text-slate-600">{lesson.description}</p>}
      {lesson.audioUrl && (
        <div className="mt-4">
          <AudioPlayer src={contentApi.lessonAudioUrl(lesson.id)} />
        </div>
      )}

      <h2 className="mt-8 text-lg font-semibold text-slate-900">Danh sách câu ({lesson.sentences.length})</h2>
      <div className="mt-4 flex flex-col gap-3">
        {lesson.sentences.map((s, i) => (
          <Card key={s.id} className="flex flex-col gap-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-medium text-slate-900">
                  {i + 1}. {s.text}
                </p>
                {s.translationVi && <p className="mt-1 text-sm text-slate-500">{s.translationVi}</p>}
                {s.ipa && <p className="mt-1 text-xs text-slate-400">/{s.ipa}/</p>}
              </div>
            </div>
            {s.audioUrl && <AudioPlayer src={contentApi.sentenceAudioUrl(s.id)} />}
            <div className="flex gap-2">
              <Link href={`/lessons/${lesson.id}/dictation/${s.id}`}>
                <Button size="sm" variant="primary">
                  ✍️ Dictation
                </Button>
              </Link>
              <Link href={`/lessons/${lesson.id}/shadowing/${s.id}`}>
                <Button size="sm" variant="secondary">
                  🗣️ Shadowing
                </Button>
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
