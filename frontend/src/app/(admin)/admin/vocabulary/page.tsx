'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { invalidateVocabCaches } from '@/components/admin/invalidate';
import { optional } from '@/components/admin/validation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input, Select } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LEVELS } from '@/lib/constants';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel, CreateDeckInput, VocabDeck } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;
const emptyDeck = { name: '', description: '', level: 'A1' as CefrLevel, isProOnly: false };

export default function AdminVocabularyPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [deckModalOpen, setDeckModalOpen] = useState(false);
  const [deckForm, setDeckForm] = useState(emptyDeck);
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
      setDeckForm(emptyDeck);
      invalidateVocabCaches(queryClient);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể tạo bộ từ vựng.')),
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

  function handleCreateDeck(e: FormEvent) {
    e.preventDefault();
    if (!deckForm.name.trim()) {
      toast.error('Vui lòng nhập tên bộ từ vựng.');
      return;
    }
    createDeckMutation.mutate({
      name: deckForm.name.trim(),
      description: optional(deckForm.description),
      level: deckForm.level,
      isProOnly: deckForm.isProOnly,
    });
  }

  return (
    <div className="mx-auto w-full max-w-5xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý từ vựng</h1>
        <Button onClick={() => setDeckModalOpen(true)} className="min-h-[44px]">
          + Tạo bộ từ vựng
        </Button>
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
                </div>
                <div className="flex shrink-0 gap-2">
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

      <Modal open={deckModalOpen} onClose={() => setDeckModalOpen(false)} title="Tạo bộ từ vựng mới">
        <form onSubmit={handleCreateDeck} className="flex flex-col gap-3">
          <Input
            label="Tên bộ từ vựng"
            required
            value={deckForm.name}
            onChange={(e) => setDeckForm({ ...deckForm, name: e.target.value })}
          />
          <Input
            label="Mô tả"
            value={deckForm.description}
            onChange={(e) => setDeckForm({ ...deckForm, description: e.target.value })}
          />
          <Select
            label="Cấp độ"
            value={deckForm.level}
            onChange={(e) => setDeckForm({ ...deckForm, level: e.target.value as CefrLevel })}
          >
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </Select>
          <label className="flex min-h-[44px] items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              className="h-4 w-4"
              checked={deckForm.isProOnly}
              onChange={(e) => setDeckForm({ ...deckForm, isProOnly: e.target.checked })}
            />
            Chỉ dành cho Premium
          </label>
          <Button type="submit" loading={createDeckMutation.isPending} className="mt-2 min-h-[44px] w-full">
            Tạo bộ từ vựng
          </Button>
        </form>
      </Modal>

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
