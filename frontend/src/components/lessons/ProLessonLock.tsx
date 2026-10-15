import { UpgradeCard } from '@/components/vocab/UpgradeCard';
import { PRO_LESSON_MESSAGE } from '@/lib/pro-lesson';
import clsx from 'clsx';

/** P2-A1: replaces the Dictation / Shadowing screen of a Pro lesson for non-premium users. */
export function ProLessonLock({
  lessonId,
  lessonTitle,
  message,
  backHref,
  backLabel,
}: {
  lessonId: string;
  lessonTitle?: string;
  /** Backend 403 text when the lock came from the server. */
  message?: string;
  backHref?: string;
  backLabel?: string;
}) {
  return (
    <div className="mt-6">
      <UpgradeCard
        title={lessonTitle ? `“${lessonTitle}” là bài học Premium` : 'Bài học Premium'}
        message={message || PRO_LESSON_MESSAGE}
        backHref={backHref ?? `/lessons/${lessonId}`}
        backLabel={backLabel ?? 'Xem bài & transcript'}
      />
    </div>
  );
}

/** Small "Pro" badge used on lesson cards, the lesson page and the admin list. */
export function ProBadge({ className }: { className?: string }) {
  return (
    <span
      className={clsx(
        'inline-flex shrink-0 items-center gap-1 rounded-full bg-pro-gradient px-2.5 py-0.5 text-xs font-semibold text-white',
        className
      )}
    >
      👑 Pro
    </span>
  );
}
