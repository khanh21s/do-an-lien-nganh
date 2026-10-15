import { describe, expect, it } from 'vitest';
import {
  SRS_RATINGS,
  formatNextDue,
  isTypedAnswerCorrect,
  maskWord,
  previewLabel,
  ratingByKey,
  ratingByValue,
  ratingDistribution,
  suggestedRating,
} from './srs';
import type { RatingPreview } from './types';

describe('SRS_RATINGS', () => {
  it('maps keys 1–4 to Again/Hard/Good/Easy = Anki ratings 1–4', () => {
    expect(SRS_RATINGS.map((r) => [r.key, r.label, r.labelEn, r.rating])).toEqual([
      ['1', 'Học lại', 'Again', 1],
      ['2', 'Khó', 'Hard', 2],
      ['3', 'Tốt', 'Good', 3],
      ['4', 'Dễ', 'Easy', 4],
    ]);
    expect(ratingByKey('3')?.rating).toBe(3);
    expect(ratingByValue(2)?.name).toBe('hard');
    expect(ratingByValue(5)).toBeUndefined();
  });
});

describe('server previews', () => {
  const previews: RatingPreview[] = [
    { rating: 1, label: '<1 phút', state: 'LEARNING', intervalDays: 0, nextReviewAt: '2026-10-15T03:01:00Z' },
    { rating: 2, label: '6 phút', state: 'LEARNING', intervalDays: 0, nextReviewAt: '2026-10-15T03:05:30Z' },
    { rating: 3, label: '10 phút', state: 'LEARNING', intervalDays: 0, nextReviewAt: '2026-10-15T03:10:00Z' },
    { rating: 4, label: '4 ngày', state: 'REVIEW', intervalDays: 4, nextReviewAt: '2026-10-18T21:00:00Z' },
  ];

  it('reads the label of each button from the server payload', () => {
    expect(SRS_RATINGS.map((r) => previewLabel(previews, r.rating))).toEqual(['<1 phút', '6 phút', '10 phút', '4 ngày']);
    expect(previewLabel(undefined, 1)).toBeUndefined();
  });
});

describe('formatNextDue', () => {
  const now = new Date(2026, 9, 15, 10, 0);
  it('today, tomorrow, later', () => {
    expect(formatNextDue(new Date(2026, 9, 15, 10, 9).toISOString(), now)).toBe('hôm nay lúc 10:09');
    expect(formatNextDue(new Date(2026, 9, 16, 4, 0).toISOString(), now)).toBe('ngày mai lúc 04:00');
    expect(formatNextDue(new Date(2026, 9, 20, 4, 0).toISOString(), now)).toBe('lúc 04:00 ngày 20/10');
    expect(formatNextDue(null, now)).toBeNull();
    expect(formatNextDue('nope', now)).toBeNull();
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
    expect(suggestedRating(true)).toBe(3);
    expect(suggestedRating(false)).toBe(1);
  });
});

describe('summary helpers', () => {
  it('counts ratings', () => {
    expect(ratingDistribution([1, 3, 3, 4, 2])).toEqual({ again: 1, hard: 1, good: 2, easy: 1 });
  });
});
