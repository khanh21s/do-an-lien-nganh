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
  invalid_amount: 'Số tiền thanh toán không khớp với giá gói. Giao dịch đã bị huỷ.',
  payment_declined: 'Giao dịch bị từ chối hoặc chưa được thanh toán.',
  payment_cancelled: 'Bạn đã huỷ giao dịch.',
};

function FailedMessage() {
  const searchParams = useSearchParams();
  const reason = searchParams.get('reason');
  const message = (reason && REASON_MESSAGES[reason]) || 'Đã có lỗi xảy ra trong quá trình thanh toán.';
  return (
    <>
      <p className="mt-2 text-slate-500">{message}</p>
      <p className="mt-1 text-xs text-slate-400">
        Nếu tài khoản ngân hàng đã bị trừ tiền, vui lòng liên hệ hỗ trợ kèm thời gian giao dịch.
      </p>
    </>
  );
}

export default function PaymentFailedPage() {
  return (
    <main className="min-h-screen bg-slate-50">
      <PublicHeader />
      <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center sm:px-6">
        <Card className="w-full">
          <div className="text-5xl" aria-hidden>
            😥
          </div>
          <h1 className="mt-4 text-xl font-bold text-slate-900">Thanh toán không thành công</h1>
          <Suspense fallback={<FullPageSpinner />}>
            <FailedMessage />
          </Suspense>
          <div className="mt-6 flex flex-col gap-2">
            <Link href="/subscription">
              <Button className="min-h-[44px] w-full">Thử lại</Button>
            </Link>
            <Link href="/pricing">
              <Button variant="secondary" className="min-h-[44px] w-full">
                Xem bảng giá
              </Button>
            </Link>
          </div>
        </Card>
      </div>
    </main>
  );
}
