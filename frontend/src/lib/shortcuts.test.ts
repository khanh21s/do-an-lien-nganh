import { describe, expect, it } from 'vitest';
import { matchesCombo, type HotkeyEventLike } from '@/hooks/useHotkeys';
import { comboFromEvent, normalizeCombo, resolveCombos, validateCombo } from './shortcuts';

function ev(key: string, code: string, mods: Partial<HotkeyEventLike> = {}): HotkeyEventLike {
  return { key, code, ctrlKey: false, shiftKey: false, altKey: false, metaKey: false, ...mods };
}

describe('comboFromEvent', () => {
  it('records letters, digits, named keys and modifiers', () => {
    expect(comboFromEvent(ev('k', 'KeyK', { ctrlKey: true }))).toBe('Ctrl+K');
    expect(comboFromEvent(ev('5', 'Digit5'))).toBe('5');
    expect(comboFromEvent(ev('ArrowRight', 'ArrowRight', { altKey: true }))).toBe('Alt+ArrowRight');
    expect(comboFromEvent(ev('F2', 'F2'))).toBe('F2');
    expect(comboFromEvent(ev(' ', 'Space', { ctrlKey: true }))).toBe('Ctrl+Space');
  });

  it('stores the unshifted key for Shift+symbol / Shift+digit', () => {
    expect(comboFromEvent(ev('~', 'Backquote', { shiftKey: true }))).toBe('Shift+`');
    expect(comboFromEvent(ev('^', 'Digit6', { shiftKey: true }))).toBe('Shift+6');
    expect(comboFromEvent(ev('+', 'Equal', { shiftKey: true }))).toBe('Shift+=');
  });

  it('ignores lone modifiers and Escape', () => {
    expect(comboFromEvent(ev('Shift', 'ShiftLeft', { shiftKey: true }))).toBeNull();
    expect(comboFromEvent(ev('Control', 'ControlLeft', { ctrlKey: true }))).toBeNull();
    expect(comboFromEvent(ev('Escape', 'Escape'))).toBeNull();
  });

  it('recorded combos match the same key press in useHotkeys', () => {
    const presses = [
      ev('k', 'KeyK', { ctrlKey: true }),
      ev('^', 'Digit6', { shiftKey: true }),
      ev('+', 'Equal', { shiftKey: true }),
      ev('~', 'Backquote', { shiftKey: true }),
      ev('F2', 'F2'),
      ev(' ', 'Space', { altKey: true }),
    ];
    for (const p of presses) expect(matchesCombo(p, comboFromEvent(p)!)).toBe(true);
  });
});

describe('validateCombo', () => {
  it('accepts a free combo', () => {
    expect(validateCombo('dictation.hint', 'Ctrl+G', {})).toBeNull();
    expect(validateCombo('flashcard.flip', 'F', {})).toBeNull();
  });

  it('rejects keys that would type into the dictation answer box', () => {
    expect(validateCombo('dictation.replay', 'R', {})).toMatch(/gõ chữ/);
    expect(validateCombo('dictation.replay', 'Shift+R', {})).toMatch(/gõ chữ/);
    expect(validateCombo('dictation.replay', 'Space', {})).toMatch(/gõ chữ/);
    expect(validateCombo('dictation.replay', 'Alt+R', {})).toBeNull();
    expect(validateCombo('dictation.replay', 'F4', {})).toBeNull();
  });

  it('rejects conflicts inside the same screen only', () => {
    expect(validateCombo('dictation.hint', 'Ctrl+R', {})).toMatch(/Nghe lại câu/);
    expect(validateCombo('flashcard.again', '2', {})).toMatch(/Khó/);
    // Space is used by shadowing but not by dictation hint → only checked per scope
    expect(validateCombo('flashcard.again', 'Shift+`', {})).toBeNull();
  });

  it('treats Ctrl+X and Mod+X as the same key', () => {
    expect(validateCombo('dictation.hint', 'Ctrl+ArrowRight', {})).toMatch(/Câu kế/);
  });

  it('respects overrides and reserved keys', () => {
    expect(validateCombo('flashcard.again', '2', { 'flashcard.hard': ['H'] })).toBeNull();
    expect(validateCombo('flashcard.flip', 'Shift+/', {})).toMatch(/\?/);
    expect(validateCombo('flashcard.flip', 'Space', {})).toMatch(/đã có/);
    // replacing Space by Enter when Enter is the other combo of the same action
    expect(validateCombo('flashcard.flip', 'Enter', {}, ['Space'])).toBeNull();
  });
});

describe('resolveCombos / normalizeCombo', () => {
  it('falls back to defaults when there is no (or an empty) override', () => {
    expect(resolveCombos('dictation.hint', {})).toEqual(['Ctrl+H']);
    expect(resolveCombos('dictation.hint', { 'dictation.hint': [] })).toEqual(['Ctrl+H']);
    expect(resolveCombos('dictation.hint', { 'dictation.hint': ['Alt+H'] })).toEqual(['Alt+H']);
  });

  it('normalises case and modifier order', () => {
    expect(normalizeCombo('shift+ctrl+K')).toBe(normalizeCombo('Ctrl+Shift+k'));
    expect(normalizeCombo('Space')).toBe(normalizeCombo('space'));
  });
});
