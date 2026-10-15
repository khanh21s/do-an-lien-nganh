'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { ErrorState } from '@/components/ui/ErrorState';
import { Modal } from '@/components/ui/Modal';
import { Spinner } from '@/components/ui/Spinner';
import { pronounce } from '@/components/vocab/pronounce';
import { currentPathForNext, loginUrlWithNext } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { DictionaryEntry } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { formatIpa, groupDefinitions, sourceNote } from './wordInfo';

/**
 * P2-C word info sheet: IPA, pronunciation, Vietnamese meaning (from the app's cards) and English
 * definitions (Free Dictionary API). Renders the shared `Modal` (role="dialog") so page shortcuts
 * pause while it is open. `onAdd` receives the lookup result (null while unknown).
 */
export function WordInfoModal({
  word,
  onClose,
  onAdd,
}: {
  /** null = closed. */
  word: string | null;
  onClose: () => void;
  onAdd: (entry: DictionaryEntry | null) => void;
}) {
  const signedIn = useAuthStore((s) => !!s.token);
  const query = useQuery({
    queryKey: qk.dictionary((word ?? '').toLowerCase()),
    queryFn: () => vocabularyApi.lookupWord(word ?? ''),
    enabled: !!word,
    staleTime: 5 * 60 * 1000,
    retry: false,
  });
  const entry = query.data;

  return (
    <Modal open={word !== null} onClose={onClose} title={entry?.word ?? word ?? ''} className="sm:max-w-md">
      {query.isLoading ? (
        <div className="flex justify-center py-8">
          <Spinner />
        </div>
      ) : query.isError ? (
        <ErrorState error={query.error} title="Không tra được từ này" onRetry={() => query.refetch()} />
      ) : entry ? (
        <WordInfoBody entry={entry} clicked={word ?? ''} />
      ) : null}

      <div className="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" className="min-h-[44px]" onClick={onClose}>
          Đóng
        </Button>
        {signedIn ? (
          <Button className="min-h-[44px]" disabled={query.isLoading} onClick={() => onAdd(entry ?? null)}>
            + Thêm vào sổ tay / bộ thẻ
          </Button>
        ) : (
          <Link
            href={loginUrlWithNext(currentPathForNext())}
            className="inline-flex min-h-[44px] items-center justify-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
          >
            Đăng nhập để lưu từ
          </Link>
        )}
      </div>
    </Modal>
  );
}

function WordInfoBody({ entry, clicked }: { entry: DictionaryEntry; clicked: string }) {
  const ipa = formatIpa(entry.ipa);
  const note = sourceNote(entry);
  const groups = groupDefinitions(entry);

  if (!entry.found) {
    return (
      <div className="flex flex-col gap-2 text-sm text-slate-600">
        <p>Chưa có dữ liệu cho “{clicked}”.</p>
        {note && <p className="text-xs text-amber-700">{note}</p>}
        <p className="text-xs text-slate-400">Bạn vẫn có thể tự nhập nghĩa và lưu từ này.</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        {ipa && <span className="text-base text-slate-500">{ipa}</span>}
        <button
          type="button"
          onClick={() => pronounce(entry.word, entry.audioUrl)}
          className="inline-flex h-11 items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 text-slate-700 hover:bg-slate-50"
          aria-label={`Nghe phát âm “${entry.word}”`}
        >
          🔊 <span>Nghe</span>
        </button>
        {entry.query !== entry.word && (
          <Badge tone="slate" className="whitespace-nowrap">
            từ gốc của “{entry.query}”
          </Badge>
        )}
      </div>

      {entry.meaningsVi.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Nghĩa tiếng Việt</h3>
          <ul className="mt-1 list-disc pl-5 text-slate-800">
            {entry.meaningsVi.map((m) => (
              <li key={m} className="break-words">
                {m}
              </li>
            ))}
          </ul>
        </section>
      )}

      {entry.examples.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Ví dụ</h3>
          {entry.examples.map((ex) => (
            <p key={ex.en} className="mt-1 break-words">
              <span className="italic text-slate-700">{ex.en}</span>
              {ex.vi && <span className="block text-slate-500">{ex.vi}</span>}
            </p>
          ))}
        </section>
      )}

      {groups.length > 0 && (
        <section>
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">Định nghĩa (tiếng Anh)</h3>
          <div className="mt-1 flex flex-col gap-2">
            {groups.map((g) => (
              <div key={g.partOfSpeech}>
                {g.partOfSpeech && <Badge tone="brand">{g.partOfSpeech}</Badge>}
                <ol className="mt-1 list-decimal pl-5 text-slate-700">
                  {g.items.slice(0, 4).map((d, i) => (
                    <li key={i} className="break-words">
                      {d.definition}
                      {d.example && <span className="block text-xs italic text-slate-500">“{d.example}”</span>}
                    </li>
                  ))}
                </ol>
              </div>
            ))}
          </div>
        </section>
      )}

      {note && <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">{note}</p>}
      {groups.length > 0 && (
        <p className="text-[11px] text-slate-400">Định nghĩa tiếng Anh: Free Dictionary API (dictionaryapi.dev).</p>
      )}
    </div>
  );
}
