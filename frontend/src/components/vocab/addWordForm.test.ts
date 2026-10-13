import { describe, expect, it } from 'vitest';
import {
  EMPTY_ADD_WORD_FORM,
  cleanClickedWord,
  cleanIpa,
  cleanOptional,
  clickableWords,
  initialFormValues,
  normalizeAddWordForm,
  validateAddWordForm,
} from './addWordForm';

describe('cleanOptional', () => {
  it('trims, collapses whitespace and maps empty to null', () => {
    expect(cleanOptional('  hello   world ')).toBe('hello world');
    expect(cleanOptional('   ')).toBeNull();
    expect(cleanOptional('')).toBeNull();
    expect(cleanOptional(null)).toBeNull();
    expect(cleanOptional(undefined)).toBeNull();
  });
});

describe('cleanIpa', () => {
  it('strips surrounding slashes and brackets', () => {
    expect(cleanIpa(' /təˈdeɪ/ ')).toBe('təˈdeɪ');
    expect(cleanIpa('[ˈwɔːtə]')).toBe('ˈwɔːtə');
    expect(cleanIpa('təˈdeɪ')).toBe('təˈdeɪ');
    expect(cleanIpa('//')).toBeNull();
    expect(cleanIpa('')).toBeNull();
  });
});

describe('normalizeAddWordForm', () => {
  it('trims required fields and turns empty optional fields into null', () => {
    expect(
      normalizeAddWordForm({
        word: '  Today ',
        meaning: ' hôm nay ',
        ipa: '',
        partOfSpeech: '  ',
        example: ' What are you doing today? ',
        exampleVi: '',
      })
    ).toEqual({
      word: 'Today',
      meaning: 'hôm nay',
      ipa: null,
      partOfSpeech: null,
      example: 'What are you doing today?',
      exampleVi: null,
    });
  });
});

describe('validateAddWordForm', () => {
  it('requires word and meaning', () => {
    expect(validateAddWordForm(EMPTY_ADD_WORD_FORM)).toEqual({
      word: 'Vui lòng nhập từ.',
      meaning: 'Vui lòng nhập nghĩa.',
    });
    expect(validateAddWordForm({ ...EMPTY_ADD_WORD_FORM, word: '   ', meaning: 'x' })).toHaveProperty('word');
  });

  it('accepts a minimal valid form', () => {
    expect(validateAddWordForm({ ...EMPTY_ADD_WORD_FORM, word: 'today', meaning: 'hôm nay' })).toEqual({});
  });

  it('enforces the contract length limits', () => {
    const errors = validateAddWordForm({
      ...EMPTY_ADD_WORD_FORM,
      word: 'a'.repeat(101),
      meaning: 'b'.repeat(500),
      partOfSpeech: 'c'.repeat(51),
    });
    expect(errors.word).toBe('Tối đa 100 ký tự.');
    expect(errors.meaning).toBeUndefined();
    expect(errors.partOfSpeech).toBe('Tối đa 50 ký tự.');
  });
});

describe('initialFormValues', () => {
  it('fills missing / null fields with empty strings', () => {
    expect(initialFormValues({ word: 'today', example: 'Hi today.', ipa: null })).toEqual({
      ...EMPTY_ADD_WORD_FORM,
      word: 'today',
      example: 'Hi today.',
    });
    expect(initialFormValues()).toEqual(EMPTY_ADD_WORD_FORM);
  });
});

describe('cleanClickedWord', () => {
  it('removes surrounding punctuation', () => {
    expect(cleanClickedWord('today?')).toBe('today');
    expect(cleanClickedWord('"Hello,')).toBe('Hello');
    expect(cleanClickedWord('(yes).')).toBe('yes');
    expect(cleanClickedWord('end...')).toBe('end');
  });

  it('keeps inner apostrophes and hyphens', () => {
    expect(cleanClickedWord("don't")).toBe("don't");
    expect(cleanClickedWord('don’t,')).toBe("don't");
    expect(cleanClickedWord('well-known.')).toBe('well-known');
    expect(cleanClickedWord('3.5')).toBe('3.5');
  });

  it('returns empty for punctuation-only chunks', () => {
    expect(cleanClickedWord('—')).toBe('');
    expect(cleanClickedWord('...')).toBe('');
  });
});

describe('clickableWords', () => {
  it('splits a sentence keeping display text and the clean word', () => {
    expect(clickableWords(' What are you doing today? — ok ')).toEqual([
      { text: 'What', word: 'What' },
      { text: 'are', word: 'are' },
      { text: 'you', word: 'you' },
      { text: 'doing', word: 'doing' },
      { text: 'today?', word: 'today' },
      { text: '—', word: '' },
      { text: 'ok', word: 'ok' },
    ]);
  });
});
