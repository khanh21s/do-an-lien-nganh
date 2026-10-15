'use client';

import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { ImportWizard } from '@/components/vocab/import/ImportWizard';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

/** P2-C: admin import wizard into a public deck (`?deckId=` preselects it). No personal limits apply. */
export default function AdminVocabularyImportPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <AdminImport />
    </Suspense>
  );
}

function AdminImport() {
  const deckId = useSearchParams().get('deckId');
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: [...qk.adminDecks, 'all'],
    queryFn: () => vocabularyApi.listDecks({ limit: 100 }),
  });
  if (isLoading) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data || data.data.length === 0)
    return <ErrorState notFound title="Chưa có bộ từ vựng" message="Hãy tạo một bộ từ vựng trước khi nhập." backHref="/admin/vocabulary" backLabel="Quản lý từ vựng" />;
  return (
    <div className="mx-auto w-full max-w-5xl">
      <Link href="/admin/vocabulary" className="text-sm text-brand-600 hover:underline">
        ← Quản lý từ vựng
      </Link>
      <h1 className="mb-4 mt-2 text-2xl font-bold text-slate-900">Nhập từ vựng vào bộ hệ thống</h1>
      <ImportWizard
        decks={data.data.map((d) => ({ id: d.id, label: d.level ? `${d.name} (${d.level})` : d.name }))}
        initialDeckId={deckId}
        usage={null}
        deckHref={(id) => `/admin/vocabulary/${id}`}
      />
    </div>
  );
}
