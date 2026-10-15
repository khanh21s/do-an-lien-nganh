'use client';

import { DictationPractice } from '@/components/practice/dictation/DictationPractice';
import type { PracticeReviewMode } from '@/components/practice/review-mode';
import { ShadowingPractice } from '@/components/practice/shadowing/ShadowingPractice';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { UpgradeCard } from '@/components/vocab/UpgradeCard';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { apiErrorMessage } from '@/lib/api';
import { isDifficulty } from '@/lib/dictation';
import { practiceApi } from '@/lib/endpoints/practice';
import { PRO_LESSON_MESSAGE } from '@/lib/pro-lesson';
import { qk } from '@/lib/queryKeys';
import { REVIEW_MODE_LABELS, nextReviewIndex, reviewLesson, reviewOutcomeText } from '@/lib/sentence-review';
import { storageKeys } from '@/lib/storageKeys';
import type { Difficulty, SentenceReviewItem, SentenceReviewOutcome } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useMemo, useState, type ReactNode } from 'react';
import toast from 'react-hot-toast';

/** One review session over a fixed snapshot of due sentences. */
export function SentenceReviewSession({ items, onRestart }: { items: SentenceReviewItem[]; onRestart: () => void }) {
  const queryClient = useQueryClient();
  const [list, setList] = useState(items);
  const [index, setIndex] = useState(0);
  const [outcomes, setOutcomes] = useState<Record<string, SentenceReviewOutcome | null>>({});
  const [storedDifficulty, setDifficulty] = useLocalStorage<Difficulty>(storageKeys.dictationDifficulty, 'Normal');
  const difficulty: Difficulty = isDifficulty(storedDifficulty) ? storedDifficulty : 'Normal';

  const current = index >= 0 ? list[index] : undefined;
  const lesson = useMemo(() => (current ? reviewLesson(current) : null), [current]);

  function goNext() {
    setIndex((i) => nextReviewIndex(list.length, i));
  }

  const removeMutation = useMutation({
    mutationFn: (id: string) => practiceApi.deleteReviewItem(id),
    onSuccess: (_res, id) => {
      toast.success('Đã xoá câu khỏi hàng ôn tập.');
      const pos = list.findIndex((i) => i.id === id);
      const next = list.filter((i) => i.id !== id);
      setList(next);
      // Stay on the same position (= the following sentence); past the end → summary.
      setIndex(pos < next.length ? pos : -1);
      void queryClient.invalidateQueries({ queryKey: qk.sentenceReviewStats });
      void queryClient.invalidateQueries({ queryKey: qk.sentenceReviewQueue });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được câu.')),
  });

  if (!current || !lesson) {
    const graded = Object.values(outcomes).filter((o) => o?.action === 'graded');
    return (
      <div className="mx-auto w-full max-w-2xl">
        <h1 className="text-2xl font-bold text-slate-900">Ôn câu sai</h1>
        <Card className="mt-4 flex flex-col items-center gap-3 text-center">
          <div className="text-4xl" aria-hidden>
            ✅
          </div>
          <p className="font-semibold text-slate-900">Hoàn thành phiên ôn tập</p>
          <p className="text-sm text-slate-600">
            Đã chấm {graded.length}/{list.length} câu
            {graded.length > 0 &&
              ` · ${graded.filter((o) => (o?.quality ?? 0) >= 3).length} câu nhớ được, ${
                graded.filter((o) => (o?.quality ?? 0) < 3).length
              } câu cần ôn lại ngày mai`}
            .
          </p>
          <div className="flex flex-wrap justify-center gap-2">
            <Button className="min-h-[44px]" onClick={onRestart}>
              Kiểm tra câu đến hạn
            </Button>
            <Link
              href="/dashboard"
              className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Về Dashboard
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  const isLast = nextReviewIndex(list.length, index) === -1;
  const outcome = current.id in outcomes ? outcomes[current.id] : undefined;

  const header = (right: ReactNode) => (
    <ReviewHeader
      item={current}
      position={index + 1}
      total={list.length}
      right={right}
      onRemove={() => removeMutation.mutate(current.id)}
      removing={removeMutation.isPending}
    />
  );

  const review: PracticeReviewMode = {
    reviewId: current.id,
    renderHeader: header,
    isLast,
    onNext: goNext,
    // Keep the first (grading) outcome of each item; retries come back as "unchanged".
    onResult: (o) => setOutcomes((prev) => (current.id in prev && prev[current.id]?.action === 'graded' ? prev : { ...prev, [current.id]: o })),
  };

  return (
    <div className="flex flex-col gap-4">
      {current.locked ? (
        <div className="mx-auto flex w-full max-w-2xl flex-col gap-4">
          {header(null)}
          <UpgradeCard
            title="Câu thuộc bài học Premium"
            message={PRO_LESSON_MESSAGE}
            backHref={`/lessons/${current.lessonId}`}
            backLabel="Xem bài học"
          />
          <Button variant="secondary" className="min-h-[44px] self-center" onClick={goNext}>
            {isLast ? 'Kết thúc phiên ôn' : 'Bỏ qua câu này →'}
          </Button>
        </div>
      ) : current.mode === 'dictation' ? (
        <DictationPractice
          key={current.id}
          lesson={lesson}
          sentence={current.sentence}
          difficulty={difficulty}
          onDifficultyChange={setDifficulty}
          review={review}
        />
      ) : (
        <ShadowingPractice key={current.id} lesson={lesson} sentence={current.sentence} review={review} />
      )}

      {outcome !== undefined && (
        <p
          className={clsx(
            'mx-auto w-full max-w-3xl rounded-xl px-4 py-3 text-sm',
            outcome && outcome.quality != null && outcome.quality < 3
              ? 'bg-amber-50 text-amber-800'
              : 'bg-green-50 text-green-800'
          )}
          aria-live="polite"
        >
          🗓️ {reviewOutcomeText(outcome)}
        </p>
      )}
    </div>
  );
}

function ReviewHeader({
  item,
  position,
  total,
  right,
  onRemove,
  removing,
}: {
  item: SentenceReviewItem;
  position: number;
  total: number;
  right: ReactNode;
  onRemove: () => void;
  removing: boolean;
}) {
  const percent = total > 0 ? Math.round((position / total) * 100) : 0;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Link
          href="/dashboard"
          className="inline-flex h-11 items-center rounded-lg px-2 text-sm text-slate-600 hover:bg-slate-100"
        >
          ← <span className="ml-1 hidden sm:inline">Dashboard</span>
        </Link>
        <div className="min-w-0 flex-1">
          <p className="text-xs font-medium uppercase tracking-wide text-brand-600">
            Ôn câu sai · {REVIEW_MODE_LABELS[item.mode]}
          </p>
          <Link href={`/lessons/${item.lessonId}`} className="block truncate text-sm text-slate-600 hover:underline">
            {item.lessonTitle ?? 'Bài học'}
          </Link>
        </div>
        {right}
      </div>
      <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600">
        <span className="font-medium text-slate-900">
          Câu {position}/{total}
        </span>
        <div className="h-2 min-w-[80px] flex-1 overflow-hidden rounded-full bg-slate-100" aria-hidden>
          <div className="h-full rounded-full bg-brand-500 transition-all" style={{ width: `${percent}%` }} />
        </div>
        <span className="text-xs text-slate-500">
          {[
            item.lastScore != null ? `Lần trước: ${Math.round(item.lastScore)}` : null,
            item.repetitions > 0 ? `Đã nhớ ${item.repetitions} lần liên tiếp` : null,
          ]
            .filter(Boolean)
            .join(' · ')}
        </span>
        <Button variant="ghost" size="sm" className="min-h-[44px]" onClick={onRemove} loading={removing}>
          Xoá khỏi hàng ôn
        </Button>
      </div>
    </div>
  );
}
