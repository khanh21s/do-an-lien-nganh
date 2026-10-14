import type { GameBoard, GameMode, GameProgress, GameResult, GameSummaryItem } from '@/lib/types';

/**
 * Pure helpers of the "Dịch nhanh" game UI (T15/T16). Scoring itself happens on the server
 * (vocabulary-service GameScoring); these only mirror its tiers for display.
 */

/** Combo thresholds of the server multiplier (5+ ×1.5, 10+ ×2, 20+ ×3). */
export const COMBO_TIERS = [
  { at: 5, multiplier: 1.5 },
  { at: 10, multiplier: 2 },
  { at: 20, multiplier: 3 },
] as const;

export function comboMultiplier(combo: number): number {
  let m = 1;
  for (const t of COMBO_TIERS) if (combo >= t.at) m = t.multiplier;
  return m;
}

/** The next tier to reach, or null at the top tier. */
export function nextComboTier(combo: number): { at: number; multiplier: number; remaining: number } | null {
  const t = COMBO_TIERS.find((tier) => combo < tier.at);
  return t ? { at: t.at, multiplier: t.multiplier, remaining: t.at - combo } : null;
}

/** Points factor after `hints` hints (mirrors the server). */
export function hintFactor(hints: number): number {
  return [1, 0.8, 0.5, 0.25][Math.min(Math.max(hints, 0), 3)];
}

const POS_LABELS: Record<string, string> = {
  n: 'danh từ',
  noun: 'danh từ',
  v: 'động từ',
  verb: 'động từ',
  adj: 'tính từ',
  adjective: 'tính từ',
  adv: 'trạng từ',
  adverb: 'trạng từ',
  prep: 'giới từ',
  preposition: 'giới từ',
  pron: 'đại từ',
  pronoun: 'đại từ',
  conj: 'liên từ',
  conjunction: 'liên từ',
  interjection: 'thán từ',
  phrase: 'cụm từ',
  'phr v': 'cụm động từ',
  'phrasal verb': 'cụm động từ',
};

/** "n" / "noun" → "danh từ"; unknown values are shown as they are. */
export function posLabel(pos: string | null | undefined): string | null {
  if (!pos) return null;
  const key = pos.trim().toLowerCase().replace(/\.$/, '');
  return POS_LABELS[key] ?? pos;
}

/** "0:42" from milliseconds (rounded up, never negative). */
export function formatClock(ms: number): string {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export const RESULT_LABELS: Record<GameResult, string> = {
  CORRECT: 'Đúng',
  NEAR: 'Gần đúng',
  WRONG: 'Sai',
  SKIPPED: 'Bỏ qua',
};

/**
 * Words worth reviewing after a round: missed or misspelt ones, once per card (a missed card
 * asked again and then answered right is still listed), in the order they were first asked.
 */
export function wordsToReview(items: GameSummaryItem[]): GameSummaryItem[] {
  const seen = new Map<string, GameSummaryItem>();
  for (const it of items) {
    if (it.result === 'CORRECT' && it.hintsUsed === 0) continue;
    if (!seen.has(it.cardId)) seen.set(it.cardId, it);
  }
  return [...seen.values()];
}

/** Message under the final score: new record, distance to the record, or first game. */
export function recordMessage(score: number, previousBest: number | null, newRecord: boolean): string {
  if (newRecord) return previousBest == null ? 'Kỷ lục đầu tiên của bạn!' : `Kỷ lục mới! (cũ: ${previousBest})`;
  if (previousBest == null) return 'Hãy ghi điểm để lập kỷ lục.';
  if (score === previousBest) return 'Bằng kỷ lục của bạn!';
  return `Còn ${previousBest - score + 1} điểm nữa là phá kỷ lục ${previousBest}.`;
}

// --- T16 ---

export const MODE_INFO: Record<GameMode, { icon: string; title: string; short: string; text: string }> = {
  SPRINT: { icon: '⚡', title: 'Nước rút', short: '60 giây', text: 'Trả lời càng nhiều càng tốt trong 60 giây.' },
  SURVIVAL: { icon: '❤️', title: 'Sinh tồn', short: '3 mạng', text: '3 mạng, 15 giây mỗi từ, từ khó dần. Sai hoặc hết giờ mất 1 mạng.' },
  DAILY: { icon: '📅', title: 'Thử thách hôm nay', short: '10 từ', text: '10 từ giống nhau cho mọi người, 20 giây mỗi từ, chơi 1 lần mỗi ngày.' },
};

export const BOARD_LABELS: Record<GameBoard, string> = {
  daily: 'Hôm nay',
  'sprint-week': 'Nước rút tuần',
  'survival-week': 'Sinh tồn tuần',
};

export const DAILY_GOAL_OPTIONS = [100, 200, 300, 500] as const;

/** 0–100, for progress bars. */
export function percent(value: number, total: number): number {
  if (total <= 0) return 100;
  return Math.max(0, Math.min(100, Math.round((value / total) * 100)));
}

export function levelPercent(p: GameProgress): number {
  return percent(p.levelXp, p.levelXpNeeded);
}

export function goalPercent(p: GameProgress): number {
  return percent(p.todayPoints, p.dailyGoal);
}

/** "Còn 40 điểm để đạt mục tiêu" / "Đã đạt mục tiêu hôm nay". */
export function goalMessage(p: GameProgress): string {
  return p.goalMet ? 'Đã đạt mục tiêu hôm nay – được tính vào chuỗi ngày học 🔥' : `Còn ${p.dailyGoal - p.todayPoints} điểm để đạt mục tiêu`;
}

/** "Hạng 3/25 · Nước rút tuần" for the summary. */
export function rankMessage(board: GameBoard | null, rank: number | null, total: number | null): string | null {
  if (!board || !rank) return null;
  return `Hạng ${rank}${total ? `/${total}` : ''} · ${BOARD_LABELS[board]}`;
}
