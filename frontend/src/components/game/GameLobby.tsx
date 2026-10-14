'use client';

import { BadgeGrid } from '@/components/game/BadgeGrid';
import { GameLeaderboard } from '@/components/game/GameLeaderboard';
import { GameProgressCard } from '@/components/game/GameProgressCard';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { KbdCombo } from '@/components/ui/Kbd';
import { comboToLabels } from '@/hooks/useHotkeys';
import { gameApi } from '@/lib/endpoints/game';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { MODE_INFO } from '@/lib/game';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel, GameMode, GameSource, StartGameInput } from '@/lib/types';
import { useShortcutKeys } from '@/store/shortcut-store';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';

const LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1'];
const MODES: GameMode[] = ['SPRINT', 'SURVIVAL', 'DAILY'];

const SOURCES: { value: GameSource; icon: string; title: string; text: string }[] = [
  { value: 'DECK', icon: '🗂️', title: 'Bộ từ', text: 'Từ trong một bộ từ vựng' },
  { value: 'LEVEL', icon: '🎯', title: 'Theo trình độ', text: 'Ngẫu nhiên theo cấp CEFR' },
  { value: 'NOTEBOOK', icon: '📓', title: 'Từ của tôi', text: 'Sổ tay, từ đến hạn ôn trước' },
];

/**
 * Lobby: progress (level, daily goal), mode picker (Sprint with a word source, Survival for
 * Premium, today's Daily challenge), leaderboards, badges, scoring rules and personal bests.
 */
