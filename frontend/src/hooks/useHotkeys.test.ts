import { describe, expect, it } from 'vitest';
import { comboToLabels, hasStrongModifier, matchesCombo, type HotkeyEventLike } from './useHotkeys';

function ev(key: string, mods: Partial<HotkeyEventLike> = {}): HotkeyEventLike {
  return { key, code: '', ctrlKey: false, shiftKey: false, altKey: false, metaKey: false, ...mods };
}

describe('matchesCombo', () => {
  it('plain keys and named keys', () => {
    expect(matchesCombo(ev('Enter'), 'Enter')).toBe(true);
    expect(matchesCombo(ev('Tab'), 'Tab')).toBe(true);
    expect(matchesCombo(ev('`', { code: 'Backquote' }), '`')).toBe(true);
    expect(matchesCombo(ev('1', { code: 'Digit1' }), '1')).toBe(true);
    expect(matchesCombo(ev('Enter', { ctrlKey: true }), 'Enter')).toBe(false);
  });

  it('modifiers are exact', () => {
    expect(matchesCombo(ev(' ', { ctrlKey: true }), 'Ctrl+Space')).toBe(true);
    expect(matchesCombo(ev(' '), 'Ctrl+Space')).toBe(false);
    expect(matchesCombo(ev('r', { ctrlKey: true }), 'Ctrl+R')).toBe(true);
    expect(matchesCombo(ev('ArrowRight', { shiftKey: true }), 'Shift+ArrowRight')).toBe(true);
    expect(matchesCombo(ev('ArrowRight'), 'Shift+ArrowRight')).toBe(false);
  });

  it('Mod = Ctrl or Meta', () => {
    expect(matchesCombo(ev('ArrowLeft', { ctrlKey: true }), 'Mod+ArrowLeft')).toBe(true);
    expect(matchesCombo(ev('ArrowLeft', { metaKey: true }), 'Mod+ArrowLeft')).toBe(true);
    expect(matchesCombo(ev('ArrowLeft'), 'Mod+ArrowLeft')).toBe(false);
  });

  it('Shift+` matches by code even though key is ~', () => {
    expect(matchesCombo(ev('~', { shiftKey: true, code: 'Backquote' }), 'Shift+`')).toBe(true);
    expect(matchesCombo(ev('~', { shiftKey: true, code: 'Backquote' }), '`')).toBe(false);
  });

  it('shifted punctuation like ? ignores Shift', () => {
    expect(matchesCombo(ev('?', { shiftKey: true, code: 'Slash' }), '?')).toBe(true);
  });
});

describe('helpers', () => {
  it('hasStrongModifier', () => {
    expect(hasStrongModifier('Ctrl+H')).toBe(true);
    expect(hasStrongModifier('Mod+ArrowRight')).toBe(true);
    expect(hasStrongModifier('Shift+ArrowRight')).toBe(false);
    expect(hasStrongModifier('Enter')).toBe(false);
  });

  it('comboToLabels', () => {
    expect(comboToLabels('Ctrl+Space')).toEqual(['Ctrl', 'Space']);
    expect(comboToLabels('Shift+ArrowRight')).toEqual(['Shift', '→']);
    expect(comboToLabels('Ctrl+h')).toEqual(['Ctrl', 'H']);
  });
});
