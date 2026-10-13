import { describe, expect, it } from 'vitest';
import { formatDuration, formatNumber, progressBySentence, summarizeLessonResult, weakestWords } from './lesson-progress';

const sentences = [
  { id: 's1', sentenceOrder: 1 },
  { id: 's2', sentenceOrder: 2 },
  { id: 's3', sentenceOrder: 3 },
];

function att(id: string, sentenceId: string, score: number, accuracy: number, at: string, mode = 'dictation') {
  return { attemptId: id, sentenceId, mode, score, accuracy, createdAt: at };
}

const attempts = [
  att('a1', 's1', 40, 50, '2026-10-01T10:00:00Z'),
  att('a2', 's1', 80, 100, '2026-10-01T10:01:00Z'),
  att('a3', 's2', 60, 75, '2026-10-01T10:02:00Z'),
  att('a4', 's2', 56, 70, '2026-10-01T10:03:00Z'),
  att('a5', 's3', 90, 90, '2026-10-01T10:04:00Z', 'shadowing'),
];

describe('progressBySentence', () => {
  it('keeps best score, counts wrong attempts and latest attempt per mode', () => {
    const p = progressBySentence(attempts, 'dictation');
    expect(p.size).toBe(2);
    expect(p.get('s1')).toMatchObject({ attempts: 2, bestScore: 80, bestAccuracy: 100, wrongAttempts: 1, latestAttemptId: 'a2' });
    expect(p.get('s2')).toMatchObject({ attempts: 2, bestScore: 60, wrongAttempts: 2, latestAttemptId: 'a4' });
    expect(progressBySentence(attempts, 'shadowing').get('s3')?.bestScore).toBe(90);
  });
});

describe('summarizeLessonResult', () => {
  it('averages the best score of done sentences and ranks mistakes', () => {
    const r = summarizeLessonResult(sentences, attempts, 'dictation');
    expect(r.done).toBe(2);
    expect(r.total).toBe(3);
    expect(r.finalScore).toBe(70);
    expect(r.avgAccuracy).toBe(87.5);
    expect(r.mostMistakes.map((m) => [m.sentence.id, m.wrongAttempts])).toEqual([
      ['s2', 2],
      ['s1', 1],
    ]);
  });

  it('handles no attempts', () => {
    const r = summarizeLessonResult(sentences, [], 'shadowing');
    expect(r).toMatchObject({ done: 0, total: 3, finalScore: 0, mostMistakes: [] });
  });
});

describe('weakestWords', () => {
  it('averages by normalised word and keeps those under the threshold', () => {
    const w = (word: string, wordAccuracy: number) => ({ word, wordAccuracy, phonemes: [], status: 'red' });
    const res = weakestWords([[w('Hello,', 50), w('world', 90)], [w('hello', 70), w('three', 40)], null]);
    expect(res).toEqual([
      { word: 'three', avgAccuracy: 40, count: 1 },
      { word: 'hello', avgAccuracy: 60, count: 2 },
    ]);
  });
});

describe('format', () => {
  it('formatDuration', () => {
    expect(formatDuration(5_400)).toBe('5 giây');
    expect(formatDuration(125_000)).toBe('2 phút 05 giây');
    expect(formatDuration(3_720_000)).toBe('1 giờ 2 phút');
  });

  it('formatNumber', () => {
    expect(formatNumber(2)).toBe('2');
    expect(formatNumber(2.345)).toBe('2.3');
  });
});
