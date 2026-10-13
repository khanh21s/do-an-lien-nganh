'use client';

import { LEVEL_LABELS, PhonemeScoreView, pronunciationLevel } from '@/components/practice/PhonemeScoreView';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import type { ShadowingAttemptResult } from '@/lib/types';
import clsx from 'clsx';

const SCORE_COLOR = {
  good: 'text-green-600',
  close: 'text-amber-600',
  retry: 'text-red-600',
} as const;

/** Mock scorer badge (architecture §8.5): mock scores only reflect how long the user spoke. */
export function DemoScoringBadge() {
  return (
    <Badge tone="amber" className="whitespace-normal">
      <span title="Chưa cấu hình Azure Speech: điểm chỉ dựa trên thời lượng có tiếng nói, không phân tích phát âm thật.">
        🧪 Chế độ demo – điểm mô phỏng
      </span>
    </Badge>
  );
}

export function ShadowingResult({
  result,
  isLast,
  onRetry,
  onNext,
}: {
  result: ShadowingAttemptResult;
  isLast: boolean;
  onRetry: () => void;
  onNext: () => void;
}) {
  const level = pronunciationLevel(result.accuracy);
  return (
    <Card aria-live="polite">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wide text-slate-500">Độ chính xác phát âm</p>
          <p className={clsx('text-4xl font-bold', SCORE_COLOR[level])}>
            {Math.round(result.accuracy)}%
            <span className="ml-2 text-base font-medium">{LEVEL_LABELS[level]}</span>
          </p>
          <p className="mt-1 text-sm text-slate-500">
            Điểm {result.score.toFixed(1)} · {result.duration.toFixed(1)} giây
          </p>
        </div>
        {result.scorer === 'mock' && <DemoScoringBadge />}
      </div>

      <div className="mt-4">
        <PhonemeScoreView key={result.attemptId} words={result.wordScores} />
        {!result.wordScores?.length && <p className="text-sm text-slate-500">Không có điểm chi tiết từng từ.</p>}
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <Button type="button" variant="secondary" className="min-h-[44px]" onClick={onRetry}>
          🎙 Ghi lại
        </Button>
        <Button type="button" variant={isLast ? 'success' : 'primary'} className="min-h-[44px]" onClick={onNext}>
          {isLast ? 'Hoàn thành bài' : 'Câu kế →'}
        </Button>
      </div>
    </Card>
  );
}
