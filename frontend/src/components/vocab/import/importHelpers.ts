import type { DeckUsage, ImportCommitResult, ImportPreview, ImportRow, ImportRowInput } from '@/lib/types';

/**
 * P2-C import wizard: pure helpers (unit tested). Limits mirror `VocabLimits` on the
 * vocabulary-service; the server re-checks everything.
 */

export const IMPORT_MAX_BYTES = 2 * 1024 * 1024;
export const IMPORT_MAX_ROWS = 2000;
export const IMPORT_ACCEPT = '.csv,.txt,.pdf,text/csv,text/plain,application/pdf';

export const CSV_TEMPLATE_COLUMNS = ['word', 'meaning', 'ipa', 'partOfSpeech', 'example', 'exampleVi'] as const;

const TEMPLATE_ROWS: string[][] = [
  ['apple', 'quả táo', 'ˈæp.əl', 'noun', 'I eat an apple every day.', 'Tôi ăn một quả táo mỗi ngày.'],
  ['take off', 'cất cánh; cởi (quần áo)', 'teɪk ɒf', 'phrasal verb', 'The plane took off at 6 a.m.', 'Máy bay cất cánh lúc 6 giờ sáng.'],
  ['beautiful', 'đẹp', '', 'adjective', '', ''],
];

/** RFC 4180 quoting: fields with a comma, quote or line break are wrapped, quotes doubled. */
export function csvEscape(value: string | null | undefined): string {
  const s = value ?? '';
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

/**
 * The downloadable template. Starts with a UTF-8 BOM so Excel opens the Vietnamese text
 * correctly (the importer strips it); CRLF line endings as Excel writes them.
 */
export function buildCsvTemplate(): string {
  const lines = [CSV_TEMPLATE_COLUMNS.join(','), ...TEMPLATE_ROWS.map((r) => r.map(csvEscape).join(','))];
  return '﻿' + lines.join('\r\n') + '\r\n';
}

/** Client-side check before uploading; null = OK. */
export function validateImportFile(file: { name: string; size: number }): string | null {
  const name = file.name.toLowerCase();
  if (!/\.(csv|txt|tsv|pdf)$/.test(name)) return 'Chỉ hỗ trợ file CSV, TXT hoặc PDF.';
  if (file.size === 0) return 'File rỗng.';
  if (file.size > IMPORT_MAX_BYTES) return 'File vượt quá 2 MB.';
  return null;
}

export type RowStatus = 'ok' | 'error' | 'duplicate' | 'exists';

export function rowStatus(row: ImportRow): RowStatus {
  if (row.errors.length > 0) return 'error';
  if (row.existsInDeck) return 'exists';
  if (row.duplicateInFile) return 'duplicate';
  return 'ok';
}

export const ROW_STATUS_LABEL: Record<RowStatus, string> = {
  ok: 'Hợp lệ',
  error: 'Lỗi',
  duplicate: 'Trùng trong file',
  exists: 'Đã có trong bộ',
};

/** Rows that will be sent for commit: valid, not duplicated, not unticked by the user (by index). */
export function importableRows(preview: ImportPreview, excluded: ReadonlySet<number> = new Set()): ImportRowInput[] {
  return preview.rows
    .filter((row, i) => rowStatus(row) === 'ok' && !excluded.has(i))
    .map((row) => ({
      word: row.word,
      meaning: row.meaning,
      ipa: row.ipa,
      partOfSpeech: row.partOfSpeech,
      example: row.example,
      exampleVi: row.exampleVi,
    }));
}

/** How many more cards fit in the user's personal decks; null = no limit (public deck / unknown). */
export function remainingCards(usage: DeckUsage | null | undefined, isNotebook: boolean): number | null {
  if (!usage) return null;
  return isNotebook
    ? Math.max(0, usage.maxNotebookCards - usage.notebookCards)
    : Math.max(0, usage.maxCards - usage.cards);
}

/** "Đã nhập 12 thẻ · bỏ qua 3 từ trùng · 1 dòng lỗi" */
export function summarizeImport(result: ImportCommitResult): string {
  const parts = [`Đã nhập ${formatCount(result.imported)} thẻ`];
  if (result.skippedDuplicate) parts.push(`bỏ qua ${formatCount(result.skippedDuplicate)} từ trùng`);
  if (result.skippedInvalid) parts.push(`${formatCount(result.skippedInvalid)} dòng lỗi`);
  return parts.join(' · ');
}

/** 8000 → "8.000" (vi-VN grouping, without depending on the runtime's ICU data). */
export function formatCount(n: number): string {
  return String(Math.trunc(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}
