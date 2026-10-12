'use client';

import { AudioPlayer } from '@/components/audio/AudioPlayer';
import { PhonemeScoreView } from '@/components/practice/PhonemeScoreView';
import { WordDiffView } from '@/components/practice/WordDiffView';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import type { ShadowingWordScore, WordScore } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';

export default function PracticeHistoryPage() {
  const [page, setPage] = useState(1);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['attempts', page],
    queryFn: () => practiceApi.listAttempts({ page, limit: 20 }),
  });

  const { data: detail } = useQuery({
    queryKey: ['attempt', selectedId],
    queryFn: () => practiceApi.getAttempt(selectedId as string),
    enabled: !!selectedId,
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Lịch sử luyện tập</h1>

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data?.data.map((attempt) => (
            <Card
              key={attempt.attemptId}
              className="flex cursor-pointer items-center justify-between transition-shadow hover:shadow-md"
              onClick={() => setSelectedId(attempt.attemptId)}
            >
              <div className="flex items-center gap-3">
                <Badge tone={attempt.mode === 'dictation' ? 'brand' : 'green'}>{attempt.mode}</Badge>
                <span className="text-sm text-slate-500">Điểm: {attempt.score.toFixed(1)}</span>
              </div>
              <span className="text-sm font-medium text-slate-700">{attempt.accuracy.toFixed(1)}% chính xác</span>
            </Card>
          ))}
          {data?.data.length === 0 && <p className="text-slate-500">Bạn chưa có lượt luyện tập nào.</p>}
        </div>
      )}

      {data && data.pagination.totalPages > 1 && (
        <div className="mt-6 flex justify-center gap-2 text-sm">
          {Array.from({ length: data.pagination.totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              onClick={() => setPage(p)}
              className={`h-8 w-8 rounded-lg ${p === page ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'}`}
            >
              {p}
            </button>
          ))}
        </div>
      )}

      <Modal open={!!selectedId} onClose={() => setSelectedId(null)} title="Chi tiết lượt luyện tập">
        {detail ? (
          <div>
            <div className="flex items-baseline gap-4">
              <span className="text-2xl font-bold text-brand-600">{detail.score.toFixed(1)}</span>
              <span className="text-sm text-slate-500">{detail.accuracy.toFixed(1)}% chính xác</span>
              {detail.difficulty && <Badge tone="slate">{detail.difficulty}</Badge>}
            </div>
            {detail.audioRecordingUrl && (
              <div className="mt-3">
                <AudioPlayer src={detail.audioRecordingUrl} />
              </div>
            )}
            <div className="mt-4">
              {detail.mode === 'dictation' ? (
                <WordDiffView words={(detail.wordScores as WordScore[]) ?? []} />
              ) : (
                <PhonemeScoreView words={(detail.wordScores as ShadowingWordScore[]) ?? []} />
              )}
            </div>
          </div>
        ) : (
          <FullPageSpinner />
        )}
      </Modal>
    </div>
  );
}
