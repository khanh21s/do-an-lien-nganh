'use client';

import { Spinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { practiceApi } from '@/lib/endpoints/practice';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';

export interface RecordingPlayerHandle {
  play: () => void;
  pause: () => void;
  toggle: () => void;
}

/**
 * SHA-05: plays a shadowing recording.
 * - `blob`: a local, just-recorded WAV → played directly.
 * - `attemptId`: fetched with Bearer via axios (`responseType: 'blob'`) → object URL.
 * Object URLs are revoked on change/unmount. The ref exposes play/pause/toggle (Space shortcut).
 */
export const RecordingPlayer = forwardRef<
  RecordingPlayerHandle,
  { attemptId?: string | null; blob?: Blob | null; className?: string }
>(function RecordingPlayer({ attemptId, blob, className }, ref) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [url, setUrl] = useState<string | null>(null);

  const remote = useQuery({
    queryKey: qk.recording(attemptId ?? ''),
    queryFn: () => practiceApi.getRecordingBlob(attemptId as string),
    enabled: !blob && !!attemptId,
    staleTime: Infinity,
    retry: false,
  });

  const source = blob ?? remote.data ?? null;

  useEffect(() => {
    if (!source) {
      setUrl(null);
      return;
    }
    const objectUrl = URL.createObjectURL(source);
    setUrl(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [source]);

  useImperativeHandle(
    ref,
    () => ({
      play: () => {
        const a = audioRef.current;
        if (!a) return;
        a.currentTime = 0;
        a.play().catch(() => undefined);
      },
      pause: () => audioRef.current?.pause(),
      toggle: () => {
        const a = audioRef.current;
        if (!a) return;
        if (a.paused) a.play().catch(() => undefined);
        else a.pause();
      },
    }),
    []
  );

  if (!blob && !attemptId) return null;

  if (!blob && remote.isLoading) {
    return (
      <div className={clsx('flex items-center gap-2 text-sm text-slate-500', className)}>
        <Spinner className="h-4 w-4" /> Đang tải bản ghi…
      </div>
    );
  }

  if (!blob && remote.isError) {
    return (
      <div className={clsx('flex items-center gap-2 text-sm text-slate-500', className)}>
        <span>🔇 {apiErrorMessage(remote.error, 'Không tải được bản ghi.')}</span>
        <button type="button" className="font-medium text-brand-600 hover:underline" onClick={() => remote.refetch()}>
          Thử lại
        </button>
      </div>
    );
  }

  if (!url) return null;

  return (
    <audio ref={audioRef} controls src={url} preload="auto" className={clsx('w-full', className)}>
      Trình duyệt của bạn không hỗ trợ phát audio.
    </audio>
  );
});
