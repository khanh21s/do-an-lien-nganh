import type { Difficulty } from './types';

/**
 * Pure dictation helpers (DIC-06, DIC-08). No React / DOM here so everything is unit-tested.
 *
 * Grading happens on the BE (`DictationScorer`): it lower-cases both texts, turns ASCII punctuation
 * into spaces, splits on whitespace and compares word *i* with typed word *i*. The FE therefore
 * always sends exactly one token per sentence word so positions stay aligned.
 */

/** One whitespace-separated chunk of the sentence: punctuation stays outside the blank. */
export interface DictationToken {
  /** Leading punctuation, e.g. `"` or `(`. */
  prefix: string;
  /** The word itself ("don't", "well-known", "3.5"); empty for punctuation-only chunks like "—". */
  word: string;
  /** Trailing punctuation, e.g. `,` `.` `?"`. */
  suffix: string;
  /** Index into the list of words (tokens with a non-empty `word`); -1 for punctuation-only chunks. */
  wordIndex: number;
}

/** What the input area shows for every word. */
export type WordCell = 'prefilled' | 'blank';

/**
 * Sent at a position whose word was revealed by a hint or left empty (DIC-08: "từ lộ được tính sai").
 * An empty string would collapse on the BE (`split("\\s+")`) and shift every following word, so a
 * non-letter, non-punctuation token is used instead. Two characters guarantee it never falls within
 * the BE typo tolerance (`levenshtein ≤ max(1, len/4)`) of any real word.
 */
export const MISSING_WORD_TOKEN = '∅∅';

const TOKEN_RE = /^([^\p{L}\p{N}]*)(.*?)([^\p{L}\p{N}]*)$/su;

/** Splits a sentence on whitespace, keeping punctuation outside the word (DIC-06). */
export function tokenize(text: string): DictationToken[] {
  const chunks = text.trim().split(/\s+/).filter(Boolean);
  let wordIndex = 0;
  return chunks.map((chunk) => {
    const m = TOKEN_RE.exec(chunk);
    const prefix = m?.[1] ?? '';
    const word = m?.[2] ?? '';
    const suffix = m?.[3] ?? '';
    if (!word) {
      // Punctuation-only chunk ("—", "..."): display only, never a blank.
      return { prefix: chunk, word: '', suffix: '', wordIndex: -1 };
    }
    return { prefix, word, suffix, wordIndex: wordIndex++ };
  });
}

/** Words of a tokenized sentence, in order. */
export function wordsOf(tokens: readonly DictationToken[]): string[] {
  return tokens.filter((t) => t.wordIndex >= 0).map((t) => t.word);
}

/**
 * Which words are shown pre-filled (DIC-06). Purely positional, so the same sentence always gets the
 * same blanks (reload-stable, "deterministic theo sentenceId"):
 * - Easy: pre-fill even positions (0, 2, 4, …) → ~50% blanks
 * - Normal: pre-fill every 4th word (0, 4, 8, …) → ~75% blanks
 * - Hard: no pre-fill (the UI uses one free textarea)
 * At least one word is always a blank (a 1-word sentence in Easy/Normal would otherwise be solved).
 */
export function blankMask(wordCount: number, difficulty: Difficulty): WordCell[] {
  const mask: WordCell[] = Array.from({ length: wordCount }, (_, i) => {
    if (difficulty === 'Easy') return i % 2 === 0 ? 'prefilled' : 'blank';
    if (difficulty === 'Normal') return i % 4 === 0 ? 'prefilled' : 'blank';
    return 'blank';
  });
  if (wordCount > 0 && !mask.includes('blank')) mask[wordCount - 1] = 'blank';
  return mask;
}

export function countBlanks(mask: readonly WordCell[]): number {
  return mask.filter((c) => c === 'blank').length;
}

/** Lower-case, drop punctuation — mirrors the BE normalisation closely enough for local checks. */
export function normalizeWord(word: string): string {
  return word
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '')
    .trim();
}

/** A typed box value as one token: whitespace removed (Space moves to the next box). */
function cleanTyped(value: string | undefined): string {
  return (value ?? '').replace(/\s+/g, '');
}

