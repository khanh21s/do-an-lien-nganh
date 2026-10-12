'use client';

import { GoogleSignInButton } from '@/components/auth/GoogleSignInButton';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import { useAuthStore } from '@/store/auth-store';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    try {
      const res = await authApi.login({ email, password });
      setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
      router.replace('/dashboard');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Email hoặc mật khẩu không đúng.'));
    } finally {
      setLoading(false);
    }
  }

  async function handleGoogleToken(idToken: string) {
    try {
      const res = await authApi.loginGoogle(idToken);
      setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
      router.replace('/dashboard');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Đăng nhập Google thất bại.'));
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-brand-50 px-4">
      <Card className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-slate-900">Đăng nhập Parroto</h1>
        <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
          <Input
            type="email"
            label="Email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="ban@example.com"
          />
          <Input
            type="password"
            label="Mật khẩu"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />
          <Button type="submit" loading={loading} className="mt-2 w-full">
            Đăng nhập
          </Button>
        </form>

        <div className="my-4 flex items-center gap-2 text-xs text-slate-400">
          <div className="h-px flex-1 bg-slate-200" />
          hoặc
          <div className="h-px flex-1 bg-slate-200" />
        </div>
        <GoogleSignInButton onToken={handleGoogleToken} />

        <p className="mt-6 text-center text-sm text-slate-600">
          Chưa có tài khoản?{' '}
          <Link href="/register" className="font-medium text-brand-600 hover:underline">
            Đăng ký
          </Link>
        </p>
      </Card>
    </main>
  );
}
