'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

const TOPIC_ICONS = ['👪', '🏫', '💼', '🍜', '🧳', '🏥', '🛍️', '🎉', '⚽', '🎬', '💻', '🌦️'];
const TOPIC_TINTS = [
  'from-amber-200 to-brand-200',
  'from-emerald-200 to-brand-200',
  'from-violet-200 to-brand-200',
  'from-rose-200 to-amber-200',
];

export default function VocabularyDecksPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['decks'],
    queryFn: () => vocabularyApi.listDecks({ limit: 50 }),
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Bộ từ vựng</h1>
      <p className="mt-1 text-slate-500">Chọn một bộ từ vựng để bắt đầu học.</p>

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data?.data.map((deck, i) => (
            <Link key={deck.id} href={`/vocabulary/decks/${deck.id}`}>
              <Card className="h-full transition-shadow hover:shadow-md">
                <div className="flex items-start justify-between gap-2">
                  <span
                    className={`flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br text-xl ${TOPIC_TINTS[i % TOPIC_TINTS.length]}`}
                  >
                    {TOPIC_ICONS[i % TOPIC_ICONS.length]}
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
          {data?.data.length === 0 && <p className="text-slate-500">Chưa có bộ từ vựng nào.</p>}
        </div>
      )}
    </div>
  );
}
