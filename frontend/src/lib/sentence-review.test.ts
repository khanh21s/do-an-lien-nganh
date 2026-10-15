import { describe, expect, it } from 'vitest';
import { formatNextReview, nextReviewIndex, reviewLesson, reviewOutcomeText } from './sentence-review';
import type { SentenceReviewItem, SentenceReviewOutcome } from './types';

const now = new Date(2026, 9, 15, 10, 0, 0);
const at = (days: number, hours = 0) => new Date(2026, 9, 15 + days, 10 + hours, 0, 0).toISOString();

const item: SentenceReviewItem = {
  id: 'r1',
  lessonId: 'l1',
  sentenceId: 's1',
  mode: 'dictation',
  repetitions: 0,
  intervalDays: 0,
  easeFactor: 2.5,
  nextReviewAt: at(0, -1),
  lastScore: 60,
  lastReviewedAt: null,
  createdAt: at(-1),
  lessonTitle: 'Morning routine',
  lessonAudioUrl: 'local:seed/lesson-1/full.mp3',
  lessonIsPro: true,
  locked: false,
  sentence: {
    id: 's1',
    text: 'I wake up at seven.',
    ipa: null,
    translationVi: null,
    sentenceOrder: 3,
    startMs: 1000,
    endMs: 2500,
    audioUrl: null,
    imageUrl: null,
  },
};

describe('reviewLesson', () => {
  it('builds a one-sentence lesson for the practice components', () => {
    const lesson = reviewLesson(item);
    expect(lesson.id).toBe('l1');
    expect(lesson.title).toBe('Morning routine');
    expect(lesson.audioUrl).toBe('local:seed/lesson-1/full.mp3');
    expect(lesson.isPro).toBe(true);
    expect(lesson.sentences).toEqual([item.sentence]);
  });
});

describe('formatNextReview', () => {
  it('labels relative days', () => {
    expect(formatNextReview(at(0, -1), now)).toBe('đã đến hạn');
    expect(formatNextReview(at(0, 2), now)).toBe('hôm nay');
    expect(formatNextReview(at(1), now)).toBe('ngày mai');
    expect(formatNextReview(at(6), now)).toBe('sau 6 ngày');
    expect(formatNextReview(null, now)).toBe('—');
  });
});

describe('reviewOutcomeText', () => {
  const base: SentenceReviewOutcome = {
    reviewId: 'r1',
    action: 'graded',
    quality: 4,
    repetitions: 2,
    intervalDays: 6,
    easeFactor: 2.5,
    nextReviewAt: at(6),
  };

  it('describes each action', () => {
    expect(reviewOutcomeText(base, now)).toBe('Đã chấm “Tốt” · lần ôn tiếp: sau 6 ngày.');
    expect(reviewOutcomeText({ ...base, quality: 1, nextReviewAt: at(1) }, now)).toBe(
      'Đã chấm “Học lại” · lần ôn tiếp: ngày mai.'
    );
    expect(reviewOutcomeText({ ...base, action: 'queued', quality: null }, now)).toContain('Ôn câu sai');
    expect(reviewOutcomeText({ ...base, action: 'unchanged', quality: null }, now)).toContain('sau 6 ngày');
    expect(reviewOutcomeText(null, now)).toContain('không còn');
  });
});

describe('nextReviewIndex', () => {
  it('walks a fixed list and stops at the end', () => {
    expect(nextReviewIndex(3, 0)).toBe(1);
    expect(nextReviewIndex(3, 2)).toBe(-1);
    expect(nextReviewIndex(0, -1)).toBe(-1);
  });
});
