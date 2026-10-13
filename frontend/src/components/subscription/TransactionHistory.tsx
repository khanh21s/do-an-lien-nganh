'use client';

import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Pagination } from '@/components/ui/Pagination';
import { Spinner } from '@/components/ui/Spinner';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { formatDateTime, formatVnd, transactionMethod, transactionStatus } from './format';

const PAGE_SIZE = 10;

export function TransactionHistory() {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.transactions(page),
    queryFn: () => subscriptionApi.getTransactions({ page, limit: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  return (
    <Card className="mt-8">
      <h2 className="font-semibold text-slate-900">Lịch sử giao dịch</h2>
      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-3 border-0" />
      ) : !data || data.data.length === 0 ? (
        <p className="py-3 text-sm text-slate-500">Chưa có giao dịch nào.</p>
      ) : (
        <>
          <ul className="mt-3 divide-y divide-slate-100">
            {data.data.map((tx) => {
              const st = transactionStatus(tx.status);
              return (
                <li key={tx.id} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-2.5 text-sm">
                  <span className="text-slate-600">{formatDateTime(tx.createdAt)}</span>
                  <span className="text-slate-600">{transactionMethod(tx.method)}</span>
                  <span className="font-medium text-slate-900">{formatVnd(tx.amount)}</span>
                  <Badge tone={st.tone}>{st.label}</Badge>
                </li>
              );
            })}
          </ul>
          <Pagination page={page} totalPages={data.pagination.totalPages} onChange={setPage} className="mt-3" />
        </>
      )}
    </Card>
  );
}
