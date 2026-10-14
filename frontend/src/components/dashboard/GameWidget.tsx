'use client';

import { GameProgressCard } from '@/components/game/GameProgressCard';
import { gameApi } from '@/lib/endpoints/game';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

/** Dashboard: game level, today's points goal and the Daily challenge (T16). Hidden on error. */
export function GameWidget() {
  const { data } = useQuery({ queryKey: qk.gameStats, queryFn: gameApi.stats });
  if (!data) return null;
  const daily = data.daily;
  return (
    <GameProgressCard progress={data.progress}>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4 sm:col-span-2">
        <p className="text-sm text-slate-600" data-testid="dashboard-daily">
          <span aria-hidden>📅 </span>
          {daily.played
            ? `Thử thách hôm nay: ${daily.score ?? 0} điểm${daily.rank ? ` · hạng ${daily.rank}/${daily.totalPlayers}` : ''}`
            : 'Thử thách hôm nay đang chờ bạn – 10 từ, 1 lượt.'}
        </p>
        <Link
          href="/games"
          className="inline-flex min-h-[44px] items-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700"
        >
          ⚡ Chơi Dịch nhanh
        </Link>
      </div>
    </GameProgressCard>
  );
}
