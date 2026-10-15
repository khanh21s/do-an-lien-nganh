'use client';

import { Recorder, type RecorderHandle } from '@/components/audio/Recorder';
import { RecordingPlayer, type RecordingPlayerHandle } from '@/components/audio/RecordingPlayer';
import { PracticeHeader } from '@/components/practice/PracticeHeader';
import { SentencePlayer } from '@/components/practice/SentencePlayer';
import { ShortcutHelp } from '@/components/practice/ShortcutHelp';
import { bindAction } from '@/lib/shortcuts';
import { useShortcutKeys } from '@/store/shortcut-store';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { KbdCombo } from '@/components/ui/Kbd';
import { Spinner } from '@/components/ui/Spinner';
import { comboToLabels, useHotkeys } from '@/hooks/useHotkeys';
import { usePracticeNav } from '@/hooks/usePracticeNav';
import { useSentenceAudio } from '@/hooks/useSentenceAudio';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import { practiceApi } from '@/lib/endpoints/practice';
import { addTimeSpent, ensureSession, incrementCounter } from '@/lib/practice-session';
import { qk } from '@/lib/queryKeys';
import type { LessonDetail, SentenceResponse, ShadowingAttemptResult } from '@/lib/types';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { ShadowingResult } from './ShadowingResult';
import { ShadowingSubmitError, submitRecording, type SubmitStage } from './submit';
import { refreshGamification } from '@/components/gamification/useGamification'; // P2-B

interface Take {
  blob: Blob;
  durationSec: number;
  /** Set once the upload succeeded, so "Gửi lại" only re-runs scoring. */
  uploadedRecordingId: string | null;
}

/** SHA-06: "Lần gần nhất" + "Trung bình" accuracy of this sentence from the lesson's attempts. */
function SentenceStats({ lessonId, sentenceId }: { lessonId: string; sentenceId: string }) {
  const { data, isLoading } = useQuery({
    queryKey: qk.lessonAttempts(lessonId),
    queryFn: () => practiceApi.getLessonAttempts(lessonId),
  });

  const stats = useMemo(() => {
    const list = (data?.attempts ?? [])
      .filter((a) => a.mode === 'shadowing' && a.sentenceId === sentenceId)
      .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1));
    if (!list.length) return null;
    const avg = list.reduce((s, a) => s + a.accuracy, 0) / list.length;
    return { last: list[0].accuracy, avg, count: list.length };
  }, [data, sentenceId]);

  if (isLoading) return <p className="text-xs text-slate-400">Đang tải lịch sử…</p>;
  if (!stats) return <p className="text-xs text-slate-500">Bạn chưa shadow câu này.</p>;
  return (
    <p className="text-xs text-slate-600">
      Lần gần nhất: <strong>{Math.round(stats.last)}%</strong> · Trung bình:{' '}
      <strong>{Math.round(stats.avg)}%</strong> ({stats.count} lần)
    </p>
  );
}

