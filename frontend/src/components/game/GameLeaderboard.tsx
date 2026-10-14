'use client';

import { Card } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { gameApi } from '@/lib/endpoints/game';
import { BOARD_LABELS } from '@/lib/game';
import { qk } from '@/lib/queryKeys';
import type { GameBoard, GameLeaderboardEntry } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { useState } from 'react';

const BOARDS: GameBoard[] = ['daily', 'sprint-week', 'survival-week'];
const MEDALS = ['🥇', '🥈', '🥉'];

/** Leaderboards (T16): today's challenge, best Sprint / Survival round of the week. */
export function GameLeaderboard({ initialBoard = 'daily' }: { initialBoard?: GameBoard }) {
  const [board, setBoard] = useState<GameBoard>(initialBoard);
  const { data, isLoading, isError } = useQuery({
    queryKey: qk.gameLeaderboard(board),
    queryFn: () => gameApi.leaderboard(board),
  });
  const meOutside = data?.me && !data.entries.some((e) => e.me);

  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">Bảng xếp hạng</h2>
      <div className="mt-3 flex flex-wrap gap-1.5" role="tablist" aria-label="Bảng xếp hạng">
        {BOARDS.map((b) => (
          <button
            key={b}
            type="button"
            role="tab"
            aria-selected={board === b}
            onClick={() => setBoard(b)}
            className={clsx(
              'min-h-[36px] rounded-full px-3 text-xs font-medium',
              board === b ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
            )}
          >
            {BOARD_LABELS[b]}
          </button>
        ))}
      </div>
      {isLoading ? (
        <div className="flex justify-center py-6">
          <Spinner />
        </div>
      ) : isError || !data ? (
        <p className="mt-4 text-sm text-slate-500">Không tải được bảng xếp hạng.</p>
      ) : (
        <>
          <p className="mt-3 text-xs text-slate-500">
            {data.title} · {data.totalPlayers} người chơi
          </p>
          {data.entries.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500">Chưa có ai ghi điểm. Hãy là người đầu tiên!</p>
          ) : (
            <ol className="mt-2 divide-y divide-slate-100" data-testid="leaderboard">
              {data.entries.map((e) => (
                <Row key={`${e.rank}-${e.displayName}`} entry={e} />
              ))}
              {meOutside && data.me && (
                <>
                  <li className="py-1 text-center text-xs text-slate-400" aria-hidden>
                    …
                  </li>
                  <Row entry={data.me} />
                </>
              )}
            </ol>
          )}
          {board !== 'daily' && (
            <p className="mt-3 text-xs text-slate-400">Tính ván cao nhất trong tuần; không tính ván chơi từ sổ tay.</p>
          )}
        </>
      )}
    </Card>
  );
}

function Row({ entry }: { entry: GameLeaderboardEntry }) {
  return (
    <li
      className={clsx('flex items-center gap-3 py-2 text-sm', entry.me && 'rounded-lg bg-brand-50 px-2 font-semibold')}
      aria-current={entry.me ? 'true' : undefined}
    >
      <span className="w-7 text-center tabular-nums text-slate-500">{MEDALS[entry.rank - 1] ?? entry.rank}</span>
      <span className="flex-1 truncate text-slate-700">
        {entry.displayName}
        {entry.me && <span className="ml-1 text-xs text-brand-600">(bạn)</span>}
      </span>
      <span className="text-xs text-orange-600">🔥 {entry.bestCombo}</span>
      <span className="w-12 text-right tabular-nums text-slate-900">{entry.score}</span>
    </li>
  );
}
