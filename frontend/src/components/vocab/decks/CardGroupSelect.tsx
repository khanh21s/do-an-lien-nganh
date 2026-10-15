'use client';

import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import type { VocabGroup } from '@/lib/types';
import { useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';

/** P2-C: compact per-card group select (moves the card with PUT /vocabs/decks/{id}/cards/group). */
export function CardGroupSelect({
  deckId,
  cardId,
  groupId,
  groups,
  onMoved,
}: {
  deckId: string;
  cardId: string;
  groupId: string | null | undefined;
  groups: VocabGroup[];
  onMoved: () => void;
}) {
  const move = useMutation({
    mutationFn: (target: string | null) => vocabularyApi.moveCardsToGroup(deckId, [cardId], target),
    onSuccess: onMoved,
    onError: (err) => toast.error(apiErrorMessage(err, 'Không chuyển được nhóm.')),
  });
  if (groups.length === 0) return null;
  return (
    <select
      aria-label="Nhóm của thẻ"
      value={groupId ?? ''}
      disabled={move.isPending}
      onChange={(e) => move.mutate(e.target.value || null)}
      className="min-h-[44px] max-w-[11rem] rounded-lg border border-slate-300 bg-white px-2 text-sm text-slate-700"
    >
      <option value="">Không nhóm</option>
      {[...groups]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((g) => (
          <option key={g.id} value={g.id}>
            {g.name}
          </option>
        ))}
    </select>
  );
}
