import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { storageKeys } from '@/lib/storageKeys';
import {
  SHORTCUT_ACTIONS,
  getAction,
  normalizeCombo,
  resolveCombos,
  type ShortcutOverrides,
  type ShortcutScope,
} from '@/lib/shortcuts';

interface ShortcutState {
  /** actionId → combos; only actions the user changed are stored. */
  overrides: ShortcutOverrides;
  setCombos: (actionId: string, combos: string[]) => void;
  resetAction: (actionId: string) => void;
  resetScope: (scope: ShortcutScope) => void;
}

/** User-customised keyboard shortcuts, persisted per browser (localStorage). */
export const useShortcutStore = create<ShortcutState>()(
  persist(
    (set) => ({
      overrides: {},
      setCombos: (actionId, combos) =>
        set((s) => {
          const defaults = getAction(actionId).defaults.map(normalizeCombo);
          const same =
            combos.length === defaults.length && combos.every((c, i) => normalizeCombo(c) === defaults[i]);
          const overrides = { ...s.overrides };
          if (same) delete overrides[actionId];
          else overrides[actionId] = combos;
          return { overrides };
        }),
      resetAction: (actionId) =>
        set((s) => {
          const overrides = { ...s.overrides };
          delete overrides[actionId];
          return { overrides };
        }),
      resetScope: (scope) =>
        set((s) => {
          const overrides = { ...s.overrides };
          for (const a of SHORTCUT_ACTIONS) if (a.scope === scope) delete overrides[a.id];
          return { overrides };
        }),
    }),
    {
      name: storageKeys.shortcuts,
      version: 1,
      // Drop overrides for actions that no longer exist.
      merge: (persisted, current) => {
        const raw = ((persisted as Partial<ShortcutState>)?.overrides ?? {}) as ShortcutOverrides;
        const known = new Set(SHORTCUT_ACTIONS.map((a) => a.id));
        const overrides: ShortcutOverrides = {};
        for (const [id, combos] of Object.entries(raw)) {
          if (known.has(id) && Array.isArray(combos) && combos.every((c) => typeof c === 'string')) overrides[id] = combos;
        }
        return { ...current, overrides };
      },
    }
  )
);

/** `keys(actionId)` → the effective combos (user override or default). Re-renders on changes. */
export function useShortcutKeys(): (actionId: string) => string[] {
  const overrides = useShortcutStore((s) => s.overrides);
  return (actionId: string) => resolveCombos(actionId, overrides);
}
