'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { CardEditorModal } from '@/components/vocab/decks/CardEditorModal';
import { CardGroupSelect } from '@/components/vocab/decks/CardGroupSelect';
import { GroupFilterChips } from '@/components/vocab/decks/GroupFilterChips';
import { GroupManager } from '@/components/vocab/decks/GroupManager';
import { formatIpa } from '@/components/vocab/dictionary/wordInfo';
import { useDebounce } from '@/hooks/useDebounce';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { UNGROUPED, type DeckCardsQuery, type VocabCard } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;

/** P2-C: manage one personal deck – groups, cards (add/edit/delete/move) and import. */
export default function MyDeckManagePage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const searchWord = useDebounce(search.trim(), 350);
  const [group, setGroup] = useState<string | null>(null);
  const [editor, setEditor] = useState<{ key: number; card: VocabCard | null } | null>(null);
  const [toDelete, setToDelete] = useState<VocabCard | null>(null);

  const detail = useQuery({ queryKey: qk.deckDetail(id), queryFn: () => vocabularyApi.getDeckDetail(id) });
  const params: DeckCardsQuery = {
    page,
    limit: PAGE_SIZE,
    ...(searchWord ? { searchWord } : {}),
    ...(group ? { groupId: group } : {}),
  };
  const cards = useQuery({
    queryKey: qk.deckCardsManage(id, params),
    queryFn: () => vocabularyApi.listDeckCards(id, params),
    placeholderData: keepPreviousData,
  });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['deck-cards', id] });
    void queryClient.invalidateQueries({ queryKey: qk.deckDetail(id) });
    void queryClient.invalidateQueries({ queryKey: qk.myDecks });
    void queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
    void queryClient.invalidateQueries({ queryKey: qk.vocabStats });
  }

  const remove = useMutation({
    mutationFn: (cardId: string) => vocabularyApi.deleteMyCard(cardId),
    onSuccess: () => {
      toast.success('Đã xoá thẻ');
      setToDelete(null);
      if (cards.data && cards.data.data.length === 1 && page > 1) setPage(page - 1);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được thẻ.')),
  });

  if (detail.isLoading) return <FullPageSpinner />;
  if (detail.isError)
    return <ErrorState error={detail.error} onRetry={detail.refetch} backHref="/vocabulary/my-decks" backLabel="Bộ thẻ của tôi" />;
  const deck = detail.data;
  if (!deck || !deck.personal) return <ErrorState notFound backHref="/vocabulary/my-decks" backLabel="Bộ thẻ của tôi" />;

  const list = cards.data?.data ?? [];
  const defaultGroup = group && group !== UNGROUPED ? group : null;

  return (
    <div className="mx-auto w-full max-w-5xl">
      <Link href="/vocabulary/my-decks" className="text-sm text-brand-600 hover:underline">
        ← Bộ thẻ của tôi
      </Link>
      <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="break-words text-2xl font-bold text-slate-900">{deck.name}</h1>
            {deck.isNotebook && <Badge tone="brand">Sổ tay</Badge>}
          </div>
          {deck.description && <p className="break-words text-sm text-slate-600">{deck.description}</p>}
          <p className="text-sm text-slate-500">
            {deck.cardCount} thẻ · {deck.groups.length} nhóm
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            href={`/vocabulary/my-decks/${deck.id}/study`}
            className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            Học bộ này
          </Link>
          <Link
            href={`/vocabulary/my-decks/import?deckId=${deck.id}`}
            className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            ⬆ Nhập từ file
          </Link>
          <Button variant="secondary" className="min-h-[44px]" onClick={() => setEditor({ key: Date.now(), card: null })}>
            + Thêm thẻ
          </Button>
        </div>
      </div>
      {deck.isNotebook && (
        <p className="mt-2 text-xs text-slate-500">Từ trong sổ tay được đưa vào hàng ôn tập ngay khi thêm.</p>
      )}

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0">
          <GroupFilterChips
            groups={deck.groups}
            total={deck.cardCount}
            ungroupedCount={deck.ungroupedCount}
            value={group}
            onChange={(g) => {
              setGroup(g);
              setPage(1);
            }}
          />
          <div className="mt-2 flex items-center gap-2">
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
            {cards.isFetching && <Spinner />}
          </div>

          {cards.isError ? (
            <ErrorState error={cards.error} onRetry={cards.refetch} className="mt-4" />
          ) : list.length === 0 && !cards.isLoading ? (
            <Card className="mt-4 text-center text-sm text-slate-500">
              {searchWord ? `Không có thẻ nào khớp “${searchWord}”.` : 'Chưa có thẻ nào. Thêm thẻ hoặc nhập từ file.'}
            </Card>
          ) : (
            <div className="mt-3 flex flex-col gap-2">
              {list.map((card) => (
                <Card key={card.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-baseline gap-2">
                      <span className="break-words font-semibold text-slate-900">{card.word}</span>
                      {card.ipa && <span className="text-sm text-slate-500">{formatIpa(card.ipa)}</span>}
                      {card.partOfSpeech && <Badge tone="slate">{card.partOfSpeech}</Badge>}
                    </div>
                    <p className="break-words text-sm text-slate-700">{card.meaning}</p>
                    {card.example && <p className="mt-1 break-words text-xs italic text-slate-500">{card.example}</p>}
                  </div>
                  <div className="flex shrink-0 flex-wrap items-center gap-2">
                    <CardGroupSelect deckId={deck.id} cardId={card.id} groupId={card.groupId} groups={deck.groups} onMoved={refresh} />
                    <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => setEditor({ key: Date.now(), card })}>
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
          {cards.data && (
            <Pagination page={page} totalPages={cards.data.pagination.totalPages} onChange={setPage} className="mt-6" />
          )}
        </div>
        <div>
          <GroupManager deckId={deck.id} groups={deck.groups} />
        </div>
      </div>

      {editor && (
        <CardEditorModal
          key={editor.key}
          open
          deckId={deck.id}
          card={editor.card}
          groups={deck.groups}
          defaultGroupId={defaultGroup}
          onClose={() => setEditor(null)}
          onSaved={refresh}
        />
      )}
      <ConfirmModal
        open={!!toDelete}
        title="Xoá thẻ?"
        message={
          <>
            Thẻ <strong>{toDelete?.word}</strong> và tiến độ ôn tập của nó sẽ bị xoá.
          </>
        }
        loading={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}
