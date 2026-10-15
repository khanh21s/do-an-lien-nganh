'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { deckIcon } from '@/components/vocab/deckIcon';
import { GroupFilterChips } from '@/components/vocab/decks/GroupFilterChips';
import { StudySessionPanel } from '@/components/vocab/study/StudySession';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { VocabDeck } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';

const DECK_LIST_PARAMS = { limit: 100 };

export default function DeckDetailPage() {
  const params = useParams<{ id: string }>();
  const { data: decks } = useQuery({
    queryKey: qk.decks(DECK_LIST_PARAMS),
    queryFn: () => vocabularyApi.listDecks(DECK_LIST_PARAMS),
  });
  const deckList = decks?.data ?? [];
  // P2-C: name + groups of any deck the user can see (public or own personal deck).
  const { data: detail } = useQuery({
    queryKey: qk.deckDetail(params.id),
    queryFn: () => vocabularyApi.getDeckDetail(params.id),
    retry: false,
  });
  const currentDeck = deckList.find((d) => d.id === params.id);
  const [groupId, setGroupId] = useState<string | null>(null);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[300px_minmax(0,1fr)]">
      <TopicList decks={deckList} activeId={params.id} />
      <div className="min-w-0">
        {detail && detail.groups.length > 0 && (
          <GroupFilterChips
            className="mb-3"
            groups={detail.groups}
            total={detail.cardCount}
            ungroupedCount={detail.ungroupedCount}
            value={groupId}
            onChange={setGroupId}
          />
        )}
        {/* Remount per deck/group: a new session (and a new order of the new cards). */}
        <DeckStudy
          key={`${params.id}:${groupId ?? 'all'}`}
          deckId={params.id}
          deck={currentDeck ?? (detail ? { name: detail.name } : undefined)}
          groupId={groupId}
          personal={!!detail?.personal}
        />
      </div>
    </div>
  );
}

function DeckStudy({
  deckId,
  deck,
  groupId,
  personal,
}: {
  deckId: string;
  deck?: Pick<VocabDeck, 'name'>;
  /** P2-C group filter (null = whole deck). */
  groupId: string | null;
  personal: boolean;
}) {
  const backHref = personal ? '/vocabulary/my-decks' : '/vocabulary';
  const backLabel = personal ? 'Bộ thẻ của tôi' : 'Danh sách bộ từ';
  return (
    <div className="min-w-0">
      <Link href={backHref} className="mb-3 inline-flex min-h-[44px] items-center text-sm text-slate-500 hover:text-slate-700 lg:hidden">
        ← {backLabel}
      </Link>
      <Card className="p-4 sm:p-6">
        {/* P2-anki: Anki-style session (learning → review → new), counters and server-computed button labels. */}
        <StudySessionPanel
          scope={{ kind: 'deck', deckId, groupId }}
          deckName={deck?.name}
          backHref={backHref}
          header={
            <p className="truncate text-xs font-semibold uppercase tracking-wide text-slate-400">
              {deck?.name ?? 'Bộ từ vựng'}
            </p>
          }
          emptyState={
            <div className="text-center">
              <p className="text-slate-600">{groupId ? 'Nhóm này chưa có thẻ nào.' : 'Bộ từ này chưa có thẻ nào.'}</p>
              <Link
                href={personal ? `/vocabulary/my-decks/${deckId}` : '/vocabulary'}
                className="mt-3 inline-block text-sm font-medium text-brand-600 hover:underline"
              >
                {personal ? 'Thêm thẻ vào bộ này' : 'Chọn bộ từ khác'}
              </Link>
            </div>
          }
          doneActions={
            <>
              <Link
                href={backHref}
                className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
              >
                {backLabel}
              </Link>
              <Link
                href="/vocabulary/review"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Ôn tập tất cả
              </Link>
            </>
          }
        />
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
