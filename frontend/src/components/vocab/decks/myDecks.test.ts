import { describe, expect, it } from 'vitest';
import type { DeckUsage, PersonalDeck, VocabGroup } from '@/lib/types';
import { canCreateDeck, deckOptions, moveGroup, usageLabel, usagePercent, validateDeckName, validateGroupName } from './myDecks';

const usage: DeckUsage = { premium: false, decks: 5, maxDecks: 5, cards: 1200, maxCards: 8000, notebookCards: 0, maxNotebookCards: 2000 };

const groups: VocabGroup[] = [
  { id: 'b', deckId: 'd', name: 'Unit 2', sortOrder: 1, cardCount: 0 },
  { id: 'a', deckId: 'd', name: 'Unit 1', sortOrder: 0, cardCount: 3 },
  { id: 'c', deckId: 'd', name: 'Unit 3', sortOrder: 2, cardCount: 1 },
];

describe('my decks helpers', () => {
  it('limits', () => {
    expect(canCreateDeck(usage)).toBe(false);
    expect(canCreateDeck({ ...usage, decks: 4 })).toBe(true);
    expect(canCreateDeck(undefined)).toBe(false);
    expect(usageLabel(usage)).toBe('5/5 bộ thẻ · 1.200/8.000 thẻ');
    expect(usagePercent(250, 500)).toBe(50);
    expect(usagePercent(600, 500)).toBe(100);
  });

  it('validates names', () => {
    expect(validateDeckName('  ')).toMatch(/Vui lòng/);
    expect(validateDeckName('x'.repeat(101))).toMatch(/100/);
    expect(validateDeckName('IELTS')).toBeNull();
    expect(validateGroupName('unit 1', groups)).toMatch(/Đã có/);
    expect(validateGroupName('unit 1', groups, 'a')).toBeNull();
    expect(validateGroupName('Unit 4', groups)).toBeNull();
  });

  it('moves groups within bounds', () => {
    expect(moveGroup(groups, 'b', -1)).toEqual(['b', 'a', 'c']);
    expect(moveGroup(groups, 'b', 1)).toEqual(['a', 'c', 'b']);
    expect(moveGroup(groups, 'a', -1)).toEqual(['a', 'b', 'c']);
    expect(moveGroup(groups, 'c', 1)).toEqual(['a', 'b', 'c']);
  });

  it('lists the notebook first', () => {
    const decks = [
      { id: '1', name: 'IELTS', isNotebook: false },
      { id: '2', name: 'Sổ tay cá nhân', isNotebook: true },
    ] as PersonalDeck[];
    expect(deckOptions(decks).map((o) => o.label)).toEqual(['📓 Sổ tay cá nhân', 'IELTS']);
  });
});
