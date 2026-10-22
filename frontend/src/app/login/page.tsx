'use client';

import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton';
import { PublicHeader } from '@/components/layout/PublicHeader';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { useNextPath } from '@/hooks/useNextPath';
import { usePostAuthRedirect } from '@/hooks/usePostAuthRedirect';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import type { AuthResponse } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { FormEvent, Suspense, useState } from 'react';
import toast from 'react-hot-toast';

export default function LoginPage() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const queryClient = useQueryClient();
  const { rawNext } = useNextPath();
  const setAuth = useAuthStore((s) => s.setAuth);
  // AUTH-05 + T14: once signed in (now or already), admin → /admin, user → `next` or /dashboard.
  const { redirecting } = usePostAuthRedirect(rawNext);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  function onAuthenticated(res: AuthResponse) {
    // Never reuse cached data (e.g. a previous account's profile/role) for the new session.
    queryClient.clear();
    setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      onAuthenticated(await authApi.login({ email, password }));
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Email hoặc mật khẩu không đúng.'));
    } finally {
      setLoading(false);
    }
  }

  async function handleGoogleToken(idToken: string) {
    try {
      onAuthenticated(await authApi.loginGoogle(idToken));
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Đăng nhập Google thất bại.'));
    }
  }

  if (redirecting) return <FullPageSpinner />;

  const registerHref = rawNext ? `/register?next=${encodeURIComponent(rawNext)}` : '/register';

  return (
    <>
      <PublicHeader />
      <main className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center bg-brand-50 px-4">
        <Card className="w-full max-w-sm">
          <h1 className="text-center text-2xl font-bold text-slate-900">Đăng nhập Parroto</h1>
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
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />
            <Button type="submit" loading={loading} className="mt-2 w-full">
              Đăng nhập
            </Button>
          </form>

          {/* AUTH-04: the button renders nothing when NEXT_PUBLIC_GOOGLE_CLIENT_ID is not configured. */}
          <GoogleSignInButton onToken={handleGoogleToken} withDivider />

          <p className="mt-6 text-center text-sm text-slate-600">
            Chưa có tài khoản?{' '}
            <Link href={registerHref} className="font-medium text-brand-600 hover:underline">
              Đăng ký
            </Link>
          </p>
        </Card>
      </main>
    </>
  );
}
