import type { AttemptSummary, PracticeMode, SentenceResponse, ShadowingWordScore } from '@/lib/types';

/**
 * Pure aggregation helpers for the lesson page (LES-05) and the result screen (DIC-10, SHA-07).
 */

type Attempt = Pick<AttemptSummary, 'attemptId' | 'sentenceId' | 'mode' | 'score' | 'accuracy' | 'createdAt'>;

export interface SentenceProgress {
  attempts: number;
  bestScore: number;
  bestAccuracy: number;
  /** Attempts with accuracy < 100. */
  wrongAttempts: number;
  latestAttemptId: string;
  latestAt: string;
}

/** Per-sentence progress in one mode, keyed by sentenceId. */
export function progressBySentence(attempts: readonly Attempt[], mode: PracticeMode): Map<string, SentenceProgress> {
  const map = new Map<string, SentenceProgress>();
  for (const a of attempts) {
    if (a.mode !== mode) continue;
    const prev = map.get(a.sentenceId);
    if (!prev) {
      map.set(a.sentenceId, {
        attempts: 1,
        bestScore: a.score,
        bestAccuracy: a.accuracy,
        wrongAttempts: a.accuracy < 100 ? 1 : 0,
        latestAttemptId: a.attemptId,
        latestAt: a.createdAt,
      });
      continue;
    }
    const newer = a.createdAt > prev.latestAt;
    map.set(a.sentenceId, {
      attempts: prev.attempts + 1,
      bestScore: Math.max(prev.bestScore, a.score),
      bestAccuracy: Math.max(prev.bestAccuracy, a.accuracy),
      wrongAttempts: prev.wrongAttempts + (a.accuracy < 100 ? 1 : 0),
      latestAttemptId: newer ? a.attemptId : prev.latestAttemptId,
      latestAt: newer ? a.createdAt : prev.latestAt,
    });
  }
  return map;
}

export interface MistakeRow<S> {
  sentence: S;
  wrongAttempts: number;
  bestAccuracy: number;
}

export interface LessonResultSummary<S> {
  /** Sentences of the lesson with ≥ 1 attempt in this mode. */
  done: number;
  total: number;
  /** Average of the best score per done sentence (0 when nothing is done). */
  finalScore: number;
  /** Average of the best accuracy per done sentence. */
  avgAccuracy: number;
  /** Sentences with the most wrong attempts (only those with ≥ 1), worst first. */
  mostMistakes: MistakeRow<S>[];
}

type ResultSentence = Pick<SentenceResponse, 'id' | 'sentenceOrder'>;

export function summarizeLessonResult<S extends ResultSentence>(
  sentences: readonly S[],
  attempts: readonly Attempt[],
  mode: PracticeMode,
  mistakesLimit = 5
): LessonResultSummary<S> {
  const progress = progressBySentence(attempts, mode);
  const doneRows = sentences
    .map((s) => ({ sentence: s, p: progress.get(s.id) }))
    .filter((r): r is { sentence: S; p: SentenceProgress } => !!r.p);
  const n = doneRows.length;
  const avg = (f: (p: SentenceProgress) => number) => (n ? doneRows.reduce((acc, r) => acc + f(r.p), 0) / n : 0);

  const mostMistakes = doneRows
    .filter((r) => r.p.wrongAttempts > 0)
    .sort(
      (a, b) =>
        b.p.wrongAttempts - a.p.wrongAttempts ||
        a.p.bestAccuracy - b.p.bestAccuracy ||
        a.sentence.sentenceOrder - b.sentence.sentenceOrder
    )
    .slice(0, mistakesLimit)
    .map((r) => ({ sentence: r.sentence, wrongAttempts: r.p.wrongAttempts, bestAccuracy: r.p.bestAccuracy }));

  return {
    done: n,
    total: sentences.length,
    finalScore: avg((p) => p.bestScore),
    avgAccuracy: avg((p) => p.bestAccuracy),
    mostMistakes,
  };
}

export interface WeakWord {
  word: string;
  avgAccuracy: number;
  count: number;
}

/**
 * SHA-07: words with the lowest average pronunciation accuracy across shadowing attempts.
 * Words are compared case-insensitively without surrounding punctuation; only words below
 * `threshold` are returned.
 */
export function weakestWords(
  wordScoreLists: readonly (readonly ShadowingWordScore[] | null | undefined)[],
  limit = 8,
  threshold = 80
): WeakWord[] {
  const acc = new Map<string, { display: string; sum: number; count: number }>();
  for (const list of wordScoreLists) {
    for (const w of list ?? []) {
      if (!w || typeof w.wordAccuracy !== 'number' || !w.word) continue;
      const key = w.word.toLowerCase().replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
      if (!key) continue;
      const prev = acc.get(key) ?? { display: key, sum: 0, count: 0 };
      acc.set(key, { display: prev.display, sum: prev.sum + w.wordAccuracy, count: prev.count + 1 });
    }
  }
  return Array.from(acc.values())
    .map((v) => ({ word: v.display, avgAccuracy: v.sum / v.count, count: v.count }))
    .filter((w) => w.avgAccuracy < threshold)
    .sort((a, b) => a.avgAccuracy - b.avgAccuracy || b.count - a.count || a.word.localeCompare(b.word))
    .slice(0, limit);
}

/** "12 phút 05 giây" style duration for the result screen. */
export function formatDuration(ms: number): string {
  const totalSec = Math.max(0, Math.round(ms / 1000));
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h} giờ ${m} phút`;
  if (m > 0) return `${m} phút ${String(s).padStart(2, '0')} giây`;
  return `${s} giây`;
}

/** One decimal, without a trailing ".0". */
export function formatNumber(value: number, digits = 1): string {
  if (!Number.isFinite(value)) return '0';
  return Number(value.toFixed(digits)).toString();
}
