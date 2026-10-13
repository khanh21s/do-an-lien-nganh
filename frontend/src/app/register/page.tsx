'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useNextPath } from '@/hooks/useNextPath';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LABELS, CEFR_LEVELS } from '@/lib/constants';
import { authApi } from '@/lib/endpoints/auth';
import type { CefrLevel } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, Suspense, useEffect, useState } from 'react';
import toast from 'react-hot-toast';

export default function RegisterPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <RegisterForm />
    </Suspense>
  );
}

function RegisterForm() {
  const router = useRouter();
  const { next, rawNext } = useNextPath();
  const setAuth = useAuthStore((s) => s.setAuth);
  const hasHydrated = useAuthStore((s) => s.hasHydrated);
  const token = useAuthStore((s) => s.token);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [cefrLevel, setCefrLevel] = useState<CefrLevel>('A1');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (hasHydrated && token) router.replace(next);
  }, [hasHydrated, token, next, router]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp.');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.register({ email, password, cefrLevel });
      setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
      router.replace(next);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Đăng ký thất bại, vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }

  const loginHref = rawNext ? `/login?next=${encodeURIComponent(rawNext)}` : '/login';

  return (
    <main className="flex min-h-screen items-center justify-center bg-brand-50 px-4 py-10">
      <Card className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-slate-900">Tạo tài khoản Parroto</h1>
        <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
          <Input
            type="email"
            label="Email"
            required
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ban@example.com"
          />
          <Input
            type="password"
            label="Mật khẩu"
            required
            minLength={8}
            autoComplete="new-password"
            hint="Tối thiểu 8 ký tự"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />
          <Input
            type="password"
            label="Xác nhận mật khẩu"
            required
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="••••••••"
          />
          <Select label="Trình độ hiện tại (CEFR)" value={cefrLevel} onChange={(e) => setCefrLevel(e.target.value as CefrLevel)}>
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {CEFR_LABELS[lvl]}
              </option>
            ))}
          </Select>
          <Button type="submit" loading={loading} className="mt-2 w-full">
            Đăng ký
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          Đã có tài khoản?{' '}
          <Link href={loginHref} className="font-medium text-brand-600 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </Card>
    </main>
  );
}
