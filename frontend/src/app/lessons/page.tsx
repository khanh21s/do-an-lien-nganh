'use client';

import { PublicHeader } from '@/components/layout/PublicHeader';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { contentApi } from '@/lib/endpoints/content';
import type { CefrLevel } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';

const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export default function LessonsPage() {
  const [cefrLevel, setCefrLevel] = useState('');
  const [page, setPage] = useState(1);

  const { data, isLoading } = useQuery({
    queryKey: ['lessons', cefrLevel, page],
    queryFn: () => contentApi.listLessons({ page, limit: 12, cefrLevel: cefrLevel || undefined }),
  });

  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="mx-auto max-w-6xl px-6 py-10">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-2xl font-bold text-slate-900">Danh sách bài học</h1>
          <Select
            value={cefrLevel}
            onChange={(e) => {
              setCefrLevel(e.target.value);
              setPage(1);
            }}
            className="w-40"
          >
            <option value="">Tất cả cấp độ</option>
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </Select>
        </div>

        {isLoading ? (
          <FullPageSpinner />
        ) : (
          <>
            <div className="mt-6 grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {data?.data.map((lesson) => (
                <Link key={lesson.id} href={`/lessons/${lesson.id}`}>
                  <Card className="h-full transition-shadow hover:shadow-md">
                    <div className="flex items-start justify-between gap-2">
                      <h3 className="font-semibold text-slate-900">{lesson.title}</h3>
                      <Badge tone="brand">{lesson.cefrLevel}</Badge>
                    </div>
                    {lesson.description && <p className="mt-2 line-clamp-2 text-sm text-slate-500">{lesson.description}</p>}
                    <div className="mt-4 flex items-center gap-3 text-xs text-slate-400">
                      {lesson.topicTag && <span>#{lesson.topicTag}</span>}
                      <span>{lesson.sentenceCount} câu</span>
                      {lesson.durationMinutes != null && <span>{lesson.durationMinutes.toFixed(1)} phút</span>}
                    </div>
                  </Card>
                </Link>
              ))}
            </div>
            {data && data.data.length === 0 && (
              <p className="mt-10 text-center text-slate-500">Không tìm thấy bài học nào.</p>
            )}
            {data && data.pagination.totalPages > 1 && (
              <div className="mt-8 flex justify-center gap-2 text-sm">
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
          </>
        )}
      </div>
    </main>
  );
}
