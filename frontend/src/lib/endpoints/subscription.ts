import { api } from '@/lib/api';
import type { CreatePaymentResult, PageResponse, Plan, Transaction, UserSubscription } from '@/lib/types';

export const subscriptionApi = {
  listPlans: () => api.get<{ plans: Plan[] }>('/subscriptions/plans').then((r) => r.data.plans),

  getUserSubscription: () => api.get<UserSubscription>('/subscriptions/user').then((r) => r.data),

  createPayment: (planId: string) =>
    api.post<CreatePaymentResult>('/subscriptions/create-payment', { planId }).then((r) => r.data),

  activateCode: (code: string) =>
    api.post<UserSubscription>('/subscriptions/activate-code', { code }).then((r) => r.data),

  getTransactions: (params: { page?: number; limit?: number } = {}) =>
    api.get<PageResponse<Transaction>>('/subscriptions/transactions', { params }).then((r) => r.data),
};
