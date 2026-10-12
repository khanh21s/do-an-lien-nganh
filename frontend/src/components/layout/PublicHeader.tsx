'use client';

import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/store/auth-store';
import Link from 'next/link';

export function PublicHeader() {
  const token = useAuthStore((s) => s.token);

  return (
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
        <Link href="/" className="text-xl font-bold text-brand-600">
          Parroto
        </Link>
        <nav className="flex items-center gap-4">
          <Link href="/lessons" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Bài học
          </Link>
          <Link href="/pricing" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Bảng giá
          </Link>
          <Link href={token ? '/dashboard' : '/login'}>
            <Button size="sm">{token ? 'Vào học' : 'Đăng nhập'}</Button>
          </Link>
        </nav>
      </div>
    </header>
  );
}
