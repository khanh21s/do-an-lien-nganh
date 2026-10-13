// Spaced-repetition helpers for the vocabulary flashcards (T10).
// `nextSrsState` implements exactly the SM-2 variant of docs/architecture.md §4.6 so the interval
// labels on the rating buttons match what vocabulary-service will schedule.

export type SrsRatingName = 'again' | 'hard' | 'good' | 'easy';

export interface SrsRating {
  name: SrsRatingName;
  /** Keyboard key (1–4). */
  key: '1' | '2' | '3' | '4';
  label: string;
  labelEn: string;
  /** SM-2 quality sent to `POST /vocabs/user/review`. */
  quality: 1 | 3 | 4 | 5;
}

/** Again = 1, Hard = 3, Good = 4, Easy = 5 (architecture §4.6), keys 1–4. */
export const SRS_RATINGS: readonly SrsRating[] = [
  { name: 'again', key: '1', label: 'Học lại', labelEn: 'Again', quality: 1 },
  { name: 'hard', key: '2', label: 'Khó', labelEn: 'Hard', quality: 3 },
  { name: 'good', key: '3', label: 'Tốt', labelEn: 'Good', quality: 4 },
  { name: 'easy', key: '4', label: 'Dễ', labelEn: 'Easy', quality: 5 },
];

export const QUALITY_AGAIN = 1;
export const QUALITY_GOOD = 4;

export function ratingByQuality(quality: number): SrsRating | undefined {
  return SRS_RATINGS.find((r) => r.quality === quality);
}

export function ratingByKey(key: string): SrsRating | undefined {
  return SRS_RATINGS.find((r) => r.key === key);
}

export interface SrsState {
  repetitions: number;
  intervalDays: number;
  easeFactor: number;
}

/** State of a card that has just been added to the notebook (vocabulary-service defaults). */
export const NEW_CARD_STATE: SrsState = { repetitions: 0, intervalDays: 1, easeFactor: 2.5 };

export const MIN_EASE_FACTOR = 1.3;

/** Round to 2 decimals, half-up (matches the backend's BigDecimal HALF_UP). */
function round2(x: number): number {
  return Math.round(x * 100 + 1e-9) / 100;
}

/** SM-2 step (architecture §4.6). */
export function nextSrsState(state: SrsState, quality: number): SrsState {
  const n = state.repetitions;
  const ef = state.easeFactor;
  let interval: number;
  let repetitions: number;
  if (quality >= 3) {
    if (n <= 0) interval = 1;
    else if (n === 1) interval = 6;
    else interval = Math.round(state.intervalDays * ef);
    repetitions = n + 1;
  } else {
    repetitions = 0;
    interval = 1;
  }
  const d = 5 - quality;
  const easeFactor = Math.max(MIN_EASE_FACTOR, round2(ef + (0.1 - d * (0.08 + d * 0.02))));
  return { repetitions, intervalDays: interval, easeFactor };
}

/** Interval (days) the card will get if rated with `quality`. `null` state = card not in the notebook yet. */
export function previewInterval(state: SrsState | null, quality: number): number {
  return nextSrsState(state ?? NEW_CARD_STATE, quality).intervalDays;
}

/** "1 ngày", "25 ngày" – exact day count so it can be compared with the backend `interval`. */
export function formatInterval(days: number): string {
  return `${days} ngày`;
}

// ---------- Daily limit (VOC-10) ----------
export const DAILY_LIMIT_MIN = 1;
export const DAILY_LIMIT_MAX = 1000;
export const DEFAULT_DAILY_LIMIT = 100;

export function clampDailyLimit(value: unknown): number {
  const n = typeof value === 'number' ? value : Number(value);
  if (!Number.isFinite(n)) return DEFAULT_DAILY_LIMIT;
  return Math.min(DAILY_LIMIT_MAX, Math.max(DAILY_LIMIT_MIN, Math.round(n)));
}

// ---------- Typing mode (VOC-09) ----------
function normalizeAnswer(s: string): string {
  return s
    .toLowerCase()
    .replace(/[’‘`]/g, "'")
    .replace(/[^\p{L}\p{N}'\- ]+/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** Case/whitespace/punctuation-insensitive comparison of the typed word. */
export function isTypedAnswerCorrect(input: string, word: string): boolean {
  const a = normalizeAnswer(input);
  return a.length > 0 && a === normalizeAnswer(word);
}

function escapeRegExp(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Hides the target word (and simple inflections like "apples", "applied") in an example sentence.
 * Each hidden occurrence becomes underscores of the same length.
 */
export function maskWord(sentence: string, word: string): string {
  const w = word.trim();
  if (!w) return sentence;
  const stem = w.length > 3 && /e$/i.test(w) ? w.slice(0, -1) : w;
  const re = new RegExp(`\\b${escapeRegExp(stem)}[a-z]{0,3}\\b`, 'gi');
  return sentence.replace(re, (m) => '_'.repeat(Math.max(3, m.length)));
}

/** Suggested rating after a typing check: correct → Good, wrong → Again. */
export function suggestedQuality(correct: boolean): number {
  return correct ? QUALITY_GOOD : QUALITY_AGAIN;
}

// ---------- Session summary (VOC-06) ----------
export type RatingDistribution = Record<SrsRatingName, number>;

export function ratingDistribution(qualities: number[]): RatingDistribution {
  const dist: RatingDistribution = { again: 0, hard: 0, good: 0, easy: 0 };
  for (const q of qualities) {
    const r = ratingByQuality(q);
    if (r) dist[r.name] += 1;
  }
  return dist;
}

/** Earliest ISO timestamp in the list (or null). */
export function earliestDate(dates: (string | null | undefined)[]): string | null {
  let best: string | null = null;
  let bestTime = Infinity;
  for (const d of dates) {
    if (!d) continue;
    const t = Date.parse(d);
    if (!Number.isNaN(t) && t < bestTime) {
      bestTime = t;
      best = d;
    }
  }
  return best;
}
