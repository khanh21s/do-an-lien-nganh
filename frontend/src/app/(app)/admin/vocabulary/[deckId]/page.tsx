'use client';

import { CardFormModal } from '@/components/admin/CardFormModal';
import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { invalidateVocabCaches } from '@/components/admin/invalidate';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { useDebounce } from '@/hooks/useDebounce';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { CardInput, DeckCardsParams, UpdateCardInput, VocabCard } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;

/** ADM-05: paginated + searchable card list of one deck, with add / edit / delete. */
export default function AdminDeckCardsPage() {
  const { deckId } = useParams<{ deckId: string }>();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const searchWord = useDebounce(search.trim(), 350);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<VocabCard | null>(null);
  const [formKey, setFormKey] = useState(0);
  const [toDelete, setToDelete] = useState<VocabCard | null>(null);

  const params: DeckCardsParams = { page, limit: PAGE_SIZE, ...(searchWord ? { searchWord } : {}) };

  // No GET /vocabs/decks/{id}: find the deck in the (admin) deck list for its header.
  const deckQuery = useQuery({
    queryKey: [...qk.adminDecks, 'all'],
    queryFn: () => vocabularyApi.listDecks({ limit: 100 }),
  });
  const deck = deckQuery.data?.data.find((d) => d.id === deckId);

  const cardsQuery = useQuery({
    queryKey: qk.adminCards(deckId, params),
    queryFn: () => vocabularyApi.searchDeckCards(deckId, params),
    placeholderData: keepPreviousData,
  });

  const refresh = () => invalidateVocabCaches(queryClient, deckId);

  const createMutation = useMutation({
    mutationFn: (input: CardInput) => vocabularyApi.addCards(deckId, [input]),
    onSuccess: () => {
      toast.success('Đã thêm thẻ.');
      setFormOpen(false);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể thêm thẻ.')),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateCardInput }) => vocabularyApi.updateCard(id, input),
    onSuccess: () => {
      toast.success('Đã lưu thẻ.');
      setFormOpen(false);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được thẻ.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => vocabularyApi.deleteCard(id),
    onSuccess: () => {
      toast.success('Đã xoá thẻ.');
      setToDelete(null);
      // Step back if the deleted card was the last one on this page.
      if (cardsQuery.data && cardsQuery.data.data.length === 1 && page > 1) setPage(page - 1);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được thẻ.')),
  });

  function openForm(card: VocabCard | null) {
    setEditing(card);
    setFormKey((k) => k + 1);
    setFormOpen(true);
  }

  if (cardsQuery.isLoading && !cardsQuery.data) return <FullPageSpinner />;
  if (cardsQuery.isError)
    return (
      <ErrorState
        error={cardsQuery.error}
        onRetry={cardsQuery.refetch}
        backHref="/admin/vocabulary"
        backLabel="Về danh sách bộ từ"
      />
    );

  const result = cardsQuery.data;
  const cards = result?.data ?? [];

  return (
    <div className="mx-auto w-full max-w-5xl">
      <Link href="/admin/vocabulary" className="text-sm text-brand-600 hover:underline">
        ← Quay lại danh sách bộ từ
      </Link>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="break-words text-2xl font-bold text-slate-900">{deck?.name ?? 'Bộ từ vựng'}</h1>
            {deck?.level && <Badge tone="brand">{deck.level}</Badge>}
            {deck?.isProOnly && <Badge tone="purple">Pro</Badge>}
          </div>
          <p className="text-sm text-slate-500">{result?.pagination.total ?? 0} thẻ{searchWord ? ' khớp tìm kiếm' : ''}</p>
        </div>
        <Button onClick={() => openForm(null)} className="min-h-[44px]">
          + Thêm thẻ
        </Button>
      </div>

      <div className="mt-4 flex items-center gap-2">
        <div className="flex-1">
          <Input
            type="search"
            aria-label="Tìm theo từ"
            placeholder="Tìm theo từ…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            className="min-h-[44px] w-full"
          />
        </div>
        {cardsQuery.isFetching && <Spinner />}
      </div>

      {cards.length === 0 ? (
        <Card className="mt-4 text-center text-sm text-slate-500">
          {searchWord ? `Không có thẻ nào khớp “${searchWord}”.` : 'Bộ này chưa có thẻ nào.'}
        </Card>
      ) : (
        <div className="mt-4 flex flex-col gap-2">
          {cards.map((card) => (
            <Card key={card.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-baseline gap-2">
                  <span className="break-words font-semibold text-slate-900">{card.word}</span>
                  {card.ipa && <span className="text-sm text-slate-500">{card.ipa}</span>}
                  {card.partOfSpeech && <Badge tone="slate">{card.partOfSpeech}</Badge>}
                  {card.audioUrl && <span title="Có audio" aria-label="Có audio">🔊</span>}
                  {card.imageUrl && <span title="Có ảnh" aria-label="Có ảnh">🖼️</span>}
                </div>
                <p className="break-words text-sm text-slate-700">{card.meaning}</p>
                {card.example && <p className="mt-1 break-words text-xs italic text-slate-500">{card.example}</p>}
                {card.exampleVi && <p className="break-words text-xs text-slate-400">{card.exampleVi}</p>}
              </div>
              <div className="flex shrink-0 gap-2">
                <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => openForm(card)}>
                  Sửa
                </Button>
                <Button size="sm" variant="danger" className="min-h-[44px]" onClick={() => setToDelete(card)}>
                  Xoá
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {result && (
        <Pagination page={page} totalPages={result.pagination.totalPages} onChange={setPage} className="mt-6" />
      )}

      <CardFormModal
        key={formKey}
        open={formOpen}
        card={editing}
        loading={createMutation.isPending || updateMutation.isPending}
        onClose={() => setFormOpen(false)}
        onCreate={(input) => createMutation.mutate(input)}
        onUpdate={(id, input) => updateMutation.mutate({ id, input })}
      />

      <ConfirmModal
        open={!!toDelete}
        title="Xoá thẻ?"
        message={
          <>
            Thẻ <strong>{toDelete?.word}</strong> sẽ bị xoá khỏi bộ và khỏi sổ tay của mọi người dùng đã học.
          </>
        }
        loading={deleteMutation.isPending}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}
