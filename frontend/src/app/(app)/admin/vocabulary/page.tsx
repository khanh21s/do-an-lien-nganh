'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi, type CardInput } from '@/lib/endpoints/vocabulary';
import type { CefrLevel } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
const emptyCard: CardInput = { word: '', meaning: '', example: '', ipa: '' };

export default function AdminVocabularyPage() {
  const queryClient = useQueryClient();
  const [deckModalOpen, setDeckModalOpen] = useState(false);
  const [deckForm, setDeckForm] = useState({ name: '', description: '', level: 'A1' as CefrLevel, isProOnly: false });
  const [cardModalDeckId, setCardModalDeckId] = useState<string | null>(null);
  const [cardForm, setCardForm] = useState<CardInput>(emptyCard);

  const { data, isLoading } = useQuery({ queryKey: ['admin-decks'], queryFn: () => vocabularyApi.listDecks({ limit: 50 }) });

  const createDeckMutation = useMutation({
    mutationFn: () => vocabularyApi.createDeck(deckForm),
    onSuccess: () => {
      toast.success('Đã tạo bộ từ vựng.');
      setDeckModalOpen(false);
      setDeckForm({ name: '', description: '', level: 'A1', isProOnly: false });
      queryClient.invalidateQueries({ queryKey: ['admin-decks'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể tạo bộ từ vựng — kiểm tra quyền admin của bạn.')),
  });

  const deleteDeckMutation = useMutation({
    mutationFn: (id: string) => vocabularyApi.deleteDeck(id),
    onSuccess: () => {
      toast.success('Đã xoá bộ từ vựng.');
      queryClient.invalidateQueries({ queryKey: ['admin-decks'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  const addCardMutation = useMutation({
    mutationFn: () => vocabularyApi.addCards(cardModalDeckId as string, [cardForm]),
    onSuccess: () => {
      toast.success('Đã thêm từ vào bộ từ vựng.');
      setCardForm(emptyCard);
      queryClient.invalidateQueries({ queryKey: ['admin-decks'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  function handleCreateDeck(e: FormEvent) {
    e.preventDefault();
    if (!deckForm.name.trim()) {
      toast.error('Vui lòng nhập tên bộ từ vựng.');
      return;
    }
    createDeckMutation.mutate();
  }

  function handleAddCard(e: FormEvent) {
    e.preventDefault();
    if (!cardForm.word.trim() || !cardForm.meaning.trim()) {
      toast.error('Vui lòng nhập từ và nghĩa.');
      return;
    }
    addCardMutation.mutate();
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý từ vựng</h1>
        <Button onClick={() => setDeckModalOpen(true)}>+ Tạo bộ từ vựng</Button>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data?.data.map((deck) => (
            <Card key={deck.id} className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <h3 className="font-semibold text-slate-900">{deck.name}</h3>
                {deck.level && <Badge tone="brand">{deck.level}</Badge>}
                {deck.isProOnly && <Badge tone="amber">Premium</Badge>}
                <span className="text-sm text-slate-500">{deck.cardCount} từ</span>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => setCardModalDeckId(deck.id)}>
                  + Thêm từ
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  loading={deleteDeckMutation.isPending && deleteDeckMutation.variables === deck.id}
                  onClick={() => {
                    if (confirm(`Xoá bộ từ vựng "${deck.name}"?`)) deleteDeckMutation.mutate(deck.id);
                  }}
                >
                  Xoá
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <Modal open={deckModalOpen} onClose={() => setDeckModalOpen(false)} title="Tạo bộ từ vựng mới">
        <form onSubmit={handleCreateDeck} className="flex flex-col gap-3">
          <Input label="Tên bộ từ vựng" required value={deckForm.name} onChange={(e) => setDeckForm({ ...deckForm, name: e.target.value })} />
          <Input
            label="Mô tả"
            value={deckForm.description}
            onChange={(e) => setDeckForm({ ...deckForm, description: e.target.value })}
          />
          <Select label="Cấp độ" value={deckForm.level} onChange={(e) => setDeckForm({ ...deckForm, level: e.target.value as CefrLevel })}>
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </Select>
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={deckForm.isProOnly}
              onChange={(e) => setDeckForm({ ...deckForm, isProOnly: e.target.checked })}
            />
            Chỉ dành cho Premium
          </label>
          <Button type="submit" loading={createDeckMutation.isPending} className="mt-2 w-full">
            Tạo bộ từ vựng
          </Button>
        </form>
      </Modal>

      <Modal open={!!cardModalDeckId} onClose={() => setCardModalDeckId(null)} title="Thêm từ mới">
        <form onSubmit={handleAddCard} className="flex flex-col gap-3">
          <Input label="Từ" required value={cardForm.word} onChange={(e) => setCardForm({ ...cardForm, word: e.target.value })} />
          <Input
            label="Nghĩa"
            required
            value={cardForm.meaning}
            onChange={(e) => setCardForm({ ...cardForm, meaning: e.target.value })}
          />
          <Input label="Ví dụ" value={cardForm.example} onChange={(e) => setCardForm({ ...cardForm, example: e.target.value })} />
          <Input label="Phiên âm IPA" value={cardForm.ipa} onChange={(e) => setCardForm({ ...cardForm, ipa: e.target.value })} />
          <Button type="submit" loading={addCardMutation.isPending} className="mt-2 w-full">
            Thêm từ
          </Button>
        </form>
      </Modal>
    </div>
  );
}
