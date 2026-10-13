'use client';

import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { LearnedWord } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import {
  ADD_WORD_LIMITS,
  initialFormValues,
  normalizeAddWordForm,
  validateAddWordForm,
  type AddWordField,
  type AddWordFormValues,
  type AddWordPrefill,
} from './addWordForm';

const FIELDS: { key: AddWordField; label: string; placeholder: string; required?: boolean }[] = [
  { key: 'word', label: 'Từ', placeholder: 'vd. today', required: true },
  { key: 'meaning', label: 'Nghĩa', placeholder: 'vd. hôm nay', required: true },
  { key: 'ipa', label: 'Phiên âm (IPA)', placeholder: 'vd. təˈdeɪ' },
  { key: 'partOfSpeech', label: 'Từ loại', placeholder: 'vd. noun, verb, adj…' },
  { key: 'example', label: 'Ví dụ', placeholder: 'Câu ví dụ tiếng Anh' },
  { key: 'exampleVi', label: 'Nghĩa ví dụ', placeholder: 'Bản dịch câu ví dụ' },
];

/**
 * VOC-NB-ADD: add a word of the user's own to the notebook, or edit one they added
 * (`editing` set). Renders the shared `Modal` (role="dialog" aria-modal) so page shortcuts
 * (dictation, flashcards) pause while it is open. On success it toasts, invalidates the shared
 * vocabulary caches and calls `onSaved`.
 */
export function AddWordModal({
  open,
  onClose,
  prefill,
  editing,
  onSaved,
}: {
  open: boolean;
  onClose: () => void;
  /** Initial values in create mode (e.g. word + example from a dictation sentence). */
  prefill?: AddWordPrefill | null;
  /** Edit mode: the custom word being edited (`userVocabId` is the PUT target). */
  editing?: LearnedWord | null;
  onSaved?: (word: LearnedWord) => void;
}) {
  const queryClient = useQueryClient();
  const isEdit = !!editing;
  const [values, setValues] = useState<AddWordFormValues>(() => initialFormValues(editing ?? prefill));
  const [errors, setErrors] = useState<Partial<Record<AddWordField, string>>>({});
  const [serverError, setServerError] = useState<string | null>(null);
  const firstInputRef = useRef<HTMLInputElement | null>(null);

  // Reset the form each time the modal opens (or switches word).
  useEffect(() => {
    if (!open) return;
    setValues(initialFormValues(editing ?? prefill));
    setErrors({});
    setServerError(null);
    const id = window.setTimeout(() => firstInputRef.current?.focus(), 0);
    return () => window.clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- prefill objects are recreated by callers; reset on open/target change only
  }, [open, editing?.userVocabId, prefill?.word, prefill?.example]);

  const mutation = useMutation({
    mutationFn: (input: ReturnType<typeof normalizeAddWordForm>) =>
      editing
        ? vocabularyApi.updatePersonalWord(editing.userVocabId, input)
        : vocabularyApi.createPersonalWord(input),
    onSuccess: (saved) => {
      toast.success(isEdit ? 'Đã cập nhật' : 'Đã thêm vào sổ tay');
      for (const queryKey of [qk.learnedWordsAll, qk.vocabStats, qk.reviewQueue]) {
        void queryClient.invalidateQueries({ queryKey });
      }
      onSaved?.(saved);
      onClose();
    },
    onError: (err) => {
      // Shown inside the form (e.g. 409 "Từ này đã có trong sổ tay"), not as a toast behind the modal.
      setServerError(apiErrorMessage(err, isEdit ? 'Không thể cập nhật từ này.' : 'Không thể thêm từ này.'));
    },
  });

  const saving = mutation.isPending;

  function setField(key: AddWordField, value: string) {
    setValues((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => (prev[key] ? { ...prev, [key]: undefined } : prev));
    setServerError(null);
  }

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (saving) return;
    const found = validateAddWordForm(values);
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    mutation.mutate(normalizeAddWordForm(values));
  }

  return (
    <Modal open={open} onClose={saving ? () => undefined : onClose} title={isEdit ? 'Sửa từ' : 'Thêm từ vào sổ tay'}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-3">
        <fieldset disabled={saving} className="flex flex-col gap-3">
          {FIELDS.map((f, i) => (
            <Input
              key={f.key}
              ref={i === 0 ? firstInputRef : undefined}
              label={f.required ? `${f.label} *` : f.label}
              placeholder={f.placeholder}
              value={values[f.key]}
              onChange={(e) => setField(f.key, e.target.value)}
              error={errors[f.key]}
              maxLength={ADD_WORD_LIMITS[f.key] + 20}
              aria-label={f.label}
              aria-required={f.required || undefined}
              aria-invalid={errors[f.key] ? true : undefined}
              autoComplete="off"
              spellCheck={f.key === 'example'}
              className="min-h-[44px]"
            />
          ))}
        </fieldset>
        {serverError && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {serverError}
          </p>
        )}
        <p className="text-xs text-slate-400">Nhấn Enter để lưu · Từ mới sẽ vào hàng ôn tập ngay.</p>
        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="secondary" className="min-h-[44px]" onClick={onClose} disabled={saving}>
            Huỷ
          </Button>
          <Button type="submit" className="min-h-[44px]" loading={saving}>
            {isEdit ? 'Lưu thay đổi' : 'Thêm vào sổ tay'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
