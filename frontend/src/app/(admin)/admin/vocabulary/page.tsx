'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { DeckFormModal } from '@/components/admin/DeckFormModal';
import { deckDraftToUpdate, type DeckDraft } from '@/components/admin/console';
import { invalidateVocabCaches } from '@/components/admin/invalidate';
import { optional } from '@/components/admin/validation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { CreateDeckInput, UpdateDeckInput, VocabDeck } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;

export default function AdminVocabularyPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [deckModalOpen, setDeckModalOpen] = useState(false);
  const [deckFormKey, setDeckFormKey] = useState(0);
  const [editing, setEditing] = useState<VocabDeck | null>(null);
  const [toDelete, setToDelete] = useState<VocabDeck | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [...qk.adminDecks, page],
    queryFn: () => vocabularyApi.listDecks({ page, limit: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const createDeckMutation = useMutation({
    mutationFn: (input: CreateDeckInput) => vocabularyApi.createDeck(input),
    onSuccess: () => {
      toast.success('Đã tạo bộ từ vựng.');
      setDeckModalOpen(false);
      invalidateVocabCaches(queryClient);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể tạo bộ từ vựng.')),
  });

  // T14: edit name / description / level / isProOnly of a system deck.
  const updateDeckMutation = useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateDeckInput }) => vocabularyApi.updateDeck(id, input),
    onSuccess: () => {
      toast.success('Đã lưu bộ từ vựng.');
      setDeckModalOpen(false);
      invalidateVocabCaches(queryClient);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được bộ từ vựng.')),
  });

  const deleteDeckMutation = useMutation({
    mutationFn: (id: string) => vocabularyApi.deleteDeck(id),
    onSuccess: () => {
      toast.success('Đã xoá bộ từ vựng.');
      setToDelete(null);
      invalidateVocabCaches(queryClient);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể xoá bộ từ vựng.')),
  });

  function openDeckForm(deck: VocabDeck | null) {
    setEditing(deck);
    setDeckFormKey((k) => k + 1);
    setDeckModalOpen(true);
  }

  function handleDeckSubmit(draft: DeckDraft) {
    if (editing) {
      updateDeckMutation.mutate({ id: editing.id, input: deckDraftToUpdate(draft) });
      return;
    }
    createDeckMutation.mutate({
      name: draft.name.trim(),
      description: optional(draft.description),
      level: draft.level || undefined,
      isProOnly: draft.isProOnly,
    });
  }

  return (
    <div className="mx-auto w-full max-w-5xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý từ vựng</h1>
        <div className="flex flex-wrap gap-2">
          {/* P2-C */}
          <Link
            href="/admin/vocabulary/import"
            className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            ⬆ Nhập từ file
          </Link>
          <Button onClick={() => openDeckForm(null)} className="min-h-[44px]">
            + Tạo bộ từ vựng
          </Button>
        </div>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-6" />
      ) : !data || data.data.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-slate-500">Chưa có bộ từ vựng nào.</Card>
      ) : (
        <>
          <div className="mt-6 flex flex-col gap-3">
            {data.data.map((deck) => (
              <Card key={deck.id} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="break-words font-semibold text-slate-900">{deck.name}</h3>
                    {deck.level && <Badge tone="brand">{deck.level}</Badge>}
                    {deck.isProOnly && <Badge tone="purple">Pro</Badge>}
                  </div>
                  <p className="text-sm text-slate-500">{deck.cardCount} thẻ</p>
                  {deck.description && <p className="break-words text-sm text-slate-500">{deck.description}</p>}
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                  <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => openDeckForm(deck)}>
                    Sửa
                  </Button>
                  <Link
                    href={`/admin/vocabulary/${deck.id}`}
                    className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    Quản lý thẻ
                  </Link>
                  <Button size="sm" variant="danger" className="min-h-[44px]" onClick={() => setToDelete(deck)}>
                    Xoá
                  </Button>
                </div>
              </Card>
            ))}
          </div>
          <Pagination page={page} totalPages={data.pagination.totalPages} onChange={setPage} className="mt-6" />
        </>
      )}

      <DeckFormModal
        key={deckFormKey}
        open={deckModalOpen}
        deck={editing}
        loading={createDeckMutation.isPending || updateDeckMutation.isPending}
        onClose={() => setDeckModalOpen(false)}
        onSubmit={handleDeckSubmit}
      />

      <ConfirmModal
        open={!!toDelete}
        title="Xoá bộ từ vựng?"
        message={
          <>
            Bộ <strong>{toDelete?.name}</strong> và toàn bộ {toDelete?.cardCount ?? 0} thẻ (kèm tiến độ học của người
            dùng) sẽ bị xoá.
          </>
        }
        loading={deleteDeckMutation.isPending}
        onConfirm={() => toDelete && deleteDeckMutation.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}
