import type { Plan, Transaction } from '@/lib/types';

/** 79000 → "79.000đ" (deterministic, does not depend on the runtime's ICU data). */
export function formatVnd(amount: number): string {
  const rounded = Math.round(amount);
  const sign = rounded < 0 ? '-' : '';
  const digits = String(Math.abs(rounded)).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${sign}${digits}đ`;
}

/** ISO date-time → "dd/mm/yyyy" in local time; invalid/empty input → "—". */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const dd = String(d.getDate()).padStart(2, '0');
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getFullYear()}`;
}

/** ISO date-time → "dd/mm/yyyy HH:mm" in local time. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  return `${formatDate(iso)} ${hh}:${mi}`;
}

/** 30 → "1 tháng", 90 → "3 tháng", 365 → "1 năm"; anything else → "<n> ngày". */
export function durationLabel(days: number): string {
  if (days === 365 || days === 366) return '1 năm';
  if (days > 0 && days % 30 === 0 && days <= 330) return `${days / 30} tháng`;
  return `${days} ngày`;
}

/** Monthly equivalent price (rounded to 1.000đ), used for the "≈ …/tháng" hint. */
export function pricePerMonth(plan: Pick<Plan, 'price' | 'durationDays'>): number {
  if (plan.durationDays <= 0) return plan.price;
  const months = plan.durationDays / 30;
  return Math.round(plan.price / months / 1000) * 1000;
}

/** `plan.features` is a JSON column: normally a string array, defensively accept other shapes. */
export function planFeatures(plan: Pick<Plan, 'features'>): string[] {
  const f = plan.features;
  if (!f) return [];
  if (Array.isArray(f)) return f.filter((x): x is string => typeof x === 'string' && x.trim() !== '');
  return Object.entries(f)
    .filter(([, v]) => v === true || typeof v === 'string')
    .map(([k, v]) => (typeof v === 'string' ? v : k));
}

/** Union of all plans' features in first-seen order (Premium column of the comparison table). */
export function premiumFeatures(plans: Pick<Plan, 'features'>[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const p of plans) {
    for (const feat of planFeatures(p)) {
      if (!seen.has(feat)) {
        seen.add(feat);
        out.push(feat);
      }
    }
  }
  return out;
}

/** The plan with the lowest monthly price ("Tiết kiệm nhất"), only when there are ≥ 2 plans. */
export function bestValuePlanId(plans: Plan[]): string | null {
  if (plans.length < 2) return null;
  let best = plans[0];
  for (const p of plans) {
    if (p.durationDays > 0 && p.price / p.durationDays < best.price / best.durationDays) best = p;
  }
  return best.id;
}

const TX_STATUS: Record<string, { label: string; tone: 'green' | 'amber' | 'red' | 'slate' }> = {
  success: { label: 'Thành công', tone: 'green' },
  pending: { label: 'Đang xử lý', tone: 'amber' },
  failed: { label: 'Thất bại', tone: 'red' },
};

export function transactionStatus(status: Transaction['status']) {
  return TX_STATUS[status] ?? { label: status, tone: 'slate' as const };
}

const TX_METHOD: Record<string, string> = {
  vnpay: 'VNPay',
  activation_code: 'Mã kích hoạt',
};

export function transactionMethod(method: string): string {
  return TX_METHOD[method] ?? method;
}
