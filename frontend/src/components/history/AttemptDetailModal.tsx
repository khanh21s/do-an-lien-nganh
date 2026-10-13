'use client';

import { RecordingPlayer } from '@/components/audio/RecordingPlayer';
import { PhonemeScoreView } from '@/components/practice/PhonemeScoreView';
import { WordDiffView } from '@/components/practice/WordDiffView';
import { Badge } from '@/components/ui/Badge';
import { ErrorState } from '@/components/ui/ErrorState';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import { practiceSentencePath } from '@/lib/practice-nav';
import { qk } from '@/lib/queryKeys';
import type { LessonDetail, ShadowingWordScore, WordScore } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import {
  difficultyLabel,
  formatAttemptDateTime,
  formatScore,
  isPracticeMode,
  modeLabel,
  scoreTone,
} from './history-data';

/**
 * PRA-06: attempt detail. Dictation → word diff; shadowing → phoneme scores + the user's recording,
 * fetched through `RecordingPlayer` (axios blob with Bearer, SHA-05).
 */
export function AttemptDetailModal({
  attemptId,
  lesson,
  onClose,
}: {
  attemptId: string | null;
  lesson?: LessonDetail;
  onClose: () => void;
}) {
  const detail = useQuery({
    queryKey: qk.attempt(attemptId ?? ''),
    queryFn: () => practiceApi.getAttempt(attemptId as string),
    enabled: !!attemptId,
  });
  const d = detail.data;
  const sentence = d && lesson?.sentences.find((s) => s.id === d.sentenceId);

  return (
    <Modal open={!!attemptId} onClose={onClose} title="Chi tiết lượt luyện tập" className="max-w-2xl">
      {detail.isLoading ? (
        <div className="flex justify-center py-10">
          <Spinner className="h-8 w-8" />
        </div>
      ) : detail.isError ? (
        <ErrorState error={detail.error} onRetry={() => detail.refetch()} className="border-0 py-6" />
      ) : !d ? (
        <ErrorState notFound className="border-0 py-6" />
      ) : (
        <div className="flex flex-col gap-4">
          <div>
            {lesson && <p className="font-medium text-slate-800">{lesson.title}</p>}
            {sentence && (
              <p className="mt-1 text-sm text-slate-600">
                <span className="text-slate-400">Câu {sentence.sentenceOrder}: </span>
                {sentence.text}
              </p>
            )}
            <p className="mt-1 text-xs text-slate-500">{formatAttemptDateTime(d.createdAt)}</p>
          </div>

          <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
            <span
              className={
                scoreTone(d.score) === 'green'
                  ? 'text-3xl font-bold text-green-600'
                  : scoreTone(d.score) === 'amber'
                    ? 'text-3xl font-bold text-amber-600'
                    : 'text-3xl font-bold text-red-600'
              }
            >
              {formatScore(d.score)}
            </span>
            <span className="text-sm text-slate-500">{formatScore(d.accuracy)}% chính xác</span>
            <Badge tone={d.mode === 'dictation' ? 'brand' : 'green'}>{modeLabel(d.mode)}</Badge>
            {d.difficulty && <Badge tone="slate">{difficultyLabel(d.difficulty)}</Badge>}
          </div>

          {d.mode === 'shadowing' && (
            <div>
              <h3 className="mb-2 text-sm font-semibold text-slate-700">Bản ghi của bạn</h3>
              {d.audioRecordingUrl ? (
                <RecordingPlayer attemptId={d.attemptId} />
              ) : (
                <p className="text-sm text-slate-500">Lượt này không có bản ghi.</p>
              )}
            </div>
          )}

          <div>
            <h3 className="mb-2 text-sm font-semibold text-slate-700">
              {d.mode === 'dictation' ? 'So sánh từng từ' : 'Điểm phát âm'}
            </h3>
            {!d.wordScores || d.wordScores.length === 0 ? (
              <p className="text-sm text-slate-500">Không có dữ liệu chi tiết.</p>
            ) : d.mode === 'dictation' ? (
              <WordDiffView words={d.wordScores as WordScore[]} />
            ) : (
              <PhonemeScoreView words={d.wordScores as ShadowingWordScore[]} />
            )}
          </div>

          {isPracticeMode(d.mode) && (
            <div className="flex justify-end">
              <Link
                href={practiceSentencePath(d.lessonId, d.mode, d.sentenceId)}
                className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
              >
                Luyện lại câu này
              </Link>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
