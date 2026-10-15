'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { deckIcon, deckTint } from '@/components/vocab/deckIcon';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';

const DECK_LIST_PARAMS = { limit: 100 };

export default function VocabularyDecksPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.decks(DECK_LIST_PARAMS),
    queryFn: () => vocabularyApi.listDecks(DECK_LIST_PARAMS),
  });

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bộ từ vựng</h1>
          <p className="mt-1 text-slate-500">Chọn một bộ từ vựng để bắt đầu học.</p>
        </div>
        <div className="flex gap-2">
          <Link
            href="/vocabulary/review"
            className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            Ôn tập
          </Link>
          <Link
            href="/vocabulary/notebook"
            className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Sổ tay
          </Link>
          {/* P2-C */}
          <Link
            href="/vocabulary/my-decks"
            className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Bộ thẻ của tôi
          </Link>
        </div>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-6" />
      ) : !data || data.data.length === 0 ? (
        <Card className="mt-6 text-center text-slate-500">Chưa có bộ từ vựng nào.</Card>
      ) : (
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data.data.map((deck) => (
            <Link key={deck.id} href={`/vocabulary/decks/${deck.id}`}>
              <Card className="h-full transition-shadow hover:shadow-md">
                <div className="flex items-start justify-between gap-2">
                  <span
                    className={clsx(
                      'flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br text-xl',
                      deckTint(deck.id)
                    )}
                  >
                    {deckIcon(deck.id)}
                  </span>
                  {deck.isProOnly && <Badge tone="amber">👑 Premium</Badge>}
                </div>
                <h3 className="mt-3 font-semibold text-slate-900">{deck.name}</h3>
                {deck.description && <p className="mt-1 line-clamp-2 text-sm text-slate-500">{deck.description}</p>}
                <div className="mt-4 flex items-center gap-2 text-xs text-slate-400">
                  {deck.level && <Badge tone="brand">{deck.level}</Badge>}
                  <span>{deck.cardCount} từ</span>
                </div>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
