'use client';

import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { deckOptions } from '@/components/vocab/decks/myDecks';
import { ImportWizard } from '@/components/vocab/import/ImportWizard';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

/** P2-C: learner import wizard into one of their decks (`?deckId=` preselects it). */
export default function MyDeckImportPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <MyDeckImport />
    </Suspense>
  );
}

function MyDeckImport() {
  const deckId = useSearchParams().get('deckId');
  const { data, isLoading, isError, error, refetch } = useQuery({ queryKey: qk.myDecks, queryFn: vocabularyApi.listMyDecks });
  if (isLoading) return <FullPageSpinner />;
  if (isError) return <ErrorState error={error} onRetry={refetch} />;
  if (!data) return <ErrorState notFound />;
  return (
    <div className="mx-auto w-full max-w-5xl">
      <Link href="/vocabulary/my-decks" className="text-sm text-brand-600 hover:underline">
        ← Bộ thẻ của tôi
      </Link>
      <h1 className="mb-4 mt-2 text-2xl font-bold text-slate-900">Nhập từ vựng từ file</h1>
      <ImportWizard
        decks={deckOptions(data.decks)}
        initialDeckId={deckId}
        usage={data.usage}
        deckHref={(id) => `/vocabulary/my-decks/${id}`}
      />
    </div>
  );
}
