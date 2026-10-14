import { comboToLabels, parseCombo, type HotkeyBinding, type HotkeyEventLike } from '@/hooks/useHotkeys';

/**
 * Customisable keyboard shortcuts: the catalogue of actions with their default combos, plus
 * pure helpers to record a combo from a key press, normalise it and validate a user's choice.
 * User overrides live in `store/shortcut-store.ts`; components read the effective combos with
 * `useShortcutKeys()` and the help modal / settings page render them with `comboToLabels`.
 */
export type ShortcutScope = 'dictation' | 'shadowing' | 'flashcard' | 'game';

export interface ShortcutAction {
  id: string;
  scope: ShortcutScope;
  description: string;
  /** Default combos in `useHotkeys` syntax (the first one is shown on buttons). */
  defaults: string[];
  /** The action also fires while the focus is in a text field (dictation answer box). */
  inInput?: boolean;
}

export const MAX_COMBOS_PER_ACTION = 2;

export const SHORTCUT_SCOPES: { scope: ShortcutScope; title: string; note?: string }[] = [
  {
    scope: 'dictation',
    title: 'Dictation',
    note: 'Các phím này hoạt động cả khi đang gõ đáp án, nên không dùng được chữ, số hay Space đơn lẻ.',
  },
  { scope: 'shadowing', title: 'Shadowing' },
  { scope: 'flashcard', title: 'Flashcard (Bộ từ & Ôn tập)', note: 'Không hoạt động khi đang gõ trong chế độ gõ từ.' },
  {
    scope: 'game',
    title: 'Trò chơi Dịch nhanh',
    note: 'Các phím này hoạt động cả khi đang gõ đáp án, nên không dùng được chữ, số hay Space đơn lẻ. Enter luôn là nộp đáp án.',
  },
];

export const SHORTCUT_ACTIONS: ShortcutAction[] = [
  { id: 'dictation.playPause', scope: 'dictation', description: 'Phát / tạm dừng', defaults: ['Tab', 'Ctrl+Space'], inInput: true },
  { id: 'dictation.replay', scope: 'dictation', description: 'Nghe lại câu', defaults: ['`', 'Ctrl+R'], inInput: true },
  { id: 'dictation.submit', scope: 'dictation', description: 'Nộp bài / sang câu kế nếu đã đúng', defaults: ['Enter'], inInput: true },
  { id: 'dictation.next', scope: 'dictation', description: 'Câu kế', defaults: ['Shift+ArrowRight', 'Mod+ArrowRight'], inInput: true },
  { id: 'dictation.prev', scope: 'dictation', description: 'Câu trước', defaults: ['Shift+ArrowLeft', 'Mod+ArrowLeft'], inInput: true },
  { id: 'dictation.hint', scope: 'dictation', description: 'Gợi ý 1 từ', defaults: ['Ctrl+H'], inInput: true },

  { id: 'shadowing.record', scope: 'shadowing', description: 'Ghi âm / dừng', defaults: ['Shift+`'] },
  { id: 'shadowing.playTake', scope: 'shadowing', description: 'Nghe lại bản ghi', defaults: ['Space'] },
  { id: 'shadowing.next', scope: 'shadowing', description: 'Câu kế', defaults: ['Shift+ArrowRight'], inInput: true },
  { id: 'shadowing.prev', scope: 'shadowing', description: 'Câu trước', defaults: ['Shift+ArrowLeft'], inInput: true },

  { id: 'flashcard.flip', scope: 'flashcard', description: 'Lật thẻ', defaults: ['Space', 'Enter'] },
  { id: 'flashcard.again', scope: 'flashcard', description: 'Học lại (Again)', defaults: ['1'] },
  { id: 'flashcard.hard', scope: 'flashcard', description: 'Khó (Hard)', defaults: ['2'] },
  { id: 'flashcard.good', scope: 'flashcard', description: 'Tốt (Good)', defaults: ['3'] },
  { id: 'flashcard.easy', scope: 'flashcard', description: 'Dễ (Easy)', defaults: ['4'] },

  { id: 'game.hint', scope: 'game', description: 'Gợi ý (mất một phần điểm)', defaults: ['Ctrl+H'], inInput: true },
  { id: 'game.skip', scope: 'game', description: 'Bỏ qua câu này', defaults: ['Tab'], inInput: true },
];

export type ShortcutActionId = (typeof SHORTCUT_ACTIONS)[number]['id'];
export type ShortcutOverrides = Record<string, string[]>;

const BY_ID = new Map(SHORTCUT_ACTIONS.map((a) => [a.id, a]));

export function getAction(id: string): ShortcutAction {
  const a = BY_ID.get(id);
  if (!a) throw new Error(`Unknown shortcut action: ${id}`);
  return a;
}

/** Effective combos of an action: the user's override when present, else the defaults. */
export function resolveCombos(id: string, overrides: ShortcutOverrides): string[] {
  const o = overrides[id];
  return o && o.length > 0 ? o : getAction(id).defaults;
}

/** Keys a screen uses itself (not customisable), per scope. */
const SCOPE_RESERVED: Partial<Record<ShortcutScope, string[]>> = { game: ['Enter'] };

/** Combos that the help modal (`?`) opens with — never assignable. */
export const RESERVED_COMBOS = ['?', 'Shift+/'];

