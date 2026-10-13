'use client';

import { DEFAULT_PLAYBACK_RATE, MAX_PLAYBACK_RATE, MIN_PLAYBACK_RATE } from '@/lib/constants';
import { contentApi } from '@/lib/endpoints/content';
import { incrementCounter } from '@/lib/practice-session';
import { storageKeys } from '@/lib/storageKeys';
import type { PracticeMode, SentenceResponse } from '@/lib/types';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocalStorage } from './useLocalStorage';

export type SentenceAudioSentence = Pick<SentenceResponse, 'id' | 'audioUrl' | 'startMs' | 'endMs'>;

export interface UseSentenceAudioOptions {
  lessonId: string;
  sentence: SentenceAudioSentence | null | undefined;
  /** `LessonDetail.audioUrl`; needed for sentences without their own audio (segment playback). */
  lessonAudioUrl?: string | null;
  /** When set, replays are counted into the practice session (DIC-08). */
  mode?: PracticeMode;
  /** Play automatically whenever the sentence changes (may be blocked by the browser). */
  autoPlay?: boolean;
}

export interface SentenceAudioController {
  /** false when neither the sentence nor the lesson has audio. */
  available: boolean;
  /** 'sentence' = the sentence's own file; 'segment' = startMs–endMs of the lesson audio. */
  source: 'sentence' | 'segment' | null;
  isPlaying: boolean;
  isLoading: boolean;
  error: string | null;
  playbackRate: number;
  setPlaybackRate: (rate: number) => void;
  play: () => void;
  pause: () => void;
  toggle: () => void;
  /** Restart from the beginning of the sentence (counts as a replay). */
  replay: () => void;
  /** Number of replays of the current sentence since it was shown. */
  replayCount: number;
}

const END_TOLERANCE_S = 0.03;

function clampRate(rate: number): number {
  if (!Number.isFinite(rate)) return DEFAULT_PLAYBACK_RATE;
  return Math.min(MAX_PLAYBACK_RATE, Math.max(MIN_PLAYBACK_RATE, rate));
}

/**
 * DIC-03/DIC-04: plays one sentence — either its own audio, or the `startMs–endMs` segment of the
 * lesson audio with auto-stop (checked on `timeupdate` and every animation frame, so the overshoot
 * stays well under 300 ms). Playback rate is persisted in localStorage and applied to every sentence.
 */
