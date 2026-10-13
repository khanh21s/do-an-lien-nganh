'use client';

import { PublicHeader } from '@/components/layout/PublicHeader';
import { formatDate } from '@/components/subscription/format';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { loginUrlWithNext } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useEffect, useState } from 'react';

const POLL_INTERVAL_MS = 2_000;
const POLL_TIMEOUT_MS = 10_000;

/**
 * SUB-03: the VNPay return URL only redirects here; the real state comes from
 * `GET /subscriptions/user`, polled every 2s for up to 10s until premium is active.
 */
export default function PaymentSuccessPage() {
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);

  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center sm:px-6">
        <Card className="w-full">
          {!hasHydrated ? (
            <div className="flex justify-center py-8">
              <Spinner className="h-8 w-8" />
            </div>
          ) : token ? (
            <SubscriptionStatus />
          ) : (
            <>
              <div className="text-5xl" aria-hidden>
                🧾
              </div>
              <h1 className="mt-4 text-xl font-bold text-slate-900">Đã nhận kết quả thanh toán</h1>
              <p className="mt-2 text-slate-500">Đăng nhập để xem trạng thái gói Premium của bạn.</p>
              <Link href={loginUrlWithNext('/subscription/success')}>
                <Button className="mt-6 min-h-[44px] w-full">Đăng nhập</Button>
              </Link>
            </>
          )}
        </Card>
      </div>
    </main>
  );
}

function SubscriptionStatus() {
  const queryClient = useQueryClient();
  const [timedOut, setTimedOut] = useState(false);

  useEffect(() => {
    const id = setTimeout(() => setTimedOut(true), POLL_TIMEOUT_MS);
    return () => clearTimeout(id);
  }, []);

  const { data, isError, refetch, isFetching } = useQuery({
    queryKey: qk.userSubscription,
    queryFn: subscriptionApi.getUserSubscription,
    refetchOnMount: 'always',
    staleTime: 0,
    refetchInterval: (query) => (query.state.data?.isPremium || timedOut ? false : POLL_INTERVAL_MS),
  });

  const active = !!data?.isPremium && !!data.subscription;

  useEffect(() => {
    if (active) {
      queryClient.invalidateQueries({ queryKey: qk.profile });
      queryClient.invalidateQueries({ queryKey: qk.transactionsAll });
    }
  }, [active, queryClient]);

  if (active && data?.subscription) {
    return (
      <>
        <div className="text-5xl" aria-hidden>
          🎉
        </div>
        <h1 className="mt-4 text-xl font-bold text-slate-900">Thanh toán thành công!</h1>
        <p className="mt-2 text-slate-600">
          Gói <strong>{data.subscription.planName}</strong> đã kích hoạt đến{' '}
          <strong>{formatDate(data.subscription.endAt)}</strong>.
        </p>
        <Link href="/subscription">
          <Button className="mt-6 min-h-[44px] w-full">Xem gói của tôi</Button>
        </Link>
      </>
    );
  }

  if (!timedOut && !isError) {
    return (
      <div role="status" aria-live="polite">
        <div className="flex justify-center py-2">
          <Spinner className="h-10 w-10" />
        </div>
        <h1 className="mt-4 text-xl font-bold text-slate-900">Đang xác nhận thanh toán…</h1>
        <p className="mt-2 text-slate-500">Vui lòng chờ trong giây lát, không đóng trang này.</p>
      </div>
    );
  }

  return (
    <div role="status" aria-live="polite">
      <div className="text-5xl" aria-hidden>
        ⏳
      </div>
      <h1 className="mt-4 text-xl font-bold text-slate-900">Đang xử lý…</h1>
      <p className="mt-2 text-slate-500">
        {isError
          ? 'Chưa kiểm tra được trạng thái gói. Vui lòng thử lại sau ít phút.'
          : 'Giao dịch đang được ngân hàng/VNPay xác nhận. Gói Premium sẽ được kích hoạt ngay khi có kết quả.'}
      </p>
      <div className="mt-6 flex flex-col gap-2">
        <Button className="min-h-[44px] w-full" onClick={() => refetch()} loading={isFetching}>
          Kiểm tra lại
        </Button>
        <Link href="/subscription">
          <Button variant="secondary" className="min-h-[44px] w-full">
            Về trang Gói Premium
          </Button>
        </Link>
      </div>
    </div>
  );
}
