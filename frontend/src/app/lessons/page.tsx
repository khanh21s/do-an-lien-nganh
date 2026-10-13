'use client';

import { FilterChip } from '@/components/lessons/FilterChip';
import { LessonCard } from '@/components/lessons/LessonCard';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useDebounce } from '@/hooks/useDebounce';
import { CEFR_LEVELS, TOPIC_TAGS, isTopicTag, topicIcon, topicLabel } from '@/lib/constants';
import { contentApi } from '@/lib/endpoints/content';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel } from '@/lib/types';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';

const PAGE_SIZE = 12;
const MIN_SEARCH_LENGTH = 2;

function isCefr(value: string | null): value is CefrLevel {
  return !!value && (CEFR_LEVELS as readonly string[]).includes(value);
}

function LessonsBrowser() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  // LES-02: the whole filter state lives in the query string (`?q=&level=&topic=&page=`).
  const q = searchParams.get('q') ?? '';
  const levelParam = searchParams.get('level');
  const level = isCefr(levelParam) ? levelParam : '';
  const topicParam = searchParams.get('topic');
  const topic = topicParam && isTopicTag(topicParam) ? topicParam : '';
  const page = Math.max(1, Number(searchParams.get('page')) || 1);

  const updateParams = useCallback(
    (patch: Record<string, string | number | null>, resetPage = true) => {
      const next = new URLSearchParams(searchParams.toString());
      for (const [k, v] of Object.entries(patch)) {
        if (v === null || v === '' || v === undefined) next.delete(k);
        else next.set(k, String(v));
      }
      if (resetPage && !('page' in patch)) next.delete('page');
      if (next.get('page') === '1') next.delete('page');
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [pathname, router, searchParams]
  );

  const [qInput, setQInput] = useState(q);
  const debouncedQ = useDebounce(qInput, 300);
  // Last `q` this component wrote to the URL; any other change came from outside.
  const pushedQRef = useRef(q);

  // Push the debounced text into the URL (only when it actually changed).
  useEffect(() => {
    const trimmed = debouncedQ.trim();
    if (trimmed !== q.trim()) {
      pushedQRef.current = trimmed;
      updateParams({ q: trimmed || null });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- react to the debounced value only
  }, [debouncedQ]);

  // Keep the box in sync when the URL changes from outside (Back/Forward, links).
  useEffect(() => {
    if (q !== pushedQRef.current) {
      pushedQRef.current = q;
      setQInput(q);
    }
  }, [q]);

  const searchTerm = q.trim();
  const searching = searchTerm.length >= MIN_SEARCH_LENGTH;

  const query = useQuery({
    queryKey: searching
      ? qk.lessonSearch(searchTerm, { page, limit: PAGE_SIZE })
      : qk.lessons({ page, limit: PAGE_SIZE, cefrLevel: level || undefined, topicTag: topic || undefined }),
    queryFn: () =>
      searching
        ? contentApi.searchLessons(searchTerm, { page, limit: PAGE_SIZE })
        : contentApi.listLessons({
            page,
            limit: PAGE_SIZE,
            cefrLevel: level || undefined,
            topicTag: topic || undefined,
          }),
    placeholderData: keepPreviousData,
  });

  // The search endpoint only takes `q`, so CEFR/topic chips are applied on its results client-side.
  const lessons = (query.data?.data ?? []).filter(
    (l) => !searching || ((!level || l.cefrLevel === level) && (!topic || l.topicTag === topic))
  );
  const totalPages = query.data?.pagination.totalPages ?? 0;
  const hasFilters = !!(q || level || topic);

  function clearFilters() {
    pushedQRef.current = '';
    setQInput('');
    router.replace(pathname, { scroll: false });
  }

  return (
    <div className="mx-auto max-w-6xl">
      <h1 className="text-2xl font-bold text-slate-900">Bài học</h1>
      <p className="mt-1 text-sm text-slate-500">Chọn bài để luyện nghe chép (Dictation) hoặc nói theo (Shadowing).</p>

      <div className="mt-5 flex flex-col gap-3">
        <div className="relative">
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden>
            🔍
          </span>
          <input
            type="search"
            value={qInput}
            onChange={(e) => setQInput(e.target.value)}
            placeholder="Tìm bài học (ít nhất 2 ký tự)…"
            aria-label="Tìm bài học"
            className="h-11 w-full rounded-xl border border-slate-300 bg-white pl-10 pr-3 text-sm outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
          />
        </div>

        <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc theo trình độ">
          <FilterChip active={!level} onClick={() => updateParams({ level: null })}>
            Mọi trình độ
          </FilterChip>
          {CEFR_LEVELS.map((lvl) => (
            <FilterChip key={lvl} active={level === lvl} onClick={() => updateParams({ level: level === lvl ? null : lvl })}>
              {lvl}
            </FilterChip>
          ))}
        </div>

        <div className="flex flex-wrap gap-2" role="group" aria-label="Lọc theo chủ đề">
          <FilterChip active={!topic} onClick={() => updateParams({ topic: null })}>
            Mọi chủ đề
          </FilterChip>
          {TOPIC_TAGS.map((t) => (
            <FilterChip key={t} active={topic === t} onClick={() => updateParams({ topic: topic === t ? null : t })}>
              <span aria-hidden>{topicIcon(t)}</span> {topicLabel(t)}
            </FilterChip>
          ))}
        </div>

        {searchTerm.length > 0 && !searching && (
          <p className="text-xs text-slate-500">Nhập ít nhất {MIN_SEARCH_LENGTH} ký tự để tìm kiếm.</p>
        )}
      </div>

      <div className="mt-6">
        {query.isLoading ? (
          <FullPageSpinner />
        ) : query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : lessons.length === 0 ? (
          <div className="mx-auto flex max-w-md flex-col items-center gap-3 rounded-2xl border border-dashed border-slate-300 bg-white px-6 py-10 text-center">
            <div className="text-4xl" aria-hidden>
              📭
            </div>
            <p className="text-sm text-slate-600">
              {searching ? `Không tìm thấy bài học nào cho “${searchTerm}”.` : 'Chưa có bài học phù hợp với bộ lọc.'}
            </p>
            {hasFilters && (
              <Button variant="secondary" onClick={clearFilters} className="min-h-[44px]">
                Xoá bộ lọc
              </Button>
            )}
          </div>
        ) : (
          <>
            <div
              className={`grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 ${query.isPlaceholderData ? 'opacity-60' : ''}`}
            >
              {lessons.map((lesson) => (
                <LessonCard key={lesson.id} lesson={lesson} />
              ))}
            </div>
            <Pagination
              className="mt-8"
              page={page}
              totalPages={totalPages}
              onChange={(p) => updateParams({ page: p }, false)}
            />
          </>
        )}
      </div>
    </div>
  );
}

export default function LessonsPage() {
  // `useSearchParams` needs a Suspense boundary in the App Router.
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <LessonsBrowser />
    </Suspense>
  );
}
