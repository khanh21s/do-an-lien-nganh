'use client';

import { AudioPlayer } from '@/components/audio/AudioPlayer';
import { Recorder } from '@/components/audio/Recorder';
import { PhonemeScoreView } from '@/components/practice/PhonemeScoreView';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { apiErrorMessage, uploadToPresignedUrl } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import type { ShadowingAttemptResult } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import toast from 'react-hot-toast';

export default function ShadowingPage() {
  const params = useParams<{ id: string; sentenceId: string }>();
  const { data: lesson, isLoading } = useQuery({
    queryKey: ['lesson', params.id],
    queryFn: () => contentApi.getLesson(params.id),
  });
  const [processing, setProcessing] = useState(false);
  const [result, setResult] = useState<ShadowingAttemptResult | null>(null);

  if (isLoading || !lesson) return <FullPageSpinner />;

  const sentence = lesson.sentences.find((s) => s.id === params.sentenceId);
  if (!sentence) {
    return <p className="text-slate-600">Không tìm thấy câu này trong bài học.</p>;
  }

  async function handleRecorded(blob: Blob, durationSec: number) {
    setProcessing(true);
    try {
      const { uploadUrl, recordingId } = await practiceApi.requestShadowingUploadUrl({
        lessonId: params.id,
        sentenceId: params.sentenceId,
      });
      await uploadToPresignedUrl(uploadUrl, blob, blob.type || 'audio/webm');
      const res = await practiceApi.submitShadowing({
        lessonId: params.id,
        sentenceId: params.sentenceId,
        recordingId,
        duration: Number(durationSec.toFixed(1)),
      });
      setResult(res);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không thể chấm điểm phát âm, vui lòng thử lại.'));
    } finally {
      setProcessing(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href={`/lessons/${params.id}`} className="text-sm text-brand-600 hover:underline">
        ← Quay lại bài học
      </Link>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">Shadowing</h1>

      <Card className="mt-4">
        <p className="text-lg font-medium text-slate-900">{sentence.text}</p>
        {sentence.ipa && <p className="mt-1 text-sm text-slate-400">/{sentence.ipa}/</p>}
        {sentence.audioUrl && (
          <div className="mt-3">
            <AudioPlayer src={contentApi.sentenceAudioUrl(sentence.id)} />
          </div>
        )}

        {!result ? (
          <div className="mt-6">
            {processing ? (
              <div className="flex flex-col items-center gap-2">
                <Spinner />
                <p className="text-sm text-slate-500">Đang chấm điểm phát âm...</p>
              </div>
            ) : (
              <Recorder onRecorded={handleRecorded} disabled={processing} />
            )}
          </div>
        ) : (
          <div className="mt-6">
            <div className="flex items-baseline gap-4">
              <span className="text-3xl font-bold text-brand-600">{result.score.toFixed(1)}</span>
              <span className="text-sm text-slate-500">Độ chính xác {result.accuracy.toFixed(1)}%</span>
            </div>
            <div className="mt-4">
              <PhonemeScoreView words={result.wordScores} />
            </div>
            <div className="mt-4 flex gap-2">
              <Button variant="secondary" onClick={() => setResult(null)}>
                Thử lại
              </Button>
              <Link href={`/lessons/${params.id}`}>
                <Button variant="ghost">Quay lại bài học</Button>
              </Link>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
