'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import {
  USER_STATUS_FILTERS,
  buildUserListParams,
  lockAction,
  premiumByUser,
  providerLabel,
} from '@/components/admin/console';
import { FilterChip } from '@/components/lessons/FilterChip';
import { formatDate, formatDateTime } from '@/components/subscription/format';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { Pagination } from '@/components/ui/Pagination';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { useDebounce } from '@/hooks/useDebounce';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import type { AdminUser, AdminUserStatusFilter, UserPremiumStatus } from '@/lib/types';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import toast from 'react-hot-toast';

/** T14: users — search by email, status filter, Premium column, lock/unlock with confirmation. */
export default function AdminUsersPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const q = useDebounce(search, 350);
  const [status, setStatus] = useState<AdminUserStatusFilter>('all');
  const [page, setPage] = useState(1);
  const [pending, setPending] = useState<AdminUser | null>(null);

  const params = buildUserListParams({ page, q, status });
  const profile = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const usersQuery = useQuery({
    queryKey: qk.adminUsers(params),
    queryFn: () => authApi.adminListUsers(params),
    placeholderData: keepPreviousData,
  });

  const users = useMemo(() => usersQuery.data?.data ?? [], [usersQuery.data]);
  const userIds = useMemo(() => users.map((u) => u.id), [users]);
  const premiumQuery = useQuery({
    queryKey: qk.adminUserStatus(userIds),
    queryFn: () => subscriptionApi.adminUserStatus(userIds),
    enabled: userIds.length > 0,
  });
  const premium = premiumByUser(premiumQuery.data);
  const premiumState = premiumQuery.isError ? 'error' : premiumQuery.isLoading ? 'loading' : 'ready';

  const lockMutation = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) => authApi.adminUpdateUser(id, { isActive }),
    onSuccess: (user) => {
      toast.success(user.isActive ? `Đã mở khoá ${user.email}.` : `Đã khoá ${user.email}.`);
      setPending(null);
      queryClient.invalidateQueries({ queryKey: qk.adminUsersAll });
      queryClient.invalidateQueries({ queryKey: qk.adminAuthStats });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không cập nhật được trạng thái tài khoản.')),
  });

  const selfId = profile.data?.userId;
  const pendingAction = pending ? lockAction(pending, selfId) : null;

  return (
    <div className="mx-auto w-full max-w-6xl">
      <h1 className="text-2xl font-bold text-slate-900">Người dùng</h1>

      <div className="mt-4 flex flex-col gap-3 md:flex-row md:items-center">
        <div className="min-w-0 flex-1">
          <Input
            type="search"
            aria-label="Tìm theo email"
            placeholder="Tìm theo email…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(1);
            }}
            className="min-h-[44px] w-full"
          />
        </div>
        <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Lọc trạng thái">
          {USER_STATUS_FILTERS.map((f) => (
            <FilterChip
              key={f.value}
              active={status === f.value}
              onClick={() => {
                setStatus(f.value);
                setPage(1);
              }}
            >
              {f.label}
            </FilterChip>
          ))}
          {usersQuery.isFetching && !usersQuery.isLoading && <Spinner />}
        </div>
      </div>

      {usersQuery.isLoading ? (
        <FullPageSpinner />
      ) : usersQuery.isError ? (
        <ErrorState error={usersQuery.error} onRetry={usersQuery.refetch} className="mt-6" />
      ) : users.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-slate-500">
          {params.q ? `Không có người dùng nào khớp “${params.q}”.` : 'Không có người dùng nào.'}
        </Card>
      ) : (
        <Card className="mt-6 overflow-hidden p-0">
          <p className="px-5 pt-3 text-xs text-slate-500">Tổng {usersQuery.data?.pagination.total ?? 0} người dùng</p>
          {/* Table from lg up, stacked cards below so 360px never scrolls horizontally. */}
          <div className="hidden lg:block">
            <table className="mt-2 w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-2 font-medium">Email</th>
                  <th className="px-3 py-2 font-medium">Đăng nhập bằng</th>
                  <th className="px-3 py-2 font-medium">Premium</th>
                  <th className="px-3 py-2 font-medium">Trạng thái</th>
                  <th className="px-3 py-2 font-medium">Ngày tạo</th>
                  <th className="px-3 py-2 font-medium">Đăng nhập gần nhất</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => (
                  <tr key={u.id}>
                    <td className="max-w-[16rem] px-5 py-2">
                      <span className="block break-all font-medium text-slate-900">{u.email}</span>
                      <span className="flex gap-1">
                        {u.isAdmin && <Badge tone="navy">Admin</Badge>}
                        {u.cefrLevel && <Badge tone="brand">{u.cefrLevel}</Badge>}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-slate-600">{providerLabel(u.authProvider)}</td>
                    <td className="px-3 py-2">
                      <PremiumCell status={premium.get(u.id)} state={premiumState} />
                    </td>
                    <td className="px-3 py-2">
                      <StatusBadge active={u.isActive} />
                    </td>
                    <td className="px-3 py-2 text-slate-600">{formatDate(u.createdAt)}</td>
                    <td className="px-3 py-2 text-slate-600">{formatDateTime(u.lastLoginAt)}</td>
                    <td className="px-3 py-2 text-right">
                      <LockButton user={u} selfId={selfId} onClick={() => setPending(u)} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="mt-2 divide-y divide-slate-100 lg:hidden">
            {users.map((u) => (
              <li key={u.id} className="flex items-start justify-between gap-3 px-5 py-3 text-sm">
                <div className="min-w-0">
                  <p className="break-all font-medium text-slate-900">{u.email}</p>
                  <div className="mt-1 flex flex-wrap items-center gap-1.5">
                    <StatusBadge active={u.isActive} />
                    <PremiumCell status={premium.get(u.id)} state={premiumState} />
                    {u.isAdmin && <Badge tone="navy">Admin</Badge>}
                    {u.cefrLevel && <Badge tone="brand">{u.cefrLevel}</Badge>}
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    {providerLabel(u.authProvider)} · Tạo {formatDate(u.createdAt)} · Đăng nhập{' '}
                    {formatDateTime(u.lastLoginAt)}
                  </p>
                </div>
                <LockButton user={u} selfId={selfId} onClick={() => setPending(u)} />
              </li>
            ))}
          </ul>
          <Pagination
            page={page}
            totalPages={usersQuery.data?.pagination.totalPages ?? 1}
            onChange={setPage}
            className="py-4"
          />
        </Card>
      )}

      <ConfirmModal
        open={!!pending}
        title={pendingAction?.nextActive ? 'Mở khoá tài khoản?' : 'Khoá tài khoản?'}
        message={
          pendingAction?.nextActive ? (
            <>
              <strong className="break-all">{pending?.email}</strong> sẽ đăng nhập lại được bình thường.
            </>
          ) : (
            <>
              <strong className="break-all">{pending?.email}</strong> sẽ không thể đăng nhập hoặc làm mới phiên. Phiên
              đang mở sẽ hết hạn trong tối đa 1 giờ.
            </>
          )
        }
        confirmLabel={pendingAction?.label ?? 'Xác nhận'}
        confirmVariant={pendingAction?.nextActive ? 'primary' : 'danger'}
        loading={lockMutation.isPending}
        onConfirm={() => pending && pendingAction && lockMutation.mutate({ id: pending.id, isActive: pendingAction.nextActive })}
        onClose={() => setPending(null)}
      />
    </div>
  );
}

