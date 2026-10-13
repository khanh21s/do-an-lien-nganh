// T14: pure helpers of the admin console (unit tested in console.test.ts).

import type {
  AdminPlan,
  AdminPlanInput,
  AdminTransactionStatusFilter,
  AdminUser,
  AdminUserStatusFilter,
  CefrLevel,
  ListAdminTransactionsParams,
  ListAdminUsersParams,
  RevenueDay,
  UpdateDeckInput,
  UserPremiumStatus,
  VocabDeck,
} from '@/lib/types';

// ---------------------------------------------------------------------------------------------
// Filters → query params
// ---------------------------------------------------------------------------------------------

export const ADMIN_PAGE_SIZE = 20;

export const USER_STATUS_FILTERS: { value: AdminUserStatusFilter; label: string }[] = [
  { value: 'all', label: 'Tất cả' },
  { value: 'active', label: 'Đang hoạt động' },
  { value: 'locked', label: 'Đã khoá' },
];

export const TRANSACTION_STATUS_FILTERS: { value: AdminTransactionStatusFilter; label: string }[] = [
  { value: 'all', label: 'Tất cả trạng thái' },
  { value: 'success', label: 'Thành công' },
  { value: 'pending', label: 'Đang xử lý' },
  { value: 'failed', label: 'Thất bại' },
];

/** '' = every method. Values are the `payment_transaction.method` column. */
export const TRANSACTION_METHOD_FILTERS: { value: string; label: string }[] = [
  { value: '', label: 'Mọi phương thức' },
  { value: 'vnpay', label: 'VNPay' },
  { value: 'momo', label: 'MoMo' },
  { value: 'gift_code', label: 'Quà tặng' },
  { value: 'activation_code', label: 'Mã kích hoạt' },
];

