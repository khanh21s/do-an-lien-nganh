'use client';

import {
  TRANSACTION_METHOD_FILTERS,
  TRANSACTION_STATUS_FILTERS,
  buildTransactionParams,
  validateDateRange,
} from '@/components/admin/console';
import { formatDateTime, formatVnd, transactionMethod, transactionStatus } from '@/components/subscription/format';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input, Select } from '@/components/ui/Input';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { useDebounce } from '@/hooks/useDebounce';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import type { AdminTransaction, AdminTransactionStatusFilter } from '@/lib/types';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';

function methodLabel(method: string | null): string {
  if (!method) return '—';
  return TRANSACTION_METHOD_FILTERS.find((m) => m.value === method)?.label ?? transactionMethod(method);
}

/** T14: all payment transactions — status/method/date filters, search by transaction or user id. */
export default function AdminTransactionsPage() {
  const [status, setStatus] = useState<AdminTransactionStatusFilter>('all');
  const [method, setMethod] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [search, setSearch] = useState('');
  const q = useDebounce(search, 350);
  const [page, setPage] = useState(1);

  const rangeError = validateDateRange(from, to);
  const params = buildTransactionParams({ page, status, method, q, from, to });
  const txQuery = useQuery({
    queryKey: qk.adminTransactions(params),
    queryFn: () => subscriptionApi.adminListTransactions(params),
    placeholderData: keepPreviousData,
    enabled: !rangeError,
  });

  const filtered = status !== 'all' || !!method || !!from || !!to || !!q.trim();
  const rows = txQuery.data?.data ?? [];

  function resetFilters() {
    setStatus('all');
    setMethod('');
    setFrom('');
    setTo('');
    setSearch('');
    setPage(1);
  }

  return (
    <div className="mx-auto w-full max-w-6xl">
      <h1 className="text-2xl font-bold text-slate-900">Giao dịch</h1>

      <Card className="mt-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <Input
            type="search"
            label="Tìm kiếm"
            placeholder="Mã giao dịch hoặc user ID…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            className="min-h-[44px] w-full"
          />
          <Select
            label="Trạng thái"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as AdminTransactionStatusFilter);
              setPage(1);
            }}
            className="min-h-[44px] w-full"
          >
            {TRANSACTION_STATUS_FILTERS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
          <Select
            label="Phương thức"
            value={method}
            onChange={(e) => {
              setMethod(e.target.value);
              setPage(1);
            }}
            className="min-h-[44px] w-full"
          >
            {TRANSACTION_METHOD_FILTERS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </Select>
          <div className="grid min-w-0 grid-cols-2 gap-2">
            <Input
              type="date"
              label="Từ ngày"
              value={from}
              max={to || undefined}
              onChange={(e) => {
                setFrom(e.target.value);
                setPage(1);
              }}
              className="min-h-[44px] w-full min-w-0"
            />
            <Input
              type="date"
              label="Đến ngày"
              value={to}
              min={from || undefined}
              onChange={(e) => {
                setTo(e.target.value);
                setPage(1);
              }}
              className="min-h-[44px] w-full min-w-0"
            />
          </div>
        </div>
        <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
          <span>Ngày tính theo giờ Việt Nam.</span>
          <div className="flex items-center gap-2">
            {txQuery.isFetching && !txQuery.isLoading && <Spinner />}
            {filtered && (
              <Button size="sm" variant="ghost" className="min-h-[44px]" onClick={resetFilters}>
                Xoá bộ lọc
              </Button>
            )}
          </div>
        </div>
        {rangeError && (
          <p role="alert" className="mt-2 text-sm text-red-600">
            {rangeError}
          </p>
        )}
      </Card>

      {rangeError ? null : txQuery.isLoading ? (
        <FullPageSpinner />
      ) : txQuery.isError ? (
        <ErrorState error={txQuery.error} onRetry={txQuery.refetch} className="mt-6" />
      ) : rows.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-slate-500">
          {filtered ? 'Không có giao dịch nào khớp bộ lọc.' : 'Chưa có giao dịch nào.'}
        </Card>
      ) : (
        <Card className="mt-6 overflow-hidden p-0">
          <p className="px-5 pt-3 text-xs text-slate-500">Tổng {txQuery.data?.pagination.total ?? 0} giao dịch</p>
          <div className="hidden lg:block">
            <table className="mt-2 w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-2 font-medium">Thời gian</th>
                  <th className="px-3 py-2 font-medium">Mã giao dịch</th>
                  <th className="px-3 py-2 font-medium">Người dùng</th>
                  <th className="px-3 py-2 font-medium">Gói</th>
                  <th className="px-3 py-2 font-medium">Phương thức</th>
                  <th className="px-3 py-2 text-right font-medium">Số tiền</th>
                  <th className="px-3 py-2 font-medium">Trạng thái</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((t) => (
                  <tr key={t.id}>
                    <td className="whitespace-nowrap px-5 py-2 text-slate-600">{formatDateTime(t.createdAt)}</td>
                    <td className="max-w-[12rem] px-3 py-2">
                      <code className="break-all text-xs">{t.transactionId ?? '—'}</code>
                    </td>
                    <td className="max-w-[12rem] px-3 py-2">
                      <code className="break-all text-xs text-slate-500">{t.userId}</code>
                    </td>
                    <td className="px-3 py-2 text-slate-600">{t.planName ?? t.planId ?? '—'}</td>
                    <td className="px-3 py-2 text-slate-600">{methodLabel(t.method)}</td>
                    <td className="whitespace-nowrap px-3 py-2 text-right font-medium text-slate-900">
                      {formatVnd(t.amount)}
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge tx={t} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="mt-2 divide-y divide-slate-100 lg:hidden">
            {rows.map((t) => (
              <li key={t.id} className="px-5 py-3 text-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">
                      {formatVnd(t.amount)} · {t.planName ?? t.planId ?? '—'}
                    </p>
                    <p className="text-xs text-slate-500">
                      {formatDateTime(t.createdAt)} · {methodLabel(t.method)}
                    </p>
                  </div>
                  <StatusBadge tx={t} />
                </div>
                <p className="mt-1 break-all text-xs text-slate-500">
                  Mã GD: <code>{t.transactionId ?? '—'}</code>
                </p>
                <p className="break-all text-xs text-slate-400">
                  User: <code>{t.userId}</code>
                </p>
                {t.description && <p className="mt-1 break-words text-xs text-slate-500">{t.description}</p>}
              </li>
            ))}
          </ul>
          <Pagination
            page={page}
            totalPages={txQuery.data?.pagination.totalPages ?? 1}
            onChange={setPage}
            className="py-4"
          />
        </Card>
      )}
    </div>
  );
}

function StatusBadge({ tx }: { tx: AdminTransaction }) {
  const s = transactionStatus(tx.status);
  return <Badge tone={s.tone}>{s.label}</Badge>;
}
