'use client';

import { Card } from '@/components/ui/Card';
import { apiErrorMessage } from '@/lib/api';
import { contentApi } from '@/lib/endpoints/content';
import type { LessonDetail } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { invalidateLessonCaches } from './invalidate';
import { AUDIO_ACCEPT, AUDIO_EXTENSIONS, formatBytes, validateAudioFile } from './validation';

/** ADM-04: lesson audio upload, blocked client-side when not audio or > 50 MB (BE re-checks → 400). */
export function LessonAudioUpload({ lesson }: { lesson: LessonDetail }) {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<File | null>(null);

  const mutation = useMutation({
    mutationFn: (file: File) => contentApi.uploadLessonAudio(lesson.id, file),
    onSuccess: () => {
      toast.success('Đã tải lên audio bài học.');
      setSelected(null);
      invalidateLessonCaches(queryClient, lesson.id);
    },
    onError: (err) => {
      const msg = apiErrorMessage(err, 'Không thể tải lên audio.');
      setError(msg);
      toast.error(msg);
    },
    onSettled: () => {
      if (inputRef.current) inputRef.current.value = '';
    },
  });

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const problem = validateAudioFile(file);
    if (problem) {
      setError(problem);
      setSelected(null);
      e.target.value = '';
      return;
    }
    setError(null);
    setSelected(file);
    mutation.mutate(file);
  }

  return (
    <Card>
      <h2 className="font-semibold text-slate-900">Audio bài học</h2>
      <p className="mt-1 break-all text-sm text-slate-500">
        {lesson.audioUrl ? (
          <>
            Hiện tại: <code className="text-xs">{lesson.audioUrl}</code>
          </>
        ) : (
          'Chưa có audio.'
        )}
      </p>
      {lesson.audioUrl && (
        <audio
          key={lesson.audioUrl}
          controls
          preload="none"
          src={contentApi.lessonAudioUrl(lesson.id)}
          className="mt-3 w-full"
        />
      )}
      <label className="mt-3 flex flex-col gap-1 text-sm">
        <span className="font-medium text-slate-700">Tải file mới ({AUDIO_EXTENSIONS.join(', ')} · tối đa 50 MB)</span>
        <input
          ref={inputRef}
          type="file"
          accept={AUDIO_ACCEPT}
          onChange={handleChange}
          disabled={mutation.isPending}
          className="min-h-[44px] text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:text-brand-700"
        />
      </label>
      {mutation.isPending && selected && (
        <p className="mt-1 text-sm text-slate-500" role="status">
          Đang tải lên {selected.name} ({formatBytes(selected.size)})…
        </p>
      )}
      {error && (
        <p className="mt-1 text-sm text-red-600" role="alert">
          {error}
        </p>
      )}
    </Card>
  );
}
