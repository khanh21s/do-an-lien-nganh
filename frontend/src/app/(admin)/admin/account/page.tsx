'use client';

import { providerLabel } from '@/components/admin/console';
import { formatDate } from '@/components/subscription/format';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { Input } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import { qk } from '@/lib/queryKeys';
import { useMutation, useQuery } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';

/** T14: admin account (replaces the learner profile): basic info + change password. */
export default function AdminAccountPage() {
  const profileQuery = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const profile = profileQuery.data;

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);

  const passwordMutation = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      toast.success('Đã đổi mật khẩu.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Mật khẩu hiện tại không đúng.')),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (newPassword.length < 8) return setError('Mật khẩu mới phải có ít nhất 8 ký tự.');
    if (newPassword !== confirmPassword) return setError('Mật khẩu xác nhận không khớp.');
    setError(null);
    passwordMutation.mutate();
  }

  if (profileQuery.isLoading) return <FullPageSpinner />;
  if (profileQuery.isError) return <ErrorState error={profileQuery.error} onRetry={profileQuery.refetch} className="mt-6" />;
  if (!profile) return <ErrorState notFound />;

  const isLocal = (profile.authProvider || 'local').toLowerCase() === 'local';

  return (
    <div className="mx-auto w-full max-w-xl">
      <h1 className="text-2xl font-bold text-slate-900">Tài khoản quản trị</h1>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Thông tin</h2>
        <dl className="mt-3 space-y-2 text-sm">
          <div className="flex justify-between gap-3">
            <dt className="text-slate-500">Email</dt>
            <dd className="min-w-0 break-all text-right text-slate-900">{profile.email}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-slate-500">Đăng nhập bằng</dt>
            <dd className="text-slate-900">{providerLabel(profile.authProvider)}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-slate-500">Vai trò</dt>
            <dd className="text-slate-900">Quản trị viên</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-slate-500">Ngày tạo</dt>
            <dd className="text-slate-900">{formatDate(profile.createdAt)}</dd>
          </div>
        </dl>
      </Card>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Đổi mật khẩu</h2>
        {isLocal ? (
          <form onSubmit={handleSubmit} className="mt-3 flex flex-col gap-3">
            <Input
              type="password"
              label="Mật khẩu hiện tại"
              required
              autoComplete="current-password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
            <Input
              type="password"
              label="Mật khẩu mới"
              required
              minLength={8}
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              hint="Ít nhất 8 ký tự."
            />
            <Input
              type="password"
              label="Nhập lại mật khẩu mới"
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              error={error ?? undefined}
            />
            <Button type="submit" loading={passwordMutation.isPending} className="min-h-[44px] w-fit">
              Đổi mật khẩu
            </Button>
          </form>
        ) : (
          <p className="mt-1 text-sm text-slate-500">
            Tài khoản đăng nhập bằng {providerLabel(profile.authProvider)} nên không có mật khẩu riêng tại Parroto.
          </p>
        )}
      </Card>
    </div>
  );
}
