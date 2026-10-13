'use client';

import { PlanCard } from '@/components/subscription/PlanCard';
import { bestValuePlanId, premiumFeatures } from '@/components/subscription/format';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { loginUrlWithNext } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

// Free tier is not a BE plan; these are the Phase 1 free capabilities (spec §SUB, parroto §6).
const FREE_FEATURES = [
  'Truy cập toàn bộ bài học miễn phí',
  'Luyện Dictation không giới hạn',
  'Luyện Shadowing với chấm phát âm',
  'Ôn từ vựng bằng flashcard (SRS)',
  'Theo dõi tiến độ, chuỗi ngày học',
];

/** SUB-06: plans + Free vs Premium comparison. Layout (PublicHeader / AppShell) comes from T07. */
export default function PricingPage() {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const { data: plans, isLoading, isError, error, refetch } = useQuery({
    queryKey: qk.plans,
    queryFn: subscriptionApi.listPlans,
  });

  function handleBuy() {
    router.push(token ? '/subscription' : loginUrlWithNext('/subscription'));
  }

  const sorted = [...(plans ?? [])].sort((a, b) => a.durationDays - b.durationDays);
  const bestId = bestValuePlanId(sorted);
  const proFeatures = premiumFeatures(sorted).filter((f) => !FREE_FEATURES.includes(f));

  return (
    <div className="mx-auto w-full max-w-5xl px-4 py-10 sm:px-6">
      <div className="text-center">
        <h1 className="text-3xl font-bold text-slate-900">Nâng cấp Parroto Premium</h1>
        <p className="mt-2 text-slate-600">Mở khoá toàn bộ bộ từ vựng Pro và nội dung nâng cao.</p>
        <p className="mt-1 text-sm font-medium text-violet-700">Thanh toán 1 lần, không tự gia hạn.</p>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : isError ? (
        <ErrorState error={error} onRetry={refetch} className="mt-10" />
      ) : sorted.length === 0 ? (
        <ErrorState title="Chưa có gói Premium" message="Hiện chưa có gói nào được mở bán." className="mt-10" />
      ) : (
        <>
          <div className="mt-10 grid grid-cols-1 gap-6 md:grid-cols-3">
            {sorted.map((plan) => (
              <PlanCard
                key={plan.id}
                plan={plan}
                highlight={plan.id === bestId}
                actionLabel={token ? 'Chọn gói này' : 'Đăng nhập để mua'}
                onAction={handleBuy}
              />
            ))}
          </div>

          <Card className="mt-10 overflow-hidden p-0">
            <h2 className="px-5 pt-5 text-lg font-semibold text-slate-900">So sánh Free và Premium</h2>
            <table className="mt-3 w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-600">
                <tr>
                  <th scope="col" className="px-5 py-3 font-medium">
                    Tính năng
                  </th>
                  <th scope="col" className="w-20 px-2 py-3 text-center font-medium sm:w-28">
                    Free
                  </th>
                  <th scope="col" className="w-20 px-2 py-3 text-center font-medium text-violet-700 sm:w-28">
                    Premium
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {FREE_FEATURES.map((f) => (
                  <ComparisonRow key={f} label={f} free premium />
                ))}
                {proFeatures.map((f) => (
                  <ComparisonRow key={f} label={f} free={false} premium />
                ))}
              </tbody>
            </table>
          </Card>

          <p className="mt-6 text-center text-sm text-slate-500">
            Có mã kích hoạt?{' '}
            <Link
              href={token ? '/subscription' : loginUrlWithNext('/subscription')}
              className="font-medium text-brand-600 hover:underline"
            >
              Nhập mã tại trang Gói Premium
            </Link>
          </p>
        </>
      )}
    </div>
  );
}

function ComparisonRow({ label, free, premium }: { label: string; free: boolean; premium: boolean }) {
  return (
    <tr>
      <td className="px-5 py-3 text-slate-700">{label}</td>
      <td className="px-2 py-3 text-center">{mark(free)}</td>
      <td className="px-2 py-3 text-center">{mark(premium)}</td>
    </tr>
  );
}

function mark(on: boolean) {
  return on ? (
    <span className="font-bold text-green-600" aria-label="Có">
      ✓
    </span>
  ) : (
    <span className="text-slate-300" aria-label="Không">
      —
    </span>
  );
}
