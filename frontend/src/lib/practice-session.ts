import { storageKeys } from './storageKeys';
import type { PracticeMode } from './types';

/**
 * Per-lesson practice counters kept in sessionStorage (architecture §6.6, DIC-08/DIC-10):
 * `parroto:session:{lessonId}:{mode}` = `{startedAt, sentences: {[sentenceId]: {submits, replays, hints, ms}}}`.
 */
export interface SentenceSessionStats {
  submits: number;
  replays: number;
  hints: number;
  /** Time spent on the sentence, in milliseconds. */
  ms: number;
}

export interface PracticeSession {
  /** Epoch ms. */
  startedAt: number;
  sentences: Record<string, SentenceSessionStats>;
}

export type SessionCounter = 'submits' | 'replays' | 'hints';

const EMPTY_STATS: SentenceSessionStats = { submits: 0, replays: 0, hints: 0, ms: 0 };

function storage(): Storage | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.sessionStorage;
  } catch {
    return null;
  }
}

export function readSession(lessonId: string, mode: PracticeMode): PracticeSession | null {
  const s = storage();
  if (!s) return null;
  try {
    const raw = s.getItem(storageKeys.practiceSession(lessonId, mode));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PracticeSession;
    if (typeof parsed?.startedAt !== 'number' || typeof parsed.sentences !== 'object' || !parsed.sentences) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

function writeSession(lessonId: string, mode: PracticeMode, session: PracticeSession): void {
  const s = storage();
  if (!s) return;
  try {
    s.setItem(storageKeys.practiceSession(lessonId, mode), JSON.stringify(session));
  } catch {
    // ignore quota/privacy errors
  }
}

/** Returns the existing session or creates (and stores) a new one. */
export function ensureSession(lessonId: string, mode: PracticeMode): PracticeSession {
  const existing = readSession(lessonId, mode);
  if (existing) return existing;
  const created: PracticeSession = { startedAt: Date.now(), sentences: {} };
  writeSession(lessonId, mode, created);
  return created;
}

/** Applies `fn` to the stats of one sentence and persists the result. */
export function updateSentenceStats(
  lessonId: string,
  mode: PracticeMode,
  sentenceId: string,
  fn: (prev: SentenceSessionStats) => SentenceSessionStats
): PracticeSession {
  const session = ensureSession(lessonId, mode);
  const prev = session.sentences[sentenceId] ?? EMPTY_STATS;
  const next: PracticeSession = {
    ...session,
    sentences: { ...session.sentences, [sentenceId]: fn({ ...prev }) },
  };
  writeSession(lessonId, mode, next);
  return next;
}

export function incrementCounter(
  lessonId: string,
  mode: PracticeMode,
  sentenceId: string,
  counter: SessionCounter,
  by = 1
): PracticeSession {
  return updateSentenceStats(lessonId, mode, sentenceId, (s) => ({ ...s, [counter]: s[counter] + by }));
}

export function addTimeSpent(lessonId: string, mode: PracticeMode, sentenceId: string, ms: number): PracticeSession {
  return updateSentenceStats(lessonId, mode, sentenceId, (s) => ({ ...s, ms: s.ms + Math.max(0, ms) }));
}

export function clearSession(lessonId: string, mode: PracticeMode): void {
  const s = storage();
  if (!s) return;
  try {
    s.removeItem(storageKeys.practiceSession(lessonId, mode));
  } catch {
    // ignore
  }
}

export interface SessionSummary {
  sentencesTouched: number;
  totalSubmits: number;
  totalReplays: number;
  totalHints: number;
  totalMs: number;
  avgSubmits: number;
  avgReplays: number;
  avgHints: number;
}

/** Aggregates a session for the result screen (DIC-10). Averages are per touched sentence. */
export function summarizeSession(session: PracticeSession | null): SessionSummary {
  const list = session ? Object.values(session.sentences) : [];
  const n = list.length;
  const sum = (k: keyof SentenceSessionStats) => list.reduce((acc, s) => acc + (s[k] ?? 0), 0);
  const totalSubmits = sum('submits');
  const totalReplays = sum('replays');
  const totalHints = sum('hints');
  return {
    sentencesTouched: n,
    totalSubmits,
    totalReplays,
    totalHints,
    totalMs: sum('ms'),
    avgSubmits: n ? totalSubmits / n : 0,
    avgReplays: n ? totalReplays / n : 0,
    avgHints: n ? totalHints / n : 0,
  };
}
