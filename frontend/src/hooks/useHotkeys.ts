'use client';

import { useEffect, useRef } from 'react';

/**
 * Keyboard shortcuts (architecture §6.5).
 *
 * Combo syntax: modifiers joined with `+`, key last. Modifiers: `Ctrl`, `Shift`, `Alt`, `Meta`,
 * `Mod` (= Ctrl **or** ⌘). Key: a `KeyboardEvent.key` value (`Enter`, `Tab`, `ArrowRight`, `r`, `1`,
 * `?`, `` ` ``) or the aliases `Space`, `Backquote`, `Esc`. Matching is case-insensitive and also
 * accepts `KeyboardEvent.code` (so `Shift+\`` works although the key value becomes `~`).
 *
 * Rules:
 * - Never fires while a modal is open (`[role="dialog"][aria-modal="true"]` in the DOM).
 * - While focus is in an input/textarea/select/contenteditable, only combos with Ctrl/⌘/Alt or
 *   bindings marked `allowInInput` fire (Enter, Tab, Ctrl+Space, Ctrl+R, Ctrl+H, Shift+←/→ …).
 * - Ignores IME composition (Vietnamese input methods) and, unless `allowRepeat`, auto-repeat.
 */
export interface HotkeyBinding {
  combo: string;
  handler: (e: KeyboardEvent) => void;
  allowInInput?: boolean;
  allowRepeat?: boolean;
  /** Default true. */
  preventDefault?: boolean;
  /** Per-binding switch (default true). */
  enabled?: boolean;
}

export interface HotkeyEventLike {
  key: string;
  code?: string;
  ctrlKey: boolean;
  shiftKey: boolean;
  altKey: boolean;
  metaKey: boolean;
}

interface ParsedCombo {
  key: string;
  ctrl: boolean;
  shift: boolean;
  alt: boolean;
  meta: boolean;
  mod: boolean;
}

const KEY_ALIASES: Record<string, string> = {
  space: ' ',
  spacebar: ' ',
  esc: 'escape',
  backquote: '`',
  left: 'arrowleft',
  right: 'arrowright',
  up: 'arrowup',
  down: 'arrowdown',
};

// KeyboardEvent.code for keys whose `key` changes with Shift.
// Covers every digit and the US-layout punctuation keys, so user-chosen combos such as
// `Shift+6` or `Shift+=` (customisable shortcuts) match on `code`.
const CODE_FOR_KEY: Record<string, string> = {
  ' ': 'space',
  '`': 'backquote',
  '/': 'slash',
  '-': 'minus',
  '=': 'equal',
  '[': 'bracketleft',
  ']': 'bracketright',
  '\\': 'backslash',
  ';': 'semicolon',
  "'": 'quote',
  ',': 'comma',
  '.': 'period',
  ...Object.fromEntries(Array.from({ length: 10 }, (_, d) => [String(d), `digit${d}`])),
};

export function parseCombo(combo: string): ParsedCombo {
  // A literal "+" key is written as "Shift++" / "+" → split carefully.
  const parts = combo === '+' ? ['+'] : combo.split(/\+(?!$)/);
  const result: ParsedCombo = { key: '', ctrl: false, shift: false, alt: false, meta: false, mod: false };
  for (const raw of parts) {
    const p = raw.trim().toLowerCase();
    if (p === 'ctrl' || p === 'control') result.ctrl = true;
    else if (p === 'shift') result.shift = true;
    else if (p === 'alt' || p === 'option') result.alt = true;
    else if (p === 'meta' || p === 'cmd' || p === '⌘') result.meta = true;
    else if (p === 'mod') result.mod = true;
    else result.key = KEY_ALIASES[p] ?? p;
  }
  return result;
}

function isPunctuation(key: string): boolean {
  return key.length === 1 && !/[a-z0-9 ]/i.test(key);
}

export function matchesCombo(e: HotkeyEventLike, combo: string | ParsedCombo): boolean {
  const c = typeof combo === 'string' ? parseCombo(combo) : combo;
  if (!c.key) return false;

  if (c.mod) {
    if (!(e.ctrlKey || e.metaKey)) return false;
  } else if (e.ctrlKey !== c.ctrl || e.metaKey !== c.meta) {
    return false;
  }
  if (e.altKey !== c.alt) return false;

  const eventKey = (KEY_ALIASES[e.key.toLowerCase()] ?? e.key.toLowerCase());
  const byKey = eventKey === c.key;
  const wantedCode = CODE_FOR_KEY[c.key] ?? (/^[a-z]$/.test(c.key) ? `key${c.key}` : null);
  const byCode = !!wantedCode && !!e.code && e.code.toLowerCase() === wantedCode;
  if (!byKey && !byCode) return false;

  // Punctuation produced by Shift (e.g. "?") matches regardless of Shift unless Shift is explicit.
  if (byKey && isPunctuation(c.key) && !c.shift) return true;
  return e.shiftKey === c.shift;
}

/** True when the combo uses Ctrl/⌘/Alt (allowed while typing). */
export function hasStrongModifier(combo: string): boolean {
  const c = parseCombo(combo);
  return c.ctrl || c.meta || c.alt || c.mod;
}

export function isTypingTarget(target: EventTarget | null): boolean {
  if (!target || typeof (target as HTMLElement).tagName !== 'string') return false;
  const el = target as HTMLElement;
  const tag = el.tagName.toLowerCase();
  return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
}

export function isModalOpen(): boolean {
  if (typeof document === 'undefined') return false;
  return document.querySelector('[role="dialog"][aria-modal="true"]') !== null;
}

/** Human-readable keys for `ShortcutHelp`/`Kbd`, e.g. `Ctrl+Space` → `['Ctrl', 'Space']`. */
export function comboToLabels(combo: string): string[] {
  const c = parseCombo(combo);
  const labels: string[] = [];
  if (c.mod) labels.push('Ctrl/⌘');
  if (c.ctrl) labels.push('Ctrl');
  if (c.meta) labels.push('⌘');
  if (c.alt) labels.push('Alt');
  if (c.shift) labels.push('Shift');
  const named: Record<string, string> = {
    ' ': 'Space',
    arrowleft: '←',
    arrowright: '→',
    arrowup: '↑',
    arrowdown: '↓',
    enter: 'Enter',
    tab: 'Tab',
    escape: 'Esc',
    backspace: 'Backspace',
  };
  labels.push(named[c.key] ?? (c.key.length === 1 ? c.key.toUpperCase() : c.key));
  return labels;
}

export function useHotkeys(bindings: HotkeyBinding[], options: { enabled?: boolean } = {}): void {
  const enabled = options.enabled ?? true;
  const bindingsRef = useRef(bindings);
  bindingsRef.current = bindings;

  useEffect(() => {
    if (!enabled) return;

    function onKeyDown(e: KeyboardEvent) {
      if (e.isComposing || e.key === 'Process' || e.key === 'Unidentified') return;
      if (isModalOpen()) return;
      const typing = isTypingTarget(e.target);

      for (const b of bindingsRef.current) {
        if (b.enabled === false) continue;
        if (e.repeat && !b.allowRepeat) continue;
        if (typing && !b.allowInInput && !hasStrongModifier(b.combo)) continue;
        if (!matchesCombo(e, b.combo)) continue;
        if (b.preventDefault !== false) e.preventDefault();
        b.handler(e);
        return;
      }
    }

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [enabled]);
}
