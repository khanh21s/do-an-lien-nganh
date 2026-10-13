'use client';

import { MicPermissionHelp } from '@/components/practice/shadowing/MicPermissionHelp';
import { classifyMediaError, startPcmCapture, type PcmCapture, type RecorderErrorCode } from '@/lib/audio/recorder';
import { MAX_RECORDING_SECONDS, MIN_RECORDING_SECONDS } from '@/lib/audio/wav';
import clsx from 'clsx';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useRef, useState } from 'react';

export interface RecorderHandle {
  start: () => void;
  stop: () => void;
  /** Start when idle, stop when recording (Shift+` shortcut). */
  toggle: () => void;
}

export interface RecorderProps {
  /** Called with a WAV PCM 16 kHz mono blob once a valid (≥ 0.5 s) take is finished. */
  onRecorded: (blob: Blob, durationSec: number) => void;
  /** Called right after the microphone opens (e.g. to pause the model audio). */
  onStart?: () => void;
  disabled?: boolean;
  maxSeconds?: number;
  className?: string;
}

type Phase = 'idle' | 'starting' | 'recording' | 'stopping';

function formatClock(sec: number): string {
  const s = Math.max(0, Math.floor(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

/**
 * SHA-01 / SHA-03: records the microphone as WAV PCM 16 kHz mono (Web Audio, no MediaRecorder).
 * Auto-stops at `maxSeconds` (30 s), rejects takes shorter than 0.5 s, and shows browser-specific
 * permission help when the mic is blocked. Imperative handle for keyboard shortcuts.
 */
export const Recorder = forwardRef<RecorderHandle, RecorderProps>(function Recorder(
  { onRecorded, onStart, disabled, maxSeconds = MAX_RECORDING_SECONDS, className },
  ref
) {
  const [phase, setPhase] = useState<Phase>('idle');
  const [elapsed, setElapsed] = useState(0);
  const [level, setLevel] = useState(0);
  const [errorCode, setErrorCode] = useState<RecorderErrorCode | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const captureRef = useRef<PcmCapture | null>(null);
  const phaseRef = useRef<Phase>('idle');
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const levelRef = useRef(0);
  const mountedRef = useRef(true);
  const callbacks = useRef({ onRecorded, onStart });
  callbacks.current = { onRecorded, onStart };

  const setPhaseBoth = (p: Phase) => {
    phaseRef.current = p;
    if (mountedRef.current) setPhase(p);
  };

  const clearTimer = () => {
    if (timerRef.current !== null) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  };

  const stop = useCallback(async () => {
    const capture = captureRef.current;
    if (!capture || phaseRef.current !== 'recording') return;
    captureRef.current = null;
    clearTimer();
    setPhaseBoth('stopping');
    try {
      const { blob, durationSec } = await capture.stop();
      if (!mountedRef.current) return;
      setLevel(0);
      if (durationSec < MIN_RECORDING_SECONDS) {
        setNotice('Bản ghi quá ngắn (tối thiểu 0,5 giây). Hãy nói cả câu rồi bấm dừng.');
      } else {
        if (durationSec >= maxSeconds - 0.05) {
          setNotice(`Đã tự dừng ở ${maxSeconds} giây (giới hạn tối đa).`);
        }
        callbacks.current.onRecorded(blob, durationSec);
      }
    } catch {
      if (mountedRef.current) setErrorCode('unknown');
    } finally {
      setPhaseBoth('idle');
    }
  }, [maxSeconds]);

  const start = useCallback(async () => {
    if (disabled || phaseRef.current !== 'idle') return;
    setErrorCode(null);
    setNotice(null);
    setElapsed(0);
    setPhaseBoth('starting');
    try {
      const capture = await startPcmCapture({
        maxSeconds,
        onLevel: (l) => {
          levelRef.current = l;
        },
      });
      if (!mountedRef.current) {
        capture.cancel();
        return;
      }
      captureRef.current = capture;
      setPhaseBoth('recording');
      callbacks.current.onStart?.();
      timerRef.current = setInterval(() => {
        const sec = capture.capturedSeconds();
        setElapsed(sec);
        setLevel(levelRef.current);
        if (sec >= maxSeconds) void stop();
      }, 100);
    } catch (err) {
      setErrorCode(classifyMediaError(err));
      setPhaseBoth('idle');
    }
  }, [disabled, maxSeconds, stop]);

  const toggle = useCallback(() => {
    if (phaseRef.current === 'recording') void stop();
    else if (phaseRef.current === 'idle') void start();
  }, [start, stop]);

  useImperativeHandle(ref, () => ({ start: () => void start(), stop: () => void stop(), toggle }), [start, stop, toggle]);

  // Release the microphone if the component unmounts mid-recording (navigation, sentence change).
  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      clearTimer();
      captureRef.current?.cancel();
      captureRef.current = null;
    };
  }, []);

  const recording = phase === 'recording';
  const busy = phase === 'starting' || phase === 'stopping';
  const percent = Math.min(100, (elapsed / maxSeconds) * 100);

  return (
    <div className={clsx('flex flex-col items-center gap-3', className)}>
      <button
        type="button"
        onClick={toggle}
        disabled={(disabled && !recording) || busy}
        aria-pressed={recording}
        aria-label={recording ? 'Dừng ghi âm' : 'Bắt đầu ghi âm'}
        title="Ghi âm / dừng (Shift+`)"
        className={clsx(
          'flex h-20 w-20 items-center justify-center rounded-full text-3xl text-white shadow-md transition-colors',
          'focus:outline-none focus-visible:ring-4 focus-visible:ring-brand-300 disabled:cursor-not-allowed disabled:opacity-50',
          recording ? 'bg-red-500 hover:bg-red-600' : 'bg-brand-600 hover:bg-brand-700'
        )}
        style={recording ? { boxShadow: `0 0 0 ${4 + Math.round(Math.min(1, level * 4) * 14)}px rgba(239, 68, 68, 0.25)` } : undefined}
      >
        {busy ? (
          <span className="h-7 w-7 animate-spin rounded-full border-4 border-white/40 border-t-white" />
        ) : recording ? (
          '⏹'
        ) : (
          '🎙'
        )}
      </button>

      <div className="w-full max-w-xs text-center">
        {recording ? (
          <>
            <p className="font-mono text-sm text-red-600" aria-live="polite">
              ● Đang ghi {formatClock(elapsed)} / {formatClock(maxSeconds)}
            </p>
            <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
              <div className="h-full rounded-full bg-red-500 transition-all" style={{ width: `${percent}%` }} />
            </div>
          </>
        ) : (
          <p className="text-sm text-slate-500">
            {phase === 'starting'
              ? 'Đang mở micro…'
              : phase === 'stopping'
                ? 'Đang xử lý bản ghi…'
                : 'Bấm để ghi âm, bấm lần nữa để dừng (tối đa 30 giây).'}
          </p>
        )}
      </div>

      {notice && !recording && (
        <p className="text-center text-sm text-amber-700" role="status">
          {notice}
        </p>
      )}
      {errorCode && <MicPermissionHelp code={errorCode} onRetry={() => void start()} />}
    </div>
  );
});
