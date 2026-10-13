'use client';

import { Input, Textarea } from '@/components/ui/Input';
import type { SentenceResponse } from '@/lib/types';
import type { SentenceDraft, SentenceErrors } from './validation';

export const emptySentenceDraft: SentenceDraft = {
  text: '',
  ipa: '',
  translationVi: '',
  startMs: '',
  endMs: '',
  sentenceOrder: '',
  audioUrl: '',
};

export function draftFromSentence(s: SentenceResponse): SentenceDraft {
  return {
    text: s.text,
    ipa: s.ipa ?? '',
    translationVi: s.translationVi ?? '',
    startMs: String(s.startMs),
    endMs: String(s.endMs),
    sentenceOrder: String(s.sentenceOrder),
    audioUrl: s.audioUrl ?? '',
  };
}

/** Shared fields of the add/edit sentence forms (ADM-03). */
export function SentenceFields({
  draft,
  errors,
  onChange,
  showOrder,
  maxOrder,
}: {
  draft: SentenceDraft;
  errors: SentenceErrors;
  onChange: (next: SentenceDraft) => void;
  showOrder?: boolean;
  maxOrder?: number;
}) {
  const set = (key: keyof SentenceDraft) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    onChange({ ...draft, [key]: e.target.value });

  return (
    <div className="flex flex-col gap-3">
      <Textarea label="Nội dung câu (tiếng Anh)" rows={2} value={draft.text} onChange={set('text')} error={errors.text} />
      <Input label="Bản dịch tiếng Việt" value={draft.translationVi} onChange={set('translationVi')} />
      <Input label="Phiên âm IPA" value={draft.ipa} onChange={set('ipa')} />
      <div className={showOrder ? 'grid grid-cols-1 gap-3 sm:grid-cols-3' : 'grid grid-cols-2 gap-3'}>
        <Input
          type="number"
          inputMode="numeric"
          min={0}
          label="Bắt đầu (ms)"
          value={draft.startMs}
          onChange={set('startMs')}
          error={errors.startMs}
        />
        <Input
          type="number"
          inputMode="numeric"
          min={0}
          label="Kết thúc (ms)"
          value={draft.endMs}
          onChange={set('endMs')}
          error={errors.endMs}
        />
        {showOrder && (
          <Input
            type="number"
            inputMode="numeric"
            min={1}
            max={maxOrder}
            label="Thứ tự"
            value={draft.sentenceOrder}
            onChange={set('sentenceOrder')}
            error={errors.sentenceOrder}
          />
        )}
      </div>
      <Input
        label="URL audio câu (tuỳ chọn)"
        value={draft.audioUrl}
        onChange={set('audioUrl')}
        hint="Để trống → phát đoạn startMs–endMs của audio bài."
      />
    </div>
  );
}
