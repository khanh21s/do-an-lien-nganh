import { TOPIC_ICONS } from '@/lib/constants';

// Decks have no topic tag, so pick a stable icon/tint from the shared topic icon set by deck id.
const ICONS = Object.values(TOPIC_ICONS);

const TINTS = [
  'from-amber-200 to-brand-200',
  'from-emerald-200 to-brand-200',
  'from-violet-200 to-brand-200',
  'from-rose-200 to-amber-200',
];

function hash(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function deckIcon(deckId: string): string {
  return ICONS[hash(deckId) % ICONS.length];
}

export function deckTint(deckId: string): string {
  return TINTS[hash(deckId) % TINTS.length];
}
