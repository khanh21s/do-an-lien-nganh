'use client';

import { PublicHeader } from '@/components/layout/PublicHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

const REASON_MESSAGES: Record<string, string> = {
  invalid_signature: 'Chữ ký giao dịch không hợp lệ.',
  missing_reference: 'Thiếu thông tin giao dịch.',
  invalid_reference: 'Mã giao dịch không hợp lệ.',
  not_found: 'Không tìm thấy giao dịch.',
  payment_declined: 'Giao dịch bị từ chối bởi ngân hàng/VNPay.',
};

function FailedMessage() {
  const searchParams = useSearchParams();
  const reason = searchParams.get('reason');
  const message = (reason && REASON_MESSAGES[reason]) || 'Đã có lỗi xảy ra trong quá trình thanh toán.';
  return <p className="mt-2 text-slate-500">{message}</p>;
}

export default function PaymentFailedPage() {
  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="mx-auto flex max-w-md flex-col items-center px-6 py-20 text-center">
        <Card className="w-full">
          <div className="text-5xl">😥</div>
          <h1 className="mt-4 text-xl font-bold text-slate-900">Thanh toán không thành công</h1>
          <Suspense fallback={<FullPageSpinner />}>
            <FailedMessage />
          </Suspense>
          <Link href="/subscription">
            <Button className="mt-6 w-full">Thử lại</Button>
          </Link>
        </Card>
      </div>
    </main>
  );
}
