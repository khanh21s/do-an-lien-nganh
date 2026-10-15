// P2-A2 sentence review ("Ôn câu sai") helpers — kept free of React so they can be unit tested.
import type { LessonDetail, PracticeMode, SentenceReviewItem, SentenceReviewOutcome } from './types';

const DAY_MS = 24 * 60 * 60 * 1000;

export const REVIEW_MODE_LABELS: Record<PracticeMode, string> = {
  dictation: 'Dictation',
  shadowing: 'Shadowing',
};

/**
 * A one-sentence `LessonDetail` for the practice components: they then have no prev/next sentence
 * and the review page drives navigation itself.
 */
export function reviewLesson(item: SentenceReviewItem): LessonDetail {
  return {
    id: item.lessonId,
    title: item.lessonTitle ?? 'Bài học',
    sourceUrl: null,
    cefrLevel: 'A1',
    topicTag: null,
    description: null,
    audioUrl: item.lessonAudioUrl,
    duration: null,
    createdAt: item.createdAt,
    updatedAt: item.createdAt,
    isPro: item.lessonIsPro,
    sentences: [item.sentence],
  };
}

/** Calendar-day difference (local time) between `now` and `iso`. */
function dayDiff(iso: string, now: Date): number {
  const d = new Date(iso);
  const a = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const b = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return Math.round((a - b) / DAY_MS);
}

/** "hôm nay" / "ngày mai" / "sau 6 ngày" / "đã đến hạn". */
export function formatNextReview(iso: string | null | undefined, now: Date = new Date()): string {
  if (!iso) return '—';
  if (new Date(iso).getTime() <= now.getTime()) return 'đã đến hạn';
  const days = dayDiff(iso, now);
  if (days <= 0) return 'hôm nay';
  if (days === 1) return 'ngày mai';
  return `sau ${days} ngày`;
}

const QUALITY_LABELS: Record<number, string> = { 1: 'Học lại', 3: 'Khó', 4: 'Tốt', 5: 'Dễ' };

/** One line under the result on the review screen. */
export function reviewOutcomeText(outcome: SentenceReviewOutcome | null | undefined, now: Date = new Date()): string {
  if (!outcome) return 'Câu này không còn trong hàng ôn tập.';
  const when = formatNextReview(outcome.nextReviewAt, now);
  if (outcome.action === 'graded') {
    const label = outcome.quality != null ? QUALITY_LABELS[outcome.quality] ?? `q=${outcome.quality}` : '';
    return `Đã chấm “${label}” · lần ôn tiếp: ${when}.`;
  }
  if (outcome.action === 'queued') return 'Câu đã vào hàng “Ôn câu sai” để luyện lại.';
  return `Đã chấm cho lượt đầu · lần ôn tiếp: ${when}.`;
}

/** Index of the next item after `index` in a fixed session list, or -1 at the end. */
export function nextReviewIndex(length: number, index: number): number {
  return index + 1 < length ? index + 1 : -1;
}
