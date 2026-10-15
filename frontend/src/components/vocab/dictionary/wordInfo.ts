import type { AddWordPrefill } from '@/components/vocab/addWordForm';
import type { DictionaryEntry } from '@/lib/types';

/** P2-C word info sheet: pure helpers (unit tested). */

/** "/həˈloʊ/" for display; the API returns IPA without slashes. */
export function formatIpa(ipa: string | null | undefined): string | null {
  const s = (ipa ?? '').trim().replace(/^[/[]+|[/\]]+$/g, '').trim();
  return s ? `/${s}/` : null;
}

/** English definitions grouped by part of speech, in API order. */
export function groupDefinitions(entry: DictionaryEntry): { partOfSpeech: string; items: DictionaryEntry['definitionsEn'] }[] {
  const groups: { partOfSpeech: string; items: DictionaryEntry['definitionsEn'] }[] = [];
  for (const d of entry.definitionsEn) {
    const pos = d.partOfSpeech ?? '';
    const g = groups.find((x) => x.partOfSpeech === pos);
    if (g) g.items.push(d);
    else groups.push({ partOfSpeech: pos, items: [d] });
  }
  return groups;
}

/**
 * AddWordModal prefill from a lookup: the word as resolved (lemma), the first Vietnamese meaning,
 * IPA and part of speech, and the sentence the word was clicked in as the example.
 */
export function prefillFromEntry(
  entry: DictionaryEntry | null | undefined,
  clicked: string,
  context?: { example?: string | null; exampleVi?: string | null }
): AddWordPrefill {
  return {
    word: entry?.found ? entry.word : clicked,
    meaning: entry?.meaningsVi[0] ?? '',
    ipa: entry?.ipa ?? '',
    partOfSpeech: entry?.partsOfSpeech[0] ?? '',
    example: context?.example ?? entry?.examples[0]?.en ?? '',
    exampleVi: context?.example ? context.exampleVi ?? '' : entry?.examples[0]?.vi ?? '',
  };
}

/** Short Vietnamese note about where the data came from / what is missing. */
export function sourceNote(entry: DictionaryEntry): string | null {
  if (entry.externalStatus === 'error') {
    return 'Không kết nối được từ điển tiếng Anh, đang hiển thị dữ liệu có sẵn.';
  }
  if (!entry.found) return null;
  if (entry.meaningsVi.length === 0) return 'Chưa có nghĩa tiếng Việt cho từ này – bạn có thể tự nhập khi lưu.';
  return null;
}
