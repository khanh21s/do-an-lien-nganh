// Flashcard rating helpers (T10, P2-anki).
// Scheduling happens only in vocabulary-service (Anki SM-2, docs/architecture.md §4.6): every
// card in a study session carries the four button labels computed by the server
// (`StudyCardItem.previews`), so this file has no copy of the formula.

import type { RatingPreview, StudyQueue } from './types';

export type SrsRatingName = 'again' | 'hard' | 'good' | 'easy';
export type SrsRatingValue = 1 | 2 | 3 | 4;

export interface SrsRating {
  name: SrsRatingName;
  /** Keyboard key (1–4). */
  key: '1' | '2' | '3' | '4';
  label: string;
  labelEn: string;
  /** Anki button number sent as `rating` to `POST /vocabs/user/review`. */
  rating: SrsRatingValue;
}

/** Again = 1, Hard = 2, Good = 3, Easy = 4 (Anki), keys 1–4. */
export const SRS_RATINGS: readonly SrsRating[] = [
  { name: 'again', key: '1', label: 'Học lại', labelEn: 'Again', rating: 1 },
  { name: 'hard', key: '2', label: 'Khó', labelEn: 'Hard', rating: 2 },
  { name: 'good', key: '3', label: 'Tốt', labelEn: 'Good', rating: 3 },
  { name: 'easy', key: '4', label: 'Dễ', labelEn: 'Easy', rating: 4 },
];

export const RATING_AGAIN: SrsRatingValue = 1;
export const RATING_GOOD: SrsRatingValue = 3;

export function ratingByValue(value: number): SrsRating | undefined {
  return SRS_RATINGS.find((r) => r.rating === value);
}

export function ratingByKey(key: string): SrsRating | undefined {
  return SRS_RATINGS.find((r) => r.key === key);
}

/** The server's label for a button ("<1 phút", "10 phút", "4 ngày"…), or undefined when not known. */
export function previewLabel(previews: readonly RatingPreview[] | undefined, rating: number): string | undefined {
  return previews?.find((p) => p.rating === rating)?.label;
}

// ---------- Session counters (Anki: new blue, learning red, review green) ----------
export const COUNTER_ORDER: readonly { queue: StudyQueue; label: string }[] = [
  { queue: 'new', label: 'Mới' },
  { queue: 'learning', label: 'Đang học' },
  { queue: 'review', label: 'Ôn' },
];

/**
 * "Đã xong hôm nay" text for the next due card: "lúc 14:05" today, "ngày mai lúc 04:00",
 * otherwise "lúc 04:00 20/10". `now` is injectable for tests.
 */
export function formatNextDue(iso: string | null | undefined, now: Date = new Date()): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  const pad = (n: number) => String(n).padStart(2, '0');
  // Built by hand: ICU data differs between browsers/Node ("20/10" vs "20-10").
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
  const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diffDays = Math.round((day(d) - day(now)) / 86_400_000);
  if (diffDays <= 0) return `hôm nay lúc ${time}`;
  if (diffDays === 1) return `ngày mai lúc ${time}`;
  return `lúc ${time} ngày ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`;
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
export function suggestedRating(correct: boolean): SrsRatingValue {
  return correct ? RATING_GOOD : RATING_AGAIN;
}

// ---------- Session summary (VOC-06) ----------
export type RatingDistribution = Record<SrsRatingName, number>;

export function ratingDistribution(ratings: number[]): RatingDistribution {
  const dist: RatingDistribution = { again: 0, hard: 0, good: 0, easy: 0 };
  for (const v of ratings) {
    const r = ratingByValue(v);
    if (r) dist[r.name] += 1;
  }
  return dist;
}
