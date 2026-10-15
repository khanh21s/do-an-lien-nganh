import type { GamificationCheckInState, GamificationMe, GamificationMission } from '@/lib/types';

/**
 * P2-B pure helpers of the app-wide gamification UI. Amounts are decided by vocabulary-service
 * (XpRules); the table below only mirrors them for help texts.
 */
export const XP_RULES: readonly { label: string; xp: string }[] = [
  { label: 'Một câu Dictation', xp: '+5 XP (+5 nếu đúng 100%)' },
  { label: 'Một câu Shadowing', xp: '+5 XP (+5 nếu đạt ≥ 85 điểm)' },
  { label: 'Ôn một thẻ từ vựng', xp: '+2 XP' },
  { label: 'Một ván Dịch nhanh', xp: 'XP = điểm của ván' },
];

export const XP_RULES_NOTE = 'Mỗi câu (theo từng chế độ) và mỗi thẻ chỉ tính XP một lần mỗi ngày.';

function clampPercent(value: number, total: number): number {
  if (total <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((value / total) * 100)));
}

/** Progress inside the current level, 0–100. */
export function levelPercent(me: Pick<GamificationMe, 'levelXp' | 'levelXpNeeded'>): number {
  return clampPercent(me.levelXp, me.levelXpNeeded);
}

export function missionPercent(m: Pick<GamificationMission, 'progress' | 'target'>): number {
  return clampPercent(m.progress, m.target);
}

export function missionsDone(missions: readonly GamificationMission[]): number {
  return missions.filter((m) => m.completed).length;
}

/** "1.234" (Vietnamese grouping) for XP / diamond counts. */
export function formatCount(n: number): string {
  return n.toLocaleString('vi-VN');
}

export function checkInLabel(c: GamificationCheckInState): string {
  if (c.checkedInToday) return `Đã điểm danh · chuỗi ${c.streak} ngày`;
  return `Điểm danh +${c.todayReward} 💎`;
}

export function checkInHint(c: GamificationCheckInState): string {
  if (!c.checkedInToday && c.daysToBonus === 1) return 'Hôm nay là ngày thứ 7: nhận thêm 100 💎!';
  if (c.daysToBonus === 1) return 'Ngày mai điểm danh để nhận thưởng 7 ngày (+100 💎).';
  return `Còn ${c.daysToBonus} ngày điểm danh liên tiếp để nhận thêm 100 💎.`;
}

type Snapshot = Pick<GamificationMe, 'xp' | 'level' | 'diamonds'>;

/**
 * Toast messages for what changed between two reads of `/vocabs/gamification/me`
 * ("+5 XP", "Lên cấp 3!", "+30 💎"). Nothing on the first read or when a number went down
 * (another account, a future shop purchase).
 */
export function gainMessages(prev: Snapshot | null | undefined, next: Snapshot): string[] {
  if (!prev) return [];
  const out: string[] = [];
  if (next.xp > prev.xp) out.push(`+${formatCount(next.xp - prev.xp)} XP`);
  if (next.level > prev.level && next.xp > prev.xp) out.push(`🎉 Lên cấp ${next.level}!`);
  if (next.diamonds > prev.diamonds) out.push(`+${formatCount(next.diamonds - prev.diamonds)} 💎`);
  return out;
}

/**
 * Practice attempts reach vocabulary-service asynchronously (practice-service fires the XP event
 * after saving), so refresh the gamification numbers a moment later as well.
 */
export const GAMIFICATION_REFRESH_DELAY_MS = 1500;
