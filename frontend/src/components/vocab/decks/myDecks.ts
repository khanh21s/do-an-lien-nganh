import type { DeckUsage, PersonalDeck, VocabGroup } from '@/lib/types';
import { formatCount } from '@/components/vocab/import/importHelpers';

/** P2-C "Bộ thẻ của tôi": pure helpers (unit tested). */

export const DECK_NAME_MAX = 100;
export const DECK_DESCRIPTION_MAX = 500;
export const GROUP_NAME_MAX = 100;

export function canCreateDeck(usage: DeckUsage | null | undefined): boolean {
  return !!usage && usage.decks < usage.maxDecks;
}

/** "2/5 bộ thẻ · 120/500 thẻ" */
export function usageLabel(usage: DeckUsage): string {
  return `${usage.decks}/${usage.maxDecks} bộ thẻ · ${formatCount(usage.cards)}/${formatCount(usage.maxCards)} thẻ`;
}

/** 0–100 for a progress bar of the card limit. */
export function usagePercent(used: number, max: number): number {
  if (max <= 0) return 100;
  return Math.min(100, Math.round((used / max) * 100));
}

export function validateDeckName(name: string): string | null {
  const s = name.trim();
  if (!s) return 'Vui lòng nhập tên bộ thẻ.';
  if (s.length > DECK_NAME_MAX) return `Tối đa ${DECK_NAME_MAX} ký tự.`;
  return null;
}

export function validateGroupName(name: string, groups: VocabGroup[], exceptId?: string): string | null {
  const s = name.trim().replace(/\s+/g, ' ');
  if (!s) return 'Vui lòng nhập tên nhóm.';
  if (s.length > GROUP_NAME_MAX) return `Tối đa ${GROUP_NAME_MAX} ký tự.`;
  if (groups.some((g) => g.id !== exceptId && g.name.toLowerCase() === s.toLowerCase())) return 'Đã có nhóm tên này.';
  return null;
}

/** Group ids after moving `groupId` one step up (-1) or down (+1); unchanged at the edges. */
export function moveGroup(groups: VocabGroup[], groupId: string, delta: -1 | 1): string[] {
  const ids = [...groups].sort((a, b) => a.sortOrder - b.sortOrder).map((g) => g.id);
  const i = ids.indexOf(groupId);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= ids.length) return ids;
  [ids[i], ids[j]] = [ids[j], ids[i]];
  return ids;
}

/** Deck picker options: the notebook first (labelled), then the other decks. */
export function deckOptions(decks: PersonalDeck[]): { id: string; label: string; isNotebook: boolean }[] {
  return [...decks]
    .sort((a, b) => Number(b.isNotebook) - Number(a.isNotebook))
    .map((d) => ({ id: d.id, label: d.isNotebook ? `📓 ${d.name}` : d.name, isNotebook: d.isNotebook }));
}
