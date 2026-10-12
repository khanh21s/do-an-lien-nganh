'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input, Select } from '@/components/ui/Input';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { authApi } from '@/lib/endpoints/auth';
import type { CefrLevel } from '@/lib/types';
import { useAuthStore } from '@/store/auth-store';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { FormEvent, useEffect, useState } from 'react';
import toast from 'react-hot-toast';

const CEFR_LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export default function ProfilePage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const { data: profile, isLoading } = useQuery({ queryKey: ['profile'], queryFn: authApi.getProfile });

  const [cefrLevel, setCefrLevel] = useState<CefrLevel>('A1');
  useEffect(() => {
    if (profile) setCefrLevel(profile.cefrLevel);
  }, [profile]);

  const updateMutation = useMutation({
    mutationFn: () => authApi.updateProfile({ cefrLevel }),
    onSuccess: () => {
      toast.success('Đã cập nhật hồ sơ.');
      queryClient.invalidateQueries({ queryKey: ['profile'] });
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

  if (isLoading || !profile) return <FullPageSpinner />;

  return (
    <div className="mx-auto max-w-xl">
      <h1 className="text-2xl font-bold text-slate-900">Hồ sơ của tôi</h1>

      <Card className="mt-6">
        <h2 className="font-semibold text-slate-900">Thông tin cơ bản</h2>
        <dl className="mt-3 space-y-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-500">Email</dt>
            <dd className="text-slate-900">{profile.email}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-500">Tổng giờ học</dt>
            <dd className="text-slate-900">{profile.totalHoursLearned.toFixed(1)}h</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-500">Tổng từ đã học</dt>
            <dd className="text-slate-900">{profile.totalWordsLearned}</dd>
          </div>
        </dl>
        <Select label="Trình độ CEFR" className="mt-4 w-40" value={cefrLevel} onChange={(e) => setCefrLevel(e.target.value as CefrLevel)}>
          {CEFR_LEVELS.map((lvl) => (
            <option key={lvl} value={lvl}>
              {lvl}
            </option>
          ))}
        </Select>
        <Button className="mt-4" loading={updateMutation.isPending} onClick={() => updateMutation.mutate()}>
          Lưu thay đổi
        </Button>
      </Card>

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
