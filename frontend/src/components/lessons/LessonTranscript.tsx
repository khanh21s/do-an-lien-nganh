'use client';

import { Badge } from '@/components/ui/Badge';
import { Spinner } from '@/components/ui/Spinner';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { useSentenceAudio } from '@/hooks/useSentenceAudio';
import { sortSentences } from '@/lib/practice-nav';
import { storageKeys } from '@/lib/storageKeys';
import type { LessonDetail, PracticeMode, SentenceResponse } from '@/lib/types';
import clsx from 'clsx';
import Link from 'next/link';
import { useMemo, useState } from 'react';
import { formatNumber, type SentenceProgress } from './lesson-progress';

function ToggleChip({ on, onClick, children }: { on: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      onClick={onClick}
      className={clsx(
        'inline-flex min-h-[36px] items-center gap-1.5 rounded-full border px-3 text-sm',
        on ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-slate-300 bg-white text-slate-500'
      )}
    >
      <span aria-hidden>{on ? '☑' : '☐'}</span>
      {children}
    </button>
  );
}

/**
 * LES-05: transcript with IPA / translation toggles (remembered in localStorage), ✓ + best score per
 * sentence and mode, a per-sentence listen button and D/S shortcuts into practice.
 */
export function LessonTranscript({
  lesson,
  progress,
  practiceHref,
}: {
  lesson: LessonDetail;
  /** `null` for guests (no attempts). */
  progress: Record<PracticeMode, Map<string, SentenceProgress>> | null;
  practiceHref: (mode: PracticeMode, sentenceId: string) => string;
}) {
  const [showIpa, setShowIpa] = useLocalStorage<boolean>(storageKeys.transcriptIpa, true);
  const [showTranslation, setShowTranslation] = useLocalStorage<boolean>(storageKeys.transcriptTranslation, true);
  const sentences = useMemo(() => sortSentences(lesson.sentences), [lesson.sentences]);

  // One shared player for "nghe câu": the selected sentence auto-plays when it changes.
  const [selected, setSelected] = useState<SentenceResponse | null>(null);
  const audio = useSentenceAudio({
    lessonId: lesson.id,
    sentence: selected,
    lessonAudioUrl: lesson.audioUrl,
    autoPlay: true,
  });
  const canPlay = (s: SentenceResponse) => !!s.audioUrl || !!lesson.audioUrl;

  function listen(s: SentenceResponse) {
    if (selected?.id === s.id) audio.toggle();
    else setSelected(s);
  }

  return (
    <section className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Transcript ({sentences.length} câu)</h2>
        <div className="flex flex-wrap gap-2">
          <ToggleChip on={showIpa} onClick={() => setShowIpa((v) => !v)}>
            IPA
          </ToggleChip>
          <ToggleChip on={showTranslation} onClick={() => setShowTranslation((v) => !v)}>
            Bản dịch
          </ToggleChip>
        </div>
      </div>

      {sentences.length === 0 ? (
        <p className="mt-4 rounded-xl border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
          Bài học chưa có câu nào.
        </p>
      ) : (
        <ol className="mt-4 flex flex-col gap-3">
          {sentences.map((s, i) => {
            const d = progress?.dictation.get(s.id);
            const sh = progress?.shadowing.get(s.id);
            const isSelected = selected?.id === s.id;
            return (
              <li
                key={s.id}
                className={clsx(
                  'rounded-2xl border bg-white p-4 shadow-sm',
                  isSelected ? 'border-brand-300' : 'border-slate-200'
                )}
              >
                <div className="flex items-start gap-3">
                  <span className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600">
                    {i + 1}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="break-words font-medium text-slate-900">{s.text}</p>
                    {showIpa && s.ipa && <p className="mt-1 break-words text-sm text-slate-400">/{s.ipa.replace(/^\/|\/$/g, '')}/</p>}
                    {showTranslation && s.translationVi && (
                      <p className="mt-1 break-words text-sm text-slate-500">{s.translationVi}</p>
                    )}
                    {(d || sh) && (
                      <div className="mt-2 flex flex-wrap gap-2">
                        {d && (
                          <Badge tone="green" className="whitespace-nowrap">
                            ✓ Dictation · tốt nhất {formatNumber(d.bestScore)}
                          </Badge>
                        )}
                        {sh && (
                          <Badge tone="purple" className="whitespace-nowrap">
                            ✓ Shadowing · tốt nhất {formatNumber(sh.bestScore)}
                          </Badge>
                        )}
                      </div>
                    )}
                    {isSelected && audio.error && <p className="mt-2 text-sm text-red-600">{audio.error}</p>}
                  </div>
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2 pl-10">
                  <button
                    type="button"
                    onClick={() => listen(s)}
                    disabled={!canPlay(s)}
                    aria-label={isSelected && audio.isPlaying ? `Dừng câu ${i + 1}` : `Nghe câu ${i + 1}`}
                    className="inline-flex h-11 items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 text-sm text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    {isSelected && audio.isLoading && !audio.isPlaying ? (
                      <Spinner className="h-4 w-4" />
                    ) : isSelected && audio.isPlaying ? (
                      '⏸'
                    ) : (
                      '🔊'
                    )}
                    <span>Nghe</span>
                  </button>
                  <Link
                    href={practiceHref('dictation', s.id)}
                    className="inline-flex h-11 items-center rounded-lg bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700"
                    title="Luyện Dictation câu này"
                  >
                    ✍️ D
                  </Link>
                  <Link
                    href={practiceHref('shadowing', s.id)}
                    className="inline-flex h-11 items-center rounded-lg border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    title="Luyện Shadowing câu này"
                  >
                    🗣️ S
                  </Link>
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </section>
  );
}