export function ShadowingPractice({ lesson, sentence }: { lesson: LessonDetail; sentence: SentenceResponse }) {
  const lessonId = lesson.id;
  const queryClient = useQueryClient();
  const nav = usePracticeNav(lessonId, 'shadowing', lesson.sentences, sentence.id);
  const audio = useSentenceAudio({ lessonId, sentence, lessonAudioUrl: lesson.audioUrl, mode: 'shadowing' });

  const recorderRef = useRef<RecorderHandle>(null);
  const playerRef = useRef<RecordingPlayerHandle>(null);
  const [take, setTake] = useState<Take | null>(null);
  const [stage, setStage] = useState<SubmitStage | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [result, setResult] = useState<ShadowingAttemptResult | null>(null);
  const processing = stage !== null;

  // Session bookkeeping for the result screen (time spent per sentence).
  useEffect(() => {
    ensureSession(lessonId, 'shadowing');
    const startedAt = Date.now();
    return () => {
      addTimeSpent(lessonId, 'shadowing', sentence.id, Date.now() - startedAt);
    };
  }, [lessonId, sentence.id]);

  const send = useCallback(
    async (t: Take) => {
      setFailure(null);
      incrementCounter(lessonId, 'shadowing', sentence.id, 'submits');
      try {
        const { result: res } = await submitRecording(practiceApi, {
          lessonId,
          sentenceId: sentence.id,
          blob: t.blob,
          durationSec: t.durationSec,
          uploadedRecordingId: t.uploadedRecordingId,
          onStage: setStage,
        });
        setResult(res);
        setTake((prev) => (prev && prev.blob === t.blob ? { ...prev, uploadedRecordingId: null } : prev));
        void queryClient.invalidateQueries({ queryKey: qk.practiceStats });
        void queryClient.invalidateQueries({ queryKey: qk.streak });
        void queryClient.invalidateQueries({ queryKey: qk.lessonAttempts(lessonId) });
        void queryClient.invalidateQueries({ queryKey: qk.lessonStats(lessonId) });
        void queryClient.invalidateQueries({ queryKey: qk.attemptsAll });
        void queryClient.invalidateQueries({ queryKey: qk.activityAll });
        void queryClient.invalidateQueries({ queryKey: qk.trendingErrors });
        refreshGamification(queryClient, { delayed: true }); // P2-B: XP arrives asynchronously
      } catch (err) {
        const se = err instanceof ShadowingSubmitError ? err : null;
        const original = se ? se.original : err;
        // A scoring 404 means the uploaded file is gone → the next try must upload again.
        const keepId = se?.stage === 'scoring' && apiErrorStatus(original) !== 404 ? se.recordingId : null;
        setTake((prev) => (prev && prev.blob === t.blob ? { ...prev, uploadedRecordingId: keepId } : prev));
        const msg = apiErrorMessage(
          original,
          se?.stage === 'uploading' ? 'Tải bản ghi lên thất bại.' : 'Không chấm được điểm phát âm.'
        );
        setFailure(msg);
        toast.error(msg);
      } finally {
        setStage(null);
      }
    },
    [lessonId, sentence.id, queryClient]
  );

  const handleRecorded = useCallback(
    (blob: Blob, durationSec: number) => {
      const t: Take = { blob, durationSec, uploadedRecordingId: null };
      setTake(t);
      void send(t);
    },
    [send]
  );

  const pauseModel = audio.pause;
  const handleStart = useCallback(() => {
    pauseModel();
    playerRef.current?.pause();
    setResult(null);
    setFailure(null);
  }, [pauseModel]);

  const keys = useShortcutKeys();
  useHotkeys([
    ...bindAction(keys('shadowing.record'), {
      handler: () => {
        if (!processing) recorderRef.current?.toggle();
      },
    }),
    ...bindAction(keys('shadowing.playTake'), { handler: () => playerRef.current?.toggle(), enabled: !!take }),
    ...bindAction(keys('shadowing.prev'), { handler: nav.goPrev, allowInInput: true }),
    ...bindAction(keys('shadowing.next'), { handler: nav.goNext, allowInInput: true }),
  ]);

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-4">
      <PracticeHeader
        lessonId={lessonId}
        lessonTitle={lesson.title}
        mode="shadowing"
        sentences={lesson.sentences}
        currentId={sentence.id}
        right={<ShortcutHelp scope="shadowing" title="Phím tắt Shadowing" />}
      />

      <Card>
        <p className="break-words text-xl font-medium leading-relaxed text-slate-900">{sentence.text}</p>
        {sentence.ipa && <p className="mt-1 break-words text-sm text-slate-500">/{sentence.ipa.replace(/^\/|\/$/g, '')}/</p>}
        {sentence.translationVi && <p className="mt-1 text-sm italic text-slate-500">{sentence.translationVi}</p>}
        <SentencePlayer audio={audio} className="mt-4" />
        <div className="mt-3">
          <SentenceStats lessonId={lessonId} sentenceId={sentence.id} />
        </div>
      </Card>

      <Card className="flex flex-col items-center gap-4">
        <p className="text-center text-sm text-slate-600">
          Nghe câu mẫu rồi nói lại theo. Phím tắt <KbdCombo keys={comboToLabels(keys('shadowing.record')[0])} /> để ghi / dừng.
        </p>
        <Recorder ref={recorderRef} onRecorded={handleRecorded} onStart={handleStart} disabled={processing} />

        {take && (
          <div className="w-full max-w-md">
            <p className="mb-1 text-xs text-slate-500">
              Bản ghi của bạn ({take.durationSec.toFixed(1)}s) · <KbdCombo keys={comboToLabels(keys('shadowing.playTake')[0])} /> để nghe lại
            </p>
            <RecordingPlayer ref={playerRef} blob={take.blob} />
          </div>
        )}

        {processing && (
          <div className="flex items-center gap-2 text-sm text-slate-600" role="status">
            <Spinner className="h-4 w-4" />
            {stage === 'uploading' ? 'Đang tải bản ghi lên…' : 'Đang chấm…'}
          </div>
        )}

        {failure && !processing && take && (
          <div
            role="alert"
            className="flex w-full max-w-md flex-wrap items-center justify-between gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
          >
            <span>{failure}</span>
            <Button type="button" variant="danger" size="sm" className="min-h-[44px]" onClick={() => void send(take)}>
              ↻ Gửi lại
            </Button>
          </div>
        )}
      </Card>

      {result && (
        <ShadowingResult
          result={result}
          isLast={nav.isLast}
          onRetry={() => recorderRef.current?.start()}
          onNext={nav.goNextOrFinish}
        />
      )}
    </div>
  );
}
