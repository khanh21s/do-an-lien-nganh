'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { deckIcon } from '@/components/vocab/deckIcon';
import { DeckStudyArea, useDeckDetail } from '@/components/vocab/study/DeckStudyArea';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { VocabDeck } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect } from 'react';

const DECK_LIST_PARAMS = { limit: 100 };

export default function DeckDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { data: decks } = useQuery({
    queryKey: qk.decks(DECK_LIST_PARAMS),
    queryFn: () => vocabularyApi.listDecks(DECK_LIST_PARAMS),
  });
  const deckList = decks?.data ?? [];
  // P2-C: name + groups of any deck the user can see (public or own personal deck).
  const { data: detail } = useDeckDetail(params.id);
  const currentDeck = deckList.find((d) => d.id === params.id);

  // Personal decks are studied under "Bộ thẻ của tôi" (old links land here).
  useEffect(() => {
    if (detail?.personal) router.replace(`/vocabulary/my-decks/${params.id}/study`);
  }, [detail?.personal, params.id, router]);
  if (detail?.personal) return null;

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
      <TopicList decks={deckList} activeId={params.id} />
      <DeckStudyArea key={params.id} deckId={params.id} deckName={currentDeck?.name} detail={detail} personal={false} />
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
