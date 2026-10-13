'use client';

import { Button } from '@/components/ui/Button';
import { Input, Select, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import type { CardInput, UpdateCardInput, VocabCard } from '@/lib/types';
import { FormEvent, useState } from 'react';
import { optional } from './validation';

export const PARTS_OF_SPEECH = [
  ['noun', 'Danh từ (noun)'],
  ['verb', 'Động từ (verb)'],
  ['adjective', 'Tính từ (adjective)'],
  ['adverb', 'Trạng từ (adverb)'],
  ['pronoun', 'Đại từ (pronoun)'],
  ['preposition', 'Giới từ (preposition)'],
  ['conjunction', 'Liên từ (conjunction)'],
  ['interjection', 'Thán từ (interjection)'],
  ['phrase', 'Cụm từ (phrase)'],
] as const;

interface CardDraft {
  word: string;
  meaning: string;
  partOfSpeech: string;
  ipa: string;
  example: string;
  exampleVi: string;
  audioUrl: string;
  imageUrl: string;
}

function draftFrom(card: VocabCard | null): CardDraft {
  return {
    word: card?.word ?? '',
    meaning: card?.meaning ?? '',
    partOfSpeech: card?.partOfSpeech ?? '',
    ipa: card?.ipa ?? '',
    example: card?.example ?? '',
    exampleVi: card?.exampleVi ?? '',
    audioUrl: card?.audioUrl ?? '',
    imageUrl: card?.imageUrl ?? '',
  };
}

/**
 * ADM-05: add (`card === null`) or edit a card with every field. The word itself cannot be changed
 * by `UpdateCardRequest`, so it is read-only in edit mode. Mount with a `key` to reset the form.
 */
export function CardFormModal({
  open,
  card,
  loading,
  onClose,
  onCreate,
  onUpdate,
}: {
  open: boolean;
  card: VocabCard | null;
  loading?: boolean;
  onClose: () => void;
  onCreate: (input: CardInput) => void;
  onUpdate: (id: string, input: UpdateCardInput) => void;
}) {
  const [d, setD] = useState<CardDraft>(() => draftFrom(card));
  const [errors, setErrors] = useState<{ word?: string; meaning?: string }>({});
  const isEdit = !!card;

  const set = (key: keyof CardDraft) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) =>
    setD({ ...d, [key]: e.target.value });

  // Legacy values not in the list stay selectable.
  const knownPos = PARTS_OF_SPEECH.some(([v]) => v === d.partOfSpeech);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const next: typeof errors = {};
    if (!isEdit && !d.word.trim()) next.word = 'Vui lòng nhập từ.';
    if (!d.meaning.trim()) next.meaning = 'Vui lòng nhập nghĩa.';
    setErrors(next);
    if (Object.keys(next).length) return;

    if (card) {
      // Edit: send every field so blanks clear the stored value.
      onUpdate(card.id, {
        meaning: d.meaning.trim(),
        partOfSpeech: d.partOfSpeech.trim(),
        ipa: d.ipa.trim(),
        example: d.example.trim(),
        exampleVi: d.exampleVi.trim(),
        audioUrl: d.audioUrl.trim(),
        imageUrl: d.imageUrl.trim(),
      });
    } else {
      onCreate({
        word: d.word.trim(),
        meaning: d.meaning.trim(),
        partOfSpeech: optional(d.partOfSpeech),
        ipa: optional(d.ipa),
        example: optional(d.example),
        exampleVi: optional(d.exampleVi),
        audioUrl: optional(d.audioUrl),
        imageUrl: optional(d.imageUrl),
      });
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={isEdit ? `Sửa thẻ “${card?.word}”` : 'Thêm thẻ mới'}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input
            label="Từ"
            value={d.word}
            onChange={set('word')}
            error={errors.word}
            disabled={isEdit}
            hint={isEdit ? 'Không thể đổi từ; hãy xoá và tạo thẻ mới.' : undefined}
          />
          <Select label="Loại từ" value={d.partOfSpeech} onChange={set('partOfSpeech')}>
            <option value="">— Không chọn —</option>
            {!knownPos && d.partOfSpeech && <option value={d.partOfSpeech}>{d.partOfSpeech}</option>}
            {PARTS_OF_SPEECH.map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </Select>
        </div>
        <Input label="Nghĩa tiếng Việt" value={d.meaning} onChange={set('meaning')} error={errors.meaning} />
        <Input label="Phiên âm IPA" value={d.ipa} onChange={set('ipa')} placeholder="/ˈeksəmpəl/" />
        <Textarea label="Ví dụ (tiếng Anh)" rows={2} value={d.example} onChange={set('example')} />
        <Textarea label="Dịch ví dụ" rows={2} value={d.exampleVi} onChange={set('exampleVi')} />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="URL audio" value={d.audioUrl} onChange={set('audioUrl')} placeholder="https://..." />
          <Input label="URL ảnh" value={d.imageUrl} onChange={set('imageUrl')} placeholder="https://..." />
        </div>
        <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" className="min-h-[44px]" onClick={onClose}>
            Huỷ
          </Button>
          <Button type="submit" loading={loading} className="min-h-[44px]">
            {isEdit ? 'Lưu thẻ' : 'Thêm thẻ'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
