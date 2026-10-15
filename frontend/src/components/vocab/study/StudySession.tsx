'use client';

import { refreshGamification } from '@/components/gamification/useGamification'; // P2-B
import { Badge } from '@/components/ui/Badge';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { StudyCard } from '@/components/vocab/StudyCard';
import { StudyToolbar } from '@/components/vocab/StudyToolbar';
import { UpgradeCard } from '@/components/vocab/UpgradeCard';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { newShuffleSeed } from '@/lib/shuffle';
import type { StudyCardItem, StudyQueue, StudySession as Session } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { StudyCounters } from './StudyCounters';
import { StudyDone } from './StudyDone';

/** Which cards the session covers: one deck (optionally one group) or all of the user's cards. */
export type StudyScope = { kind: 'deck'; deckId: string; groupId: string | null } | { kind: 'global' };

/** Refetch automatically when the next card falls due within this window (e.g. a learning step). */
const AUTO_REFRESH_WINDOW_MS = 60 * 60 * 1000;

const QUEUE_BADGE: Record<StudyQueue, { tone: 'slate' | 'red' | 'green'; text: string }> = {
  new: { tone: 'slate', text: 'Từ mới' },
  learning: { tone: 'red', text: 'Đang học' },
  review: { tone: 'green', text: 'Ôn tập' },
};

/**
 * P2-anki: an Anki-style session. The server picks the next card (due learning → due review → new →
 * learn ahead) and computes the button labels; every answer returns the refreshed session, which
 * replaces the cached one. The seed fixes the random order of new cards for this visit.
 */
export function useStudySession(scope: StudyScope) {
  const queryClient = useQueryClient();
  const [seed] = useState(newShuffleSeed);
  const deckId = scope.kind === 'deck' ? scope.deckId : undefined;
  const groupId = scope.kind === 'deck' ? scope.groupId : null;
  const queryKey = deckId ? qk.deckStudy(deckId, groupId, seed) : qk.studySession(seed);

  const query = useQuery({
    queryKey,
    queryFn: () =>
      deckId
        ? vocabularyApi.getDeckStudy(deckId, { seed, ...(groupId ? { groupId } : {}) })
        : vocabularyApi.getStudy({ seed }),
    // The card on screen must not change under the learner; answers update the cache instead.
    refetchOnWindowFocus: false,
    staleTime: Infinity,
    retry: (count, err) => ![401, 403, 404].includes(apiErrorStatus(err) ?? 0) && count < 2,
  });

  const [ratings, setRatings] = useState<number[]>([]);
  /** Increments per answer: part of the card key, since a learning card can follow itself. */
  const [answered, setAnswered] = useState(0);
  const [last, setLast] = useState<{ word: string; label: string } | null>(null);
  const [blocked, setBlocked] = useState<string | null>(null);

  const grade = useMutation({
    mutationFn: ({ card, rating }: { card: StudyCardItem; rating: number }) =>
      vocabularyApi.submitReview({
        ...(card.userVocabId ? { userVocabId: card.userVocabId } : { cardId: card.cardId }),
        rating: rating as 1 | 2 | 3 | 4,
        seed,
        ...(deckId ? { deckId, ...(groupId ? { groupId } : {}) } : {}),
      }),
    onSuccess: (res, { card, rating }) => {
      queryClient.setQueryData<Session>(queryKey, res.session);
      setRatings((r) => [...r, rating]);
      setAnswered((n) => n + 1);
      setLast({ word: card.word, label: res.label });
      refreshGamification(queryClient); // P2-B: +2 XP per card per day
      void queryClient.invalidateQueries({ queryKey: qk.vocabStats });
      void queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
    },
    onError: (err) => {
      if (apiErrorStatus(err) === 403) {
        setBlocked(apiErrorMessage(err));
        return;
      }
      toast.error(apiErrorMessage(err, 'Không thể lưu kết quả.'));
    },
  });

  // Done for now, but a card falls due soon (learning step, the next day at 04:00): fetch it then.
  const nextDueAt = query.data && !query.data.card ? query.data.nextDueAt : null;
  const { refetch } = query;
  useEffect(() => {
    if (!nextDueAt) return;
    const wait = Date.parse(nextDueAt) - Date.now();
    if (Number.isNaN(wait) || wait > AUTO_REFRESH_WINDOW_MS) return;
    const t = window.setTimeout(() => void refetch(), Math.max(wait, 0) + 1000);
    return () => window.clearTimeout(t);
  }, [nextDueAt, refetch]);

  return { query, grade, ratings, answered, last, blocked };
}

/**
 * Session UI shared by the deck page and "Ôn tập": counters, the card (typing mode included), the
 * four buttons with server labels, keys 1–4, and the "Đã xong hôm nay" screen.
 */
export function StudySessionPanel({
  scope,
  header,
  doneActions,
  emptyState,
  doneHint,
  deckName,
  backHref = '/vocabulary',
}: {
  scope: StudyScope;
  /** Title area left of the toolbar. */
  header: React.ReactNode;
  doneActions: React.ReactNode;
  /** Shown instead of the done screen when the scope has no cards at all. */
  emptyState?: React.ReactNode;
  doneHint?: React.ReactNode;
  deckName?: string;
  backHref?: string;
}) {
  const [typingMode, setTypingMode] = useState(false);
  const { query, grade, ratings, answered, last, blocked } = useStudySession(scope);
  const { data, isLoading, isError, error, refetch } = query;

  if (isLoading) return <FullPageSpinner />;
  if (isError) {
    if (apiErrorStatus(error) === 403) return <UpgradeCard message={apiErrorMessage(error)} deckName={deckName} />;
    return <ErrorState error={error} onRetry={() => void refetch()} backHref={backHref} backLabel="Danh sách bộ từ" />;
  }
  if (!data) return <ErrorState notFound backHref={backHref} backLabel="Danh sách bộ từ" />;
  if (blocked) return <UpgradeCard message={blocked} deckName={deckName} />;

  const card = data.card;
  const total = data.counts.new + data.counts.learning + data.counts.review;
  const empty = !card && !data.nextDueAt && total === 0 && ratings.length === 0;

  return (
    <div className="min-w-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">{header}</div>
        {card && <StudyToolbar typingMode={typingMode} onTypingModeChange={setTypingMode} />}
      </div>

      {empty && emptyState ? (
        <div className="mt-6">{emptyState}</div>
      ) : (
        <>
          <div className="mt-3">
            <StudyCounters counts={data.counts} active={card?.queue ?? null} />
          </div>
          {last && (
            <p className="mt-2 text-center text-xs text-slate-500" role="status">
              Đã lưu &ldquo;{last.word}&rdquo; – gặp lại sau {last.label}.
            </p>
          )}
          <div className="mt-4">
            {card ? (
              <StudyCard
                key={`${card.cardId}:${answered}`}
                card={card}
                previews={card.previews}
                onRate={(rating) => grade.mutate({ card, rating })}
                busy={grade.isPending}
                typingMode={typingMode}
                badge={<Badge tone={QUEUE_BADGE[card.queue].tone}>{QUEUE_BADGE[card.queue].text}</Badge>}
              />
            ) : (
              <StudyDone ratings={ratings} nextDueAt={data.nextDueAt} hint={doneHint}>
                {doneActions}
              </StudyDone>
            )}
          </div>
        </>
      )}
    </div>
  );
}
