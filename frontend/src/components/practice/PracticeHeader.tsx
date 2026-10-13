'use client';

import { Button } from '@/components/ui/Button';
import { usePracticeNav } from '@/hooks/usePracticeNav';
import type { PracticeMode, SentenceResponse } from '@/lib/types';
import clsx from 'clsx';
import Link from 'next/link';

type NavSentence = Pick<SentenceResponse, 'id' | 'sentenceOrder'>;

/**
 * "Câu n/N" + progress bar + Prev/Next; on the last sentence "Next" becomes "Hoàn thành bài"
 * (→ `/lessons/[id]/result?mode=…`). Navigation uses `router.replace` (DIC-02).
 */
export function PracticeHeader({
  lessonId,
  lessonTitle,
  mode,
  sentences,
  currentId,
  backHref,
  right,
  className,
}: {
  lessonId: string;
  lessonTitle?: string;
  mode: PracticeMode;
  sentences: readonly NavSentence[];
  currentId: string;
  /** Default: the lesson page. */
  backHref?: string;
  /** Extra controls on the right (e.g. difficulty switch, `ShortcutHelp`). */
  right?: React.ReactNode;
  className?: string;
}) {
  const nav = usePracticeNav(lessonId, mode, sentences, currentId);
  const position = nav.index >= 0 ? nav.index + 1 : 0;
  const percent = nav.total > 0 ? Math.round((position / nav.total) * 100) : 0;

  return (
    <div className={clsx('flex flex-col gap-3', className)}>
      <div className="flex flex-wrap items-center gap-2">
        <Link
          href={backHref ?? `/lessons/${lessonId}`}
          className="inline-flex h-11 items-center rounded-lg px-2 text-sm text-slate-600 hover:bg-slate-100"
        >
          ← <span className="ml-1 hidden sm:inline">Bài học</span>
        </Link>
        <div className="min-w-0 flex-1">
          {lessonTitle && <p className="truncate text-sm font-medium text-slate-900">{lessonTitle}</p>}
          <p className="text-xs text-slate-500">
            {mode === 'dictation' ? 'Nghe chép' : 'Shadowing'} · Câu {position}/{nav.total}
          </p>
        </div>
        {right}
      </div>

      <div
        className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={nav.total}
        aria-valuenow={position}
        aria-label={`Câu ${position}/${nav.total}`}
      >
        <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${percent}%` }} />
      </div>

      <div className="flex items-center justify-between gap-2">
        <Button
          type="button"
          variant="secondary"
          onClick={nav.goPrev}
          disabled={!nav.prev}
          className="min-h-[44px]"
          title="Câu trước (Shift+←)"
        >
          ← Câu trước
        </Button>
        <span className="text-sm font-medium text-slate-700">
          Câu {position}/{nav.total}
        </span>
        {nav.isLast ? (
          <Button type="button" variant="success" onClick={nav.finish} className="min-h-[44px]">
            Hoàn thành bài
          </Button>
        ) : (
          <Button
            type="button"
            variant="secondary"
            onClick={nav.goNext}
            disabled={!nav.next}
            className="min-h-[44px]"
            title="Câu kế (Shift+→)"
          >
            Câu kế →
          </Button>
        )}
      </div>
    </div>
  );
}
