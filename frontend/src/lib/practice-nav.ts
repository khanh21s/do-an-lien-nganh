import type { AttemptSummary, PracticeMode, SentenceResponse } from './types';

type NavSentence = Pick<SentenceResponse, 'id' | 'sentenceOrder'>;
type NavAttempt = Pick<AttemptSummary, 'sentenceId' | 'mode'>;

export interface SentenceNeighbors<S extends NavSentence> {
  current: S | null;
  prev: S | null;
  next: S | null;
  /** 0-based position in `sentenceOrder` order; -1 when `id` is not found. */
  index: number;
  total: number;
  isFirst: boolean;
  isLast: boolean;
}

/** Sentences sorted by `sentenceOrder` (stable; does not mutate the input). */
export function sortSentences<S extends NavSentence>(sentences: readonly S[]): S[] {
  return [...sentences].sort((a, b) => a.sentenceOrder - b.sentenceOrder);
}

/** Prev/next sentence around `id` for the "Câu n/N" header (DIC-02). */
export function getNeighbors<S extends NavSentence>(sentences: readonly S[], id: string): SentenceNeighbors<S> {
  const sorted = sortSentences(sentences);
  const index = sorted.findIndex((s) => s.id === id);
  const total = sorted.length;
  if (index === -1) {
    return { current: null, prev: null, next: null, index: -1, total, isFirst: false, isLast: false };
  }
  return {
    current: sorted[index],
    prev: index > 0 ? sorted[index - 1] : null,
    next: index < total - 1 ? sorted[index + 1] : null,
    index,
    total,
    isFirst: index === 0,
    isLast: index === total - 1,
  };
}

/**
 * First sentence (in `sentenceOrder`) without any attempt in `mode`.
 * Returns `null` when every sentence already has an attempt (or the list is empty);
 * callers typically fall back to `sortSentences(sentences)[0]`.
 */
export function firstUndoneSentence<S extends NavSentence>(
  sentences: readonly S[],
  attempts: readonly NavAttempt[],
  mode: PracticeMode
): S | null {
  const done = new Set(attempts.filter((a) => a.mode === mode).map((a) => a.sentenceId));
  return sortSentences(sentences).find((s) => !done.has(s.id)) ?? null;
}

/** Route of a practice sentence, e.g. `/lessons/L/dictation/S`. */
export function practiceSentencePath(lessonId: string, mode: PracticeMode, sentenceId: string): string {
  return `/lessons/${lessonId}/${mode}/${sentenceId}`;
}

/** Result screen route (DIC-10). */
export function practiceResultPath(lessonId: string, mode: PracticeMode): string {
  return `/lessons/${lessonId}/result?mode=${mode}`;
}
