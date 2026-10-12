'use client';

import { AudioPlayer } from '@/components/audio/AudioPlayer';
import { WordDiffView } from '@/components/practice/WordDiffView';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select, Textarea } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import { practiceApi } from '@/lib/endpoints/practice';
import type { DictationAttemptResult, Difficulty } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import toast from 'react-hot-toast';

const DIFFICULTIES: Difficulty[] = ['Easy', 'Normal', 'Hard'];

export default function DictationPage() {
  const params = useParams<{ id: string; sentenceId: string }>();
  const { data: lesson, isLoading } = useQuery({
    queryKey: ['lesson', params.id],
    queryFn: () => contentApi.getLesson(params.id),
  });
  const [userInput, setUserInput] = useState('');
  const [difficulty, setDifficulty] = useState<Difficulty>('Normal');
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<DictationAttemptResult | null>(null);

  if (isLoading || !lesson) return <FullPageSpinner />;

  const sentence = lesson.sentences.find((s) => s.id === params.sentenceId);
  if (!sentence) {
    return <p className="text-slate-600">Không tìm thấy câu này trong bài học.</p>;
  }

  async function handleSubmit() {
    if (!userInput.trim()) {
      toast.error('Vui lòng nhập nội dung bạn nghe được.');
      return;
    }
    setSubmitting(true);
    try {
      const res = await practiceApi.submitDictation({
        lessonId: params.id,
        sentenceId: params.sentenceId,
        userInput,
        difficulty,
      });
      setResult(res);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không thể chấm điểm, vui lòng thử lại.'));
    } finally {
      setSubmitting(false);
    }
  }

  function handleRetry() {
    setResult(null);
    setUserInput('');
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Link href={`/lessons/${params.id}`} className="text-sm text-brand-600 hover:underline">
        ← Quay lại bài học
      </Link>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">Dictation</h1>

      <Card className="mt-4">
        {sentence.audioUrl ? (
          <AudioPlayer src={contentApi.sentenceAudioUrl(sentence.id)} />
        ) : (
          <p className="text-sm text-slate-500">Câu này chưa có audio.</p>
        )}

        {!result ? (
          <>
            <Select
              label="Độ khó"
              className="mt-4 w-40"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value as Difficulty)}
            >
              {DIFFICULTIES.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </Select>
            <Textarea
              label="Gõ lại những gì bạn nghe được"
              className="mt-4 w-full"
              rows={3}
              value={userInput}
              onChange={(e) => setUserInput(e.target.value)}
              placeholder="Nhập câu bạn nghe được ở đây..."
            />
            <Button className="mt-4" loading={submitting} onClick={handleSubmit}>
              Chấm điểm
            </Button>
          </>
        ) : (
          <div className="mt-4">
            <div className="flex items-baseline gap-4">
              <span className="text-3xl font-bold text-brand-600">{result.score.toFixed(1)}</span>
              <span className="text-sm text-slate-500">
                Độ chính xác {result.accuracy.toFixed(1)}% ({result.correctWords}/{result.totalWords} từ đúng)
              </span>
            </div>
            <div className="mt-4">
              <WordDiffView words={result.wordScores} />
            </div>
            <p className="mt-4 text-sm text-slate-500">Đáp án đúng: {sentence.text}</p>
            <div className="mt-4 flex gap-2">
              <Button variant="secondary" onClick={handleRetry}>
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
