'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { DeckStudyArea, useDeckDetail } from '@/components/vocab/study/DeckStudyArea';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { PersonalDeck } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams } from 'next/navigation';

/**
 * Studying one of the learner's own decks stays under "Bộ thẻ của tôi": the side list shows only the
 * learner's decks (never the admin's public decks), and the back links return to `/vocabulary/my-decks`.
 */
export default function MyDeckStudyPage() {
  const params = useParams<{ id: string }>();
  const { data: myDecks } = useQuery({ queryKey: qk.myDecks, queryFn: vocabularyApi.listMyDecks });
  const { data: detail, isError, error } = useDeckDetail(params.id);

  // A public deck (or someone else's) is not part of "Bộ thẻ của tôi".
  if (isError || (detail && !detail.personal)) {
    return (
      <ErrorState
        error={isError ? error : undefined}
        notFound={!isError}
        backHref="/vocabulary/my-decks"
        backLabel="Bộ thẻ của tôi"
      />
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
      <MyDeckList decks={myDecks?.decks ?? []} activeId={params.id} />
      <DeckStudyArea key={params.id} deckId={params.id} detail={detail} personal />
    </div>
  );
}

function MyDeckList({ decks, activeId }: { decks: PersonalDeck[]; activeId: string }) {
  return (
    <Card className="hidden h-fit p-4 lg:block">
      <div className="mb-3 flex items-center justify-between px-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">Bộ thẻ của tôi</p>
        <Link href="/vocabulary/my-decks" className="text-xs font-medium text-brand-600 hover:underline">
          Quản lý
        </Link>
      </div>
      <div className="space-y-2">
        {decks.map((deck) => {
          const active = deck.id === activeId;
          return (
            <Link
              key={deck.id}
              href={`/vocabulary/my-decks/${deck.id}/study`}
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
                aria-hidden
              >
                {deck.isNotebook ? '📓' : '🗂️'}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">{deck.name}</span>
                <span className={clsx('text-xs', active ? 'text-white/70' : 'text-slate-400')}>{deck.cardCount} thẻ</span>
              </span>
              {deck.isNotebook && <Badge tone="brand">Sổ tay</Badge>}
            </Link>
          );
        })}
        {decks.length === 0 && <p className="px-1 text-sm text-slate-500">Bạn chưa có bộ thẻ nào.</p>}
      </div>
    </Card>
  );
}
