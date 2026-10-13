'use client';

import { copyText } from '@/components/admin/clipboard';
import { activationCodeStatus, activationCodesToCsv, downloadCsv } from '@/components/admin/csv';
import { localIsoDate, validateCodeBatch } from '@/components/admin/validation';
import { durationLabel, formatDate, formatDateTime } from '@/components/subscription/format';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input, Select } from '@/components/ui/Input';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import type { ActivationCode, ListActivationCodesParams } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const PAGE_SIZE = 20;
type Filter = 'all' | 'unused' | 'used';

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'unused', label: 'Chưa dùng' },
  { value: 'used', label: 'Đã dùng' },
];

const STATUS_BADGE = {
  used: { label: 'Đã dùng', tone: 'slate' },
  expired: { label: 'Hết hạn', tone: 'red' },
  unused: { label: 'Chưa dùng', tone: 'green' },
} as const;

async function copyWithToast(text: string, okMsg: string) {
  if (await copyText(text)) toast.success(okMsg);
  else toast.error('Không sao chép được, hãy chọn và copy thủ công.');
}

function csvFilename(prefix: string) {
  return `${prefix}-${localIsoDate()}.csv`;
}

/** ADM-06: generate activation-code batches, list/filter, copy and export CSV. */
export default function AdminActivationCodesPage() {
  const queryClient = useQueryClient();
  const today = localIsoDate();

  const [planId, setPlanId] = useState('');
  const [quantity, setQuantity] = useState('10');
  const [expiresAt, setExpiresAt] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [batch, setBatch] = useState<ActivationCode[] | null>(null);

  const [filter, setFilter] = useState<Filter>('all');
  const [page, setPage] = useState(1);
  const params: ListActivationCodesParams = {
    page,
    limit: PAGE_SIZE,
    ...(filter === 'all' ? {} : { used: filter === 'used' }),
  };

  const plansQuery = useQuery({ queryKey: qk.plans, queryFn: subscriptionApi.listPlans });
  const plans = [...(plansQuery.data ?? [])].sort((a, b) => a.durationDays - b.durationDays);
  const selectedPlanId = planId || plans[0]?.id || '';

  const codesQuery = useQuery({
    queryKey: qk.activationCodes(params),
    queryFn: () => subscriptionApi.listActivationCodes(params),
    placeholderData: keepPreviousData,
  });

  const createMutation = useMutation({
    mutationFn: subscriptionApi.createActivationCodes,
    onSuccess: (res) => {
      setBatch(res.codes);
      toast.success(`Đã tạo ${res.codes.length} mã.`);
      queryClient.invalidateQueries({ queryKey: qk.activationCodesAll });
    },
    onError: (err) => {
      const msg = apiErrorMessage(err, 'Không tạo được mã kích hoạt.');
      setFormError(msg);
      toast.error(msg);
    },
  });

  function handleCreate(e: FormEvent) {
    e.preventDefault();
    if (!selectedPlanId) {
      setFormError('Chưa có gói để tạo mã.');
      return;
    }
    const problem = validateCodeBatch(quantity, expiresAt, today);
    setFormError(problem);
    if (problem) return;
    createMutation.mutate({
      planId: selectedPlanId,
      quantity: Number(quantity),
      // Plain date → BE treats it as end of that day (Asia/Ho_Chi_Minh); omitted → now + 365 days.
      ...(expiresAt ? { expiresAt } : {}),
    });
  }

  const listed = codesQuery.data?.data ?? [];

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <h1 className="text-2xl font-bold text-slate-900">Mã kích hoạt Premium</h1>

      <Card>
        <h2 className="font-semibold text-slate-900">Tạo lô mã mới</h2>
        {plansQuery.isError ? (
          <ErrorState error={plansQuery.error} onRetry={plansQuery.refetch} className="mt-3 border-0" />
        ) : (
          <form onSubmit={handleCreate} className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-4 sm:items-end" noValidate>
            <Select
              label="Gói"
              value={selectedPlanId}
              onChange={(e) => setPlanId(e.target.value)}
              disabled={plansQuery.isLoading}
              className="min-h-[44px]"
            >
              {plans.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name} ({durationLabel(p.durationDays)})
                </option>
              ))}
            </Select>
            <Input
              label="Số lượng (1–500)"
              type="number"
              inputMode="numeric"
              min={1}
              max={500}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="min-h-[44px]"
            />
            <Input
              label="Hết hạn (tuỳ chọn)"
              type="date"
              min={today}
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
              className="min-h-[44px]"
            />
            <Button type="submit" loading={createMutation.isPending} className="min-h-[44px]">
              Tạo mã
            </Button>
            <p className="text-xs text-slate-500 sm:col-span-4">Không chọn ngày hết hạn → mặc định 365 ngày kể từ hôm nay.</p>
            {formError && (
              <p className="text-sm text-red-600 sm:col-span-4" role="alert">
                {formError}
              </p>
            )}
          </form>
        )}
      </Card>

      {batch && batch.length > 0 && (
        <Card className="border-green-200 bg-green-50/40">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="font-semibold text-slate-900">Vừa tạo {batch.length} mã</h2>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                variant="secondary"
                className="min-h-[44px]"
                onClick={() => copyWithToast(batch.map((c) => c.code).join('\n'), `Đã sao chép ${batch.length} mã.`)}
              >
                Copy tất cả
              </Button>
              <Button
                size="sm"
                variant="secondary"
                className="min-h-[44px]"
                onClick={() => downloadCsv(csvFilename('activation-codes-batch'), activationCodesToCsv(batch))}
              >
                Xuất CSV
              </Button>
              <Button size="sm" variant="ghost" className="min-h-[44px]" onClick={() => setBatch(null)}>
                Đóng
              </Button>
            </div>
          </div>
          <ul className="mt-3 grid grid-cols-1 gap-1 sm:grid-cols-2 lg:grid-cols-3">
            {batch.map((c) => (
              <li key={c.id} className="flex items-center justify-between gap-2 rounded-lg bg-white px-3 py-1.5">
                <code className="break-all text-sm">{c.code}</code>
                <CopyButton code={c.code} />
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs text-slate-500">
            {batch[0].planName} · hết hạn {formatDate(batch[0].expiresAt)}
          </p>
        </Card>
      )}

      <Card className="p-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-5 pt-5">
          <div role="tablist" aria-label="Lọc mã" className="flex gap-1 rounded-lg bg-slate-100 p-1">
            {FILTERS.map((f) => (
              <button
                key={f.value}
                type="button"
                role="tab"
                aria-selected={filter === f.value}
                onClick={() => {
                  setFilter(f.value);
                  setPage(1);
                }}
                className={clsx(
                  'min-h-[40px] rounded-md px-3 text-sm font-medium',
                  filter === f.value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'
                )}
              >
                {f.label}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {codesQuery.isFetching && <Spinner />}
            <Button
              size="sm"
              variant="secondary"
              className="min-h-[44px]"
              disabled={listed.length === 0}
              onClick={() => copyWithToast(listed.map((c) => c.code).join('\n'), `Đã sao chép ${listed.length} mã.`)}
            >
              Copy trang này
            </Button>
            <Button
              size="sm"
              variant="secondary"
              className="min-h-[44px]"
              disabled={listed.length === 0}
              onClick={() => downloadCsv(csvFilename(`activation-codes-${filter}-p${page}`), activationCodesToCsv(listed))}
            >
              Xuất CSV trang này
            </Button>
          </div>
        </div>

        {codesQuery.isLoading ? (
          <FullPageSpinner />
        ) : codesQuery.isError ? (
          <ErrorState error={codesQuery.error} onRetry={codesQuery.refetch} className="my-6 border-0" />
        ) : listed.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-slate-500">Không có mã nào.</p>
        ) : (
          <>
            <p className="px-5 pt-3 text-xs text-slate-500">Tổng {codesQuery.data?.pagination.total ?? 0} mã</p>
            {/* Table from md up, stacked cards below to avoid horizontal scroll at 360px. */}
            <div className="hidden md:block">
              <table className="mt-2 w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-600">
                  <tr>
                    <th className="px-5 py-2 font-medium">Mã</th>
                    <th className="px-3 py-2 font-medium">Gói</th>
                    <th className="px-3 py-2 font-medium">Trạng thái</th>
                    <th className="px-3 py-2 font-medium">Người dùng</th>
                    <th className="px-3 py-2 font-medium">Hết hạn</th>
                    <th className="px-3 py-2" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {listed.map((c) => {
                    const st = STATUS_BADGE[activationCodeStatus(c)];
                    return (
                      <tr key={c.id}>
                        <td className="px-5 py-2">
                          <code>{c.code}</code>
                        </td>
                        <td className="px-3 py-2 text-slate-600">{c.planName}</td>
                        <td className="px-3 py-2">
                          <Badge tone={st.tone}>{st.label}</Badge>
                        </td>
                        <td className="px-3 py-2 text-slate-600">
                          {c.usedAt || c.usedByUserId ? (
                            <>
                              <span className="block break-all">{c.usedByEmail ?? c.usedByUserId ?? '—'}</span>
                              <span className="text-xs text-slate-400">{formatDateTime(c.usedAt)}</span>
                            </>
                          ) : (
                            '—'
                          )}
                        </td>
                        <td className="px-3 py-2 text-slate-600">{formatDate(c.expiresAt)}</td>
                        <td className="px-3 py-2 text-right">
                          <CopyButton code={c.code} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <ul className="mt-2 divide-y divide-slate-100 md:hidden">
              {listed.map((c) => {
                const st = STATUS_BADGE[activationCodeStatus(c)];
                return (
                  <li key={c.id} className="flex items-start justify-between gap-2 px-5 py-3 text-sm">
                    <div className="min-w-0">
                      <code className="break-all">{c.code}</code>
                      <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                        <Badge tone={st.tone}>{st.label}</Badge>
                        <span>{c.planName}</span>
                        <span>HSD {formatDate(c.expiresAt)}</span>
                      </div>
                      {(c.usedAt || c.usedByUserId) && (
                        <p className="mt-1 break-all text-xs text-slate-500">
                          {c.usedByEmail ?? c.usedByUserId} · {formatDateTime(c.usedAt)}
                        </p>
                      )}
                    </div>
                    <CopyButton code={c.code} />
                  </li>
                );
              })}
            </ul>
            <Pagination
              page={page}
              totalPages={codesQuery.data?.pagination.totalPages ?? 1}
              onChange={setPage}
              className="py-4"
            />
          </>
        )}
      </Card>
    </div>
  );
}

function CopyButton({ code }: { code: string }) {
  return (
    <button
      type="button"
      onClick={() => copyWithToast(code, `Đã sao chép ${code}`)}
      className="inline-flex h-11 min-w-[44px] shrink-0 items-center justify-center rounded-lg px-2 text-sm text-brand-600 hover:bg-brand-50"
      aria-label={`Sao chép mã ${code}`}
    >
      Copy
    </button>
  );
}
