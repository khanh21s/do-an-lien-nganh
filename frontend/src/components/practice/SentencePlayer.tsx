'use client';

import { Spinner } from '@/components/ui/Spinner';
import type { SentenceAudioController } from '@/hooks/useSentenceAudio';
import { PLAYBACK_RATES } from '@/lib/constants';
import clsx from 'clsx';

/**
 * Play/Pause + Replay + speed (0.5–1.5) for one sentence (DIC-03/DIC-04).
 * Presentational: pass the controller from `useSentenceAudio` so the page can bind hotkeys to it.
 */
export function SentencePlayer({
  audio,
  className,
  showRate = true,
}: {
  audio: SentenceAudioController;
  className?: string;
  showRate?: boolean;
}) {
  if (!audio.available) {
    return (
      <p className={clsx('rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-500', className)}>
        🔇 Câu này chưa có audio.
      </p>
    );
  }

  return (
    <div className={clsx('flex flex-wrap items-center gap-2', className)}>
      <button
        type="button"
        onClick={audio.toggle}
        aria-label={audio.isPlaying ? 'Tạm dừng' : 'Phát'}
        className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-600 text-xl text-white shadow-sm transition-colors hover:bg-brand-700"
      >
        {audio.isLoading && !audio.isPlaying ? (
          <Spinner className="h-5 w-5 border-white/40 border-t-white" />
        ) : audio.isPlaying ? (
          '⏸'
        ) : (
          '▶'
        )}
      </button>
      <button
        type="button"
        onClick={audio.replay}
        aria-label="Nghe lại từ đầu"
        title="Nghe lại"
        className="flex h-11 w-11 items-center justify-center rounded-full border border-slate-300 bg-white text-lg text-slate-700 hover:bg-slate-50"
      >
        ↻
      </button>

      {showRate && (
        <label className="ml-1 flex items-center gap-1 text-sm text-slate-600">
          <span className="sr-only sm:not-sr-only">Tốc độ</span>
          <select
            value={audio.playbackRate}
            onChange={(e) => audio.setPlaybackRate(Number(e.target.value))}
            className="h-11 rounded-lg border border-slate-300 bg-white px-2 text-sm outline-none focus:border-brand-500"
            aria-label="Tốc độ phát"
          >
            {PLAYBACK_RATES.map((r) => (
              <option key={r} value={r}>
                {r}x
              </option>
            ))}
          </select>
        </label>
      )}

      {audio.error && <span className="text-sm text-red-600">{audio.error}</span>}
    </div>
  );
}
