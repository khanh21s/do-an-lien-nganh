'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useRouter } from 'next/navigation';

export default function PricingPage() {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const { data: plans, isLoading } = useQuery({ queryKey: ['plans'], queryFn: subscriptionApi.listPlans });

  function handleSubscribe() {
    router.push(token ? '/subscription' : '/login');
  }

  return (
    <main className="min-h-screen bg-slate-50 px-6 py-16">
      <div className="mx-auto max-w-4xl text-center">
        <Link href="/" className="text-xl font-bold text-brand-600">
          Parroto
        </Link>
        <h1 className="mt-6 text-3xl font-bold text-slate-900">Bảng giá</h1>
        <p className="mt-2 text-slate-600">Chọn gói phù hợp để mở khoá toàn bộ nội dung học tập.</p>
      </div>

      {isLoading ? (
        <FullPageSpinner />
      ) : (
        <div className="mx-auto mt-10 grid max-w-4xl grid-cols-1 gap-6 sm:grid-cols-2">
          {plans?.map((plan) => (
            <Card key={plan.id} className="flex flex-col">
              <h2 className="text-lg font-semibold text-slate-900">{plan.name}</h2>
              <p className="mt-2 text-3xl font-bold text-brand-600">{plan.priceDisplay}</p>
              <p className="text-sm text-slate-500">{plan.durationDays} ngày</p>
              {Array.isArray(plan.features) && (
                <ul className="mt-4 flex-1 space-y-1 text-sm text-slate-600">
                  {(plan.features as string[]).map((f, i) => (
                    <li key={i}>✓ {f}</li>
                  ))}
                </ul>
              )}
              <Button className="mt-6 w-full" onClick={handleSubscribe}>
                Đăng ký gói này
              </Button>
            </Card>
          ))}
        </div>
      )}
    </main>
  );
}
