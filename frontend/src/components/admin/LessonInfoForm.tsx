'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select, Textarea } from '@/components/ui/Input';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LEVELS, TOPIC_LABELS, TOPIC_TAGS, isTopicTag } from '@/lib/constants';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel, LessonDetail, UpdateLessonInput } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';
import { invalidateLessonCaches } from './invalidate';

/** ADM-02: edits exactly `UpdateLessonRequest` (title, description, topicTag, cefrLevel; P2-A1: isPro). */
export function LessonInfoForm({ lesson }: { lesson: LessonDetail }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    title: lesson.title,
    description: lesson.description ?? '',
    topicTag: lesson.topicTag ?? '',
    cefrLevel: lesson.cefrLevel,
    isPro: !!lesson.isPro,
  });
  const [titleError, setTitleError] = useState<string | null>(null);

  const dirty =
    form.title !== lesson.title ||
    form.description !== (lesson.description ?? '') ||
    form.topicTag !== (lesson.topicTag ?? '') ||
    form.cefrLevel !== lesson.cefrLevel ||
    form.isPro !== !!lesson.isPro;

  const mutation = useMutation({
    mutationFn: (input: UpdateLessonInput) => contentApi.updateLesson(lesson.id, input),
    onSuccess: (updated) => {
      toast.success('Đã lưu thông tin bài học.');
      queryClient.setQueryData(qk.lesson(lesson.id), updated);
      invalidateLessonCaches(queryClient, lesson.id);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được thông tin bài học.')),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const title = form.title.trim();
    if (!title) {
      setTitleError('Tiêu đề không được để trống.');
      return;
    }
    setTitleError(null);
    mutation.mutate({
      title,
      description: form.description.trim(),
      topicTag: form.topicTag,
      cefrLevel: form.cefrLevel,
      isPro: form.isPro,
    });
  }

  // Keep a legacy/unknown tag selectable so saving does not silently change it.
  const legacyTag = form.topicTag && !isTopicTag(form.topicTag) ? form.topicTag : null;

  return (
    <Card>
      <h2 className="font-semibold text-slate-900">Thông tin bài học</h2>
      <form onSubmit={handleSubmit} className="mt-3 flex flex-col gap-3">
        <Input
          label="Tiêu đề"
          value={form.title}
          error={titleError ?? undefined}
          onChange={(e) => setForm({ ...form, title: e.target.value })}
        />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Select
            label="Cấp độ CEFR"
            value={form.cefrLevel}
            onChange={(e) => setForm({ ...form, cefrLevel: e.target.value as CefrLevel })}
          >
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </Select>
          <Select label="Chủ đề" value={form.topicTag} onChange={(e) => setForm({ ...form, topicTag: e.target.value })}>
            <option value="">— Không chọn —</option>
            {legacyTag && <option value={legacyTag}>{legacyTag}</option>}
            {TOPIC_TAGS.map((t) => (
              <option key={t} value={t}>
                {TOPIC_LABELS[t]}
              </option>
            ))}
          </Select>
        </div>
        <Textarea
          label="Mô tả"
          rows={3}
          value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })}
        />
        <label className="flex min-h-[44px] items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={form.isPro}
            onChange={(e) => setForm({ ...form, isPro: e.target.checked })}
          />
          Chỉ dành cho Premium
          <span className="text-xs text-slate-400">(người dùng free vẫn xem được bài và transcript)</span>
        </label>
        <Button type="submit" loading={mutation.isPending} disabled={!dirty} className="min-h-[44px] sm:w-fit">
          Lưu thay đổi
        </Button>
      </form>
    </Card>
  );
}
