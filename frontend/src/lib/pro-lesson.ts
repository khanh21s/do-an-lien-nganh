// P2-A1 "preview, lock practice": a free user can open a Pro lesson and read its transcript, but
// Dictation / Shadowing show the upgrade card. practice-service enforces the same rule (403).

export interface LessonAccess {
  /** `GET /subscriptions/user`.isPremium; undefined while loading / unknown. */
  isPremium?: boolean | null;
  /** `GET /auth/profile`.isAdmin. */
  isAdmin?: boolean | null;
}

/** True when practising this lesson is locked for the user. Unknown status → not locked (server decides). */
export function isLessonLocked(lesson: { isPro?: boolean | null } | null | undefined, access: LessonAccess): boolean {
  if (!lesson?.isPro) return false;
  if (access.isAdmin) return false;
  return access.isPremium === false;
}

export const PRO_LESSON_MESSAGE =
  'Bạn vẫn xem được nội dung và transcript. Nâng cấp Premium để luyện Dictation và Shadowing bài này.';