function StatusBadge({ active }: { active: boolean }) {
  return active ? <Badge tone="green">Hoạt động</Badge> : <Badge tone="red">Đã khoá</Badge>;
}

function PremiumCell({ status, state }: { status?: UserPremiumStatus; state: 'loading' | 'error' | 'ready' }) {
  if (state === 'loading') return <span className="text-xs text-slate-400">…</span>;
  if (state === 'error') return <span className="text-xs text-slate-400" title="Không tải được trạng thái Premium">?</span>;
  if (!status?.premium) return <Badge tone="slate">Free</Badge>;
  return (
    <span title={status.endAt ? `Đến ${formatDate(status.endAt)}` : undefined}>
      <Badge tone="purple">PRO{status.endAt ? ` · ${formatDate(status.endAt)}` : ''}</Badge>
    </span>
  );
}

function LockButton({ user, selfId, onClick }: { user: AdminUser; selfId?: string; onClick: () => void }) {
  const action = lockAction(user, selfId);
  return (
    <Button
      size="sm"
      variant={action.nextActive ? 'secondary' : 'danger'}
      className="min-h-[44px] shrink-0"
      disabled={action.disabled}
      title={action.reason ?? undefined}
      aria-label={`${action.label} ${user.email}${action.reason ? ` (${action.reason})` : ''}`}
      onClick={onClick}
    >
      {action.label}
    </Button>
  );
}
