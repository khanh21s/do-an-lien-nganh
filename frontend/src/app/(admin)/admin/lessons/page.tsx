'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { ProBadge } from '@/components/lessons/ProLessonLock';
import { invalidateLessonCaches } from '@/components/admin/invalidate';
import { optional } from '@/components/admin/validation';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input, Select, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LEVELS, TOPIC_LABELS, TOPIC_TAGS, topicLabel } from '@/lib/constants';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel, CreateLessonInput, LessonSummary } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;

interface LessonForm {
  title: string;
  cefrLevel: CefrLevel;
  audioUrl: string;
  sourceUrl: string;
  topicTag: string;
  description: string;
  isPro: boolean;
}

const emptyForm: LessonForm = {
  title: '',
  cefrLevel: 'A1',
  audioUrl: '',
  sourceUrl: '',
  topicTag: '',
  description: '',
  isPro: false,
};

export default function AdminLessonsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<LessonForm>(emptyForm);
  const [toDelete, setToDelete] = useState<LessonSummary | null>(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.adminLessons(page),
    queryFn: () => contentApi.listLessons({ page, limit: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const createMutation = useMutation({
    mutationFn: (input: CreateLessonInput) => contentApi.createLesson(input),
    onSuccess: (lesson) => {
      toast.success('Đã tạo bài học.');
      setModalOpen(false);
      setForm(emptyForm);
      invalidateLessonCaches(queryClient, lesson.id);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể tạo bài học.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => contentApi.deleteLesson(id),
    onSuccess: (_res, id) => {
      toast.success('Đã xoá bài học.');
      setToDelete(null);
      queryClient.removeQueries({ queryKey: qk.lesson(id) });
      invalidateLessonCaches(queryClient);
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể xoá bài học.')),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.title.trim() || !form.audioUrl.trim()) {
      toast.error('Vui lòng nhập tiêu đề và URL audio.');
      return;
    }
    createMutation.mutate({
      title: form.title.trim(),
      cefrLevel: form.cefrLevel,
      audioUrl: form.audioUrl.trim(),
      sourceUrl: optional(form.sourceUrl),
      topicTag: optional(form.topicTag),
      description: optional(form.description),
      isPro: form.isPro,
    });
  }

  return (
    <div className="mx-auto w-full max-w-5xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý bài học</h1>
        <Button onClick={() => setModalOpen(true)} className="min-h-[44px]">
          + Tạo bài học
        </Button>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-6" />
      ) : !data || data.data.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-slate-500">
          Chưa có bài học nào.{' '}
          <button type="button" className="font-medium text-brand-600 hover:underline" onClick={() => setModalOpen(true)}>
            Tạo bài học đầu tiên
          </button>
        </Card>
      ) : (
        <>
          <div className="mt-6 flex flex-col gap-3">
            {data.data.map((lesson) => (
              <Card key={lesson.id} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="break-words font-semibold text-slate-900">{lesson.title}</h3>
                    <Badge tone="brand">{lesson.cefrLevel}</Badge>
                    {lesson.isPro && <ProBadge />}
                    {lesson.topicTag && <Badge tone="slate">{topicLabel(lesson.topicTag)}</Badge>}
                  </div>
                  <p className="text-sm text-slate-500">{lesson.sentenceCount} câu</p>
                </div>
                <div className="flex shrink-0 gap-2">
                  <Link
                    href={`/admin/lessons/${lesson.id}`}
                    className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
                  >
                    Sửa bài & câu
                  </Link>
                  <Button size="sm" variant="danger" className="min-h-[44px]" onClick={() => setToDelete(lesson)}>
                    Xoá
                  </Button>
                </div>
              </Card>
            ))}
          </div>
          <Pagination page={page} totalPages={data.pagination.totalPages} onChange={setPage} className="mt-6" />
        </>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Tạo bài học mới">
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <Input label="Tiêu đề" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
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
              {TOPIC_TAGS.map((t) => (
                <option key={t} value={t}>
                  {TOPIC_LABELS[t]}
                </option>
              ))}
            </Select>
          </div>
          <Input
            label="URL audio bài học"
            required
            value={form.audioUrl}
            onChange={(e) => setForm({ ...form, audioUrl: e.target.value })}
            placeholder="https://... hoặc local:..."
            hint="Có thể tải file audio lên thay thế ở trang chi tiết bài."
          />
          <Input
            label="URL nguồn (tuỳ chọn)"
            value={form.sourceUrl}
            onChange={(e) => setForm({ ...form, sourceUrl: e.target.value })}
          />
          <Textarea
            label="Mô tả"
            rows={2}
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
          <Button type="submit" loading={createMutation.isPending} className="mt-2 min-h-[44px] w-full">
            Tạo bài học
          </Button>
        </form>
      </Modal>

      <ConfirmModal
        open={!!toDelete}
        title="Xoá bài học?"
        message={
          <>
            Bài <strong>{toDelete?.title}</strong> cùng toàn bộ câu sẽ bị xoá vĩnh viễn.
          </>
        }
        loading={deleteMutation.isPending}
        onConfirm={() => toDelete && deleteMutation.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </div>
  );
}
