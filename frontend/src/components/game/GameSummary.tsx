'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { pronounce } from '@/components/vocab/pronounce';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { RESULT_LABELS, posLabel, recordMessage, wordsToReview } from '@/lib/game';
import { qk } from '@/lib/queryKeys';
import type { GameResult, GameSummary as Summary, GameSummaryItem } from '@/lib/types';
import { useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useState } from 'react';
import toast from 'react-hot-toast';

const RESULT_TONE: Record<GameResult, 'green' | 'amber' | 'red' | 'slate'> = {
  CORRECT: 'green',
  NEAR: 'amber',
  WRONG: 'red',
  SKIPPED: 'slate',
};

/** End of a round: score vs. record, counters, the words to review (+ add them to the notebook). */
export function GameSummary({
  summary,
  onReplay,
  onChangeSource,
  replaying,
}: {
  summary: Summary;
  onReplay: () => void;
  onChangeSource: () => void;
  replaying: boolean;
}) {
  const queryClient = useQueryClient();
  const review = wordsToReview(summary.items);
  // cards already in the notebook, plus the ones added from this screen
  const [inNotebook, setInNotebook] = useState<Set<string>>(
    () => new Set(summary.items.filter((i) => i.userVocabId).map((i) => i.cardId))
  );
  const [adding, setAdding] = useState(false);
  const notAdded = review.filter((i) => !inNotebook.has(i.cardId));

  async function add(items: GameSummaryItem[]) {
    setAdding(true);
    const added: string[] = [];
    let failed = 0;
    let lastError: unknown = null;
    for (const it of items) {
      try {
        await vocabularyApi.addFromLesson(it.cardId, 'game');
        added.push(it.cardId);
      } catch (err) {
        if (apiErrorStatus(err) === 409) added.push(it.cardId);
        else {
          failed++;
          lastError = err;
        }
      }
    }
    setInNotebook((s) => new Set([...s, ...added]));
    setAdding(false);
    queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
    queryClient.invalidateQueries({ queryKey: qk.vocabStats });
    queryClient.invalidateQueries({ queryKey: qk.reviewQueue });
    if (added.length) toast.success(`Đã thêm ${added.length} từ vào sổ tay`);
    if (failed) toast.error(apiErrorMessage(lastError, `Không thêm được ${failed} từ.`));
  }

  const stats = [
    { label: 'Đúng', value: summary.correct, className: 'text-green-600' },
    { label: 'Gần đúng', value: summary.near, className: 'text-amber-600' },
    { label: 'Sai', value: summary.wrong, className: 'text-red-600' },
    { label: 'Bỏ qua', value: summary.skipped, className: 'text-slate-500' },
    { label: 'Chính xác', value: `${summary.accuracy}%`, className: 'text-slate-900' },
    { label: 'Combo cao nhất', value: `🔥 ${summary.bestCombo}`, className: 'text-orange-600' },
  ];

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <Card className="text-center">
        <p className="text-sm text-slate-500">{summary.sourceLabel} · Nước rút 60 giây</p>
        <p className="mt-2 text-5xl font-extrabold tabular-nums text-slate-900" data-testid="summary-score">
          {summary.score}
        </p>
        <p className={clsx('mt-2 font-semibold', summary.newRecord ? 'text-amber-600' : 'text-slate-600')} data-testid="summary-record">
          {summary.newRecord && '🏆 '}
          {recordMessage(summary.score, summary.previousBest, summary.newRecord)}
        </p>
        <dl className="mt-5 grid grid-cols-3 gap-3 sm:grid-cols-6">
          {stats.map((s) => (
            <div key={s.label} className="rounded-lg bg-slate-50 px-2 py-2">
              <dt className="text-xs text-slate-500">{s.label}</dt>
              <dd className={clsx('text-lg font-bold', s.className)}>{s.value}</dd>
            </div>
          ))}
        </dl>
        {summary.srsUpdated > 0 && (
          <p className="mt-3 text-xs text-slate-500">Đã cập nhật lịch ôn tập cho {summary.srsUpdated} từ trong sổ tay.</p>
        )}
        <div className="mt-5 flex flex-wrap justify-center gap-2">
          <Button size="lg" className="min-h-[48px]" onClick={onReplay} loading={replaying} autoFocus>
            Chơi lại
          </Button>
          <Button size="lg" variant="secondary" className="min-h-[48px]" onClick={onChangeSource}>
            Đổi nguồn từ
          </Button>
        </div>
      </Card>

      {review.length > 0 && (
        <Card>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-slate-900">Từ cần ôn lại ({review.length})</h2>
            {notAdded.length > 0 && (
              <Button size="sm" variant="secondary" onClick={() => void add(notAdded)} loading={adding}>
                Thêm tất cả vào sổ tay ({notAdded.length})
              </Button>
            )}
          </div>
          <ul className="mt-3 divide-y divide-slate-100">
            {review.map((it) => (
              <li key={it.cardId} className="py-3" data-testid="review-word">
                <div className="flex flex-wrap items-center gap-2">
                  <b className="text-slate-900">{it.word}</b>
                  {it.ipa && <span className="text-sm text-slate-500">{it.ipa}</span>}
                  {it.partOfSpeech && <span className="text-xs italic text-slate-500">({posLabel(it.partOfSpeech)})</span>}
                  <button
                    type="button"
                    className="rounded px-1 hover:bg-slate-100"
                    aria-label={`Phát âm ${it.word}`}
                    onClick={() => pronounce(it.word, it.audioUrl)}
                  >
                    🔊
                  </button>
                  <Badge tone={RESULT_TONE[it.result]}>{RESULT_LABELS[it.result]}</Badge>
                  <span className="ml-auto">
                    {inNotebook.has(it.cardId) ? (
                      <span className="text-xs text-green-600">✓ Trong sổ tay</span>
                    ) : (
                      <Button size="sm" variant="ghost" onClick={() => void add([it])} disabled={adding}>
                        + Sổ tay
                      </Button>
                    )}
                  </span>
                </div>
                <p className="text-sm text-slate-600">{it.meaning}</p>
                {it.answer && it.result !== 'CORRECT' && (
                  <p className="text-xs text-slate-500">
                    Bạn gõ: <s>{it.answer}</s>
                  </p>
                )}
                {it.example && <p className="mt-0.5 text-xs italic text-slate-500">{it.example}</p>}
              </li>
            ))}
          </ul>
        </Card>
      )}

      {summary.items.length > 0 && (
        <details className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <summary className="cursor-pointer text-sm font-semibold text-slate-700">Tất cả các câu ({summary.items.length})</summary>
          <ol className="mt-3 space-y-1 text-sm">
            {summary.items.map((it) => (
              <li key={it.seq} className="flex items-center justify-between gap-3">
                <span className="truncate">
                  <span className="text-slate-400">{it.seq}.</span> {it.meaning} → <b>{it.word}</b>
                  {it.retry && <span className="ml-1 text-xs text-amber-600">(ôn lại)</span>}
                </span>
                <span className="flex shrink-0 items-center gap-2">
                  <Badge tone={RESULT_TONE[it.result]}>{RESULT_LABELS[it.result]}</Badge>
                  <span className="w-10 text-right tabular-nums text-slate-700">+{it.points}</span>
                </span>
              </li>
            ))}
          </ol>
        </details>
      )}
    </div>
  );
}
