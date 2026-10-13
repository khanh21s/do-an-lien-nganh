import type { ActivationCode } from '@/lib/types';

/** RFC 4180 field escaping: quote when the value contains a comma, quote, CR or LF. */
export function csvField(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return '';
  const s = String(value);
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function toCsv(header: string[], rows: (string | number | null | undefined)[][]): string {
  return [header, ...rows].map((r) => r.map(csvField).join(',')).join('\r\n');
}

export const ACTIVATION_CODE_CSV_HEADER = [
  'code',
  'plan',
  'status',
  'usedByEmail',
  'usedAt',
  'expiresAt',
  'createdAt',
];

export function activationCodeStatus(c: Pick<ActivationCode, 'usedAt' | 'usedByUserId' | 'expiresAt'>, now = Date.now()) {
  if (c.usedAt || c.usedByUserId) return 'used' as const;
  if (new Date(c.expiresAt).getTime() < now) return 'expired' as const;
  return 'unused' as const;
}

/** ADM-06: one line per code + header row. */
export function activationCodesToCsv(codes: ActivationCode[], now = Date.now()): string {
  return toCsv(
    ACTIVATION_CODE_CSV_HEADER,
    codes.map((c) => [
      c.code,
      c.planName || c.planId,
      activationCodeStatus(c, now),
      c.usedByEmail,
      c.usedAt,
      c.expiresAt,
      c.createdAt,
    ])
  );
}

/** Browser-only: download text as a UTF-8 CSV (BOM so Excel shows Vietnamese correctly). */
export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
