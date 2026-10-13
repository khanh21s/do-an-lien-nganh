'use client';

import { formatVnd } from '@/components/subscription/format';
import { Button } from '@/components/ui/Button';
import { Input, Textarea } from '@/components/ui/Input';
import { Modal } from '@/components/ui/Modal';
import type { AdminPlan, AdminPlanInput } from '@/lib/types';
import { FormEvent, useState } from 'react';
import {
  EMPTY_PLAN_DRAFT,
  planDraftToInput,
  planToDraft,
  validatePlanDraft,
  type PlanDraft,
  type PlanErrors,
} from './console';

/**
 * T14: create (plan = null) or edit a Premium plan. Mount with a fresh `key` per open so the
 * draft is re-initialised from `plan`. The plan id cannot be changed after creation.
 */
export function PlanFormModal({
  open,
  plan,
  loading,
  onClose,
  onSubmit,
}: {
  open: boolean;
  plan: AdminPlan | null;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (input: AdminPlanInput) => void;
}) {
  const isNew = !plan;
  const [draft, setDraft] = useState<PlanDraft>(() => (plan ? planToDraft(plan) : EMPTY_PLAN_DRAFT));
  const [errors, setErrors] = useState<PlanErrors>({});

  function set<K extends keyof PlanDraft>(key: K, value: PlanDraft[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const errs = validatePlanDraft(draft, { isNew });
    setErrors(errs);
    if (Object.values(errs).some(Boolean)) return;
    onSubmit(planDraftToInput(draft));
  }

  const price = Number(draft.price);

  return (
    <Modal open={open} onClose={loading ? () => undefined : onClose} title={isNew ? 'Tạo gói mới' : `Sửa gói ${plan.id}`}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3" noValidate>
        {isNew && (
          <Input
            label="Mã gói"
            required
            placeholder="vd: premium-2-years"
            value={draft.id}
            onChange={(e) => set('id', e.target.value.toLowerCase())}
            error={errors.id}
            hint="3–50 ký tự a-z, 0-9, dấu gạch ngang. Không đổi được sau khi tạo."
          />
        )}
        <Input label="Tên gói" required value={draft.name} onChange={(e) => set('name', e.target.value)} error={errors.name} />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input
            label="Thời hạn (ngày)"
            type="number"
            inputMode="numeric"
            min={1}
            max={3650}
            required
            value={draft.durationDays}
            onChange={(e) => set('durationDays', e.target.value)}
            error={errors.durationDays}
          />
          <Input
            label="Giá (VND)"
            type="number"
            inputMode="numeric"
            min={1000}
            step={1000}
            required
            value={draft.price}
            onChange={(e) => set('price', e.target.value)}
            error={errors.price}
            hint={Number.isFinite(price) && price > 0 ? formatVnd(price) : undefined}
          />
        </div>
        <Textarea
          label="Quyền lợi"
          rows={4}
          value={draft.features}
          onChange={(e) => set('features', e.target.value)}
          hint="Mỗi dòng một quyền lợi (hiện ở trang Bảng giá)."
        />
        <label className="flex min-h-[44px] items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="h-4 w-4"
            checked={draft.isActive}
            onChange={(e) => set('isActive', e.target.checked)}
          />
          Đang bán (hiện cho người dùng)
        </label>
        <div className="mt-2 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="secondary" onClick={onClose} disabled={loading} className="min-h-[44px]">
            Huỷ
          </Button>
          <Button type="submit" loading={loading} className="min-h-[44px]">
            {isNew ? 'Tạo gói' : 'Lưu thay đổi'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
