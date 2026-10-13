import Link from 'next/link';

export const metadata = {
  title: 'Không tìm thấy trang — Parroto',
};

/** NAV-02: Vietnamese 404 page. */
export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-brand-50 px-4 text-center">
      <p className="text-6xl font-extrabold text-brand-600">404</p>
      <h1 className="text-2xl font-bold text-slate-900">Không tìm thấy trang</h1>
      <p className="max-w-md text-sm text-slate-600">
        Trang bạn truy cập không tồn tại hoặc đã được chuyển đi. Hãy kiểm tra lại đường dẫn.
      </p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Link
          href="/"
          className="inline-flex min-h-[44px] items-center rounded-lg border border-slate-300 bg-white px-4 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Về trang chủ
        </Link>
        <Link
          href="/dashboard"
          className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
        >
          Vào bảng điều khiển
        </Link>
      </div>
    </main>
  );
}
