import { describe, expect, it } from 'vitest';
import type { ActivationCode } from '@/lib/types';
import { activationCodeStatus, activationCodesToCsv, csvField, toCsv } from './csv';
import {
  MAX_AUDIO_BYTES,
  optional,
  validateAudioFile,
  validateCodeBatch,
  validateSentenceDraft,
  type SentenceDraft,
} from './validation';

const MB = 1024 * 1024;

describe('validateAudioFile (ADM-04)', () => {
  it('accepts a 20MB mp3', () => {
    expect(validateAudioFile({ name: 'lesson.mp3', type: 'audio/mpeg', size: 20 * MB })).toBeNull();
  });
  it('accepts m4a with empty MIME type (browser quirk)', () => {
    expect(validateAudioFile({ name: 'a.M4A', type: '', size: MB })).toBeNull();
  });
  it('blocks .exe and non-audio MIME types', () => {
    expect(validateAudioFile({ name: 'setup.exe', type: 'application/x-msdownload', size: MB })).toMatch(/không phải audio/);
    expect(validateAudioFile({ name: 'setup.exe', type: '', size: MB })).toMatch(/không phải audio/);
    expect(validateAudioFile({ name: 'fake.mp3', type: 'application/octet-stream', size: MB })).toMatch(/không phải audio/);
    expect(validateAudioFile({ name: 'clip.flac', type: 'audio/flac', size: MB })).toMatch(/không phải audio/);
  });
  it('blocks files over 50MB', () => {
    expect(validateAudioFile({ name: 'a.wav', type: 'audio/wav', size: MAX_AUDIO_BYTES })).toBeNull();
    expect(validateAudioFile({ name: 'a.wav', type: 'audio/wav', size: MAX_AUDIO_BYTES + 1 })).toMatch(/50 MB/);
  });
});

describe('validateSentenceDraft (ADM-03)', () => {
  const base: SentenceDraft = {
    text: 'Hello there.',
    ipa: '',
    translationVi: '',
    startMs: '0',
    endMs: '1500',
    sentenceOrder: '2',
    audioUrl: '',
  };
  it('accepts a valid draft', () => {
    expect(validateSentenceDraft(base, { requireOrder: true, maxOrder: 5 })).toEqual({});
  });
  it('requires text and end > start', () => {
    const e = validateSentenceDraft({ ...base, text: '  ', startMs: '2000', endMs: '1000' });
    expect(e.text).toBeDefined();
    expect(e.endMs).toBeDefined();
  });
  it('rejects negative / non-integer times and out-of-range order', () => {
    expect(validateSentenceDraft({ ...base, startMs: '-1' }).startMs).toBeDefined();
    expect(validateSentenceDraft({ ...base, endMs: '1.5' }).endMs).toBeDefined();
    expect(validateSentenceDraft({ ...base, sentenceOrder: '0' }, { requireOrder: true }).sentenceOrder).toBeDefined();
    expect(validateSentenceDraft({ ...base, sentenceOrder: '9' }, { requireOrder: true, maxOrder: 5 }).sentenceOrder).toBeDefined();
  });
  it('optional() drops blanks', () => {
    expect(optional('  ')).toBeUndefined();
    expect(optional(null)).toBeUndefined();
    expect(optional(' x ')).toBe('x');
  });
});

describe('validateCodeBatch (ADM-06)', () => {
  it('enforces 1–500 and a non-past expiry', () => {
    expect(validateCodeBatch('10', '', '2026-10-13')).toBeNull();
    expect(validateCodeBatch('500', '2026-10-13', '2026-10-13')).toBeNull();
    expect(validateCodeBatch('0', '', '2026-10-13')).not.toBeNull();
    expect(validateCodeBatch('501', '', '2026-10-13')).not.toBeNull();
    expect(validateCodeBatch('2.5', '', '2026-10-13')).not.toBeNull();
    expect(validateCodeBatch('10', '2026-10-12', '2026-10-13')).not.toBeNull();
  });
});

describe('CSV export (ADM-06)', () => {
  const code = (i: number, used = false): ActivationCode => ({
    id: `id-${i}`,
    code: `PROMO-AAAA${i}-BBBBB`,
    planId: 'plan-1month',
    planName: 'Premium 1 tháng',
    usedByUserId: used ? 'u1' : null,
    usedByEmail: used ? 'a@b.com' : null,
    usedAt: used ? '2026-10-12T10:00:00+07:00' : null,
    expiresAt: '2027-10-13T00:00:00+07:00',
    createdAt: '2026-10-13T00:00:00+07:00',
  });

  it('escapes commas, quotes and newlines', () => {
    expect(csvField('a,b')).toBe('"a,b"');
    expect(csvField('say "hi"')).toBe('"say ""hi"""');
    expect(csvField('x\ny')).toBe('"x\ny"');
    expect(csvField(null)).toBe('');
    expect(toCsv(['a', 'b'], [[1, null]])).toBe('a,b\r\n1,');
  });

  it('10 codes → header + 10 lines', () => {
    const codes = Array.from({ length: 10 }, (_, i) => code(i));
    const lines = activationCodesToCsv(codes, Date.parse('2026-10-13T12:00:00Z')).split('\r\n');
    expect(lines).toHaveLength(11);
    expect(lines[0]).toBe('code,plan,status,usedByEmail,usedAt,expiresAt,createdAt');
    expect(lines[1].startsWith('PROMO-AAAA0-BBBBB,Premium 1 tháng,unused,,')).toBe(true);
  });

  it('derives status used / expired / unused', () => {
    const now = Date.parse('2026-10-13T12:00:00Z');
    expect(activationCodeStatus(code(1, true), now)).toBe('used');
    expect(activationCodeStatus(code(1), now)).toBe('unused');
    expect(activationCodeStatus({ ...code(1), expiresAt: '2026-01-01T00:00:00Z' }, now)).toBe('expired');
  });
});
