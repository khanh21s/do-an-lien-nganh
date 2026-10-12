'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

function formatVnd(amount: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

export default function SubscriptionPage() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');

  const { data: userSub, isLoading: subLoading } = useQuery({
    queryKey: ['user-subscription'],
    queryFn: subscriptionApi.getUserSubscription,
  });
  const { data: plans, isLoading: plansLoading } = useQuery({ queryKey: ['plans'], queryFn: subscriptionApi.listPlans });
  const { data: transactions } = useQuery({
    queryKey: ['transactions'],
    queryFn: () => subscriptionApi.getTransactions({ limit: 20 }),
  });

  const payMutation = useMutation({
    mutationFn: (planId: string) => subscriptionApi.createPayment(planId),
    onSuccess: (res) => {
      window.location.href = res.paymentUrl;
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể khởi tạo thanh toán.')),
  });

  const activateMutation = useMutation({
    mutationFn: (code: string) => subscriptionApi.activateCode(code),
    onSuccess: () => {
      toast.success('Kích hoạt gói Premium thành công!');
      setCode('');
      queryClient.invalidateQueries({ queryKey: ['user-subscription'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Mã kích hoạt không hợp lệ.')),
  });

  function handleActivate(e: FormEvent) {
    e.preventDefault();
    if (!code.trim()) return;
    activateMutation.mutate(code.trim());
  }

  if (subLoading || plansLoading) return <FullPageSpinner />;

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Gói Premium</h1>

      {userSub?.isPremium && userSub.subscription ? (
        <Card className="mt-4 border-0 bg-pro-gradient text-white">
          <Badge tone="green" className="bg-white/20 text-white">
            Đang hoạt động
          </Badge>
          <p className="mt-2 text-lg font-semibold">👑 {userSub.subscription.planName}</p>
          <p className="text-sm text-white/80">Còn lại {userSub.subscription.daysRemaining} ngày</p>
        </Card>
      ) : (
        <Card className="mt-4">
          <Badge tone="slate">Chưa đăng ký Premium</Badge>
          <p className="mt-2 text-sm text-slate-500">Nâng cấp để mở khoá toàn bộ bộ từ vựng nâng cao.</p>
        </Card>
      )}

      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
        {plans?.map((plan) => (
          <Card key={plan.id} className="relative overflow-hidden">
            <span className="absolute right-4 top-4 text-2xl">👑</span>
            <h3 className="font-semibold text-slate-900">{plan.name}</h3>
            <p className="mt-1 text-2xl font-bold text-brand-600">{plan.priceDisplay}</p>
            <p className="text-sm text-slate-500">{plan.durationDays} ngày</p>
            <Button
              variant="pro"
              className="mt-4 w-full"
              loading={payMutation.isPending && payMutation.variables === plan.id}
              onClick={() => payMutation.mutate(plan.id)}
            >
              Thanh toán qua VNPay
            </Button>
          </Card>
        ))}
      </div>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Kích hoạt bằng mã</h2>
        <form onSubmit={handleActivate} className="mt-3 flex flex-wrap items-end gap-3">
          <Input
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Nhập mã kích hoạt"
            className="w-56"
          />
          <Button type="submit" loading={activateMutation.isPending}>
            Kích hoạt
          </Button>
        </form>
      </Card>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Lịch sử giao dịch</h2>
        <div className="mt-3 divide-y divide-slate-100">
          {transactions?.data.map((tx) => (
            <div key={tx.id} className="flex items-center justify-between py-2 text-sm">
              <span className="text-slate-600">{new Date(tx.createdAt).toLocaleString('vi-VN')}</span>
              <span className="text-slate-600">{tx.method}</span>
              <span className="font-medium text-slate-900">{formatVnd(tx.amount)}</span>
              <Badge tone={tx.status === 'success' ? 'green' : tx.status === 'pending' ? 'amber' : 'red'}>{tx.status}</Badge>
            </div>
          ))}
          {transactions?.data.length === 0 && <p className="py-2 text-sm text-slate-500">Chưa có giao dịch nào.</p>}
        </div>
      </Card>
    </div>
  );
}
