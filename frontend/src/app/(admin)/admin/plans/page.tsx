'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { PlanFormModal } from '@/components/admin/PlanFormModal';
import { planToggleInput } from '@/components/admin/console';
import { durationLabel, formatDate, formatVnd } from '@/components/subscription/format';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import type { AdminPlan, AdminPlanInput } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useState } from 'react';
import toast from 'react-hot-toast';

/** T14: Premium plans — edit, create, and switch on/off (plans are never hard-deleted). */
export default function AdminPlansPage() {
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [formKey, setFormKey] = useState(0);
  const [editing, setEditing] = useState<AdminPlan | null>(null);
  const [toDisable, setToDisable] = useState<AdminPlan | null>(null);

  const plansQuery = useQuery({ queryKey: qk.adminPlans, queryFn: subscriptionApi.adminListPlans });

  function refresh() {
    queryClient.invalidateQueries({ queryKey: qk.adminPlans });
    queryClient.invalidateQueries({ queryKey: qk.plans });
    queryClient.invalidateQueries({ queryKey: qk.adminSubscriptionStats });
  }

  const saveMutation = useMutation({
    mutationFn: ({ input, isNew }: { input: AdminPlanInput; isNew: boolean }) => {
      if (isNew) return subscriptionApi.adminCreatePlan(input);
      const { id, ...body } = input;
      return subscriptionApi.adminUpdatePlan(id, body);
    },
    onSuccess: (_plan, { isNew }) => {
      toast.success(isNew ? 'Đã tạo gói mới.' : 'Đã lưu gói.');
      setFormOpen(false);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không lưu được gói.')),
  });

  const toggleMutation = useMutation({
    mutationFn: (plan: AdminPlan) => subscriptionApi.adminUpdatePlan(plan.id, planToggleInput(plan)),
    onSuccess: (plan) => {
      toast.success(plan.isActive ? `Đã mở bán ${plan.name}.` : `Đã ngừng bán ${plan.name}.`);
      setToDisable(null);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không cập nhật được trạng thái gói.')),
  });

  function openForm(plan: AdminPlan | null) {
    setEditing(plan);
    setFormKey((k) => k + 1);
    setFormOpen(true);
  }

  function toggle(plan: AdminPlan) {
    if (plan.isActive) setToDisable(plan);
    else toggleMutation.mutate(plan);
  }

  const plans = [...(plansQuery.data ?? [])].sort((a, b) => a.durationDays - b.durationDays);

  return (
    <div className="mx-auto w-full max-w-6xl">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Gói Premium</h1>
          <p className="mt-1 text-sm text-slate-500">Gói đã có người mua không xoá được; hãy tắt để ngừng bán.</p>
        </div>
        <Button onClick={() => openForm(null)} className="min-h-[44px]">
          + Tạo gói
        </Button>
      </div>

      {plansQuery.isLoading ? (
        <FullPageSpinner />
      ) : plansQuery.isError ? (
        <ErrorState error={plansQuery.error} onRetry={plansQuery.refetch} className="mt-6" />
      ) : plans.length === 0 ? (
        <Card className="mt-6 text-center text-sm text-slate-500">Chưa có gói nào.</Card>
      ) : (
        <Card className="mt-6 overflow-hidden p-0">
          <div className="hidden lg:block">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th className="px-5 py-2 font-medium">Gói</th>
                  <th className="px-3 py-2 font-medium">Thời hạn</th>
                  <th className="px-3 py-2 text-right font-medium">Giá</th>
                  <th className="px-3 py-2 font-medium">Quyền lợi</th>
                  <th className="px-3 py-2 text-right font-medium">Đang dùng</th>
                  <th className="px-3 py-2 font-medium">Đang bán</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {plans.map((p) => (
                  <tr key={p.id} className={clsx(!p.isActive && 'bg-slate-50/60')}>
                    <td className="px-5 py-3">
                      <span className="block font-medium text-slate-900">{p.name}</span>
                      <code className="text-xs text-slate-500">{p.id}</code>
                    </td>
                    <td className="px-3 py-3 text-slate-600">{durationLabel(p.durationDays)}</td>
                    <td className="px-3 py-3 text-right font-medium text-slate-900">{formatVnd(p.price)}</td>
                    <td className="max-w-xs px-3 py-3 text-slate-600">
                      <FeatureList features={p.features} />
                    </td>
                    <td className="px-3 py-3 text-right text-slate-600">{p.activeSubscribers}</td>
                    <td className="px-3 py-3">
                      <ActiveSwitch plan={p} busy={toggleMutation.isPending} onToggle={() => toggle(p)} />
                    </td>
                    <td className="px-3 py-3 text-right">
                      <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => openForm(p)}>
                        Sửa
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <ul className="divide-y divide-slate-100 lg:hidden">
            {plans.map((p) => (
              <li key={p.id} className="px-5 py-4 text-sm">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-medium text-slate-900">{p.name}</p>
                    <code className="break-all text-xs text-slate-500">{p.id}</code>
                    <p className="mt-1 text-slate-700">
                      <strong>{formatVnd(p.price)}</strong> · {durationLabel(p.durationDays)} · {p.activeSubscribers} đang dùng
                    </p>
                  </div>
                  <ActiveSwitch plan={p} busy={toggleMutation.isPending} onToggle={() => toggle(p)} />
                </div>
                <div className="mt-2 text-slate-600">
                  <FeatureList features={p.features} />
                </div>
                <div className="mt-2 flex items-center justify-between gap-2">
                  <span className="text-xs text-slate-400">Cập nhật {formatDate(p.updatedAt ?? p.createdAt)}</span>
                  <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => openForm(p)}>
                    Sửa
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      )}

      <PlanFormModal
        key={formKey}
        open={formOpen}
        plan={editing}
        loading={saveMutation.isPending}
        onClose={() => setFormOpen(false)}
        onSubmit={(input) => saveMutation.mutate({ input, isNew: !editing })}
      />

      <ConfirmModal
        open={!!toDisable}
        title="Ngừng bán gói?"
        message={
          <>
            Gói <strong>{toDisable?.name}</strong> sẽ không còn hiện ở trang Bảng giá và không mua/kích hoạt được nữa.
            {toDisable && toDisable.activeSubscribers > 0 && (
              <> {toDisable.activeSubscribers} người đang dùng vẫn giữ Premium tới hết hạn.</>
            )}
          </>
        }
        confirmLabel="Ngừng bán"
        loading={toggleMutation.isPending}
        onConfirm={() => toDisable && toggleMutation.mutate(toDisable)}
        onClose={() => setToDisable(null)}
      />
    </div>
  );
}

function FeatureList({ features }: { features: string[] | null }) {
  if (!features || features.length === 0) return <span className="text-slate-400">—</span>;
  return (
    <ul className="list-inside list-disc space-y-0.5 text-xs">
      {features.map((f, i) => (
        <li key={`${f}-${i}`} className="break-words">
          {f}
        </li>
      ))}
    </ul>
  );
}

function ActiveSwitch({ plan, busy, onToggle }: { plan: AdminPlan; busy: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={plan.isActive}
      aria-label={`${plan.isActive ? 'Ngừng bán' : 'Mở bán'} ${plan.name}`}
      disabled={busy}
      onClick={onToggle}
      className="flex min-h-[44px] shrink-0 items-center gap-2 text-sm disabled:opacity-60"
    >
      <span
        className={clsx(
          'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
          plan.isActive ? 'bg-green-500' : 'bg-slate-300'
        )}
      >
        <span
          className={clsx(
            'inline-block h-5 w-5 rounded-full bg-white shadow transition-transform',
            plan.isActive ? 'translate-x-5' : 'translate-x-0.5'
          )}
        />
      </span>
      <Badge tone={plan.isActive ? 'green' : 'slate'}>{plan.isActive ? 'Đang bán' : 'Đã tắt'}</Badge>
    </button>
  );
}
