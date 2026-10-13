import { api } from '@/lib/api';
import type {
  ActivationCode,
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
};
