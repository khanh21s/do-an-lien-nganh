import { describe, expect, it } from 'vitest';
import { isLessonLocked } from './pro-lesson';

describe('isLessonLocked (P2-A1)', () => {
  it('never locks free lessons', () => {
    expect(isLessonLocked({ isPro: false }, { isPremium: false })).toBe(false);
    expect(isLessonLocked(undefined, { isPremium: false })).toBe(false);
  });

  it('locks Pro lessons for known non-premium users only', () => {
    expect(isLessonLocked({ isPro: true }, { isPremium: false })).toBe(true);
    expect(isLessonLocked({ isPro: true }, { isPremium: true })).toBe(false);
    // unknown (loading/failed) → let the server decide
    expect(isLessonLocked({ isPro: true }, {})).toBe(false);
  });

  it('admins bypass', () => {
    expect(isLessonLocked({ isPro: true }, { isPremium: false, isAdmin: true })).toBe(false);
  });
});
