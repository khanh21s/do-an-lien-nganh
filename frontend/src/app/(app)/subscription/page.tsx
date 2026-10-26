'use client';

import { ActivateCodeForm } from '@/components/subscription/ActivateCodeForm';
import { PlanCard } from '@/components/subscription/PlanCard';
import { TransactionHistory } from '@/components/subscription/TransactionHistory';
import { bestValuePlanId, formatDate } from '@/components/subscription/format';
import { Badge } from '@/components/ui/Badge';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage, apiErrorStatus } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useRef } from 'react';
import toast from 'react-hot-toast';

const ALREADY_PREMIUM_MSG =
  'Bạn đang có gói Premium còn hạn nên không thể thanh toán thêm. Hãy dùng mã kích hoạt bên dưới để cộng dồn thời hạn.';

/** Maps createPayment errors to a clear Vietnamese message (409 = already premium). */
function paymentErrorMessage(err: unknown): string {
  const status = apiErrorStatus(err);
  if (status === 409) return ALREADY_PREMIUM_MSG;
  if (status === 503) return 'Cổng thanh toán chưa được cấu hình. Bạn có thể dùng mã kích hoạt.';
  return apiErrorMessage(err, 'Không thể khởi tạo thanh toán, vui lòng thử lại.');
}

export default function SubscriptionPage() {
  const activateRef = useRef<HTMLDivElement>(null);

  const subQuery = useQuery({ queryKey: qk.userSubscription, queryFn: subscriptionApi.getUserSubscription });
  const plansQuery = useQuery({ queryKey: qk.plans, queryFn: subscriptionApi.listPlans });

  const payMutation = useMutation({
    mutationFn: (planId: string) => subscriptionApi.createPayment(planId),
    onSuccess: (res) => {
      window.location.href = res.paymentUrl;
    },
    onError: (err) => {
      toast.error(paymentErrorMessage(err), { duration: 6000 });
      if (apiErrorStatus(err) === 409) {
        void subQuery.refetch();
        activateRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    },
  });

  if (subQuery.isLoading || plansQuery.isLoading) return <FullPageSpinner />;
  if (subQuery.isError) return <ErrorState error={subQuery.error} onRetry={subQuery.refetch} />;

  const userSub = subQuery.data;
  const sub = userSub?.subscription ?? null;
  const isPremium = !!userSub?.isPremium && !!sub;
  const plans = [...(plansQuery.data ?? [])].sort((a, b) => a.durationDays - b.durationDays);
  const bestId = bestValuePlanId(plans);

  return (
    <div className="mx-auto w-full max-w-5xl">
      <h1 className="text-2xl font-bold text-slate-900">Gói Premium</h1>

      {isPremium && sub ? (
        <Card className="mt-4 border-0 bg-pro-gradient text-white">
          <Badge tone="green" className="bg-white/20 text-white">
            Đang hoạt động
          </Badge>
          <p className="mt-2 text-lg font-semibold">👑 {sub.planName}</p>
          <p className="text-sm text-white/90">
            Hiệu lực đến <strong>{formatDate(sub.endAt)}</strong> · còn {sub.daysRemaining} ngày
          </p>
          <p className="mt-1 text-xs text-white/80">Thanh toán 1 lần, không tự gia hạn.</p>
        </Card>
      ) : (
        <Card className="mt-4">
          <Badge tone="slate">Gói Free</Badge>
          <p className="mt-2 text-sm text-slate-600">
            Nâng cấp Premium để mở khoá toàn bộ bộ từ vựng Pro. Thanh toán 1 lần, không tự gia hạn.
          </p>
          {sub && sub.status !== 'active' && (
            <p className="mt-1 text-xs text-slate-500">Gói gần nhất ({sub.planName}) đã hết hạn ngày {formatDate(sub.endAt)}.</p>
          )}
        </Card>
      )}

      <section className="mt-8">
        <h2 className="text-lg font-semibold text-slate-900">Mua gói Premium</h2>
        {isPremium && (
          <p className="mt-1 rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800" role="note">
            {ALREADY_PREMIUM_MSG}
          </p>
        )}
        {plansQuery.isError ? (
          <ErrorState error={plansQuery.error} onRetry={plansQuery.refetch} className="mt-4" />
        ) : (
          <div className="mt-6 grid grid-cols-1 gap-6 md:grid-cols-3">
            {plans.map((plan) => (
              <PlanCard
                key={plan.id}
                plan={plan}
                highlight={plan.id === bestId}
                actionLabel={isPremium ? 'Đang là Premium' : 'Thanh toán ngay'}
                disabled={isPremium || (payMutation.isPending && payMutation.variables !== plan.id)}
                loading={payMutation.isPending && payMutation.variables === plan.id}
                onAction={() => payMutation.mutate(plan.id)}
              />
            ))}
          </div>
        )}
      </section>

      <div ref={activateRef}>
        <Card className="mt-8">
          <h2 className="font-semibold text-slate-900">Kích hoạt bằng mã</h2>
          <p className="mb-3 mt-1 text-sm text-slate-500">Nhập mã kích hoạt Premium bạn nhận được.</p>
          <ActivateCodeForm />
        </Card>
      </div>

      <TransactionHistory />
    </div>
  );
}
