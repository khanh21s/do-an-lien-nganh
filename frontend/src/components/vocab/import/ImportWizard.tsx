'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Select } from '@/components/ui/Input';
import { DeckGroupPicker, NEW_GROUP, type DeckChoice } from '@/components/vocab/decks/DeckGroupPicker';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { DeckUsage, ImportCommitResult, ImportPreview } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';
import { useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import {
  IMPORT_ACCEPT,
  IMPORT_MAX_ROWS,
  ROW_STATUS_LABEL,
  buildCsvTemplate,
  formatCount,
  importableRows,
  remainingCards,
  rowStatus,
  summarizeImport,
  validateImportFile,
  type RowStatus,
} from './importHelpers';

const STATUS_TONE: Record<RowStatus, 'green' | 'red' | 'amber' | 'slate'> = {
  ok: 'green',
  error: 'red',
  duplicate: 'amber',
  exists: 'slate',
};

const STEPS = ['Chọn file', 'Xem trước', 'Nhập'] as const;

/**
 * P2-C import wizard: upload (CSV/TXT/PDF) → preview with per-row errors → choose deck/group →
 * import → result. Used by learners (their personal decks) and admins (public decks).
 */
export function ImportWizard({
  decks,
  initialDeckId,
  usage,
  deckHref,
}: {
  decks: DeckChoice[];
  initialDeckId?: string | null;
  /** Learner limits (null for admins: public decks have none). */
  usage?: DeckUsage | null;
  /** Where "Xem bộ thẻ" goes after a successful import. */
  deckHref: (deckId: string) => string;
}) {
  const queryClient = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [deckId, setDeckId] = useState<string>(
    initialDeckId && decks.some((d) => d.id === initialDeckId) ? initialDeckId : decks[0]?.id ?? ''
  );
  const [groupId, setGroupId] = useState('');
  const [newGroupName, setNewGroupName] = useState('');
  const [fileError, setFileError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [result, setResult] = useState<ImportCommitResult | null>(null);

  const deck = decks.find((d) => d.id === deckId);
  const rows = useMemo(() => (preview ? importableRows(preview, excluded) : []), [preview, excluded]);
  const remaining = usage ? remainingCards(usage, !!deck?.isNotebook) : null;
  const overLimit = remaining !== null && rows.length > remaining;
  const step = result ? 2 : preview ? 1 : 0;

  const previewMutation = useMutation({
    mutationFn: (file: File) => vocabularyApi.importPreview(file, deckId || undefined),
    onSuccess: (p) => {
      setPreview(p);
      setExcluded(new Set());
    },
    onError: (err) => setFileError(apiErrorMessage(err, 'Không đọc được file.')),
  });

  const commitMutation = useMutation({
    mutationFn: () =>
      vocabularyApi.importCommit(deckId, {
        rows,
        groupId: groupId && groupId !== NEW_GROUP ? groupId : null,
        newGroupName: groupId === NEW_GROUP ? newGroupName.trim() : null,
      }),
    onSuccess: (r) => {
      setResult(r);
      toast.success(summarizeImport(r));
      for (const queryKey of [qk.myDecks, qk.deckDetailAll, qk.deckCardsAll, qk.decksAll, qk.adminDecks, qk.adminCardsAll, qk.learnedWordsAll, qk.vocabStats, qk.reviewQueue]) {
        void queryClient.invalidateQueries({ queryKey });
      }
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không nhập được.')),
  });

  function onFile(file: File | undefined) {
    setFileError(null);
    if (!file) return;
    const err = validateImportFile(file);
    if (err) {
      setFileError(err);
      return;
    }
    previewMutation.mutate(file);
  }

  function downloadTemplate() {
    const blob = new Blob([buildCsvTemplate()], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'parroto-mau-nhap-tu-vung.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  }

  function reset() {
    setPreview(null);
    setResult(null);
    setExcluded(new Set());
    setGroupId('');
    setNewGroupName('');
    if (fileRef.current) fileRef.current.value = '';
  }

  function toggle(i: number) {
    setExcluded((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <ol className="flex flex-wrap gap-2 text-sm" aria-label="Các bước">
        {STEPS.map((label, i) => (
          <li
            key={label}
            aria-current={i === step ? 'step' : undefined}
            className={clsx(
              'rounded-full px-3 py-1',
              i === step ? 'bg-brand-600 text-white' : i < step ? 'bg-brand-100 text-brand-700' : 'bg-slate-100 text-slate-500'
            )}
          >
            {i + 1}. {label}
          </li>
        ))}
      </ol>

      {step === 0 && (
        <Card className="flex flex-col gap-4 p-4 sm:p-6">
          <Select label="Bộ thẻ nhận từ" value={deckId} onChange={(e) => setDeckId(e.target.value)} className="min-h-[44px]">
            {decks.map((d) => (
              <option key={d.id} value={d.id}>
                {d.label}
              </option>
            ))}
          </Select>
          <div>
            <label
              className={clsx(
                'flex min-h-[120px] cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed p-6 text-center',
                previewMutation.isPending ? 'border-brand-300 bg-brand-50' : 'border-slate-300 hover:border-brand-400'
              )}
            >
              <span className="text-3xl" aria-hidden>
                📄
              </span>
              <span className="font-medium text-slate-800">
                {previewMutation.isPending ? 'Đang đọc file…' : 'Chọn file CSV, TXT hoặc PDF (tối đa 2 MB)'}
              </span>
              <span className="text-xs text-slate-500">Tối đa {formatCount(IMPORT_MAX_ROWS)} dòng mỗi lần. Chưa lưu gì cho tới bước cuối.</span>
              <input
                ref={fileRef}
                type="file"
                accept={IMPORT_ACCEPT}
                className="sr-only"
                disabled={previewMutation.isPending || !deckId}
                onChange={(e) => onFile(e.target.files?.[0])}
              />
            </label>
            {fileError && (
              <p role="alert" className="mt-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
                {fileError}
              </p>
            )}
          </div>
          <FormatHelp onDownloadTemplate={downloadTemplate} />
        </Card>
      )}

      {step === 1 && preview && (
        <>
          <Card className="flex flex-col gap-3 p-4">
            <div className="flex flex-wrap items-center gap-2 text-sm">
              <Badge tone="brand">{preview.format.toUpperCase()}</Badge>
              <span className="break-all text-slate-600">{preview.fileName}</span>
            </div>
            <div className="flex flex-wrap gap-2 text-sm">
              <Badge tone="slate">{formatCount(preview.totalRows)} dòng</Badge>
              <Badge tone="green">{formatCount(preview.validRows)} hợp lệ</Badge>
              {preview.invalidRows > 0 && <Badge tone="red">{formatCount(preview.invalidRows)} lỗi</Badge>}
              {preview.duplicateRows > 0 && <Badge tone="amber">{formatCount(preview.duplicateRows)} trùng</Badge>}
            </div>
            {preview.warnings.map((w) => (
              <p key={w} className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
                {w}
              </p>
            ))}
          </Card>

          <PreviewTable preview={preview} excluded={excluded} onToggle={toggle} />

          <Card className="flex flex-col gap-3 p-4">
            <DeckGroupPicker
              decks={decks}
              deckId={deckId}
              groupId={groupId}
              onChange={(d, g) => {
                if (d !== deckId) {
                  setDeckId(d);
                  toast('Đã đổi bộ thẻ: từ trùng sẽ được kiểm tra lại khi nhập.');
                }
                setGroupId(g);
              }}
              allowNewGroup
              newGroupName={newGroupName}
              onNewGroupNameChange={setNewGroupName}
              deckLabel="Nhập vào bộ thẻ"
            />
            {remaining !== null && (
              <p className={clsx('text-xs', overLimit ? 'text-red-600' : 'text-slate-500')}>
                Còn trống {formatCount(remaining)} thẻ{deck?.isNotebook ? ' trong sổ tay' : ' trong các bộ thẻ của bạn'}
                {overLimit ? ` – hãy bỏ bớt ${formatCount(rows.length - remaining)} dòng hoặc nâng cấp Premium.` : '.'}
              </p>
            )}
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
              <Button variant="secondary" className="min-h-[44px]" onClick={reset} disabled={commitMutation.isPending}>
                ← Chọn file khác
              </Button>
              <Button
                className="min-h-[44px]"
                loading={commitMutation.isPending}
                disabled={rows.length === 0 || overLimit || (groupId === NEW_GROUP && !newGroupName.trim())}
                onClick={() => commitMutation.mutate()}
              >
                Nhập {formatCount(rows.length)} thẻ
              </Button>
            </div>
          </Card>
        </>
      )}

      {step === 2 && result && (
        <Card className="flex flex-col items-center gap-3 p-6 text-center">
          <p className="text-3xl" aria-hidden>
            ✅
          </p>
          <p className="text-lg font-semibold text-slate-900">{summarizeImport(result)}</p>
          {result.errors.length > 0 && (
            <ul className="max-h-48 w-full overflow-y-auto rounded-lg bg-red-50 p-3 text-left text-xs text-red-700">
              {result.errors.map((e) => (
                <li key={e.index}>
                  Dòng gửi #{e.index + 1} {e.word ? `(${e.word})` : ''}: {e.message}
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap justify-center gap-2">
            <Link
              href={deckHref(result.deckId)}
              className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
            >
              Xem bộ thẻ
            </Link>
            <Button variant="secondary" className="min-h-[44px]" onClick={reset}>
              Nhập file khác
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}

function PreviewTable({
  preview,
  excluded,
  onToggle,
}: {
  preview: ImportPreview;
  excluded: ReadonlySet<number>;
  onToggle: (i: number) => void;
}) {
  return (
    <Card className="overflow-hidden p-0">
      <div className="max-h-[55vh] overflow-auto">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="sticky top-0 bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="w-12 px-3 py-2">Nhập</th>
              <th className="w-14 px-2 py-2">Dòng</th>
              <th className="px-2 py-2">Từ</th>
              <th className="px-2 py-2">Nghĩa</th>
              <th className="px-2 py-2">IPA / Từ loại</th>
              <th className="px-2 py-2">Trạng thái</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {preview.rows.map((row, i) => {
              const status = rowStatus(row);
              const ok = status === 'ok';
              return (
                <tr key={i} className={clsx(!ok && 'bg-slate-50/60 text-slate-500')}>
                  <td className="px-3 py-2">
                    <input
                      type="checkbox"
                      className="h-5 w-5"
                      aria-label={`Nhập dòng ${row.line}`}
                      checked={ok && !excluded.has(i)}
                      disabled={!ok}
                      onChange={() => onToggle(i)}
                    />
                  </td>
                  <td className="px-2 py-2 text-xs text-slate-400">{row.line}</td>
                  <td className="break-words px-2 py-2 font-medium text-slate-900">{row.word ?? '—'}</td>
                  <td className="break-words px-2 py-2">
                    {row.meaning ?? '—'}
                    {row.example && <span className="block text-xs italic text-slate-500">{row.example}</span>}
                  </td>
                  <td className="px-2 py-2 text-xs text-slate-500">
                    {[row.ipa && `/${row.ipa}/`, row.partOfSpeech].filter(Boolean).join(' · ') || '—'}
                  </td>
                  <td className="px-2 py-2">
                    <Badge tone={STATUS_TONE[status]}>{ROW_STATUS_LABEL[status]}</Badge>
                    {row.errors.map((e) => (
                      <span key={e} className="mt-1 block text-xs text-red-600">
                        {e}
                      </span>
                    ))}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function FormatHelp({ onDownloadTemplate }: { onDownloadTemplate: () => void }) {
  return (
    <div className="flex flex-col gap-2 rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
      <p className="font-medium text-slate-800">Định dạng hỗ trợ</p>
      <ul className="list-disc space-y-1 pl-5">
        <li>
          <strong>CSV</strong> (UTF-8, dấu phẩy hoặc chấm phẩy, dòng tiêu đề tuỳ chọn): cột <code>word</code>,{' '}
          <code>meaning</code>, <code>ipa</code>, <code>partOfSpeech</code>, <code>example</code>, <code>exampleVi</code> – chỉ
          cần từ và nghĩa.
        </li>
        <li>
          <strong>TXT</strong>: mỗi dòng một từ, dạng <code>word⇥meaning</code>, <code>word - meaning</code>,{' '}
          <code>word: meaning</code> hoặc <code>word = meaning</code>. Dòng trống và dòng bắt đầu bằng <code>#</code> được bỏ
          qua.
        </li>
        <li>
          <strong>PDF</strong>: hệ thống trích chữ rồi đọc từng dòng như TXT (bảng được trải thành dòng). Kết quả{' '}
          <strong>chỉ gần đúng</strong> – PDF scan/ảnh không đọc được; luôn kiểm tra bảng xem trước.
        </li>
      </ul>
      <div>
        <Button type="button" size="sm" variant="secondary" className="min-h-[44px]" onClick={onDownloadTemplate}>
          ⬇ Tải file CSV mẫu
        </Button>
      </div>
    </div>
  );
}
