'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { PersonalDeckFormModal } from '@/components/vocab/decks/DeckFormModal';
import { canCreateDeck, usageLabel, usagePercent } from '@/components/vocab/decks/myDecks';
import { formatCount } from '@/components/vocab/import/importHelpers';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { PersonalDeck, PersonalDeckInput } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import toast from 'react-hot-toast';

/** P2-C "Bộ thẻ của tôi": the notebook + the learner's own decks, with the free/premium limits. */
export default function MyDecksPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: qk.myDecks, queryFn: vocabularyApi.listMyDecks });
  const [formOpen, setFormOpen] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const [editing, setEditing] = useState<PersonalDeck | null>(null);
  const [toDelete, setToDelete] = useState<PersonalDeck | null>(null);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: qk.myDecks });
    void queryClient.invalidateQueries({ queryKey: qk.deckDetailAll });
  };

  const save = useMutation({
    mutationFn: (input: PersonalDeckInput) =>
      editing ? vocabularyApi.updateMyDeck(editing.id, input) : vocabularyApi.createMyDeck(input),
    onSuccess: () => {
      toast.success(editing ? 'Đã lưu bộ thẻ' : 'Đã tạo bộ thẻ');
      setFormOpen(false);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được bộ thẻ.')),
  });
  const remove = useMutation({
    mutationFn: (id: string) => vocabularyApi.deleteMyDeck(id),
    onSuccess: () => {
      toast.success('Đã xoá bộ thẻ');
      setToDelete(null);
      refresh();
      void queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
      void queryClient.invalidateQueries({ queryKey: qk.vocabStats });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được bộ thẻ.')),
  });

  function openForm(deck: PersonalDeck | null) {
    setEditing(deck);
    setFormKey((k) => k + 1);
    setFormOpen(true);
  }

  if (isLoading) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return <ErrorState notFound />;

  const { decks, usage } = data;
  const allowed = canCreateDeck(usage);

  return (
    <div className="mx-auto w-full max-w-5xl">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bộ thẻ của tôi</h1>
          <p className="mt-1 text-slate-500">Tạo bộ thẻ riêng, chia nhóm và nhập từ vựng từ file.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link
            href="/vocabulary/my-decks/import"
            className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            ⬆ Nhập từ file
          </Link>
          <Button className="min-h-[44px]" disabled={!allowed} onClick={() => openForm(null)}>
            + Tạo bộ thẻ
          </Button>
        </div>
      </div>

      <Card className="mt-4 p-4">
        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
          <span className="font-medium text-slate-700">{usageLabel(usage)}</span>
          {usage.premium ? (
            <Badge tone="amber">👑 Premium</Badge>
          ) : (
            <Link href="/subscription" className="text-sm font-medium text-brand-600 hover:underline">
              Nâng cấp Premium: 50 bộ / 8.000 thẻ
            </Link>
          )}
        </div>
        <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100" aria-hidden>
          <div className="h-full rounded-full bg-brand-500" style={{ width: `${usagePercent(usage.cards, usage.maxCards)}%` }} />
        </div>
        <p className="mt-2 text-xs text-slate-500">
          Sổ tay: {formatCount(usage.notebookCards)}/{formatCount(usage.maxNotebookCards)} từ (tính riêng).
          {!allowed && ' Bạn đã dùng hết số bộ thẻ cho phép.'}
        </p>
      </Card>

      <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {decks.map((deck) => (
          <Card key={deck.id} className="flex h-full flex-col gap-3">
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <h3 className="break-words font-semibold text-slate-900">
                  {deck.isNotebook ? '📓 ' : '🗂️ '}
                  {deck.name}
                </h3>
                {deck.description && <p className="mt-1 line-clamp-2 text-sm text-slate-500">{deck.description}</p>}
              </div>
              {deck.isNotebook && <Badge tone="brand">Sổ tay</Badge>}
            </div>
            <p className="text-xs text-slate-400">
              {formatCount(deck.cardCount)} thẻ · {deck.groupCount} nhóm
            </p>
            <div className="mt-auto flex flex-wrap gap-2">
              <Link
                href={`/vocabulary/decks/${deck.id}`}
                className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700"
              >
                Học
              </Link>
              <Link
                href={`/vocabulary/my-decks/${deck.id}`}
                className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Quản lý
              </Link>
              {!deck.isNotebook && (
                <>
                  <Button size="sm" variant="ghost" className="min-h-[44px]" onClick={() => openForm(deck)}>
                    Sửa
                  </Button>
                  <Button size="sm" variant="ghost" className="min-h-[44px] text-red-600" onClick={() => setToDelete(deck)}>
                    Xoá
                  </Button>
                </>
              )}
            </div>
          </Card>
        ))}
      </div>
      {decks.length <= 1 && (
        <Card className="mt-4 text-center text-sm text-slate-500">
          Bạn chưa có bộ thẻ riêng nào. Tạo bộ đầu tiên hoặc nhập từ file CSV/TXT/PDF.
        </Card>
      )}

      <PersonalDeckFormModal
        key={formKey}
        open={formOpen}
        deck={editing}
        loading={save.isPending}
        onClose={() => setFormOpen(false)}
        onSubmit={(input) => save.mutate(input)}
      />
      <ConfirmModal
        open={!!toDelete}
        title="Xoá bộ thẻ?"
        message={
          <>
            Bộ <strong>{toDelete?.name}</strong>, các nhóm và {toDelete?.cardCount ?? 0} thẻ trong đó (kèm tiến độ ôn tập
            của các thẻ này) sẽ bị xoá vĩnh viễn.
          </>
        }
        loading={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}
