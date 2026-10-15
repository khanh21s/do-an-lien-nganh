'use client';

import { Button } from '@/components/ui/Button';
import { Input, Select } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import {
  ADD_WORD_FIELDS,
  ADD_WORD_LIMITS,
  initialFormValues,
  normalizeAddWordForm,
  validateAddWordForm,
  type AddWordField,
  type AddWordFormValues,
} from '@/components/vocab/addWordForm';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import type { VocabCard, VocabGroup } from '@/lib/types';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import toast from 'react-hot-toast';

/**
 * P2-C: add / edit a card of one of the learner's own decks, including its group.
 * Render with a changing `key` so the form resets per card.
 */
export function CardEditorModal({
  open,
  deckId,
  card,
  groups,
  defaultGroupId,
  onClose,
  onSaved,
}: {
  open: boolean;
  deckId: string;
  /** Edit mode when set. */
  card: VocabCard | null;
  groups: VocabGroup[];
  defaultGroupId?: string | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [values, setValues] = useState<AddWordFormValues>(() => initialFormValues(card));
  const [groupId, setGroupId] = useState<string>(card ? card.groupId ?? '' : defaultGroupId ?? '');
  const [errors, setErrors] = useState<Partial<Record<AddWordField, string>>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => {
      const input = { ...normalizeAddWordForm(values), groupId: groupId || null };
      return card ? vocabularyApi.updateMyCard(card.id, input) : vocabularyApi.addMyCard(deckId, input);
    },
    onSuccess: () => {
      toast.success(card ? 'Đã lưu thẻ' : 'Đã thêm thẻ');
      onSaved();
      onClose();
    },
    onError: (err) => setServerError(apiErrorMessage(err, 'Không lưu được thẻ.')),
  });

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const found = validateAddWordForm(values);
    setErrors(found);
    if (Object.keys(found).length === 0) save.mutate();
  }

  return (
    <Modal open={open} onClose={save.isPending ? () => undefined : onClose} title={card ? 'Sửa thẻ' : 'Thêm thẻ'}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <fieldset disabled={save.isPending} className="flex flex-col gap-3">
          {ADD_WORD_FIELDS.map((f) => (
            <Input
              key={f.key}
              label={f.required ? `${f.label} *` : f.label}
              placeholder={f.placeholder}
              value={values[f.key]}
              maxLength={ADD_WORD_LIMITS[f.key] + 20}
              error={errors[f.key]}
              aria-label={f.label}
              autoComplete="off"
              onChange={(e) => {
                setValues((v) => ({ ...v, [f.key]: e.target.value }));
                setServerError(null);
              }}
              className="min-h-[44px]"
            />
          ))}
          {groups.length > 0 && (
            <Select label="Nhóm" value={groupId} onChange={(e) => setGroupId(e.target.value)} className="min-h-[44px]">
              <option value="">Không thuộc nhóm nào</option>
              {[...groups]
                .sort((a, b) => a.sortOrder - b.sortOrder)
                .map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.name}
                  </option>
                ))}
            </Select>
          )}
        </fieldset>
        {serverError && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </p>
        )}
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-[44px]" onClick={onClose} disabled={save.isPending}>
            Huỷ
          </Button>
          <Button type="submit" className="min-h-[44px]" loading={save.isPending}>
            {card ? 'Lưu' : 'Thêm thẻ'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
