import { describe, expect, it } from 'vitest';
import { comboMultiplier, formatClock, hintFactor, nextComboTier, posLabel, recordMessage, wordsToReview } from './game';
import { validateCombo } from './shortcuts';
import type { GameSummaryItem } from './types';

function item(cardId: string, result: GameSummaryItem['result'], extra: Partial<GameSummaryItem> = {}): GameSummaryItem {
  return {
    seq: 0, cardId, word: cardId, meaning: 'm', ipa: null, partOfSpeech: null, example: null, exampleVi: null,
    audioUrl: null, result, answer: null, hintsUsed: 0, points: 0, retry: false, userVocabId: null, ...extra,
  };
}

describe('combo tiers', () => {
  it('mirrors the server multiplier', () => {
    expect([0, 4, 5, 9, 10, 19, 20, 50].map(comboMultiplier)).toEqual([1, 1, 1.5, 1.5, 2, 2, 3, 3]);
  });
  it('tells how far the next tier is', () => {
    expect(nextComboTier(3)).toEqual({ at: 5, multiplier: 1.5, remaining: 2 });
    expect(nextComboTier(12)).toEqual({ at: 20, multiplier: 3, remaining: 8 });
    expect(nextComboTier(20)).toBeNull();
  });
  it('hint factors', () => {
    expect([0, 1, 2, 3, 9].map(hintFactor)).toEqual([1, 0.8, 0.5, 0.25, 0.25]);
  });
});

describe('formatting', () => {
  it('labels parts of speech in Vietnamese', () => {
    expect(posLabel('n')).toBe('danh từ');
    expect(posLabel('Adj.')).toBe('tính từ');
    expect(posLabel('phr v')).toBe('cụm động từ');
    expect(posLabel('idiom')).toBe('idiom');
    expect(posLabel(null)).toBeNull();
  });
  it('formats the countdown', () => {
    expect(formatClock(60_000)).toBe('1:00');
    expect(formatClock(41_200)).toBe('0:42');
    expect(formatClock(-5)).toBe('0:00');
  });
  it('record messages', () => {
    expect(recordMessage(120, null, true)).toBe('Kỷ lục đầu tiên của bạn!');
    expect(recordMessage(120, 100, true)).toBe('Kỷ lục mới! (cũ: 100)');
    expect(recordMessage(80, 100, false)).toBe('Còn 21 điểm nữa là phá kỷ lục 100.');
    expect(recordMessage(100, 100, false)).toBe('Bằng kỷ lục của bạn!');
  });
});

describe('wordsToReview', () => {
  it('lists missed, misspelt and hinted words once each', () => {
    const items = [
      item('a', 'CORRECT'),
      item('b', 'WRONG'),
      item('c', 'NEAR'),
      item('d', 'CORRECT', { hintsUsed: 1 }),
      item('e', 'SKIPPED'),
      item('b', 'CORRECT', { retry: true }),
    ];
    expect(wordsToReview(items).map((i) => i.cardId)).toEqual(['b', 'c', 'd', 'e']);
  });
});

describe('game shortcuts', () => {
  it('keeps Enter for submitting and refuses text keys', () => {
    expect(validateCombo('game.hint', 'Enter', {})).toMatch(/Enter/);
    expect(validateCombo('game.skip', 'S', {})).toMatch(/gõ chữ/);
    expect(validateCombo('game.skip', 'Ctrl+H', {})).toMatch(/Gợi ý/);
    expect(validateCombo('game.skip', 'Alt+S', {})).toBeNull();
  });
});
