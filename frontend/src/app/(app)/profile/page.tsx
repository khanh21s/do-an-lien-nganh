'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { formatHours } from '@/components/dashboard/dashboard-data';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { CEFR_LABELS, CEFR_LEVELS } from '@/lib/constants';
import { authApi } from '@/lib/endpoints/auth';
import { practiceApi } from '@/lib/endpoints/practice';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { FormEvent, useEffect, useState } from 'react';
import toast from 'react-hot-toast';

export default function ProfilePage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const profileQuery = useQuery({ queryKey: qk.profile, queryFn: authApi.getProfile });
  const profile = profileQuery.data;
  // AUTH-06: same sources (and cache keys) as the Dashboard, not the stale `"user".total_*` columns.
  const statsQuery = useQuery({ queryKey: qk.practiceStats, queryFn: practiceApi.getStats });
  const vocabQuery = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });

  const [cefrLevel, setCefrLevel] = useState<CefrLevel>('A1');
  useEffect(() => {
    if (profile) setCefrLevel(profile.cefrLevel);
  }, [profile]);

  const updateMutation = useMutation({
    mutationFn: () => authApi.updateProfile({ cefrLevel }),
    onSuccess: () => {
      toast.success('Đã cập nhật hồ sơ.');
      queryClient.invalidateQueries({ queryKey: qk.profile });
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const passwordMutation = useMutation({
    mutationFn: () => authApi.changePassword({ currentPassword, newPassword }),
    onSuccess: () => {
      toast.success('Đã đổi mật khẩu.');
      setCurrentPassword('');
      setNewPassword('');
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Mật khẩu hiện tại không đúng.')),
  });

  const deleteMutation = useMutation({
    mutationFn: () => authApi.deleteAccount(),
    onSuccess: () => {
      clearAuth();
      router.replace('/');
    },
    onError: (err) => toast.error(apiErrorMessage(err)),
  });

  function handleChangePassword(e: FormEvent) {
    e.preventDefault();
    if (newPassword.length < 8) {
      toast.error('Mật khẩu mới phải có ít nhất 8 ký tự.');
      return;
    }
    passwordMutation.mutate();
  }

  function handleDelete() {
    if (confirm('Bạn có chắc chắn muốn xoá tài khoản? Hành động này không thể hoàn tác.')) {
      deleteMutation.mutate();
    }
  }

  if (profileQuery.isLoading) return <FullPageSpinner />;
  if (profileQuery.isError) {
    return <ErrorState className="mt-10" error={profileQuery.error} title="Không tải được hồ sơ" onRetry={() => profileQuery.refetch()} />;
  }
  if (!profile) return <ErrorState notFound />;

  // AUTH-07: password change only exists for email/password accounts.
  const isLocalAccount = (profile.authProvider || 'local').toLowerCase() === 'local';

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold text-slate-900">Hồ sơ của tôi</h1>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Thông tin cơ bản</h2>
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
            <dt className="text-slate-500">Tổng giờ học</dt>
            <dd className="text-slate-900">
              <StatValue query={statsQuery} render={(d) => formatHours(d.totalHoursLearned)} />
            </dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="text-slate-500">Tổng từ đã học</dt>
            <dd className="text-slate-900">
              <StatValue query={vocabQuery} render={(d) => d.totalLearned} />
            </dd>
          </div>
        </dl>
        <Select label="Trình độ CEFR" className="mt-4 w-full sm:w-64" value={cefrLevel} onChange={(e) => setCefrLevel(e.target.value as CefrLevel)}>
          {CEFR_LEVELS.map((lvl) => (
            <option key={lvl} value={lvl}>
              {CEFR_LABELS[lvl]}
            </option>
          ))}
        </Select>
        <Button className="mt-4" loading={updateMutation.isPending} onClick={() => updateMutation.mutate()}>
          Lưu thay đổi
        </Button>
      </Card>

      {isLocalAccount ? (
        <Card className="mt-6">
          <h2 className="font-semibold text-slate-900">Đổi mật khẩu</h2>
          <form onSubmit={handleChangePassword} className="mt-3 flex flex-col gap-3">
            <Input
              type="password"
              label="Mật khẩu hiện tại"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
            />
            <Input
              type="password"
              label="Mật khẩu mới"
              required
              minLength={8}
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
            />
            <Button type="submit" loading={passwordMutation.isPending} className="w-fit">
              Đổi mật khẩu
            </Button>
          </form>
        </Card>
      ) : (
        <Card className="mt-6">
          <h2 className="font-semibold text-slate-900">Mật khẩu</h2>
          <p className="mt-1 text-sm text-slate-500">
            Tài khoản của bạn đăng nhập bằng {providerLabel(profile.authProvider)} nên không có mật khẩu riêng tại Parroto.
          </p>
        </Card>
      )}

      <Card className="mt-6 border-red-200">
        <h2 className="font-semibold text-red-700">Vùng nguy hiểm</h2>
        <p className="mt-1 text-sm text-slate-500">Xoá tài khoản sẽ vô hiệu hoá tài khoản của bạn vĩnh viễn.</p>
        <Button variant="danger" className="mt-3" loading={deleteMutation.isPending} onClick={handleDelete}>
          Xoá tài khoản
        </Button>
      </Card>
    </div>
  );
}

function providerLabel(provider: string | null | undefined): string {
  switch ((provider || 'local').toLowerCase()) {
    case 'google':
      return 'Google';
    case 'apple':
      return 'Apple';
    case 'local':
      return 'Email & mật khẩu';
    default:
      return provider ?? '';
  }
}

/** Inline value for a stats query: spinner while loading, "—" + retry on error (AUTH-08). */
function StatValue<T>({
  query,
  render,
}: {
  query: { data?: T; isLoading: boolean; isError: boolean; refetch: () => unknown };
  render: (data: T) => React.ReactNode;
}) {
  if (query.isLoading) return <Spinner className="h-4 w-4" />;
  if (query.isError || query.data === undefined) {
    return (
      <span className="inline-flex items-center gap-2">
        <span className="text-slate-400">—</span>
        <button type="button" className="text-xs font-medium text-brand-600 hover:underline" onClick={() => query.refetch()}>
          Thử lại
        </button>
      </span>
    );
  }
  return <>{render(query.data)}</>;
}
