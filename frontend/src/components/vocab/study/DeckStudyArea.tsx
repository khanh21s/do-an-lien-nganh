'use client';

import { Card } from '@/components/ui/Card';
import { GroupFilterChips } from '@/components/vocab/decks/GroupFilterChips';
import { StudySessionPanel } from '@/components/vocab/study/StudySession';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { DeckDetail } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';

/** The deck detail (name + groups) of any deck the user can see; shared cache with the study pages. */
export function useDeckDetail(deckId: string) {
  return useQuery({
    queryKey: qk.deckDetail(deckId),
    queryFn: () => vocabularyApi.getDeckDetail(deckId),
    retry: false,
  });
}

/**
 * Group filter + Anki session of one deck. Used by the public deck page (`/vocabulary/decks/[id]`)
 * and by the personal deck page (`/vocabulary/my-decks/[id]/study`), which differ only in their
 * deck list and back links.
 */
export function DeckStudyArea({
  deckId,
  deckName,
  detail,
  personal,
}: {
  deckId: string;
  /** Falls back to `detail.name`. */
  deckName?: string;
  detail: DeckDetail | undefined;
  personal: boolean;
}) {
  const [groupId, setGroupId] = useState<string | null>(null);
  const name = deckName ?? detail?.name;
  const backHref = personal ? '/vocabulary/my-decks' : '/vocabulary';
  const backLabel = personal ? 'Bộ thẻ của tôi' : 'Danh sách bộ từ';

  return (
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
      <Link
        href={backHref}
        className="mb-3 inline-flex min-h-[44px] items-center text-sm text-slate-500 hover:text-slate-700 lg:hidden"
      >
        ← {backLabel}
      </Link>
      <Card className="p-4 sm:p-6">
        {/* Remount per deck/group: a new session (and a new order of the new cards). */}
        {/* P2-anki: Anki-style session (learning → review → new), counters and server-computed button labels. */}
        <StudySessionPanel
          key={`${deckId}:${groupId ?? 'all'}`}
          scope={{ kind: 'deck', deckId, groupId }}
          deckName={name}
          backHref={backHref}
          header={
            <p className="truncate text-xs font-semibold uppercase tracking-wide text-slate-400">
              {name ?? 'Bộ từ vựng'}
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