// KeyboardEvent.code → the unshifted key, so Shift+` is stored as "Shift+`" rather than "Shift+~".
const CODE_TO_KEY: Record<string, string> = {
  Backquote: '`',
  Minus: '-',
  Equal: '=',
  BracketLeft: '[',
  BracketRight: ']',
  Backslash: '\\',
  Semicolon: ';',
  Quote: "'",
  Comma: ',',
  Period: '.',
  Slash: '/',
  Space: 'Space',
};

const MODIFIER_KEYS = new Set(['Control', 'Shift', 'Alt', 'Meta', 'OS', 'AltGraph', 'CapsLock', 'Fn']);

/**
 * Turns a key press into a combo string (`Ctrl+Shift+K`, `Alt+ArrowRight`, `Shift+\``, `F2`),
 * or `null` while only modifiers are held / for keys that cannot be bound (Escape cancels).
 */
export function comboFromEvent(e: HotkeyEventLike): string | null {
  if (MODIFIER_KEYS.has(e.key) || e.key === 'Escape' || e.key === 'Dead' || e.key === 'Process' || e.key === 'Unidentified') {
    return null;
  }
  let key: string;
  const code = e.code ?? '';
  if (/^Key[A-Z]$/.test(code)) key = code.slice(3);
  else if (/^Digit[0-9]$/.test(code)) key = code.slice(5);
  else if (CODE_TO_KEY[code]) key = CODE_TO_KEY[code];
  else if (e.key === ' ') key = 'Space';
  else if (e.key.length === 1) key = e.key.toUpperCase();
  else key = e.key; // Enter, Tab, ArrowLeft, F2, Home, Backspace…
  const mods: string[] = [];
  if (e.ctrlKey) mods.push('Ctrl');
  if (e.altKey) mods.push('Alt');
  if (e.shiftKey) mods.push('Shift');
  if (e.metaKey) mods.push('Meta');
  return [...mods, key].join('+');
}

/** Canonical form used to compare combos (`ctrl+space` ≡ `Ctrl+Space`). */
export function normalizeCombo(combo: string): string {
  const c = parseCombo(combo);
  const mods = [c.mod && 'mod', c.ctrl && 'ctrl', c.alt && 'alt', c.shift && 'shift', c.meta && 'meta'].filter(Boolean);
  return [...mods, c.key].join('+');
}

/** Ctrl+X conflicts with Mod+X (and ⌘+X with Mod+X) since Mod matches both. */
function combosOverlap(a: string, b: string): boolean {
  const na = normalizeCombo(a);
  const nb = normalizeCombo(b);
  if (na === nb) return true;
  const pa = parseCombo(a);
  const pb = parseCombo(b);
  if (pa.key !== pb.key || pa.alt !== pb.alt || pa.shift !== pb.shift) return false;
  const ctrlish = (p: typeof pa) => p.mod || p.ctrl;
  const metaish = (p: typeof pa) => p.mod || p.meta;
  return (pa.mod || pb.mod) && ((ctrlish(pa) && ctrlish(pb)) || (metaish(pa) && metaish(pb)));
}

/** A combo that would type text into the answer box (letters, digits, Space, with or without Shift). */
function typesText(combo: string): boolean {
  const c = parseCombo(combo);
  if (c.ctrl || c.alt || c.meta || c.mod) return false;
  return /^[a-z0-9 ]$/.test(c.key);
}

/**
 * Validates assigning `combo` to `actionId` given the current overrides. Returns a Vietnamese
 * error message, or `null` when the combo can be used.
 */
export function validateCombo(
  actionId: string,
  combo: string,
  overrides: ShortcutOverrides,
  /** The action's other combos (when replacing one, pass the list without it). */
  ownCombos: string[] = resolveCombos(actionId, overrides)
): string | null {
  const action = getAction(actionId);
  if (RESERVED_COMBOS.some((r) => combosOverlap(r, combo))) {
    return 'Phím "?" dùng để mở bảng phím tắt, hãy chọn phím khác.';
  }
  if ((SCOPE_RESERVED[action.scope] ?? []).some((r) => combosOverlap(r, combo))) {
    return 'Enter dùng để nộp đáp án, hãy chọn phím khác.';
  }
  if (action.inInput && typesText(combo)) {
    return 'Phím này sẽ gõ chữ vào ô đáp án. Hãy kết hợp với Ctrl hoặc Alt, hoặc dùng phím như Tab, mũi tên, F1–F12, `.';
  }
  if (ownCombos.some((c) => normalizeCombo(c) === normalizeCombo(combo))) {
    return 'Hành động này đã có phím này.';
  }
  for (const other of SHORTCUT_ACTIONS) {
    if (other.id === actionId || other.scope !== action.scope) continue;
    if (resolveCombos(other.id, overrides).some((c) => combosOverlap(c, combo))) {
      return `Trùng với "${other.description}". Hãy đổi phím của hành động đó trước.`;
    }
  }
  return null;
}

/** Display label for the first combo of an action, e.g. "Ctrl+H" (for button titles). */
export function primaryLabel(combos: string[]): string {
  return combos.length ? comboToLabels(combos[0]).join('+') : '';
}

/** One `useHotkeys` binding per combo of an action (all combos share the handler/options). */
export function bindAction(combos: string[], binding: Omit<HotkeyBinding, 'combo'>): HotkeyBinding[] {
  return combos.map((combo) => ({ ...binding, combo }));
}

/** True for the bare key (no modifiers), e.g. `isBareKey('Tab', 'tab')`. */
export function isBareKey(combo: string, key: string): boolean {
  const c = parseCombo(combo);
  return !c.ctrl && !c.alt && !c.shift && !c.meta && !c.mod && c.key === key.toLowerCase();
}
