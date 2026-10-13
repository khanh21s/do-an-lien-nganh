import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { topicIcon, topicLabel } from '@/lib/constants';
import type { LessonSummary } from '@/lib/types';
import Link from 'next/link';
import { formatNumber } from './lesson-progress';

/** Minutes of a lesson: `durationMinutes`, else `duration` (seconds). */
export function lessonMinutes(lesson: Pick<LessonSummary, 'duration' | 'durationMinutes'>): number | null {
  if (lesson.durationMinutes != null) return lesson.durationMinutes;
  if (lesson.duration != null) return lesson.duration / 60;
  return null;
}

export function LessonCard({ lesson }: { lesson: LessonSummary }) {
  const minutes = lessonMinutes(lesson);
  return (
    <Link href={`/lessons/${lesson.id}`} className="block h-full rounded-2xl focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
      <Card className="flex h-full flex-col transition-shadow hover:shadow-md">
        <div className="flex items-start justify-between gap-2">
          <h3 className="min-w-0 break-words font-semibold text-slate-900">{lesson.title}</h3>
          <Badge tone="brand" className="shrink-0">
            {lesson.cefrLevel}
          </Badge>
        </div>
        {lesson.description && <p className="mt-2 line-clamp-2 text-sm text-slate-500">{lesson.description}</p>}
        <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 pt-4 text-xs text-slate-500">
          {lesson.topicTag && (
            <span>
              {topicIcon(lesson.topicTag)} {topicLabel(lesson.topicTag)}
            </span>
          )}
          <span>{lesson.sentenceCount} câu</span>
          {minutes != null && <span>⏱ {formatNumber(minutes)} phút</span>}
        </div>
      </Card>
    </Link>
  );
}