export function useSentenceAudio({
  lessonId,
  sentence,
  lessonAudioUrl,
  mode,
  autoPlay = false,
}: UseSentenceAudioOptions): SentenceAudioController {
  const [storedRate, setStoredRate] = useLocalStorage<number>(storageKeys.playbackRate, DEFAULT_PLAYBACK_RATE);
  const playbackRate = clampRate(storedRate);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const rafRef = useRef<number | null>(null);
  const startedOnceRef = useRef(false);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [replayCount, setReplayCount] = useState(0);

  const sentenceId = sentence?.id ?? null;
  const ownAudio = !!sentence?.audioUrl;
  const source: SentenceAudioController['source'] = !sentence
    ? null
    : ownAudio
      ? 'sentence'
      : lessonAudioUrl
        ? 'segment'
        : null;
  const src =
    source === 'sentence' && sentenceId
      ? contentApi.sentenceAudioUrl(sentenceId)
      : source === 'segment'
        ? contentApi.lessonAudioUrl(lessonId)
        : null;
  const startS = source === 'segment' ? (sentence?.startMs ?? 0) / 1000 : 0;
  const endS = source === 'segment' ? (sentence?.endMs ?? 0) / 1000 : null;

  // Keep the latest values for event handlers without re-binding them.
  const live = useRef({ startS, endS, lessonId, sentenceId, mode, playbackRate });
  live.current = { startS, endS, lessonId, sentenceId, mode, playbackRate };

  const stopRaf = useCallback(() => {
    if (rafRef.current !== null) {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = null;
    }
  }, []);

  /** Pauses at the segment end and rewinds so the next play starts at the sentence start. */
  const checkSegmentEnd = useCallback(() => {
    const audio = audioRef.current;
    const { endS: end, startS: start } = live.current;
    if (!audio || end === null || end <= start) return false;
    if (audio.currentTime >= end - END_TOLERANCE_S) {
      audio.pause();
      audio.currentTime = start;
      return true;
    }
    return false;
  }, []);

  const tick = useCallback(() => {
    if (checkSegmentEnd()) {
      rafRef.current = null;
      return;
    }
    rafRef.current = requestAnimationFrame(tick);
  }, [checkSegmentEnd]);

  // Create the <audio> element once.
  useEffect(() => {
    const audio = new Audio();
    audio.preload = 'auto';
    audioRef.current = audio;

    const onPlay = () => {
      setIsPlaying(true);
      setIsLoading(false);
      stopRaf();
      if (live.current.endS !== null) rafRef.current = requestAnimationFrame(tick);
    };
    const onPause = () => {
      setIsPlaying(false);
      stopRaf();
    };
    const onEnded = () => {
      setIsPlaying(false);
      stopRaf();
    };
    const onWaiting = () => setIsLoading(true);
    const onCanPlay = () => setIsLoading(false);
    const onTimeUpdate = () => {
      checkSegmentEnd();
    };
    const onError = () => {
      setIsLoading(false);
      setIsPlaying(false);
      setError('Audio chưa khả dụng.');
    };

    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);
    audio.addEventListener('ended', onEnded);
    audio.addEventListener('waiting', onWaiting);
    audio.addEventListener('canplay', onCanPlay);
    audio.addEventListener('playing', onCanPlay);
    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('error', onError);

    return () => {
      stopRaf();
      audio.pause();
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
      audio.removeEventListener('ended', onEnded);
      audio.removeEventListener('waiting', onWaiting);
      audio.removeEventListener('canplay', onCanPlay);
      audio.removeEventListener('playing', onCanPlay);
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('error', onError);
      audio.removeAttribute('src');
      audio.load();
      audioRef.current = null;
    };
  }, [checkSegmentEnd, stopRaf, tick]);

  const startPlayback = useCallback((fromStart: boolean) => {
    const audio = audioRef.current;
    if (!audio || !audio.src) return;
    const { startS: start, endS: end, lessonId: lid, sentenceId: sid, mode: m, playbackRate: rate } = live.current;

    const atEnd = audio.ended || (end !== null && audio.currentTime >= end - END_TOLERANCE_S);
    const outside = end !== null && (audio.currentTime < start - END_TOLERANCE_S || atEnd);
    const restart = fromStart || atEnd || outside;
    if (restart) audio.currentTime = start;

    // Every restart after the first play of this sentence counts as a replay.
    if (restart && startedOnceRef.current && sid) {
      setReplayCount((n) => n + 1);
      if (m) incrementCounter(lid, m, sid, 'replays');
    }
    startedOnceRef.current = true;

    audio.playbackRate = rate;
    setError(null);
    setIsLoading(true);
    audio.play().catch((err: unknown) => {
      setIsLoading(false);
      // AbortError = interrupted by pause()/src change; NotAllowedError = autoplay blocked.
      if (err instanceof DOMException && (err.name === 'AbortError' || err.name === 'NotAllowedError')) return;
      setError('Không phát được audio.');
    });
  }, []);

  // Load the sentence (or the lesson audio for segment playback) when it changes.
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    audio.pause();
    stopRaf();
    startedOnceRef.current = false;
    setReplayCount(0);
    setError(null);
    setIsPlaying(false);
    setIsLoading(false);

    if (!src) {
      audio.removeAttribute('src');
      return;
    }
    const absolute = new URL(src, window.location.href).href;
    if (audio.src !== absolute) {
      audio.src = src;
      audio.load();
    }
    audio.currentTime = startS;
    if (autoPlay) startPlayback(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-run only when the sentence/source changes
  }, [src, sentenceId, startS]);

  // Apply rate changes immediately.
  useEffect(() => {
    if (audioRef.current) audioRef.current.playbackRate = playbackRate;
  }, [playbackRate]);

  const play = useCallback(() => startPlayback(false), [startPlayback]);
  const replay = useCallback(() => startPlayback(true), [startPlayback]);
  const pause = useCallback(() => audioRef.current?.pause(), []);
  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) startPlayback(false);
    else audio.pause();
  }, [startPlayback]);

  const setPlaybackRate = useCallback((rate: number) => setStoredRate(clampRate(rate)), [setStoredRate]);

  return {
    available: source !== null,
    source,
    isPlaying,
    isLoading,
    error,
    playbackRate,
    setPlaybackRate,
    play,
    pause,
    toggle,
    replay,
    replayCount,
  };
}
