import { describe, expect, it } from 'vitest';
import {
  NEW_CARD_STATE,
  SRS_RATINGS,
  clampDailyLimit,
  earliestDate,
  formatInterval,
  isTypedAnswerCorrect,
  maskWord,
  nextSrsState,
  previewInterval,
  ratingByKey,
  ratingDistribution,
  suggestedQuality,
} from './srs';

const mature = { repetitions: 3, intervalDays: 10, easeFactor: 2.5 };

describe('SRS_RATINGS', () => {
  it('maps keys 1–4 to Again/Hard/Good/Easy = q1/q3/q4/q5', () => {
    expect(SRS_RATINGS.map((r) => [r.key, r.labelEn, r.quality])).toEqual([
      ['1', 'Again', 1],
      ['2', 'Hard', 3],
      ['3', 'Good', 4],
      ['4', 'Easy', 5],
    ]);
    expect(ratingByKey('3')?.quality).toBe(4);
  });
});

describe('nextSrsState (architecture §4.6 examples)', () => {
  it('n=3, I=10, EF=2.5', () => {
    expect(nextSrsState(mature, 1)).toEqual({ repetitions: 0, intervalDays: 1, easeFactor: 1.96 });
    expect(nextSrsState(mature, 3)).toEqual({ repetitions: 4, intervalDays: 25, easeFactor: 2.36 });
    expect(nextSrsState(mature, 4)).toEqual({ repetitions: 4, intervalDays: 25, easeFactor: 2.5 });
    expect(nextSrsState(mature, 5)).toEqual({ repetitions: 4, intervalDays: 25, easeFactor: 2.6 });
  });

  it('n=1 Good → 6 days; n=0 → 1 day', () => {
    expect(nextSrsState({ repetitions: 1, intervalDays: 1, easeFactor: 2.5 }, 4).intervalDays).toBe(6);
    expect(nextSrsState(NEW_CARD_STATE, 5).intervalDays).toBe(1);
  });

  it('EF never drops below 1.3', () => {
    expect(nextSrsState({ repetitions: 2, intervalDays: 6, easeFactor: 1.35 }, 1).easeFactor).toBe(1.3);
  });

  it('previewInterval and labels', () => {
    expect(SRS_RATINGS.map((r) => previewInterval(mature, r.quality))).toEqual([1, 25, 25, 25]);
    expect(previewInterval(null, 4)).toBe(1);
    expect(formatInterval(25)).toBe('25 ngày');
  });
});

describe('daily limit', () => {
  it('clamps to 1–1000 with default 100', () => {
    expect(clampDailyLimit(0)).toBe(1);
    expect(clampDailyLimit(5000)).toBe(1000);
    expect(clampDailyLimit('abc')).toBe(100);
    expect(clampDailyLimit('42')).toBe(42);
  });
});

describe('typing mode', () => {
  it('compares ignoring case, spaces and punctuation', () => {
    expect(isTypedAnswerCorrect('  Apple ', 'apple')).toBe(true);
    expect(isTypedAnswerCorrect('give up!', 'Give up')).toBe(true);
    expect(isTypedAnswerCorrect('aple', 'apple')).toBe(false);
    expect(isTypedAnswerCorrect('', 'apple')).toBe(false);
  });

  it('masks the word and simple inflections', () => {
    expect(maskWord('I ate two apples.', 'apple')).toBe('I ate two ______.');
    expect(maskWord('Pineapple is sweet', 'apple')).toBe('Pineapple is sweet');
  });

  it('suggests Good when correct, Again when wrong', () => {
    expect(suggestedQuality(true)).toBe(4);
    expect(suggestedQuality(false)).toBe(1);
  });
});

describe('summary helpers', () => {
  it('counts ratings and finds the earliest date', () => {
    expect(ratingDistribution([1, 4, 4, 5, 3])).toEqual({ again: 1, hard: 1, good: 2, easy: 1 });
    expect(earliestDate(['2026-10-20T00:00:00Z', null, '2026-10-14T00:00:00Z'])).toBe('2026-10-14T00:00:00Z');
    expect(earliestDate([])).toBeNull();
  });
});
