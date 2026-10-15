'use client';

import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import type { PersonalDeck, PersonalDeckInput } from '@/lib/types';
import { useState } from 'react';
import { DECK_DESCRIPTION_MAX, DECK_NAME_MAX, validateDeckName } from './myDecks';

/** P2-C: create / rename a personal deck. Render with a changing `key` to reset. */
export function PersonalDeckFormModal({
  open,
  deck,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  deck: PersonalDeck | null;
  loading: boolean;
  onClose: () => void;
  onSubmit: (input: PersonalDeckInput) => void;
}) {
  const [name, setName] = useState(deck?.name ?? '');
  const [description, setDescription] = useState(deck?.description ?? '');
  const [error, setError] = useState<string | null>(null);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const err = validateDeckName(name);
    setError(err);
    if (!err) onSubmit({ name: name.trim(), description: description.trim() || null });
  }

  return (
    <Modal open={open} onClose={loading ? () => undefined : onClose} title={deck ? 'Sửa bộ thẻ' : 'Tạo bộ thẻ mới'}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <Input
          label="Tên bộ thẻ *"
          value={name}
          maxLength={DECK_NAME_MAX + 10}
          error={error ?? undefined}
          autoFocus
          onChange={(e) => {
            setName(e.target.value);
            setError(null);
          }}
          className="min-h-[44px]"
        />
        <Textarea
          label="Mô tả"
          value={description}
          maxLength={DECK_DESCRIPTION_MAX}
          rows={3}
          onChange={(e) => setDescription(e.target.value)}
        />
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-[44px]" onClick={onClose} disabled={loading}>
            Huỷ
          </Button>
          <Button type="submit" className="min-h-[44px]" loading={loading}>
            {deck ? 'Lưu' : 'Tạo bộ thẻ'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
