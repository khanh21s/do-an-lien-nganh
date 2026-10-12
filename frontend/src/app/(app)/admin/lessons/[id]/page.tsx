'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Textarea } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import type { SentenceInput } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { FormEvent, useRef, useState } from 'react';
import toast from 'react-hot-toast';

const emptySentence: SentenceInput = { text: '', translationVi: '', ipa: '', startMs: 0, endMs: 0 };

export default function AdminLessonDetailPage() {
  const params = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [form, setForm] = useState<SentenceInput>(emptySentence);
  const [uploading, setUploading] = useState(false);

  const { data: lesson, isLoading } = useQuery({
    queryKey: ['lesson', params.id],
    queryFn: () => contentApi.getLesson(params.id),
  });

  const addSentenceMutation = useMutation({
    mutationFn: (sentence: SentenceInput) => contentApi.addSentences(params.id, [sentence]),
    onSuccess: () => {
      toast.success('Đã thêm câu.');
      setForm(emptySentence);
      queryClient.invalidateQueries({ queryKey: ['lesson', params.id] });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể thêm câu — kiểm tra quyền admin của bạn.')),
  });

  async function handleUploadAudio(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      await contentApi.uploadLessonAudio(params.id, file);
      toast.success('Đã tải lên audio.');
      queryClient.invalidateQueries({ queryKey: ['lesson', params.id] });
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không thể tải lên audio.'));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  function handleAddSentence(e: FormEvent) {
    e.preventDefault();
    if (!form.text.trim()) {
      toast.error('Vui lòng nhập nội dung câu.');
      return;
    }
    addSentenceMutation.mutate(form);
  }

  if (isLoading || !lesson) return <FullPageSpinner />;

  return (
    <div className="mx-auto max-w-2xl">
      <Link href="/admin/lessons" className="text-sm text-brand-600 hover:underline">
        ← Quay lại danh sách bài học
      </Link>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">{lesson.title}</h1>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900">Audio bài học</h2>
        <p className="mt-1 text-sm text-slate-500">{lesson.audioUrl ? 'Đã có audio.' : 'Chưa có audio.'}</p>
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          onChange={handleUploadAudio}
          disabled={uploading}
          className="mt-2 text-sm"
        />
        {uploading && <p className="mt-1 text-sm text-slate-500">Đang tải lên...</p>}
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900">Danh sách câu ({lesson.sentences.length})</h2>
        <div className="mt-3 flex flex-col gap-2">
          {lesson.sentences.map((s, i) => (
            <div key={s.id} className="rounded-lg bg-slate-50 p-3 text-sm">
              <span className="font-medium text-slate-700">{i + 1}.</span> {s.text}
              {s.translationVi && <span className="text-slate-500"> — {s.translationVi}</span>}
            </div>
          ))}
        </div>
      </Card>

      <Card className="mt-4">
        <h2 className="font-semibold text-slate-900">Thêm câu mới</h2>
        <form onSubmit={handleAddSentence} className="mt-3 flex flex-col gap-3">
          <Textarea
            label="Nội dung câu (tiếng Anh)"
            required
            rows={2}
            value={form.text}
            onChange={(e) => setForm({ ...form, text: e.target.value })}
          />
          <Input
            label="Bản dịch tiếng Việt"
            value={form.translationVi ?? ''}
            onChange={(e) => setForm({ ...form, translationVi: e.target.value })}
          />
          <Input label="Phiên âm IPA" value={form.ipa ?? ''} onChange={(e) => setForm({ ...form, ipa: e.target.value })} />
          <div className="grid grid-cols-2 gap-3">
            <Input
              type="number"
              label="Bắt đầu (ms)"
              value={form.startMs}
              onChange={(e) => setForm({ ...form, startMs: Number(e.target.value) })}
            />
            <Input
              type="number"
              label="Kết thúc (ms)"
              value={form.endMs}
              onChange={(e) => setForm({ ...form, endMs: Number(e.target.value) })}
            />
          </div>
          <Input
            label="URL audio câu (tuỳ chọn)"
            value={form.audioUrl ?? ''}
            onChange={(e) => setForm({ ...form, audioUrl: e.target.value })}
          />
          <Button type="submit" loading={addSentenceMutation.isPending} className="w-fit">
            Thêm câu
          </Button>
        </form>
      </Card>
    </div>
  );
}
