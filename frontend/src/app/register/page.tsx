'use client';

import { Button } from '@/components/ui/Button';
import { PublicHeader } from '@/components/layout/PublicHeader';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useNextPath } from '@/hooks/useNextPath';
import { usePostAuthRedirect } from '@/hooks/usePostAuthRedirect';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LABELS, CEFR_LEVELS } from '@/lib/constants';
import { authApi } from '@/lib/endpoints/auth';
import type { CefrLevel } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { FormEvent, Suspense, useState } from 'react';
import toast from 'react-hot-toast';

export default function RegisterPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <RegisterForm />
    </Suspense>
  );
}

function RegisterForm() {
  const queryClient = useQueryClient();
  const { rawNext } = useNextPath();
  const setAuth = useAuthStore((s) => s.setAuth);
  // T14: once signed in, admin → /admin, user → `next` or /dashboard.
  const { redirecting } = usePostAuthRedirect(rawNext);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [cefrLevel, setCefrLevel] = useState<CefrLevel>('A1');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (password !== confirmPassword) {
      toast.error('Mật khẩu xác nhận không khớp.');
      return;
    }
    setLoading(true);
    try {
      const res = await authApi.register({ email, password, cefrLevel });
      queryClient.clear();
      setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Đăng ký thất bại, vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }

  if (redirecting) return <FullPageSpinner />;

  const loginHref = rawNext ? `/login?next=${encodeURIComponent(rawNext)}` : '/login';

  return (
    <>
      <PublicHeader />
      <main className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center bg-brand-50 px-4 py-10">
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
    </>
  );
}