// ASCII punctuation, exactly what the BE's `\p{Punct}` strips.
const ASCII_PUNCT = /[!-/:-@[-`{-~]/g;

/** How the BE splits a piece of text into graded words (ASCII punctuation → space). */
export function beSplit(text: string): string[] {
  return text.replace(ASCII_PUNCT, ' ').split(/\s+/).filter(Boolean);
}

/**
 * Makes a typed word occupy exactly `parts` BE positions (e.g. "don't" is graded as "don" + "t"):
 * missing parts are padded with `MISSING_WORD_TOKEN`, extra parts are glued onto the last one.
 * This keeps every following word aligned with the reference.
 */
function alignToParts(typed: string, parts: number): string {
  const got = beSplit(typed);
  if (got.length === parts) return typed;
  if (got.length === 0) return Array(parts).fill(MISSING_WORD_TOKEN).join(' ');
  if (got.length < parts) return [...got, ...Array(parts - got.length).fill(MISSING_WORD_TOKEN)].join(' ');
  return [...got.slice(0, parts - 1), got.slice(parts - 1).join('')].join(' ');
}

/**
 * Builds `userInput` for Easy/Normal (DIC-06/DIC-08): the sentence with pre-filled words kept and
 * blanks replaced by what was typed (punctuation chunks are sent verbatim so the BE sees the same
 * word positions). Revealed or empty blanks become `MISSING_WORD_TOKEN` (graded as wrong).
 * `mask`, `typed` and `revealed` are indexed by `DictationToken.wordIndex`.
 */
export function composeUserInput(
  tokens: readonly DictationToken[],
  mask: readonly WordCell[],
  typed: readonly (string | undefined)[],
  revealed: ReadonlySet<number>
): string {
  return tokens
    .map((t) => {
      if (t.wordIndex < 0) return t.prefix;
      const i = t.wordIndex;
      let core: string;
      if (mask[i] === 'prefilled') {
        core = t.word;
      } else {
        const parts = Math.max(1, beSplit(t.word).length);
        const value = revealed.has(i) ? '' : cleanTyped(typed[i]);
        core = alignToParts(value, parts);
      }
      return `${t.prefix}${core}${t.suffix}`;
    })
    .join(' ');
}

/**
 * BE word positions covered by each sentence word (indexed by `wordIndex`), used to colour the
 * blanks after grading: "well-known," → two positions, "—" → its own position, "..." → none.
 */
export function bePositionsByWord(tokens: readonly DictationToken[]): number[][] {
  const out: number[][] = [];
  let p = 0;
  for (const t of tokens) {
    const count = beSplit(`${t.prefix}${t.word}${t.suffix}`).length;
    if (t.wordIndex >= 0) out[t.wordIndex] = Array.from({ length: count }, (_, k) => p + k);
    p += count;
  }
  return out;
}

/** Per-word correctness from the BE `wordScores` (`null` when unknown). */
export function wordCorrectness(
  tokens: readonly DictationToken[],
  wordScores: readonly { position: number; isCorrect: boolean }[]
): (boolean | null)[] {
  const byPos = new Map(wordScores.map((w) => [w.position, w.isCorrect]));
  return bePositionsByWord(tokens).map((positions) => {
    if (!positions.length) return null;
    const flags = positions.map((pos) => byPos.get(pos));
    if (flags.some((f) => f === undefined)) return null;
    return flags.every(Boolean);
  });
}

/** Splits free text (Hard mode textarea) into word tokens. */
export function splitTyped(text: string): string[] {
  return text.trim().split(/\s+/).filter(Boolean);
}

/**
 * Hard mode: the textarea is sent as typed, except that revealed positions are forced to
 * `MISSING_WORD_TOKEN` (and missing words before them are padded) so hints still count as wrong.
 */
export function composeHardInput(text: string, wordCount: number, revealed: ReadonlySet<number>): string {
  const tokens = splitTyped(text);
  if (revealed.size === 0) return tokens.join(' ');
  const maxRevealed = Math.max(...Array.from(revealed));
  const length = Math.max(tokens.length, Math.min(wordCount, maxRevealed + 1));
  const out: string[] = [];
  for (let i = 0; i < length; i++) {
    out.push(revealed.has(i) ? MISSING_WORD_TOKEN : tokens[i] || MISSING_WORD_TOKEN);
  }
  return out.join(' ');
}

/**
 * Next word to reveal with a hint (Ctrl+H, DIC-08): `preferred` if it is a blank that is not yet
 * revealed, otherwise the first blank that is not revealed and not already typed correctly.
 * Returns -1 when there is nothing left to reveal.
 */
export function nextHintIndex(
  words: readonly string[],
  mask: readonly WordCell[],
  typed: readonly (string | undefined)[],
  revealed: ReadonlySet<number>,
  preferred?: number | null
): number {
  const isCandidate = (i: number) => mask[i] === 'blank' && !revealed.has(i);
  if (preferred != null && preferred >= 0 && preferred < words.length && isCandidate(preferred)) {
    return preferred;
  }
  for (let i = 0; i < words.length; i++) {
    if (!isCandidate(i)) continue;
    if (normalizeWord(typed[i] ?? '') === normalizeWord(words[i])) continue;
    return i;
  }
  return -1;
}

/** All blanks not yet revealed ("Hiện tất cả"). */
export function unrevealedBlanks(mask: readonly WordCell[], revealed: ReadonlySet<number>): number[] {
  const out: number[] = [];
  mask.forEach((c, i) => {
    if (c === 'blank' && !revealed.has(i)) out.push(i);
  });
  return out;
}

/** True when `difficulty` is a valid value (used to sanitise localStorage). */
export function isDifficulty(value: unknown): value is Difficulty {
  return value === 'Easy' || value === 'Normal' || value === 'Hard';
}

/** Score label for the result screen (DIC-10). */
export function scoreLabel(score: number): { label: string; tone: 'green' | 'brand' | 'amber' } {
  if (score >= 90) return { label: 'Xuất sắc', tone: 'green' };
  if (score >= 70) return { label: 'Tốt', tone: 'brand' };
  return { label: 'Cần cải thiện', tone: 'amber' };
}

/** Max score per difficulty (architecture §4.7: score = accuracy × 0.6 / 0.8 / 1.0). */
export const DIFFICULTY_WEIGHTS: Record<Difficulty, number> = { Easy: 0.6, Normal: 0.8, Hard: 1.0 };
