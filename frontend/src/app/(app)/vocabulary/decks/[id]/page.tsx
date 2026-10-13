'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { deckIcon } from '@/components/vocab/deckIcon';
import { StudyCard } from '@/components/vocab/StudyCard';
import { StudyToolbar } from '@/components/vocab/StudyToolbar';
import { UpgradeCard } from '@/components/vocab/UpgradeCard';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { formatInterval } from '@/lib/srs';
import type { VocabCard, VocabDeck } from '@/lib/types';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 50;
const DECK_LIST_PARAMS = { limit: 100 };
/** Start loading the next page this many cards before the end of what is loaded. */
const PREFETCH_MARGIN = 5;

export default function DeckDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: decks } = useQuery({
    queryKey: qk.decks(DECK_LIST_PARAMS),
    queryFn: () => vocabularyApi.listDecks(DECK_LIST_PARAMS),
  });
  const deckList = decks?.data ?? [];
  const currentDeck = deckList.find((d) => d.id === params.id);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
      <TopicList decks={deckList} activeId={params.id} />
      {/* Remount per deck so the study position resets. */}
      <DeckStudy key={params.id} deckId={params.id} deck={currentDeck} />
    </div>
  );
}

function DeckStudy({ deckId, deck }: { deckId: string; deck?: VocabDeck }) {
  const queryClient = useQueryClient();
  const [index, setIndex] = useState(0);
  const [typingMode, setTypingMode] = useState(false);
  const [proBlocked, setProBlocked] = useState<string | null>(null);
  const [lastResult, setLastResult] = useState<{ word: string; interval: number } | null>(null);
  // cardId -> userVocabId for cards added during this visit (the server value comes in card.userVocabId).
  const [added, setAdded] = useState<Record<string, string>>({});

  const cardsQuery = useInfiniteQuery({
    queryKey: qk.deckCardsInfinite(deckId),
    queryFn: ({ pageParam }) => vocabularyApi.getDeckCards(deckId, { page: pageParam, limit: PAGE_SIZE }),
    initialPageParam: 1,
    getNextPageParam: (last) =>
      last.pagination.page < last.pagination.totalPages ? last.pagination.page + 1 : undefined,
  });
  const { data, isLoading, isError, error, refetch, hasNextPage, isFetchingNextPage, fetchNextPage } = cardsQuery;

  const cards = data?.pages.flatMap((p) => p.data) ?? [];
  const total = data?.pages[0]?.pagination.total ?? cards.length;
  const current: VocabCard | undefined = cards[index];

  // VOC-04: load the next page before the learner reaches the end of the loaded cards.
  useEffect(() => {
    if (hasNextPage && !isFetchingNextPage && index >= cards.length - PREFETCH_MARGIN) {
      fetchNextPage();
    }
  }, [index, cards.length, hasNextPage, isFetchingNextPage, fetchNextPage]);

  function userVocabIdOf(card: VocabCard): string | null {
    return added[card.id] ?? card.userVocabId;
  }

  async function ensureInNotebook(card: VocabCard): Promise<string> {
    const known = userVocabIdOf(card);
    if (known) return known;
    try {
      const res = await vocabularyApi.addFromLesson(card.id, 'deck');
      setAdded((m) => ({ ...m, [card.id]: res.userVocabId }));
      return res.userVocabId;
    } catch (err) {
      if (apiErrorStatus(err) !== 409) throw err;
      // Already in the notebook (e.g. added from another tab): look its id up by word.
      const found = await vocabularyApi.getLearned({ searchWord: card.word, limit: 50 });
      const match = found.data.find((w) => w.cardId === card.id);
      if (!match) throw err;
      setAdded((m) => ({ ...m, [card.id]: match.userVocabId }));
      return match.userVocabId;
    }
  }

  const gradeMutation = useMutation({
    mutationFn: async ({ card, quality }: { card: VocabCard; quality: number }) => {
      const userVocabId = await ensureInNotebook(card);
      return vocabularyApi.submitReview({ userVocabId, quality });
    },
    onSuccess: (res, { card }) => {
      setLastResult({ word: card.word, interval: res.interval });
      setIndex((i) => i + 1);
      queryClient.invalidateQueries({ queryKey: qk.vocabStats });
      queryClient.invalidateQueries({ queryKey: qk.reviewQueue });
      queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
    },
    onError: (err) => {
      if (apiErrorStatus(err) === 403) {
        setProBlocked(apiErrorMessage(err));
        return;
      }
      toast.error(apiErrorMessage(err, 'Không thể lưu kết quả.'));
    },
  });

  if (isLoading) return <FullPageSpinner />;
  if (isError) {
    if (apiErrorStatus(error) === 403) return <UpgradeCard message={apiErrorMessage(error)} deckName={deck?.name} />;
    return <ErrorState error={error} onRetry={refetch} backHref="/vocabulary" backLabel="Danh sách bộ từ" />;
  }
  if (!data) return <ErrorState notFound backHref="/vocabulary" backLabel="Danh sách bộ từ" />;
  if (proBlocked) return <UpgradeCard message={proBlocked} deckName={deck?.name} />;

  if (total === 0) {
    return (
      <Card className="text-center">
        <p className="text-slate-600">Bộ từ này chưa có thẻ nào.</p>
        <Link href="/vocabulary" className="mt-3 inline-block text-sm font-medium text-brand-600 hover:underline">
          Chọn bộ từ khác
        </Link>
      </Card>
    );
  }

  const position = Math.min(index + 1, total);
  const learnedCount = cards.filter((c) => userVocabIdOf(c)).length;

  return (
    <div className="min-w-0">
      <Link href="/vocabulary" className="mb-3 inline-flex min-h-[44px] items-center text-sm text-slate-500 hover:text-slate-700 lg:hidden">
        ← Tất cả bộ từ
      </Link>
      <Card className="p-4 sm:p-6">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate text-xs font-semibold uppercase tracking-wide text-slate-400">
              {deck?.name ?? 'Bộ từ vựng'}
            </p>
            <p className="text-sm font-semibold text-slate-600">
              Thẻ {position}/{total}
              <span className="ml-2 font-normal text-slate-400">· đã học {learnedCount}</span>
            </p>
          </div>
          <StudyToolbar typingMode={typingMode} onTypingModeChange={setTypingMode} />
        </div>
        <div
          className="mt-3 h-2 w-full overflow-hidden rounded-full bg-slate-100"
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={Math.min(index, total)}
        >
          <div
            className="h-full rounded-full bg-brand-500 transition-all"
            style={{ width: `${total ? (Math.min(index, total) / total) * 100 : 0}%` }}
          />
        </div>
        {lastResult && (
          <p className="mt-2 text-xs text-slate-500" role="status">
            Đã lưu &ldquo;{lastResult.word}&rdquo; – ôn lại sau {formatInterval(lastResult.interval)}.
          </p>
        )}

        <div className="mt-4">
          {current ? (
            <>
              <StudyCard
                key={current.id}
                card={current}
                // Learned cards: the SM-2 state is not part of CardResponse, so interval labels are hidden.
                srsState={userVocabIdOf(current) ? undefined : null}
                onRate={(quality) => gradeMutation.mutate({ card: current, quality })}
                busy={gradeMutation.isPending}
                typingMode={typingMode}
                badge={userVocabIdOf(current) ? <Badge tone="green">✓ Đã học</Badge> : <Badge tone="slate">Từ mới</Badge>}
              />
              <div className="mt-4 flex justify-between gap-2">
                <Button
                  variant="ghost"
                  className="min-h-[44px]"
                  disabled={index === 0}
                  onClick={() => setIndex((i) => Math.max(0, i - 1))}
                >
                  ← Thẻ trước
                </Button>
                <Button
                  variant="ghost"
                  className="min-h-[44px]"
                  disabled={index + 1 >= total}
                  onClick={() => setIndex((i) => i + 1)}
                >
                  Bỏ qua →
                </Button>
              </div>
            </>
          ) : index < total && (hasNextPage || isFetchingNextPage) ? (
            <div className="flex flex-col items-center gap-3 py-10">
              {isFetchingNextPage ? (
                <Spinner />
              ) : (
                <Button className="min-h-[44px]" onClick={() => fetchNextPage()}>
                  Tải thêm thẻ
                </Button>
              )}
            </div>
          ) : (
            <div className="flex flex-col items-center py-8 text-center">
              <p className="text-lg font-semibold text-slate-900">🎉 Bạn đã đi hết bộ từ này!</p>
              <p className="mt-2 text-sm text-slate-500">Ôn lại các từ đã lưu hoặc chọn một bộ từ khác.</p>
              <div className="mt-4 flex flex-wrap justify-center gap-3">
                <Link
                  href="/vocabulary/review"
                  className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
                >
                  Ôn tập từ đã lưu
                </Link>
                <Button variant="secondary" className="min-h-[44px]" onClick={() => setIndex(0)}>
                  Học lại từ đầu
                </Button>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  );
}

function TopicList({ decks, activeId }: { decks: VocabDeck[]; activeId: string }) {
  return (
    <Card className="hidden h-fit p-4 lg:block">
      <p className="mb-3 px-1 text-xs font-semibold uppercase tracking-wide text-slate-400">Danh sách chủ đề</p>
      <div className="space-y-2">
        {decks.map((deck) => {
          const active = deck.id === activeId;
          return (
            <Link
              key={deck.id}
              href={`/vocabulary/decks/${deck.id}`}
              aria-current={active ? 'page' : undefined}
              className={clsx(
                'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
                active ? 'bg-ink-800 text-white' : 'bg-slate-50 text-slate-700 hover:bg-slate-100'
              )}
            >
              <span
                className={clsx(
                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg',
                  active ? 'bg-white/15' : 'bg-white'
                )}
              >
                {deckIcon(deck.id)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">{deck.name}</span>
                <span className={clsx('text-xs', active ? 'text-white/70' : 'text-slate-400')}>{deck.cardCount} thẻ</span>
              </span>
              {deck.isProOnly && <Badge tone="amber">PRO</Badge>}
            </Link>
          );
        })}
        {decks.length === 0 && <p className="px-1 text-sm text-slate-500">Chưa có bộ từ vựng nào.</p>}
      </div>
    </Card>
  );
}
