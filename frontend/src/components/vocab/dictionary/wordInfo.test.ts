import { describe, expect, it } from 'vitest';
import type { DictionaryEntry } from '@/lib/types';
import { clickableWords } from '@/components/vocab/addWordForm';
import { formatIpa, groupDefinitions, prefillFromEntry, sourceNote } from './wordInfo';

const entry: DictionaryEntry = {
  query: 'running',
  word: 'run',
  found: true,
  ipa: 'rʌn',
  audioUrl: 'https://x/run-us.mp3',
  meaningsVi: ['chạy', 'điều hành'],
  partsOfSpeech: ['verb', 'noun'],
  definitionsEn: [
    { partOfSpeech: 'verb', definition: 'Move fast.', example: 'I run.' },
    { partOfSpeech: 'noun', definition: 'An act of running.', example: null },
    { partOfSpeech: 'verb', definition: 'Manage.', example: null },
  ],
  examples: [{ en: 'He runs fast.', vi: 'Anh ấy chạy nhanh.' }],
  source: 'cards+dictionary',
  externalStatus: 'ok',
};

describe('word info helpers', () => {
  it('formats IPA with slashes once', () => {
    expect(formatIpa('rʌn')).toBe('/rʌn/');
    expect(formatIpa('/rʌn/')).toBe('/rʌn/');
    expect(formatIpa('[rʌn]')).toBe('/rʌn/');
    expect(formatIpa('')).toBeNull();
    expect(formatIpa(null)).toBeNull();
  });

  it('groups definitions by part of speech', () => {
    const g = groupDefinitions(entry);
    expect(g.map((x) => x.partOfSpeech)).toEqual(['verb', 'noun']);
    expect(g[0].items.map((d) => d.definition)).toEqual(['Move fast.', 'Manage.']);
  });

  it('prefills the add form with the lemma and the clicked sentence', () => {
    expect(prefillFromEntry(entry, 'running', { example: 'She is running.', exampleVi: 'Cô ấy đang chạy.' })).toEqual({
      word: 'run',
      meaning: 'chạy',
      ipa: 'rʌn',
      partOfSpeech: 'verb',
      example: 'She is running.',
      exampleVi: 'Cô ấy đang chạy.',
    });
    // no context: the card example is used
    expect(prefillFromEntry(entry, 'running').example).toBe('He runs fast.');
    // unknown word: keep what was clicked, empty meaning
    expect(prefillFromEntry({ ...entry, found: false, meaningsVi: [] }, 'qwz')).toMatchObject({ word: 'qwz', meaning: '' });
    expect(prefillFromEntry(null, 'hello').word).toBe('hello');
  });

  it('explains missing data', () => {
    expect(sourceNote({ ...entry, externalStatus: 'error' })).toMatch(/Không kết nối/);
    expect(sourceNote({ ...entry, meaningsVi: [] })).toMatch(/Chưa có nghĩa tiếng Việt/);
    expect(sourceNote(entry)).toBeNull();
  });

  it('extracts clickable words from a transcript sentence', () => {
    expect(clickableWords('"Hello," she said — don’t go!').map((c) => c.word)).toEqual([
      'Hello',
      'she',
      'said',
      '',
      "don't",
      'go',
    ]);
  });
});
