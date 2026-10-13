import { describe, expect, it } from 'vitest';
import {
  firstUndoneSentence,
  getNeighbors,
  practiceResultPath,
  practiceSentencePath,
  sortSentences,
} from './practice-nav';

// Deliberately unsorted input.
const sentences = [
  { id: 's3', sentenceOrder: 3 },
  { id: 's1', sentenceOrder: 1 },
  { id: 's2', sentenceOrder: 2 },
];

describe('sortSentences', () => {
  it('sorts by sentenceOrder without mutating', () => {
    const copy = [...sentences];
    expect(sortSentences(sentences).map((s) => s.id)).toEqual(['s1', 's2', 's3']);
    expect(sentences).toEqual(copy);
  });
});

describe('getNeighbors', () => {
  it('first sentence', () => {
    const n = getNeighbors(sentences, 's1');
    expect(n.prev).toBeNull();
    expect(n.next?.id).toBe('s2');
    expect(n.index).toBe(0);
    expect(n.total).toBe(3);
    expect(n.isFirst).toBe(true);
    expect(n.isLast).toBe(false);
  });

  it('middle sentence', () => {
    const n = getNeighbors(sentences, 's2');
    expect(n.prev?.id).toBe('s1');
    expect(n.next?.id).toBe('s3');
    expect(n.current?.id).toBe('s2');
    expect(n.index).toBe(1);
  });

  it('last sentence', () => {
    const n = getNeighbors(sentences, 's3');
    expect(n.prev?.id).toBe('s2');
    expect(n.next).toBeNull();
    expect(n.isLast).toBe(true);
  });

  it('unknown id', () => {
    const n = getNeighbors(sentences, 'nope');
    expect(n).toMatchObject({ current: null, prev: null, next: null, index: -1, total: 3 });
  });

  it('single sentence is both first and last', () => {
    const n = getNeighbors([{ id: 'x', sentenceOrder: 1 }], 'x');
    expect(n.isFirst && n.isLast).toBe(true);
  });
});

describe('firstUndoneSentence', () => {
  it('returns the first sentence without an attempt in that mode', () => {
    const attempts = [
      { sentenceId: 's1', mode: 'dictation' },
      { sentenceId: 's2', mode: 'shadowing' },
    ];
    expect(firstUndoneSentence(sentences, attempts, 'dictation')?.id).toBe('s2');
    expect(firstUndoneSentence(sentences, attempts, 'shadowing')?.id).toBe('s1');
  });

  it('no attempts → first sentence by order', () => {
    expect(firstUndoneSentence(sentences, [], 'dictation')?.id).toBe('s1');
  });

  it('all done or empty → null', () => {
    const all = sentences.map((s) => ({ sentenceId: s.id, mode: 'dictation' }));
    expect(firstUndoneSentence(sentences, all, 'dictation')).toBeNull();
    expect(firstUndoneSentence([], [], 'dictation')).toBeNull();
  });
});

describe('paths', () => {
  it('builds practice and result paths', () => {
    expect(practiceSentencePath('L', 'dictation', 'S')).toBe('/lessons/L/dictation/S');
    expect(practiceResultPath('L', 'shadowing')).toBe('/lessons/L/result?mode=shadowing');
  });
});
