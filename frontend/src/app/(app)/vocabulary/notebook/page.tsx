'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import toast from 'react-hot-toast';

export default function VocabNotebookPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['learned-words', search],
    queryFn: () => vocabularyApi.getLearned({ limit: 100, searchWord: search || undefined }),
  });

  const removeMutation = useMutation({
    mutationFn: (userVocabId: string) => vocabularyApi.removeFromNotebook(userVocabId),
    onSuccess: () => {
      toast.success('Đã xoá khỏi sổ tay.');
      queryClient.invalidateQueries({ queryKey: ['learned-words'] });
      queryClient.invalidateQueries({ queryKey: ['vocab-stats'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Sổ tay từ vựng</h1>
      <Input
        className="mt-4 max-w-sm"
        placeholder="Tìm từ trong sổ tay..."
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {data?.data.map((word) => (
            <Card key={word.userVocabId} className="flex items-start justify-between gap-2 transition-shadow hover:shadow-md">
              <div className="min-w-0">
                <h3 className="font-semibold text-slate-900">{word.word}</h3>
                <p className="text-sm text-slate-600">{word.meaning}</p>
                <div className="mt-2 flex items-center gap-2">
                  <Badge tone="brand">Đã ôn {word.repetitions} lần</Badge>
                  {word.accuracy > 0 && <Badge tone="green">{Math.round(word.accuracy)}% chính xác</Badge>}
                </div>
              </div>
              <button
                onClick={() => removeMutation.mutate(word.userVocabId)}
                disabled={removeMutation.isPending && removeMutation.variables === word.userVocabId}
                className="shrink-0 text-slate-400 hover:text-red-600"
                aria-label="Xoá khỏi sổ tay"
              >
                ✕
              </button>
            </Card>
          ))}
          {data?.data.length === 0 && <p className="text-slate-500">Sổ tay của bạn còn trống.</p>}
        </div>
      )}
    </div>
  );
}
