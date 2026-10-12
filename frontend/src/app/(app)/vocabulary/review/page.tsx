'use client';

import { Flashcard } from '@/components/practice/Flashcard';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useState } from 'react';
import toast from 'react-hot-toast';

const QUALITY_OPTIONS = [
  { value: 0, label: 'Quên hẳn', hint: '10 phút', className: 'bg-red-500 hover:bg-red-600' },
  { value: 2, label: 'Khó nhớ', hint: '1-3 ngày', className: 'bg-amber-500 hover:bg-amber-600' },
  { value: 3, label: 'Nhớ được', hint: '1-4 ngày', className: 'bg-green-500 hover:bg-green-600' },
  { value: 5, label: 'Dễ dàng', hint: '1-7 ngày', className: 'bg-brand-500 hover:bg-brand-600' },
];

export default function VocabReviewPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['review-queue'],
    queryFn: vocabularyApi.getReviewQueue,
  });
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  const [reviewedCount, setReviewedCount] = useState(0);
  const [submitting, setSubmitting] = useState(false);

  if (isLoading) return <FullPageSpinner />;

  const queue = data?.queue ?? [];
  const current = queue[index];

  async function handleAnswer(quality: number) {
    if (!current) return;
    setSubmitting(true);
    try {
      await vocabularyApi.submitReview({ userVocabId: current.id, quality });
      setReviewedCount((c) => c + 1);
      setIndex((i) => i + 1);
      setFlipped(false);
      queryClient.invalidateQueries({ queryKey: ['vocab-stats'] });
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không thể lưu kết quả ôn tập.'));
    } finally {
      setSubmitting(false);
    }
  }

  if (queue.length === 0) {
    return (
      <Card className="mx-auto max-w-md text-center">
        <p className="text-lg font-semibold text-slate-900">Không có từ nào cần ôn tập hôm nay 🎉</p>
        <p className="mt-2 text-sm text-slate-500">Quay lại sau hoặc thêm từ mới vào sổ tay.</p>
        <Link href="/vocabulary">
          <Button className="mt-4">Khám phá bộ từ vựng</Button>
        </Link>
      </Card>
    );
  }

  if (!current) {
    return (
      <Card className="mx-auto max-w-md text-center">
        <p className="text-lg font-semibold text-slate-900">Hoàn thành phiên ôn tập! 🎉</p>
        <p className="mt-2 text-sm text-slate-500">Bạn đã ôn {reviewedCount} từ.</p>
        <Link href="/dashboard">
          <Button className="mt-4">Về trang tổng quan</Button>
        </Link>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-lg">
      <div className="flex items-center justify-between text-sm text-slate-500">
        <span>Ôn tập từ vựng</span>
        <span className="font-semibold text-slate-700">
          {index + 1}/{queue.length} thẻ
        </span>
      </div>
      <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
        <div
          className="h-full rounded-full bg-brand-500 transition-all"
          style={{ width: `${((index + 1) / queue.length) * 100}%` }}
        />
      </div>
      <div className="mt-4">
        <Flashcard
          word={current.word}
          meaning={current.meaning}
          example={current.example}
          ipa={current.ipa}
          flipped={flipped}
          onFlip={setFlipped}
        />
      </div>
      {flipped && (
        <>
          <p className="mt-4 text-center text-sm text-slate-500">Bạn nhớ từ này ở mức nào?</p>
          <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-4">
            {QUALITY_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                disabled={submitting}
                onClick={() => handleAnswer(opt.value)}
                className={clsx(
                  'flex flex-col items-center gap-0.5 rounded-xl px-3 py-3 text-sm font-semibold text-white transition-colors disabled:opacity-60',
                  opt.className
                )}
              >
                {opt.label}
                <span className="text-xs font-normal opacity-90">{opt.hint}</span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
