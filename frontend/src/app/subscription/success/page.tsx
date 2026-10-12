'use client';

import { PublicHeader } from '@/components/layout/PublicHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useAuthStore } from '@/store/auth-store';
import Link from 'next/link';

export default function PaymentSuccessPage() {
  const token = useAuthStore((s) => s.token);

  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="mx-auto flex max-w-md flex-col items-center px-6 py-20 text-center">
        <Card className="w-full">
          <div className="text-5xl">🎉</div>
          <h1 className="mt-4 text-xl font-bold text-slate-900">Thanh toán thành công!</h1>
          <p className="mt-2 text-slate-500">Gói Premium của bạn đã được kích hoạt.</p>
          <Link href={token ? '/subscription' : '/login'}>
            <Button className="mt-6 w-full">{token ? 'Xem gói của tôi' : 'Đăng nhập để xem'}</Button>
          </Link>
        </Card>
      </div>
    </main>
  );
}
