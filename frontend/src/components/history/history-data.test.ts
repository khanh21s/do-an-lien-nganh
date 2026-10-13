import { describe, expect, it } from 'vitest';
import {
  difficultyLabel,
  formatAttemptDateTime,
  formatScore,
  modeLabel,
  scoreTone,
  uniqueLessonIds,
} from './history-data';

describe('uniqueLessonIds', () => {
  it('returns distinct lesson ids in first-seen order (20 rows / 3 lessons → 3 ids)', () => {
    const ids = ['a', 'b', 'c'];
    const attempts = Array.from({ length: 20 }, (_, i) => ({ lessonId: ids[(i * 7) % 3] }));
    expect(uniqueLessonIds(attempts)).toEqual(['a', 'b', 'c']);
    expect(uniqueLessonIds([{ lessonId: 'x' }, { lessonId: 'x' }, { lessonId: '' }])).toEqual(['x']);
  });
});

describe('formatAttemptDateTime', () => {
  it('formats in Vietnam time (UTC+7)', () => {
    expect(formatAttemptDateTime('2026-10-12T16:30:00Z')).toBe('12/10/2026 23:30');
    expect(formatAttemptDateTime('2026-10-12T23:30:00+07:00')).toBe('12/10/2026 23:30');
    expect(formatAttemptDateTime('2026-01-01T00:05:00+07:00')).toBe('01/01/2026 00:05');
  });

  it('returns a dash for missing or invalid input', () => {
    expect(formatAttemptDateTime(null)).toBe('—');
    expect(formatAttemptDateTime('not a date')).toBe('—');
  });
});

describe('labels', () => {
  it('mode and difficulty labels', () => {
    expect(modeLabel('dictation')).toBe('Dictation');
    expect(modeLabel('other')).toBe('other');
    expect(difficultyLabel('Hard')).toBe('Khó');
    expect(difficultyLabel(null)).toBe('—');
  });

  it('scoreTone and formatScore', () => {
    expect(scoreTone(95)).toBe('green');
    expect(scoreTone(60)).toBe('amber');
    expect(scoreTone(10)).toBe('red');
    expect(formatScore(87.66)).toBe('87.7');
    expect(formatScore(100)).toBe('100');
  });
});
