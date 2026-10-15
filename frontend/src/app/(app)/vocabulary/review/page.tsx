'use client';

import { Card } from '@/components/ui/Card';
import { StudySessionPanel } from '@/components/vocab/study/StudySession';
import Link from 'next/link';

/**
 * "Ôn tập" (P2-anki): one Anki-style session over all the user's cards, every deck included.
 * Due learning cards first, then due review cards, then new cards (words added to the notebook,
 * "add to notebook" from lessons/games) in the order they were added. No daily limits.
 */
export default function VocabReviewPage() {
  return (
    <div className="mx-auto max-w-xl">
      <Card className="p-4 sm:p-6">
        <StudySessionPanel
          scope={{ kind: 'global' }}
          header={<h1 className="text-xl font-bold text-slate-900">Ôn tập từ vựng</h1>}
          emptyState={
            <div className="text-center">
              <p className="text-4xl" aria-hidden>
                📒
              </p>
              <p className="mt-2 text-lg font-semibold text-slate-900">Sổ tay đang trống</p>
              <p className="mt-2 text-sm text-slate-500">
                Thêm từ vào sổ tay hoặc học một bộ từ để bắt đầu ôn tập.
              </p>
              <div className="mt-4 flex flex-wrap justify-center gap-2">
                <Link
                  href="/vocabulary"
                  className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
                >
                  Khám phá bộ từ vựng
                </Link>
                <Link
                  href="/vocabulary/notebook"
                  className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Sổ tay
                </Link>
              </div>
            </div>
          }
          doneActions={
            <>
              <Link
                href="/vocabulary"
                className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
              >
                Danh sách bộ từ
              </Link>
              <Link
                href="/dashboard"
                className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                Về trang tổng quan
              </Link>
            </>
          }
        />
      </Card>
    </div>
  );
}
