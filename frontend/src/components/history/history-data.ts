import type { AttemptSummary, Difficulty, PracticeMode } from '@/lib/types';

// Pure helpers for /practice/history (PRA-05). No React, unit-tested.

const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export type ModeFilter = 'all' | PracticeMode;

export const MODE_FILTERS: { value: ModeFilter; label: string }[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'dictation', label: 'Dictation' },
  { value: 'shadowing', label: 'Shadowing' },
];

export const MODE_LABELS: Record<PracticeMode, string> = {
  dictation: 'Dictation',
  shadowing: 'Shadowing',
};

export const DIFFICULTY_LABELS: Record<Difficulty, string> = {
  Easy: 'Dễ',
  Normal: 'Vừa',
  Hard: 'Khó',
};

export function isPracticeMode(mode: string): mode is PracticeMode {
  return mode === 'dictation' || mode === 'shadowing';
}

export function modeLabel(mode: string): string {
  return isPracticeMode(mode) ? MODE_LABELS[mode] : mode;
}

export function difficultyLabel(difficulty: Difficulty | string | null | undefined): string {
  if (!difficulty) return '—';
  return (DIFFICULTY_LABELS as Record<string, string>)[difficulty] ?? difficulty;
}

/** Distinct lesson ids in first-seen order: one `GET /content/lessons/{id}` per lesson. */
export function uniqueLessonIds(attempts: readonly Pick<AttemptSummary, 'lessonId'>[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const a of attempts) {
    if (a.lessonId && !seen.has(a.lessonId)) {
      seen.add(a.lessonId);
      out.push(a.lessonId);
    }
  }
  return out;
}

/** ISO timestamp → 'dd/MM/yyyy HH:mm' in Vietnam time; invalid input → '—'. */
export function formatAttemptDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '—';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: VN_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('day')}/${get('month')}/${get('year')} ${get('hour')}:${get('minute')}`;
}

/** Score colour bucket shared by the table and the modal. */
export function scoreTone(score: number): 'green' | 'amber' | 'red' {
  if (score >= 80) return 'green';
  if (score >= 50) return 'amber';
  return 'red';
}

export function formatScore(score: number | null | undefined): string {
  const v = Number(score ?? 0);
  return Number.isFinite(v) ? String(Math.round(v * 10) / 10) : '0';
}
