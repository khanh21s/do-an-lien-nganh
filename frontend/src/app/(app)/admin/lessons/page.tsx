'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import type { CefrLevel, CreateLessonInput } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

const emptyForm: CreateLessonInput = {
  title: '',
  cefrLevel: 'A1',
  audioUrl: '',
  sourceUrl: '',
  topicTag: '',
  description: '',
  duration: undefined,
};

export default function AdminLessonsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [form, setForm] = useState<CreateLessonInput>(emptyForm);

  const { data, isLoading } = useQuery({
    queryKey: ['admin-lessons', page],
    queryFn: () => contentApi.listLessons({ page, limit: 20 }),
  });

  const createMutation = useMutation({
    mutationFn: (input: CreateLessonInput) => contentApi.createLesson(input),
    onSuccess: () => {
      toast.success('Đã tạo bài học.');
      setModalOpen(false);
      setForm(emptyForm);
      queryClient.invalidateQueries({ queryKey: ['admin-lessons'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể tạo bài học — kiểm tra quyền admin của bạn.')),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => contentApi.deleteLesson(id),
    onSuccess: () => {
      toast.success('Đã xoá bài học.');
      queryClient.invalidateQueries({ queryKey: ['admin-lessons'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form.title.trim() || !form.audioUrl.trim()) {
      toast.error('Vui lòng nhập tiêu đề và URL audio.');
      return;
    }
    createMutation.mutate(form);
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-900">Quản lý bài học</h1>
        <Button onClick={() => setModalOpen(true)}>+ Tạo bài học</Button>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mt-6 flex flex-col gap-3">
          {data?.data.map((lesson) => (
            <Card key={lesson.id} className="flex items-center justify-between">
              <div>
                <div className="flex items-center gap-2">
                  <h3 className="font-semibold text-slate-900">{lesson.title}</h3>
                  <Badge tone="brand">{lesson.cefrLevel}</Badge>
                </div>
                <p className="text-sm text-slate-500">{lesson.sentenceCount} câu</p>
              </div>
              <div className="flex gap-2">
                <Link href={`/admin/lessons/${lesson.id}`}>
                  <Button size="sm" variant="secondary">
                    Quản lý câu
                  </Button>
                </Link>
                <Button
                  size="sm"
                  variant="danger"
                  loading={deleteMutation.isPending && deleteMutation.variables === lesson.id}
                  onClick={() => {
                    if (confirm(`Xoá bài học "${lesson.title}"?`)) deleteMutation.mutate(lesson.id);
                  }}
                >
                  Xoá
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {data && data.pagination.totalPages > 1 && (
        <div className="mt-6 flex justify-center gap-2 text-sm">
          {Array.from({ length: data.pagination.totalPages }, (_, i) => i + 1).map((p) => (
            <button
              key={p}
              onClick={() => setPage(p)}
              className={`h-8 w-8 rounded-lg ${p === page ? 'bg-brand-600 text-white' : 'bg-white text-slate-600 hover:bg-slate-100'}`}
            >
              {p}
            </button>
          ))}
        </div>
      )}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Tạo bài học mới">
        <form onSubmit={handleSubmit} className="flex flex-col gap-3">
          <Input label="Tiêu đề" required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
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
          <Input
            label="URL audio bài học"
            required
            value={form.audioUrl}
            onChange={(e) => setForm({ ...form, audioUrl: e.target.value })}
            placeholder="https://..."
          />
          <Input
            label="Thẻ chủ đề"
            value={form.topicTag ?? ''}
            onChange={(e) => setForm({ ...form, topicTag: e.target.value })}
          />
          <Textarea
            label="Mô tả"
            rows={2}
            value={form.description ?? ''}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <Button type="submit" loading={createMutation.isPending} className="mt-2 w-full">
            Tạo bài học
          </Button>
        </form>
      </Modal>
    </div>
  );
}
