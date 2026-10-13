'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Modal } from '@/components/ui/Modal';
import { apiErrorMessage } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import type { LessonDetail, SentenceInput, SentenceResponse, UpdateSentenceInput } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';
import { ConfirmModal } from './ConfirmModal';
import { SentenceFields, draftFromSentence, emptySentenceDraft } from './SentenceFields';
import { invalidateLessonCaches } from './invalidate';
import { optional, validateSentenceDraft, type SentenceDraft, type SentenceErrors } from './validation';

function msLabel(ms: number) {
  return `${(ms / 1000).toFixed(1)}s`;
}

/** ADM-03: list, add, edit (modal) and delete (with confirmation) sentences of a lesson. */
export function SentenceManager({ lesson }: { lesson: LessonDetail }) {
  const queryClient = useQueryClient();
  const sentences = [...lesson.sentences].sort((a, b) => a.sentenceOrder - b.sentenceOrder);
  const lastEnd = sentences.length ? sentences[sentences.length - 1].endMs : 0;

  const [editing, setEditing] = useState<SentenceResponse | null>(null);
  const [editDraft, setEditDraft] = useState<SentenceDraft>(emptySentenceDraft);
  const [editErrors, setEditErrors] = useState<SentenceErrors>({});
  const [toDelete, setToDelete] = useState<SentenceResponse | null>(null);
  const [addDraft, setAddDraft] = useState<SentenceDraft>({ ...emptySentenceDraft, startMs: String(lastEnd) });
  const [addErrors, setAddErrors] = useState<SentenceErrors>({});

  const refresh = () => invalidateLessonCaches(queryClient, lesson.id);

  const addMutation = useMutation({
    mutationFn: (input: SentenceInput) => contentApi.addSentences(lesson.id, [input]),
    onSuccess: (_res, input) => {
      toast.success('Đã thêm câu.');
      setAddDraft({ ...emptySentenceDraft, startMs: String(input.endMs) });
      setAddErrors({});
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể thêm câu.')),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: string; input: UpdateSentenceInput }) =>
      contentApi.updateSentence(lesson.id, id, input),
    onSuccess: () => {
      toast.success('Đã lưu câu.');
      setEditing(null);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được câu.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => contentApi.deleteSentence(lesson.id, id),
    onSuccess: () => {
      toast.success('Đã xoá câu. Thứ tự các câu còn lại đã được đánh lại.');
      setToDelete(null);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được câu.')),
  });

  function openEdit(s: SentenceResponse) {
    setEditing(s);
    setEditDraft(draftFromSentence(s));
    setEditErrors({});
  }

  function handleEdit(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    const errors = validateSentenceDraft(editDraft, { requireOrder: true, maxOrder: sentences.length });
    setEditErrors(errors);
    if (Object.keys(errors).length) return;
    const order = Number(editDraft.sentenceOrder);
    updateMutation.mutate({
      id: editing.id,
      input: {
        text: editDraft.text.trim(),
        // Blank strings clear the optional fields (BE: blank → null).
        ipa: editDraft.ipa.trim(),
        translationVi: editDraft.translationVi.trim(),
        audioUrl: editDraft.audioUrl.trim(),
        startMs: Number(editDraft.startMs),
        endMs: Number(editDraft.endMs),
        sentenceOrder: order !== editing.sentenceOrder ? order : undefined,
      },
    });
  }

  function handleAdd(e: FormEvent) {
    e.preventDefault();
    const errors = validateSentenceDraft(addDraft);
    setAddErrors(errors);
    if (Object.keys(errors).length) return;
    addMutation.mutate({
      text: addDraft.text.trim(),
      ipa: optional(addDraft.ipa),
      translationVi: optional(addDraft.translationVi),
      audioUrl: optional(addDraft.audioUrl),
      startMs: Number(addDraft.startMs),
      endMs: Number(addDraft.endMs),
    });
  }

  return (
    <>
      <Card>
        <h2 className="font-semibold text-slate-900">Danh sách câu ({sentences.length})</h2>
        {sentences.length === 0 ? (
          <p className="mt-3 text-sm text-slate-500">Bài chưa có câu nào. Thêm câu ở form bên dưới.</p>
        ) : (
          <ol className="mt-3 flex flex-col gap-2">
            {sentences.map((s) => (
              <li key={s.id} className="flex flex-col gap-2 rounded-lg bg-slate-50 p-3 text-sm sm:flex-row sm:items-start">
                <span className="w-8 shrink-0 font-semibold text-slate-500">{s.sentenceOrder}.</span>
                <div className="min-w-0 flex-1">
                  <p className="break-words text-slate-900">{s.text}</p>
                  {s.ipa && <p className="break-words text-xs text-slate-500">{s.ipa}</p>}
                  {s.translationVi && <p className="break-words text-slate-600">{s.translationVi}</p>}
                  <p className="mt-1 text-xs text-slate-400">
                    {msLabel(s.startMs)} → {msLabel(s.endMs)}
                    {s.audioUrl ? ' · có audio riêng' : ''}
                  </p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => openEdit(s)}>
                    Sửa
                  </Button>
                  <Button size="sm" variant="danger" className="min-h-[44px]" onClick={() => setToDelete(s)}>
                    Xoá
                  </Button>
                </div>
              </li>
            ))}
          </ol>
        )}
      </Card>

      <Card>
        <h2 className="font-semibold text-slate-900">Thêm câu mới</h2>
        <form onSubmit={handleAdd} className="mt-3 flex flex-col gap-3" noValidate>
          <SentenceFields draft={addDraft} errors={addErrors} onChange={setAddDraft} />
          <Button type="submit" loading={addMutation.isPending} className="min-h-[44px] sm:w-fit">
            Thêm câu
          </Button>
        </form>
      </Card>

      <Modal open={!!editing} onClose={() => setEditing(null)} title={`Sửa câu ${editing?.sentenceOrder ?? ''}`}>
        <form onSubmit={handleEdit} className="flex flex-col gap-3" noValidate>
          <SentenceFields
            draft={editDraft}
            errors={editErrors}
            onChange={setEditDraft}
            showOrder
            maxOrder={sentences.length}
          />
          <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="secondary" className="min-h-[44px]" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button type="submit" loading={updateMutation.isPending} className="min-h-[44px]">
              Lưu câu
            </Button>
          </div>
        </form>
      </Modal>

      <ConfirmModal
        open={!!toDelete}
        title="Xoá câu?"
        message={
          <>
            Câu {toDelete?.sentenceOrder}: <em className="break-words">“{toDelete?.text}”</em> sẽ bị xoá. Các câu sau sẽ
            được đánh lại số thứ tự.
          </>
        }
        loading={deleteMutation.isPending}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </>
  );
}
