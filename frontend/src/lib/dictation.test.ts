import { describe, expect, it } from 'vitest';
import {
  MISSING_WORD_TOKEN,
  bePositionsByWord,
  blankMask,
  composeHardInput,
  composeUserInput,
  countBlanks,
  isDifficulty,
  nextHintIndex,
  normalizeWord,
  scoreLabel,
  tokenize,
  unrevealedBlanks,
  wordCorrectness,
  wordsOf,
} from './dictation';

const TEN = 'I want to travel around the world next summer, Tom!';

/** Mirrors the BE `DictationScorer.normalize` so we can assert what the grader sees. */
function beNormalize(text: string): string[] {
  const cleaned = text
    .toLowerCase()
    .trim()
    .replace(/[!-/:-@[-`{-~]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return cleaned ? cleaned.split(' ') : [];
}

describe('tokenize', () => {
  it('keeps punctuation outside the word', () => {
    const t = tokenize('"Hello," she said... — really?');
    expect(t.map((x) => [x.prefix, x.word, x.suffix])).toEqual([
      ['"', 'Hello', ',"'],
      ['', 'she', ''],
      ['', 'said', '...'],
      ['—', '', ''],
      ['', 'really', '?'],
    ]);
    expect(t.map((x) => x.wordIndex)).toEqual([0, 1, 2, -1, 3]);
  });

  it('keeps inner apostrophes/hyphens in the word', () => {
    expect(wordsOf(tokenize("Don't be a well-known  man."))).toEqual(["Don't", 'be', 'a', 'well-known', 'man']);
  });

  it('counts 10 words in the reference sentence', () => {
    expect(wordsOf(tokenize(TEN))).toHaveLength(10);
  });
});

describe('blankMask', () => {
  it('Easy: 10 words → 5 blanks, even positions pre-filled', () => {
    const m = blankMask(10, 'Easy');
    expect(countBlanks(m)).toBe(5);
    expect(m.map((c, i) => (c === 'prefilled' ? i : null)).filter((x) => x !== null)).toEqual([0, 2, 4, 6, 8]);
  });

  it('Normal: 10 words → 7 blanks, pre-filled at 0/4/8', () => {
    const m = blankMask(10, 'Normal');
    expect(countBlanks(m)).toBe(7);
    expect(m.map((c, i) => (c === 'prefilled' ? i : null)).filter((x) => x !== null)).toEqual([0, 4, 8]);
  });

  it('Hard: everything is a blank', () => {
    expect(countBlanks(blankMask(10, 'Hard'))).toBe(10);
  });

  it('is deterministic', () => {
    expect(blankMask(13, 'Normal')).toEqual(blankMask(13, 'Normal'));
    expect(blankMask(7, 'Easy')).toEqual(blankMask(7, 'Easy'));
  });

  it('always leaves at least one blank', () => {
    expect(blankMask(1, 'Easy')).toEqual(['blank']);
    expect(blankMask(1, 'Normal')).toEqual(['blank']);
    expect(blankMask(0, 'Easy')).toEqual([]);
  });
});

describe('composeUserInput', () => {
  const tokens = tokenize(TEN);
  const words = wordsOf(tokens);

  it('Easy, all blanks correct → equals the sentence (ignoring case and punctuation)', () => {
    const mask = blankMask(words.length, 'Easy');
    const typed = words.map((w, i) => (mask[i] === 'blank' ? w.toUpperCase() : ''));
    const input = composeUserInput(tokens, mask, typed, new Set());
    expect(beNormalize(input)).toEqual(beNormalize(TEN));
  });

  it('revealed and empty blanks are sent as a placeholder at the same position', () => {
    const mask = blankMask(words.length, 'Normal');
    const typed = [...words];
    typed[2] = '';
    const input = composeUserInput(tokens, mask, typed, new Set([1]));
    const sent = beNormalize(input);
    expect(sent).toHaveLength(words.length);
    expect(sent[1]).toBe(MISSING_WORD_TOKEN);
    expect(sent[2]).toBe(MISSING_WORD_TOKEN);
    expect(sent[3]).toBe('travel');
  });

  it('strips whitespace typed inside a box', () => {
    const t = tokenize('a b');
    expect(composeUserInput(t, blankMask(2, 'Hard'), [' a ', 'b c'], new Set())).toBe('a bc');
  });

  it('keeps BE positions aligned for apostrophes, dashes and curly quotes', () => {
    const text = '“Don’t” we — well-known people, don\'t go!';
    const t = tokenize(text);
    const w = wordsOf(t);
    const mask = blankMask(w.length, 'Hard');
    // User drops the apostrophes and hyphen: every following word must stay aligned.
    const typed = ['Don’t', 'we', 'wellknown', 'people', 'dont', 'go'];
    const sent = beNormalize(composeUserInput(t, mask, typed, new Set()));
    const ref = beNormalize(text);
    expect(sent).toHaveLength(ref.length);
    expect(sent[sent.length - 1]).toBe('go');
    expect(sent[0]).toBe(ref[0]);
  });

  it('placeholder survives BE punctuation stripping', () => {
    expect(beNormalize(MISSING_WORD_TOKEN)).toEqual([MISSING_WORD_TOKEN]);
  });
});

describe('wordCorrectness', () => {
  it('maps BE positions back to sentence words', () => {
    const t = tokenize('Well-known — people, go!');
    expect(bePositionsByWord(t)).toEqual([[0, 1], [3], [4]]);
    const scores = [
      { position: 0, isCorrect: true },
      { position: 1, isCorrect: false },
      { position: 2, isCorrect: true },
      { position: 3, isCorrect: true },
      { position: 4, isCorrect: true },
    ];
    expect(wordCorrectness(t, scores)).toEqual([false, true, true]);
    expect(wordCorrectness(t, [])).toEqual([null, null, null]);
  });
});

describe('composeHardInput', () => {
  it('passes text through without hints', () => {
    expect(composeHardInput('  hello   world ', 2, new Set())).toBe('hello world');
  });

  it('forces revealed positions to the placeholder, padding missing words', () => {
    expect(composeHardInput('one two three', 5, new Set([1]))).toBe(`one ${MISSING_WORD_TOKEN} three`);
    expect(composeHardInput('one', 5, new Set([3]))).toBe(
      ['one', MISSING_WORD_TOKEN, MISSING_WORD_TOKEN, MISSING_WORD_TOKEN].join(' ')
    );
  });
});

describe('hints', () => {
  const words = ['I', 'want', 'to', 'travel'];
  const mask = blankMask(4, 'Easy'); // blanks at 1 and 3

  it('prefers the focused blank', () => {
    expect(nextHintIndex(words, mask, [], new Set(), 3)).toBe(3);
  });

  it('skips pre-filled, revealed and correctly typed words', () => {
    expect(nextHintIndex(words, mask, [undefined, 'Want!'], new Set(), 0)).toBe(3);
    expect(nextHintIndex(words, mask, [], new Set([1]))).toBe(3);
    expect(nextHintIndex(words, mask, [], new Set([1, 3]))).toBe(-1);
  });

  it('unrevealedBlanks', () => {
    expect(unrevealedBlanks(mask, new Set([1]))).toEqual([3]);
  });
});

describe('misc', () => {
  it('normalizeWord', () => {
    expect(normalizeWord("Don't!")).toBe('dont');
  });

  it('isDifficulty', () => {
    expect(isDifficulty('Easy')).toBe(true);
    expect(isDifficulty('easy')).toBe(false);
    expect(isDifficulty(null)).toBe(false);
  });

  it('scoreLabel thresholds', () => {
    expect(scoreLabel(90).label).toBe('Xuất sắc');
    expect(scoreLabel(70).label).toBe('Tốt');
    expect(scoreLabel(69.9).label).toBe('Cần cải thiện');
  });
});
