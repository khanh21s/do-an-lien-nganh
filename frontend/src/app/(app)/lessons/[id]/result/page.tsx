'use client';

import { LessonResult } from '@/components/lessons/LessonResult';
import { FullPageSpinner } from '@/components/ui/Spinner';
import type { PracticeMode } from '@/lib/types';
import { useParams, useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

function ResultContent() {
  const params = useParams<{ id: string }>();
  const search = useSearchParams();
  const mode: PracticeMode = search.get('mode') === 'shadowing' ? 'shadowing' : 'dictation';
  return <LessonResult key={`${params.id}:${mode}`} lessonId={params.id} mode={mode} />;
}

/** `/lessons/[id]/result?mode=dictation|shadowing` (DIC-10, SHA-07). */
export default function LessonResultPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <ResultContent />
    </Suspense>
  );
}
