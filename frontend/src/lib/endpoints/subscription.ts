import { api } from '@/lib/api';
import type {
  ActivationCode,
  AdminPlan,
  AdminPlanInput,
  AdminSubscriptionStats,
  AdminTransaction,
  ListAdminTransactionsParams,
  UpdateAdminPlanInput,
  UserPremiumStatus,
  CreateActivationCodesInput,
  CreateActivationCodesResult,
  CreatePaymentResult,
  ListActivationCodesParams,
  PageParams,
  PageResponse,
  Plan,
  Transaction,
  UserSubscription,
} from '@/lib/types';

export const subscriptionApi = {
  listPlans: () => api.get<{ plans: Plan[] }>('/subscriptions/plans').then((r) => r.data.plans),

  getUserSubscription: () => api.get<UserSubscription>('/subscriptions/user').then((r) => r.data),

  createPayment: (planId: string) =>
    api.post<CreatePaymentResult>('/subscriptions/create-payment', { planId }).then((r) => r.data),

  activateCode: (code: string) =>
    api.post<UserSubscription>('/subscriptions/activate-code', { code }).then((r) => r.data),

  getTransactions: (params: PageParams = {}) =>
    api.get<PageResponse<Transaction>>('/subscriptions/transactions', { params }).then((r) => r.data),

  /** Admin (NEW): generate 1–500 codes for a plan. */
  createActivationCodes: (data: CreateActivationCodesInput) =>
    api.post<CreateActivationCodesResult>('/subscriptions/admin/activation-codes', data).then((r) => r.data),

  /** Admin (NEW): `used` omitted → all codes. */
  listActivationCodes: (params: ListActivationCodesParams = {}) =>
    api
      .get<PageResponse<ActivationCode>>('/subscriptions/admin/activation-codes', { params })
      .then((r) => r.data),

  // --- T12 additions ---
  // --- end T12 ---

  // --- T14 additions ---
  /** Admin: every plan, inactive ones included. */
  adminListPlans: () => api.get<AdminPlan[]>('/subscriptions/admin/plans').then((r) => r.data),

  /** Admin: 201; duplicate id → 409 "Mã gói đã tồn tại". */
  adminCreatePlan: (data: AdminPlanInput) =>
    api.post<AdminPlan>('/subscriptions/admin/plans', data).then((r) => r.data),

  /** Admin: plans are never hard-deleted; deactivate with `isActive=false`. */
  adminUpdatePlan: (id: string, data: UpdateAdminPlanInput) =>
    api.put<AdminPlan>(`/subscriptions/admin/plans/${encodeURIComponent(id)}`, data).then((r) => r.data),

  adminListTransactions: (params: ListAdminTransactionsParams = {}) =>
    api
      .get<PageResponse<AdminTransaction>>('/subscriptions/admin/transactions', { params })
      .then((r) => r.data),

  adminStats: () => api.get<AdminSubscriptionStats>('/subscriptions/admin/stats').then((r) => r.data),

  /** Admin: premium status of up to 100 users (Premium column of the users table). */
  adminUserStatus: (userIds: readonly string[]) =>
    api
      .post<UserPremiumStatus[]>('/subscriptions/admin/user-status', { userIds: [...userIds] })
      .then((r) => r.data),
  // --- end T14 ---
};
