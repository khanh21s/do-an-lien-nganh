import type { SentenceReviewOutcome } from '@/lib/types';
import type { ReactNode } from 'react';

/**
 * P2-A2: lets the "Ôn câu sai" page reuse DictationPractice / ShadowingPractice for one queued
 * sentence. Attempts carry `reviewId`, navigation and header come from the review page.
 */
export interface PracticeReviewMode {
  reviewId: string;
  /** Replaces PracticeHeader; `right` = the screen's own controls (difficulty, shortcuts). */
  renderHeader: (right: ReactNode) => ReactNode;
  /** Last item of the review session ("Hoàn thành" instead of "Câu kế"). */
  isLast: boolean;
  onNext: () => void;
  /** Called after every scored attempt with its effect on the queue. */
  onResult?: (outcome: SentenceReviewOutcome | null) => void;
}
