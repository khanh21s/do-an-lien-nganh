'use client';

import { Card } from '@/components/ui/Card';
import { apiErrorMessage } from '@/lib/api';
import { gameApi } from '@/lib/endpoints/game';
import { DAILY_GOAL_OPTIONS, goalMessage, goalPercent, levelPercent } from '@/lib/game';
import { qk } from '@/lib/queryKeys';
import type { GameProgress } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import toast from 'react-hot-toast';

/** Level + XP bar and the daily points goal (T16). `editable` shows the goal picker. */
export function GameProgressCard({
  progress,
  editable = false,
  className,
  children,
}: {
  progress: GameProgress;
  editable?: boolean;
  className?: string;
  children?: React.ReactNode;
}) {
  const queryClient = useQueryClient();
  const goalMutation = useMutation({
    mutationFn: gameApi.setDailyGoal,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: qk.gameStats });
      queryClient.invalidateQueries({ queryKey: qk.streak });
      queryClient.invalidateQueries({ queryKey: qk.practiceStats });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không đổi được mục tiêu.')),
  });

  return (
    <Card className={clsx('grid gap-5 sm:grid-cols-2', className)} data-testid="game-progress">
      <div>
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-sm font-semibold text-slate-900">
            <span aria-hidden>🏆 </span>Cấp {progress.level}
          </span>
          <span className="text-xs text-slate-500 tabular-nums">
            {progress.levelXp}/{progress.levelXpNeeded} XP
          </span>
        </div>
        <Bar value={levelPercent(progress)} className="bg-violet-500" label={`Cấp ${progress.level}`} />
        <p className="mt-1 text-xs text-slate-500">
          Tổng {progress.xp.toLocaleString('vi-VN')} XP · còn {progress.levelXpNeeded - progress.levelXp} XP để lên cấp {progress.level + 1}
        </p>
      </div>
      <div>
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-sm font-semibold text-slate-900">
            <span aria-hidden>🎯 </span>Mục tiêu hôm nay
          </span>
          {editable ? (
            <label className="text-xs text-slate-500">
              <span className="sr-only">Mục tiêu điểm mỗi ngày</span>
              <select
                className="rounded border border-slate-300 bg-white px-1.5 py-0.5 text-xs"
                value={progress.dailyGoal}
                disabled={goalMutation.isPending}
                onChange={(e) => goalMutation.mutate(Number(e.target.value))}
              >
                {DAILY_GOAL_OPTIONS.map((g) => (
                  <option key={g} value={g}>
                    {g} điểm/ngày
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <span className="text-xs text-slate-500">{progress.dailyGoal} điểm/ngày</span>
          )}
        </div>
        <Bar
          value={goalPercent(progress)}
          className={progress.goalMet ? 'bg-green-500' : 'bg-amber-500'}
          label="Mục tiêu hôm nay"
        />
        <p className={clsx('mt-1 text-xs', progress.goalMet ? 'text-green-700' : 'text-slate-500')} data-testid="goal-message">
          <span className="tabular-nums">
            {progress.todayPoints}/{progress.dailyGoal}
          </span>{' '}
          · {goalMessage(progress)}
        </p>
      </div>
      {children}
    </Card>
  );
}

function Bar({ value, className, label }: { value: number; className: string; label: string }) {
  return (
    <div
      className="mt-2 h-2.5 w-full overflow-hidden rounded-full bg-slate-100"
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={value}
    >
      <div className={clsx('h-full rounded-full transition-all', className)} style={{ width: `${value}%` }} />
    </div>
  );
}
