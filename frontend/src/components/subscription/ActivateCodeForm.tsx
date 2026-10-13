'use client';

import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { apiErrorMessage } from '@/lib/api';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { qk } from '@/lib/queryKeys';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import toast from 'react-hot-toast';
import { formatDate } from './format';

/**
 * SUB-05 (FE): redeem an activation code. BE errors ("Mã đã được sử dụng", "Mã đã hết hạn",
 * "Mã không hợp lệ") are shown verbatim; on success premium-related caches are invalidated.
 */
export function ActivateCodeForm() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (value: string) => subscriptionApi.activateCode(value),
    onSuccess: (res) => {
      setCode('');
      setError(null);
      const endAt = res.subscription?.endAt;
      toast.success(endAt ? `Đã kích hoạt Premium đến ${formatDate(endAt)}` : 'Kích hoạt Premium thành công!');
      queryClient.setQueryData(qk.userSubscription, res);
      queryClient.invalidateQueries({ queryKey: qk.userSubscription });
      queryClient.invalidateQueries({ queryKey: qk.profile });
      queryClient.invalidateQueries({ queryKey: qk.transactionsAll });
    },
    onError: (err) => {
      const msg = apiErrorMessage(err, 'Không kích hoạt được mã, vui lòng thử lại.');
      setError(msg);
      toast.error(msg);
    },
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const value = code.trim().toUpperCase();
    if (!value) {
      setError('Vui lòng nhập mã kích hoạt.');
      return;
    }
    mutation.mutate(value);
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-start" noValidate>
      <div className="flex-1">
        <Input
          aria-label="Mã kích hoạt"
          value={code}
          onChange={(e) => {
            setCode(e.target.value);
            if (error) setError(null);
          }}
          placeholder="PROMO-XXXXX-XXXXX"
          autoComplete="off"
          spellCheck={false}
          className="min-h-[44px] w-full font-mono uppercase"
          error={error ?? undefined}
          hint="Mã cộng dồn vào hạn Premium hiện tại (nếu có)."
        />
      </div>
      <Button type="submit" loading={mutation.isPending} className="min-h-[44px] sm:w-32">
        Kích hoạt
      </Button>
    </form>
  );
}