export function buildUserListParams(f: {
  page: number;
  q: string;
  status: AdminUserStatusFilter;
  limit?: number;
}): ListAdminUsersParams {
  const q = f.q.trim();
  return {
    page: Math.max(1, f.page),
    limit: f.limit ?? ADMIN_PAGE_SIZE,
    status: f.status,
    ...(q ? { q } : {}),
  };
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Vietnamese error when the date range is invalid, otherwise null (empty bounds are fine). */
export function validateDateRange(from: string, to: string): string | null {
  if (from && !ISO_DATE.test(from)) return 'Ngày bắt đầu không hợp lệ.';
  if (to && !ISO_DATE.test(to)) return 'Ngày kết thúc không hợp lệ.';
  if (from && to && from > to) return 'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.';
  return null;
}

export function buildTransactionParams(f: {
  page: number;
  status: AdminTransactionStatusFilter;
  method: string;
  q: string;
  from: string;
  to: string;
  limit?: number;
}): ListAdminTransactionsParams {
  const q = f.q.trim();
  const params: ListAdminTransactionsParams = {
    page: Math.max(1, f.page),
    limit: f.limit ?? ADMIN_PAGE_SIZE,
    status: f.status,
  };
  if (f.method) params.method = f.method;
  if (q) params.q = q;
  if (f.from && ISO_DATE.test(f.from)) params.from = f.from;
  if (f.to && ISO_DATE.test(f.to)) params.to = f.to;
  return params;
}

// ---------------------------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------------------------

/** Lock button state: the BE refuses to lock admins and the current account (400). */
export function lockAction(
  user: Pick<AdminUser, 'id' | 'isActive' | 'isAdmin'>,
  selfId: string | null | undefined
): { label: string; nextActive: boolean; disabled: boolean; reason: string | null } {
  if (!user.isActive) return { label: 'Mở khoá', nextActive: true, disabled: false, reason: null };
  if (user.isAdmin) {
    return { label: 'Khoá', nextActive: false, disabled: true, reason: 'Không thể khoá tài khoản quản trị' };
  }
  if (selfId && user.id === selfId) {
    return { label: 'Khoá', nextActive: false, disabled: true, reason: 'Không thể khoá chính bạn' };
  }
  return { label: 'Khoá', nextActive: false, disabled: false, reason: null };
}

export function premiumByUser(list: UserPremiumStatus[] | undefined): Map<string, UserPremiumStatus> {
  return new Map((list ?? []).map((s) => [s.userId, s]));
}

const PROVIDERS: Record<string, string> = { local: 'Email', google: 'Google', apple: 'Apple' };

export function providerLabel(provider: string | null | undefined): string {
  const p = (provider || 'local').toLowerCase();
  return PROVIDERS[p] ?? provider ?? 'Email';
}

// ---------------------------------------------------------------------------------------------
// Revenue chart
// ---------------------------------------------------------------------------------------------

/** Smallest "nice" number (1, 2, 2.5, 5 × 10^k) ≥ value; 0 for value ≤ 0. */
export function niceCeil(value: number): number {
  if (!(value > 0)) return 0;
  const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
  for (const step of [1, 2, 2.5, 5, 10]) {
    const candidate = step * magnitude;
    if (candidate >= value - 1e-9) return candidate;
  }
  return 10 * magnitude;
}

/** Axis label: 1_500_000 → "1,5tr", 250_000 → "250k", 0 → "0". */
export function formatCompactVnd(amount: number): string {
  const abs = Math.abs(amount);
  const trim = (n: number) => String(Math.round(n * 10) / 10).replace('.', ',');
  if (abs >= 1_000_000_000) return `${trim(amount / 1_000_000_000)}tỷ`;
  if (abs >= 1_000_000) return `${trim(amount / 1_000_000)}tr`;
  if (abs >= 1_000) return `${trim(amount / 1_000)}k`;
  return String(Math.round(amount));
}

export interface ChartGeometry {
  width: number;
  height: number;
  padLeft: number;
  padRight: number;
  padTop: number;
  padBottom: number;
}

export interface RevenueBar {
  date: string;
  amount: number;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface RevenueChart {
  bars: RevenueBar[];
  /** Grid lines from 0 to `max` (inclusive). */
  ticks: { value: number; y: number }[];
  max: number;
  total: number;
}

/**
 * Scales daily revenue to SVG bars. The y-axis tops out at a nice round number ≥ the largest
 * day so bars never overflow; an all-zero series keeps a 0 baseline with flat bars.
 * Negative/invalid amounts are treated as 0.
 */
export function buildRevenueChart(days: readonly RevenueDay[], g: ChartGeometry, tickCount = 4): RevenueChart {
  const innerW = g.width - g.padLeft - g.padRight;
  const innerH = g.height - g.padTop - g.padBottom;
  const baseY = g.padTop + innerH;
  const amounts = days.map((d) => (Number.isFinite(d.amount) && d.amount > 0 ? d.amount : 0));
  const max = niceCeil(Math.max(0, ...amounts));
  const slot = days.length ? innerW / days.length : 0;
  const barW = Math.max(1, slot * 0.7);

  const bars = days.map((d, i) => {
    const h = max > 0 ? (amounts[i] / max) * innerH : 0;
    return {
      date: d.date,
      amount: amounts[i],
      x: g.padLeft + i * slot + (slot - barW) / 2,
      y: baseY - h,
      width: barW,
      height: h,
    };
  });

  const ticks =
    max > 0
      ? Array.from({ length: tickCount + 1 }, (_, i) => {
          const value = (max * i) / tickCount;
          return { value, y: baseY - (value / max) * innerH };
        })
      : [{ value: 0, y: baseY }];

  return { bars, ticks, max, total: amounts.reduce((a, b) => a + b, 0) };
}

/** 'YYYY-MM-DD' → 'dd/mm' (no Date parsing, so no timezone shift). */
export function shortDate(isoDate: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(isoDate);
  return m ? `${m[3]}/${m[2]}` : isoDate;
}

// ---------------------------------------------------------------------------------------------
// Plans
// ---------------------------------------------------------------------------------------------

export interface PlanDraft {
  id: string;
  name: string;
  durationDays: string;
  price: string;
  /** One feature per line. */
  features: string;
  isActive: boolean;
}

export type PlanErrors = Partial<Record<keyof PlanDraft, string>>;

export const EMPTY_PLAN_DRAFT: PlanDraft = {
  id: '',
  name: '',
  durationDays: '30',
  price: '',
  features: '',
  isActive: true,
};

const PLAN_ID = /^[a-z0-9-]{3,50}$/;

export function parseFeatures(text: string): string[] {
  return text
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l !== '');
}

export function planToDraft(plan: AdminPlan): PlanDraft {
  return {
    id: plan.id,
    name: plan.name,
    durationDays: String(plan.durationDays),
    price: String(plan.price),
    features: (plan.features ?? []).join('\n'),
    isActive: plan.isActive,
  };
}

/** Same limits as subscription-service: id `^[a-z0-9-]{3,50}$`, name 1–255, price ≥ 1000, 1–3650 days. */
export function validatePlanDraft(d: PlanDraft, opts: { isNew: boolean }): PlanErrors {
  const errors: PlanErrors = {};
  if (opts.isNew && !PLAN_ID.test(d.id.trim())) {
    errors.id = 'Mã gói 3–50 ký tự: chữ thường, số hoặc dấu gạch ngang.';
  }
  const name = d.name.trim();
  if (!name) errors.name = 'Vui lòng nhập tên gói.';
  else if (name.length > 255) errors.name = 'Tên gói tối đa 255 ký tự.';
  const days = Number(d.durationDays);
  if (!Number.isInteger(days) || days < 1 || days > 3650) errors.durationDays = 'Thời hạn từ 1 đến 3650 ngày.';
  const price = Number(d.price);
  if (d.price.trim() === '' || !Number.isInteger(price) || price < 1000) errors.price = 'Giá là số nguyên ≥ 1.000đ.';
  return errors;
}

export function planDraftToInput(d: PlanDraft): AdminPlanInput {
  return {
    id: d.id.trim(),
    name: d.name.trim(),
    durationDays: Number(d.durationDays),
    price: Number(d.price),
    features: parseFeatures(d.features),
    isActive: d.isActive,
  };
}

/** Full PUT body for flipping `isActive` without touching the other fields. */
export function planToggleInput(plan: AdminPlan): Omit<AdminPlanInput, 'id'> {
  return {
    name: plan.name,
    durationDays: plan.durationDays,
    price: plan.price,
    features: plan.features ?? [],
    isActive: !plan.isActive,
  };
}

// ---------------------------------------------------------------------------------------------
// Decks
// ---------------------------------------------------------------------------------------------

export interface DeckDraft {
  name: string;
  description: string;
  /** '' = no level. */
  level: CefrLevel | '';
  isProOnly: boolean;
}

export const EMPTY_DECK_DRAFT: DeckDraft = { name: '', description: '', level: 'A1', isProOnly: false };

export function deckToDraft(deck: Pick<VocabDeck, 'name' | 'description' | 'level' | 'isProOnly'>): DeckDraft {
  return {
    name: deck.name,
    description: deck.description ?? '',
    level: deck.level ?? '',
    isProOnly: deck.isProOnly,
  };
}

export function validateDeckDraft(d: DeckDraft): string | null {
  const name = d.name.trim();
  if (!name) return 'Vui lòng nhập tên bộ từ vựng.';
  if (name.length > 255) return 'Tên bộ từ vựng tối đa 255 ký tự.';
  return null;
}

/** Body of `PUT /vocabs/decks/{id}`: blank description / no level are sent as null. */
export function deckDraftToUpdate(d: DeckDraft): UpdateDeckInput {
  const description = d.description.trim();
  return {
    name: d.name.trim(),
    description: description === '' ? null : description,
    level: d.level === '' ? null : d.level,
    isProOnly: d.isProOnly,
  };
}
