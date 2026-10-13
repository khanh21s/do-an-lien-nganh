// Pure client-side validation helpers for admin forms (unit tested in validation.test.ts).

/** ADM-04: same limits as content-service (50 MB, audio/* with an accepted extension). */
export const MAX_AUDIO_BYTES = 50 * 1024 * 1024;
export const AUDIO_EXTENSIONS = ['mp3', 'm4a', 'wav', 'ogg', 'webm'] as const;
export const AUDIO_ACCEPT = 'audio/*,' + AUDIO_EXTENSIONS.map((e) => `.${e}`).join(',');

export interface FileLike {
  name: string;
  type: string;
  size: number;
}

function extensionOf(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i + 1).toLowerCase() : '';
}

/** Returns a Vietnamese error message, or null when the file can be uploaded. */
export function validateAudioFile(file: FileLike): string | null {
  const ext = extensionOf(file.name);
  const extOk = (AUDIO_EXTENSIONS as readonly string[]).includes(ext);
  // Some browsers report an empty MIME type for .m4a/.ogg: then trust the extension.
  const typeOk = file.type === '' ? extOk : file.type.startsWith('audio/');
  if (!typeOk || !extOk) {
    return `File không phải audio hợp lệ. Chỉ chấp nhận ${AUDIO_EXTENSIONS.join(', ')}.`;
  }
  if (file.size <= 0) return 'File audio rỗng.';
  if (file.size > MAX_AUDIO_BYTES) {
    return `File quá lớn (${formatBytes(file.size)}). Dung lượng tối đa 50 MB.`;
  }
  return null;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export interface SentenceDraft {
  text: string;
  ipa: string;
  translationVi: string;
  startMs: string;
  endMs: string;
  sentenceOrder: string;
  audioUrl: string;
}

export type SentenceErrors = Partial<Record<keyof SentenceDraft, string>>;

function parseNonNegativeInt(v: string): number | null {
  if (v.trim() === '') return null;
  const n = Number(v);
  return Number.isInteger(n) && n >= 0 ? n : NaN;
}

/** Validates a sentence form; `requireOrder` for edit (sentenceOrder ≥ 1, ≤ maxOrder). */
export function validateSentenceDraft(
  d: SentenceDraft,
  opts: { requireOrder?: boolean; maxOrder?: number } = {}
): SentenceErrors {
  const errors: SentenceErrors = {};
  if (!d.text.trim()) errors.text = 'Vui lòng nhập nội dung câu.';
  const start = parseNonNegativeInt(d.startMs);
  const end = parseNonNegativeInt(d.endMs);
  if (start === null) errors.startMs = 'Bắt buộc.';
  else if (Number.isNaN(start)) errors.startMs = 'Số nguyên ≥ 0.';
  if (end === null) errors.endMs = 'Bắt buộc.';
  else if (Number.isNaN(end)) errors.endMs = 'Số nguyên ≥ 0.';
  if (start !== null && end !== null && !Number.isNaN(start) && !Number.isNaN(end) && end <= start) {
    errors.endMs = 'Phải lớn hơn thời điểm bắt đầu.';
  }
  if (opts.requireOrder) {
    const order = parseNonNegativeInt(d.sentenceOrder);
    if (order === null || Number.isNaN(order) || order < 1) errors.sentenceOrder = 'Số thứ tự ≥ 1.';
    else if (opts.maxOrder && order > opts.maxOrder) errors.sentenceOrder = `Tối đa ${opts.maxOrder}.`;
  }
  return errors;
}

/** Trimmed value or undefined (so optional fields are omitted instead of sent as ""). */
export function optional(v: string | null | undefined): string | undefined {
  const t = (v ?? '').trim();
  return t === '' ? undefined : t;
}

/** Activation-code batch form: quantity 1–500, optional expiry date not in the past. */
export function validateCodeBatch(quantity: string, expiresAt: string, today: string): string | null {
  const q = Number(quantity);
  if (!Number.isInteger(q) || q < 1 || q > 500) return 'Số lượng phải từ 1 đến 500.';
  if (expiresAt && expiresAt < today) return 'Ngày hết hạn phải từ hôm nay trở đi.';
  return null;
}

/** Local date as YYYY-MM-DD (for <input type="date"> min/compare). */
export function localIsoDate(d = new Date()): string {
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}
