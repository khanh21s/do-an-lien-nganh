import type { CreatePersonalWordInput } from '@/lib/types';

/**
 * Pure helpers for `AddWordModal` (VOC-NB-ADD). No React here so everything is unit-tested.
 * Limits mirror `CreatePersonalWordRequest` on the vocabulary-service (T13 contract).
 */

export interface AddWordFormValues {
  word: string;
  meaning: string;
  ipa: string;
  partOfSpeech: string;
  example: string;
  exampleVi: string;
}

export type AddWordField = keyof AddWordFormValues;

/** Prefill from dictation, or an existing `LearnedWord` when editing (extra fields are ignored). */
export type AddWordPrefill = Partial<Record<AddWordField, string | null | undefined>>;

export const ADD_WORD_LIMITS: Record<AddWordField, number> = {
  word: 100,
  meaning: 500,
  ipa: 100,
  partOfSpeech: 50,
  example: 500,
  exampleVi: 500,
};

export const EMPTY_ADD_WORD_FORM: AddWordFormValues = {
  word: '',
  meaning: '',
  ipa: '',
  partOfSpeech: '',
  example: '',
  exampleVi: '',
};

/** Builds form values from a partial prefill (dictation) or an existing notebook word (edit). */
export function initialFormValues(prefill?: AddWordPrefill | null): AddWordFormValues {
  const v = (k: AddWordField) => prefill?.[k] ?? '';
  return {
    word: v('word'),
    meaning: v('meaning'),
    ipa: v('ipa'),
    partOfSpeech: v('partOfSpeech'),
    example: v('example'),
    exampleVi: v('exampleVi'),
  };
}

/** Trims and collapses inner whitespace; an empty result becomes `null` (BE: "chuỗi rỗng → null"). */
export function cleanOptional(value: string | null | undefined): string | null {
  const s = (value ?? '').replace(/\s+/g, ' ').trim();
  return s === '' ? null : s;
}

/** Strips the slashes / brackets users often type around IPA: `/təˈdeɪ/` → `təˈdeɪ`. */
export function cleanIpa(value: string | null | undefined): string | null {
  const s = cleanOptional(value);
  if (!s) return null;
  return cleanOptional(s.replace(/^[/[]+|[/\]]+$/g, ''));
}

/** Request body: required fields trimmed, optional ones trimmed or `null`. */
export function normalizeAddWordForm(values: AddWordFormValues): CreatePersonalWordInput {
  return {
    word: cleanOptional(values.word) ?? '',
    meaning: cleanOptional(values.meaning) ?? '',
    ipa: cleanIpa(values.ipa),
    partOfSpeech: cleanOptional(values.partOfSpeech),
    example: cleanOptional(values.example),
    exampleVi: cleanOptional(values.exampleVi),
  };
}

/** Client-side validation (Vietnamese messages); an empty object means the form is valid. */
export function validateAddWordForm(values: AddWordFormValues): Partial<Record<AddWordField, string>> {
  const input = normalizeAddWordForm(values);
  const errors: Partial<Record<AddWordField, string>> = {};
  if (!input.word) errors.word = 'Vui lòng nhập từ.';
  if (!input.meaning) errors.meaning = 'Vui lòng nhập nghĩa.';
  (Object.keys(ADD_WORD_LIMITS) as AddWordField[]).forEach((k) => {
    const value = input[k];
    if (!errors[k] && value && value.length > ADD_WORD_LIMITS[k]) {
      errors[k] = `Tối đa ${ADD_WORD_LIMITS[k]} ký tự.`;
    }
  });
  return errors;
}

/**
 * The word to save when a learner clicks a word of a sentence: surrounding punctuation is removed
 * (`today?` → `today`, `"Hello,` → `Hello`) while inner apostrophes / hyphens stay (`don't`,
 * `well-known`). Curly apostrophes become `'`. Returns '' when nothing word-like is left.
 */
export function cleanClickedWord(raw: string): string {
  const s = raw.trim().replace(/[‘’ʼ]/g, "'");
  const m = /^[^\p{L}\p{N}]*(.*?)[^\p{L}\p{N}]*$/su.exec(s);
  return m?.[1] ?? '';
}

/** Words of a sentence as `{ text, word }` chunks: `text` keeps punctuation for display. */
export function clickableWords(sentence: string): { text: string; word: string }[] {
  return sentence
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((text) => ({ text, word: cleanClickedWord(text) }));
}
