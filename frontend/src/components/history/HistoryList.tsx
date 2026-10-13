'use client';

import { Badge } from '@/components/ui/Badge';
import { isNotFoundError } from '@/lib/api';
import { practiceSentencePath } from '@/lib/practice-nav';
import type { AttemptSummary, LessonDetail } from '@/lib/types';
import clsx from 'clsx';
import Link from 'next/link';
import {
  difficultyLabel,
  formatAttemptDateTime,
  formatScore,
  isPracticeMode,
  modeLabel,
  scoreTone,
} from './history-data';

export interface LessonLookup {
  data?: LessonDetail;
  isLoading: boolean;
  error: unknown;
}

const SCORE_CLASSES = {
  green: 'text-green-700',
  amber: 'text-amber-700',
  red: 'text-red-700',
} as const;

function LessonName({ lookup }: { lookup?: LessonLookup }) {
  if (lookup?.data) return <span className="font-medium text-slate-800">{lookup.data.title}</span>;
  if (!lookup || lookup.isLoading) return <span className="inline-block h-4 w-32 animate-pulse rounded bg-slate-200" />;
  return (
    <span className="italic text-slate-400">
      {isNotFoundError(lookup.error) ? 'Bài học đã bị xoá' : 'Không tải được tên bài'}
    </span>
  );
}

function sentenceLabel(attempt: AttemptSummary, lesson?: LessonDetail): string | null {
  const s = lesson?.sentences.find((x) => x.id === attempt.sentenceId);
  return s ? `Câu ${s.sentenceOrder}` : null;
}

function RetryLink({ attempt, compact }: { attempt: AttemptSummary; compact?: boolean }) {
  if (!isPracticeMode(attempt.mode)) return null;
  return (
    <Link
      href={practiceSentencePath(attempt.lessonId, attempt.mode, attempt.sentenceId)}
      onClick={(e) => e.stopPropagation()}
      className={clsx(
        'inline-flex min-h-[44px] items-center justify-center rounded-lg border border-brand-200 px-3 text-sm font-medium text-brand-700 hover:bg-brand-50',
        compact && 'shrink-0'
      )}
    >
      Luyện lại
    </Link>
  );
}

/**
 * PRA-05: attempts as a table on md+ and as stacked cards on mobile (no horizontal scroll at 360px).
 * Clicking a row opens the detail modal.
 */
export function HistoryList({
  attempts,
  lessons,
  onSelect,
}: {
  attempts: AttemptSummary[];
  lessons: Map<string, LessonLookup>;
  onSelect: (attempt: AttemptSummary) => void;
}) {
  return (
    <>
      {/* Desktop table */}
      <div className="hidden overflow-hidden rounded-2xl border border-slate-200 bg-white md:block">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th className="px-4 py-3 font-medium">Bài học</th>
              <th className="px-4 py-3 font-medium">Thời gian</th>
              <th className="px-4 py-3 font-medium">Chế độ</th>
              <th className="px-4 py-3 font-medium">Độ khó</th>
              <th className="px-4 py-3 text-right font-medium">Điểm</th>
              <th className="px-4 py-3">
                <span className="sr-only">Hành động</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {attempts.map((a) => {
              const lookup = lessons.get(a.lessonId);
              const sentence = sentenceLabel(a, lookup?.data);
              return (
                <tr
                  key={a.attemptId}
                  className="cursor-pointer hover:bg-slate-50"
                  onClick={() => onSelect(a)}
                  data-attempt-id={a.attemptId}
                >
                  <td className="max-w-xs px-4 py-3">
                    <button
                      type="button"
                      className="block max-w-full truncate text-left hover:underline"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSelect(a);
                      }}
                    >
                      <LessonName lookup={lookup} />
                    </button>
                    {sentence && <span className="text-xs text-slate-500">{sentence}</span>}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatAttemptDateTime(a.createdAt)}</td>
                  <td className="px-4 py-3">
                    <Badge tone={a.mode === 'dictation' ? 'brand' : 'green'}>{modeLabel(a.mode)}</Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-600">{a.mode === 'dictation' ? difficultyLabel(a.difficulty) : '—'}</td>
                  <td className={clsx('px-4 py-3 text-right font-semibold', SCORE_CLASSES[scoreTone(a.score)])}>
                    {formatScore(a.score)}
                  </td>
                  <td className="px-4 py-2 text-right">
                    <RetryLink attempt={a} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Mobile cards */}
      <ul className="flex flex-col gap-3 md:hidden">
        {attempts.map((a) => {
          const lookup = lessons.get(a.lessonId);
          const sentence = sentenceLabel(a, lookup?.data);
          return (
            <li key={a.attemptId} className="rounded-2xl border border-slate-200 bg-white p-4" data-attempt-id={a.attemptId}>
              <button type="button" className="block w-full min-w-0 text-left" onClick={() => onSelect(a)}>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate">
                      <LessonName lookup={lookup} />
                    </p>
                    <p className="mt-0.5 text-xs text-slate-500">
                      {formatAttemptDateTime(a.createdAt)}
                      {sentence ? ` · ${sentence}` : ''}
                    </p>
                  </div>
                  <span className={clsx('shrink-0 text-lg font-bold', SCORE_CLASSES[scoreTone(a.score)])}>
                    {formatScore(a.score)}
                  </span>
                </div>
              </button>
              <div className="mt-2 flex items-center justify-between gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={a.mode === 'dictation' ? 'brand' : 'green'}>{modeLabel(a.mode)}</Badge>
                  {a.mode === 'dictation' && a.difficulty && <Badge tone="slate">{difficultyLabel(a.difficulty)}</Badge>}
                </div>
                <RetryLink attempt={a} compact />
              </div>
            </li>
          );
        })}
      </ul>
    </>
  );
}
