'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { pronounce } from '@/components/vocab/pronounce';
import { formatDateTime } from '@/components/vocab/format';
import { useDebounce } from '@/hooks/useDebounce';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { formatInterval } from '@/lib/srs';
import type { LearnedWord, LearnedWordSchedule } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;

type NotebookWord = LearnedWord & LearnedWordSchedule;

export default function VocabNotebookPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebounce(search.trim(), 300);
  // The page belongs to a search term: a new (debounced) search starts at page 1 without an extra request.
  const [pageState, setPageState] = useState({ q: '', page: 1 });
  const page = pageState.q === debouncedSearch ? pageState.page : 1;
  const setPage = (p: number) => setPageState({ q: debouncedSearch, page: p });
  const [pendingDelete, setPendingDelete] = useState<NotebookWord | null>(null);

  const params = { page, limit: PAGE_SIZE, searchWord: debouncedSearch || undefined };
  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: qk.learnedWords(params),
    queryFn: () => vocabularyApi.getLearned(params),
    placeholderData: keepPreviousData,
  });

  const totalPages = data?.pagination.totalPages ?? 0;
  // After deleting the last word of the last page, step back a page.
  useEffect(() => {
    if (data && page > 1 && page > totalPages) setPageState({ q: debouncedSearch, page: Math.max(1, totalPages) });
  }, [data, page, totalPages, debouncedSearch]);

  const removeMutation = useMutation({
    mutationFn: (userVocabId: string) => vocabularyApi.removeFromNotebook(userVocabId),
    onSuccess: () => {
      toast.success('Đã xoá khỏi sổ tay.');
      setPendingDelete(null);
      queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
      queryClient.invalidateQueries({ queryKey: qk.vocabStats });
      queryClient.invalidateQueries({ queryKey: qk.reviewQueue });
      queryClient.invalidateQueries({ queryKey: qk.deckCardsAll });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể xoá từ này.')),
  });

  const words = (data?.data ?? []) as NotebookWord[];

  return (
    <div>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Sổ tay từ vựng</h1>
          {data && <p className="mt-1 text-sm text-slate-500">{data.pagination.total} từ</p>}
        </div>
        <Link
          href="/vocabulary/review"
          className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
        >
          Ôn tập
        </Link>
      </div>
      <div className="mt-4 max-w-sm">
        <Input
          type="search"
          placeholder="Tìm từ trong sổ tay..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          aria-label="Tìm từ trong sổ tay"
        />
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-6" />
      ) : words.length === 0 ? (
        <Card className="mt-6 text-center">
          {debouncedSearch ? (
            <p className="text-slate-500">Không tìm thấy từ nào khớp &ldquo;{debouncedSearch}&rdquo;.</p>
          ) : (
            <>
              <p className="text-slate-500">Sổ tay của bạn còn trống.</p>
              <Link href="/vocabulary" className="mt-3 inline-block text-sm font-medium text-brand-600 hover:underline">
                Học một bộ từ vựng
              </Link>
            </>
          )}
        </Card>
      ) : (
        <>
          <div className={`mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 ${isFetching ? 'opacity-70' : ''}`}>
            {words.map((word) => (
              <WordCard key={word.userVocabId} word={word} onDelete={() => setPendingDelete(word)} />
            ))}
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} className="mt-6" />
        </>
      )}

      <Modal open={!!pendingDelete} onClose={() => setPendingDelete(null)} title="Xoá khỏi sổ tay?">
        <p className="text-sm text-slate-600">
          Từ <strong>{pendingDelete?.word}</strong> và toàn bộ lịch ôn tập của nó sẽ bị xoá. Bạn có chắc không?
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="secondary" className="min-h-[44px]" onClick={() => setPendingDelete(null)}>
            Huỷ
          </Button>
          <Button
            variant="danger"
            className="min-h-[44px]"
            loading={removeMutation.isPending}
            onClick={() => pendingDelete && removeMutation.mutate(pendingDelete.userVocabId)}
          >
            Xoá
          </Button>
        </div>
      </Modal>
    </div>
  );
}

function WordCard({ word, onDelete }: { word: NotebookWord; onDelete: () => void }) {
  const due = word.nextReviewAt ? Date.parse(word.nextReviewAt) <= Date.now() : false;
  return (
    <Card className="flex items-start justify-between gap-2 transition-shadow hover:shadow-md">
      <div className="min-w-0">
        <div className="flex items-center gap-1">
          <h3 className="break-all font-semibold text-slate-900">{word.word}</h3>
          <button
            type="button"
            onClick={() => pronounce(word.word)}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-brand-600 hover:bg-brand-50"
            aria-label={`Nghe phát âm "${word.word}"`}
          >
            🔊
          </button>
        </div>
        <p className="text-sm text-slate-600">{word.meaning}</p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge tone="brand">Đã ôn {word.repetitions} lần</Badge>
          {word.accuracy > 0 && <Badge tone="green">{Math.round(word.accuracy)}% chính xác</Badge>}
          {word.intervalDays != null && <Badge tone="slate">Chu kỳ {formatInterval(word.intervalDays)}</Badge>}
        </div>
        {word.nextReviewAt && (
          <p className={`mt-2 text-xs ${due ? 'font-medium text-amber-700' : 'text-slate-500'}`}>
            {due ? 'Đến hạn ôn' : 'Ôn tiếp'}: {formatDateTime(word.nextReviewAt)}
          </p>
        )}
      </div>
      <button
        type="button"
        onClick={onDelete}
        className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-slate-400 hover:bg-red-50 hover:text-red-600"
        aria-label={`Xoá "${word.word}" khỏi sổ tay`}
      >
        ✕
      </button>
    </Card>
  );
}
