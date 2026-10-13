'use client';

import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { CEFR_LEVELS } from '@/lib/constants';
import type { CefrLevel, VocabDeck } from '@/lib/types';
import { FormEvent, useState } from 'react';
import { EMPTY_DECK_DRAFT, deckToDraft, validateDeckDraft, type DeckDraft } from './console';

/**
 * T14: create (deck = null) or edit a system deck (name, description, level, isProOnly).
 * Mount with a fresh `key` per open so the draft is re-initialised from `deck`.
 */
export function DeckFormModal({
  open,
  deck,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  deck: VocabDeck | null;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (draft: DeckDraft) => void;
}) {
  const [draft, setDraft] = useState<DeckDraft>(() => (deck ? deckToDraft(deck) : EMPTY_DECK_DRAFT));
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const err = validateDeckDraft(draft);
    setError(err);
    if (!err) onSubmit(draft);
  }

  return (
    <Modal
      open={open}
      onClose={loading ? () => undefined : onClose}
      title={deck ? 'Sửa bộ từ vựng' : 'Tạo bộ từ vựng mới'}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        <Input
          label="Tên bộ từ vựng"
          required
          maxLength={255}
          value={draft.name}
          onChange={(e) => {
            setDraft({ ...draft, name: e.target.value });
            setError(null);
          }}
          error={error ?? undefined}
        />
        <Textarea
          label="Mô tả"
          rows={3}
          value={draft.description}
          onChange={(e) => setDraft({ ...draft, description: e.target.value })}
        />
        <Select
          label="Cấp độ"
          value={draft.level}
          onChange={(e) => setDraft({ ...draft, level: e.target.value as CefrLevel | '' })}
        >
          <option value="">Không đặt</option>
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
            checked={draft.isProOnly}
            onChange={(e) => setDraft({ ...draft, isProOnly: e.target.checked })}
          />
          Chỉ dành cho Premium
        </label>
        <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading} className="min-h-[44px]">
            Huỷ
          </Button>
          <Button type="submit" loading={loading} className="min-h-[44px]">
            {deck ? 'Lưu thay đổi' : 'Tạo bộ từ vựng'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
