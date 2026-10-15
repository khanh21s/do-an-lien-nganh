import { describe, expect, it } from 'vitest';
import type { ImportPreview, ImportRow } from '@/lib/types';
import {
  buildCsvTemplate,
  csvEscape,
  formatCount,
  importableRows,
  remainingCards,
  rowStatus,
  summarizeImport,
  validateImportFile,
} from './importHelpers';

function row(partial: Partial<ImportRow>): ImportRow {
  return {
    line: 1,
    word: 'w',
    meaning: 'm',
    ipa: null,
    partOfSpeech: null,
    example: null,
    exampleVi: null,
    errors: [],
    duplicateInFile: false,
    existsInDeck: false,
    ...partial,
  };
}

/** Minimal RFC 4180 reader used to check the template round-trips. */
function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let cell = '';
  let cells: string[] = [];
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') quoted = false;
      else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') {
      cells.push(cell);
      cell = '';
    } else if (c === '\n') {
      cells.push(cell);
      rows.push(cells);
      cells = [];
      cell = '';
    } else if (c !== '\r') cell += c;
  }
  return rows;
}

describe('CSV template', () => {
  it('has a BOM, the documented header and parseable rows', () => {
    const csv = buildCsvTemplate();
    expect(csv.startsWith('﻿')).toBe(true);
    const rows = parseCsv(csv.slice(1));
    expect(rows[0]).toEqual(['word', 'meaning', 'ipa', 'partOfSpeech', 'example', 'exampleVi']);
    expect(rows.length).toBe(4);
    expect(rows.every((r) => r.length === 6)).toBe(true);
    expect(rows[2][1]).toBe('cất cánh; cởi (quần áo)');
  });

  it('escapes commas, quotes and line breaks', () => {
    expect(csvEscape('plain')).toBe('plain');
    expect(csvEscape('a, b')).toBe('"a, b"');
    expect(csvEscape('say "hi"')).toBe('"say ""hi"""');
    expect(csvEscape('x\ny')).toBe('"x\ny"');
    expect(csvEscape(null)).toBe('');
  });
});

describe('import file checks', () => {
  it('accepts csv/txt/pdf up to 2 MB', () => {
    expect(validateImportFile({ name: 'Words.CSV', size: 10 })).toBeNull();
    expect(validateImportFile({ name: 'a.pdf', size: 2 * 1024 * 1024 })).toBeNull();
    expect(validateImportFile({ name: 'a.docx', size: 10 })).toMatch(/CSV, TXT hoặc PDF/);
    expect(validateImportFile({ name: 'a.txt', size: 2 * 1024 * 1024 + 1 })).toMatch(/2 MB/);
    expect(validateImportFile({ name: 'a.txt', size: 0 })).toMatch(/rỗng/);
  });
});

describe('preview rows', () => {
  const preview = {
    rows: [
      row({ word: 'apple' }),
      row({ word: 'apple', duplicateInFile: true }),
      row({ word: 'book', existsInDeck: true }),
      row({ word: null, errors: ['Thiếu từ'] }),
      row({ word: 'cat', ipa: 'kæt' }),
    ],
  } as ImportPreview;

  it('classifies rows', () => {
    expect(preview.rows.map(rowStatus)).toEqual(['ok', 'duplicate', 'exists', 'error', 'ok']);
  });

  it('sends only importable, ticked rows', () => {
    expect(importableRows(preview).map((r) => r.word)).toEqual(['apple', 'cat']);
    expect(importableRows(preview, new Set([0])).map((r) => r.word)).toEqual(['cat']);
    expect(importableRows(preview)[1]).toMatchObject({ ipa: 'kæt', meaning: 'm' });
  });
});

describe('limits and summary', () => {
  const usage = {
    premium: false,
    decks: 2,
    maxDecks: 5,
    cards: 480,
    maxCards: 500,
    notebookCards: 1990,
    maxNotebookCards: 2000,
  };

  it('remaining cards per deck kind', () => {
    expect(remainingCards(usage, false)).toBe(20);
    expect(remainingCards(usage, true)).toBe(10);
    expect(remainingCards({ ...usage, cards: 600 }, false)).toBe(0);
    expect(remainingCards(null, false)).toBeNull();
  });

  it('summarises the result in Vietnamese', () => {
    expect(
      summarizeImport({ deckId: 'd', groupId: null, received: 1300, imported: 1200, skippedDuplicate: 99, skippedInvalid: 1, errors: [] })
    ).toBe('Đã nhập 1.200 thẻ · bỏ qua 99 từ trùng · 1 dòng lỗi');
    expect(formatCount(8000)).toBe('8.000');
    expect(formatCount(12)).toBe('12');
  });
});