export function GameLobby({
  choice,
  onChoiceChange,
  onStart,
  starting,
}: {
  choice: StartGameInput;
  onChoiceChange: (c: StartGameInput) => void;
  /** Called with the complete choice (deck/level defaults filled in). */
  onStart: (c: StartGameInput) => void;
  starting: boolean;
}) {
  const keys = useShortcutKeys();
  const mode: GameMode = choice.mode ?? 'SPRINT';
  const source: GameSource = choice.source ?? 'DECK';
  const { data: decks } = useQuery({ queryKey: qk.decks({ limit: 100 }), queryFn: () => vocabularyApi.listDecks({ limit: 100 }) });
  const { data: stats } = useQuery({ queryKey: qk.gameStats, queryFn: gameApi.stats });
  const { data: sub } = useQuery({ queryKey: qk.userSubscription, queryFn: subscriptionApi.getUserSubscription });
  const isPremium = !!sub?.isPremium;

  const deckList = decks?.data ?? [];
  const deckId = choice.deckId ?? deckList.find((d) => !d.isProOnly)?.id;
  const daily = stats?.daily;

  const blocked =
    (mode === 'SPRINT' && source === 'DECK' && !deckId) ||
    (mode === 'SURVIVAL' && !isPremium) ||
    (mode === 'DAILY' && !!daily?.played);

  function start() {
    if (mode !== 'SPRINT') {
      onStart({ mode });
      return;
    }
    onStart({
      mode,
      source,
      deckId: source === 'DECK' ? deckId : undefined,
      level: source === 'LEVEL' ? (choice.level ?? 'B1') : undefined,
    });
  }

  const startLabel =
    mode === 'SPRINT' ? 'Bắt đầu (60 giây)' : mode === 'SURVIVAL' ? 'Bắt đầu Sinh tồn' : daily?.active ? 'Tiếp tục thử thách' : 'Chơi thử thách';

  return (
    <div className="mx-auto max-w-4xl space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">⚡ Dịch nhanh</h1>
        <p className="mt-1 text-sm text-slate-500">Xem nghĩa tiếng Việt, gõ từ tiếng Anh. Trả lời đúng liên tiếp để nhân điểm combo.</p>
      </div>

      {stats && <GameProgressCard progress={stats.progress} editable />}

      <Card>
        <h2 className="text-base font-semibold text-slate-900">Chọn chế độ</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Chế độ chơi">
          {MODES.map((m) => (
            <button
              key={m}
              type="button"
              role="radio"
              aria-checked={mode === m}
              onClick={() => onChoiceChange({ ...choice, mode: m })}
              className={clsx(
                'min-h-[44px] rounded-xl border p-3 text-left transition-colors',
                mode === m ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500' : 'border-slate-200 hover:bg-slate-50'
              )}
            >
              <span className="flex items-center justify-between">
                <span className="text-xl" aria-hidden>
                  {MODE_INFO[m].icon}
                </span>
                {m === 'SURVIVAL' && <Badge tone="purple">Premium</Badge>}
                {m === 'DAILY' && daily?.played && <Badge tone="green">Đã chơi</Badge>}
              </span>
              <span className="mt-1 block font-semibold text-slate-900">{MODE_INFO[m].title}</span>
              <span className="block text-xs text-slate-500">{MODE_INFO[m].text}</span>
            </button>
          ))}
        </div>

        {mode === 'SPRINT' && (
          <div className="mt-5">
            <h3 className="text-sm font-semibold text-slate-700">Nguồn từ</h3>
            <div className="mt-2 grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Nguồn từ">
              {SOURCES.map((s) => (
                <button
                  key={s.value}
                  type="button"
                  role="radio"
                  aria-checked={source === s.value}
                  onClick={() => onChoiceChange({ ...choice, mode, source: s.value })}
                  className={clsx(
                    'min-h-[44px] rounded-xl border p-3 text-left transition-colors',
                    source === s.value ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500' : 'border-slate-200 hover:bg-slate-50'
                  )}
                >
                  <span className="text-lg" aria-hidden>
                    {s.icon}
                  </span>
                  <span className="mt-1 block text-sm font-semibold text-slate-900">{s.title}</span>
                  <span className="block text-xs text-slate-500">{s.text}</span>
                </button>
              ))}
            </div>

            {source === 'DECK' && (
              <label className="mt-4 block text-sm">
                <span className="font-medium text-slate-700">Bộ từ</span>
                <select
                  className="mt-1 block min-h-[44px] w-full rounded-lg border border-slate-300 bg-white px-3 text-sm"
                  value={deckId ?? ''}
                  onChange={(e) => onChoiceChange({ ...choice, mode, deckId: e.target.value })}
                >
                  {deckList.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                      {d.level ? ` · ${d.level}` : ''} · {d.cardCount} từ{d.isProOnly ? ' · Pro' : ''}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {source === 'LEVEL' && (
              <div className="mt-4 flex flex-wrap gap-2" role="radiogroup" aria-label="Trình độ">
                {LEVELS.map((l) => (
                  <button
                    key={l}
                    type="button"
                    role="radio"
                    aria-checked={(choice.level ?? 'B1') === l}
                    onClick={() => onChoiceChange({ ...choice, mode, level: l })}
                    className={clsx(
                      'min-h-[44px] min-w-[56px] rounded-lg border px-3 text-sm font-semibold',
                      (choice.level ?? 'B1') === l ? 'border-brand-500 bg-brand-600 text-white' : 'border-slate-300 text-slate-700 hover:bg-slate-50'
                    )}
                  >
                    {l}
                  </button>
                ))}
              </div>
            )}

            {source === 'NOTEBOOK' && (
              <p className="mt-4 text-sm text-slate-500">
                Từ đến hạn ôn được hỏi trước. Kết quả sẽ cập nhật lịch ôn tập của các từ này. Ván chơi từ sổ tay không tính vào bảng
                xếp hạng.{' '}
                <Link href="/vocabulary/notebook" className="text-brand-600 hover:underline">
                  Mở sổ tay
                </Link>
              </p>
            )}
          </div>
        )}

        {mode === 'SURVIVAL' && (
          <div className="mt-5 text-sm text-slate-600">
            <p>Từ dễ đến khó: A1–A2 → B1 → B2 → C1. Sai, bỏ qua hoặc hết 15 giây đều mất 1 mạng. Hết 3 mạng là kết thúc.</p>
            {sub && !isPremium && (
              <p className="mt-2 rounded-lg bg-violet-50 px-3 py-2 text-violet-800">
                Chế độ Sinh tồn dành cho tài khoản Premium.{' '}
                <Link href="/subscription" className="font-semibold underline">
                  Nâng cấp Premium
                </Link>
              </p>
            )}
          </div>
        )}

        {mode === 'DAILY' && daily && (
          <div className="mt-5 text-sm text-slate-600" data-testid="daily-status">
            {daily.played ? (
              <p className="rounded-lg bg-green-50 px-3 py-2 text-green-800">
                Bạn đã chơi thử thách hôm nay: <b>{daily.score ?? 0} điểm</b>
                {daily.rank ? ` · hạng ${daily.rank}/${daily.totalPlayers}` : ''}. Quay lại vào ngày mai nhé!
              </p>
            ) : daily.active ? (
              <p>Bạn đang chơi dở thử thách hôm nay. Thời gian của từ đang hỏi vẫn tiếp tục chạy.</p>
            ) : (
              <p>Mọi người cùng nhận 10 từ (3 dễ, 3 trung bình, 4 khó). Chỉ có 1 lượt – hãy tập trung!</p>
            )}
          </div>
        )}

        <Button size="lg" className="mt-5 min-h-[48px] w-full sm:w-auto" onClick={start} loading={starting} disabled={blocked}>
          {startLabel}
        </Button>
      </Card>

      <div className="grid gap-5 md:grid-cols-2">
        <GameLeaderboard initialBoard={mode === 'SURVIVAL' ? 'survival-week' : mode === 'SPRINT' ? 'sprint-week' : 'daily'} />
        {stats && <BadgeGrid badges={stats.badges} />}
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <Card>
          <h2 className="text-base font-semibold text-slate-900">Cách tính điểm</h2>
          <ul className="mt-3 space-y-2 text-sm text-slate-600">
            <li>
              Điểm gốc theo độ khó: <b>A1–A2 10</b> · <b>B1 15</b> · <b>B2 20</b> · <b>C1 30</b>
            </li>
            <li>
              🔥 Combo đúng liên tiếp: <b>5 câu ×1.5</b> · <b>10 câu ×2</b> · <b>20 câu ×3</b>. Sai hoặc bỏ qua thì combo về 0.
            </li>
            <li>⚡ Trả lời dưới 3 giây +5, dưới 6 giây +2 (khi không dùng gợi ý).</li>
            <li>💡 Gợi ý: lần 1 ×0.8 · lần 2 ×0.5 · lần 3 ×0.25.</li>
            <li>🟡 Gõ sai 1 chữ cái: được nửa số điểm và vẫn giữ combo.</li>
            <li>Từ đồng nghĩa có trong hệ thống cũng được tính đúng.</li>
            <li>🏆 Điểm mỗi ván cộng vào XP để lên cấp; đạt mục tiêu ngày được tính vào chuỗi ngày học.</li>
          </ul>
          <p className="mt-3 flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <span>Phím tắt:</span>
            <KbdCombo keys={['Enter']} /> nộp ·
            <KbdCombo keys={comboToLabels(keys('game.hint')[0] ?? '')} /> gợi ý ·
            <KbdCombo keys={comboToLabels(keys('game.skip')[0] ?? '')} /> bỏ qua
            <Link href="/profile/shortcuts" className="text-brand-600 hover:underline">
              Đổi phím
            </Link>
          </p>
        </Card>

        <Card>
          <h2 className="text-base font-semibold text-slate-900">Kỷ lục của bạn</h2>
          {stats && stats.gamesPlayed > 0 ? (
            <>
              <p className="mt-1 text-sm text-slate-500">
                {stats.gamesPlayed} ván · tổng {stats.totalScore.toLocaleString('vi-VN')} điểm
              </p>
              <ul className="mt-3 divide-y divide-slate-100">
                {stats.bests.slice(0, 6).map((b) => (
                  <li key={`${b.mode}-${b.sourceKey}`} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="truncate text-slate-700">
                      <span aria-hidden>{MODE_INFO[b.mode].icon} </span>
                      {b.sourceLabel}
                    </span>
                    <span className="flex shrink-0 items-center gap-2">
                      <Badge tone="amber">🔥 {b.bestCombo}</Badge>
                      <span className="font-semibold text-slate-900">{b.score}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="mt-3 text-sm text-slate-500">Chưa có ván nào. Chơi ván đầu tiên để lập kỷ lục!</p>
          )}
        </Card>
      </div>
    </div>
  );
}
