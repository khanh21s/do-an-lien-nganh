import type { PracticeMode } from './types';

/** Browser storage keys (architecture §6.6). Never hard-code these strings elsewhere. */
export const storageKeys = {
  // localStorage (read/write through `useLocalStorage` / `readLocalStorage`)
  playbackRate: 'parroto:playback-rate',
  dictationDifficulty: 'parroto:dictation-difficulty',
  transcriptIpa: 'parroto:transcript-ipa',
  transcriptTranslation: 'parroto:transcript-translation',
  sidebarCollapsed: 'parroto:sidebar-collapsed',
  reviewDailyLimit: 'parroto:review-daily-limit',
  reviewPopupDate: 'parroto:review-popup-date',
  shortcuts: 'parroto:shortcuts',
  /** T15: last word source picked in the game lobby. */
  gameSource: 'parroto:game-source',

  // sessionStorage (see lib/practice-session.ts)
  practiceSession: (lessonId: string, mode: PracticeMode) => `parroto:session:${lessonId}:${mode}`,
} as const;

/** SSR-safe JSON read from localStorage; returns `fallback` on any error. */
export function readLocalStorage<T>(key: string, fallback: T): T {
  if (typeof window === 'undefined') return fallback;
  try {
    const raw = window.localStorage.getItem(key);
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

/** SSR-safe JSON write to localStorage; silently ignores quota/privacy errors. */
export function writeLocalStorage<T>(key: string, value: T): void {
  if (typeof window === 'undefined') return;
  try {
    window.localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // ignore
  }
}
