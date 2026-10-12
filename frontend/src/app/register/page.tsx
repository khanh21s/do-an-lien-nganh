'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import type { CefrLevel } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export default function RegisterPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
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
      setAuth({ userId: res.userId, email: res.email, cefrLevel: res.cefrLevel }, res.token, res.refreshToken);
      router.replace('/dashboard');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Đăng ký thất bại, vui lòng thử lại.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-brand-50 px-4 py-10">
      <Card className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-bold text-slate-900">Tạo tài khoản Parroto</h1>
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
            minLength={8}
            hint="Tối thiểu 8 ký tự"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
          />
          <Input
            type="password"
            label="Xác nhận mật khẩu"
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="••••••••"
          />
          <Select label="Trình độ hiện tại (CEFR)" value={cefrLevel} onChange={(e) => setCefrLevel(e.target.value as CefrLevel)}>
            {CEFR_LEVELS.map((lvl) => (
              <option key={lvl} value={lvl}>
                {lvl}
              </option>
            ))}
          </Select>
          <Button type="submit" loading={loading} className="mt-2 w-full">
            Đăng ký
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-slate-600">
          Đã có tài khoản?{' '}
          <Link href="/login" className="font-medium text-brand-600 hover:underline">
            Đăng nhập
          </Link>
        </p>
      </Card>
    </main>
  );
}
