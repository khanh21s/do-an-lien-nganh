'use client';

import { getNeighbors, practiceResultPath, practiceSentencePath, type SentenceNeighbors } from '@/lib/practice-nav';
import type { PracticeMode, SentenceResponse } from '@/lib/types';
import { useRouter } from 'next/navigation';
import { useCallback, useMemo } from 'react';

type NavSentence = Pick<SentenceResponse, 'id' | 'sentenceOrder'>;

export interface PracticeNav<S extends NavSentence> extends SentenceNeighbors<S> {
  goPrev: () => void;
  goNext: () => void;
  /** Navigates to `/lessons/[id]/result?mode=…`. */
  finish: () => void;
  /** Next sentence, or the result screen when on the last sentence. */
  goNextOrFinish: () => void;
}

/**
 * Prev/Next between sentences of a lesson (DIC-02, NAV-06). Uses `router.replace` so the browser
 * Back button leaves the lesson instead of walking back through every sentence.
 */
export function usePracticeNav<S extends NavSentence>(
  lessonId: string,
  mode: PracticeMode,
  sentences: readonly S[] | undefined,
  currentId: string
): PracticeNav<S> {
  const router = useRouter();
  const neighbors = useMemo(() => getNeighbors(sentences ?? [], currentId), [sentences, currentId]);
  const prevId = neighbors.prev?.id;
  const nextId = neighbors.next?.id;

  const goPrev = useCallback(() => {
    if (prevId) router.replace(practiceSentencePath(lessonId, mode, prevId));
  }, [router, lessonId, mode, prevId]);

  const goNext = useCallback(() => {
    if (nextId) router.replace(practiceSentencePath(lessonId, mode, nextId));
  }, [router, lessonId, mode, nextId]);

  const finish = useCallback(() => {
    router.replace(practiceResultPath(lessonId, mode));
  }, [router, lessonId, mode]);

  const goNextOrFinish = useCallback(() => {
    if (nextId) goNext();
    else finish();
  }, [nextId, goNext, finish]);

  return { ...neighbors, goPrev, goNext, finish, goNextOrFinish };
}
