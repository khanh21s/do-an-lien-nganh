'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { KbdCombo } from '@/components/ui/Kbd';
import { comboToLabels } from '@/hooks/useHotkeys';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { gameApi } from '@/lib/endpoints/game';
import { qk } from '@/lib/queryKeys';
import type { CefrLevel, GameSource, StartGameInput } from '@/lib/types';
import { useShortcutKeys } from '@/store/shortcut-store';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import Link from 'next/link';

const LEVELS: CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1'];

const SOURCES: { value: GameSource; icon: string; title: string; text: string }[] = [
  { value: 'DECK', icon: '🗂️', title: 'Bộ từ', text: 'Từ trong một bộ từ vựng' },
  { value: 'LEVEL', icon: '🎯', title: 'Theo trình độ', text: 'Ngẫu nhiên theo cấp CEFR' },
  { value: 'NOTEBOOK', icon: '📓', title: 'Từ của tôi', text: 'Sổ tay, từ đến hạn ôn trước' },
];

/** Lobby: pick where the words come from, see personal bests and the scoring rules. */
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
  const { data: decks } = useQuery({ queryKey: qk.decks({ limit: 100 }), queryFn: () => vocabularyApi.listDecks({ limit: 100 }) });
  const { data: stats } = useQuery({ queryKey: qk.gameStats, queryFn: gameApi.stats });

  const deckList = decks?.data ?? [];
  const deckId = choice.deckId ?? deckList.find((d) => !d.isProOnly)?.id;
  const ready = choice.source !== 'DECK' || !!deckId;

  function start() {
    onStart({
      source: choice.source,
      deckId: choice.source === 'DECK' ? deckId : undefined,
      level: choice.source === 'LEVEL' ? (choice.level ?? 'B1') : undefined,
    });
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">⚡ Dịch nhanh</h1>
        <p className="mt-1 text-sm text-slate-500">
          Xem nghĩa tiếng Việt, gõ từ tiếng Anh. 60 giây, trả lời đúng liên tiếp để nhân điểm combo.
        </p>
      </div>

      <Card>
        <h2 className="text-base font-semibold text-slate-900">Chọn nguồn từ</h2>
        <div className="mt-3 grid gap-3 sm:grid-cols-3" role="radiogroup" aria-label="Nguồn từ">
          {SOURCES.map((s) => (
            <button
              key={s.value}
              type="button"
              role="radio"
              aria-checked={choice.source === s.value}
              onClick={() => onChoiceChange({ ...choice, source: s.value })}
              className={clsx(
                'min-h-[44px] rounded-xl border p-3 text-left transition-colors',
                choice.source === s.value ? 'border-brand-500 bg-brand-50 ring-1 ring-brand-500' : 'border-slate-200 hover:bg-slate-50'
              )}
            >
              <span className="text-xl" aria-hidden>
                {s.icon}
              </span>
              <span className="mt-1 block font-semibold text-slate-900">{s.title}</span>
              <span className="block text-xs text-slate-500">{s.text}</span>
            </button>
          ))}
        </div>

        {choice.source === 'DECK' && (
          <label className="mt-4 block text-sm">
            <span className="font-medium text-slate-700">Bộ từ</span>
            <select
              className="mt-1 block min-h-[44px] w-full rounded-lg border border-slate-300 bg-white px-3 text-sm"
              value={deckId ?? ''}
              onChange={(e) => onChoiceChange({ ...choice, deckId: e.target.value })}
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

        {choice.source === 'LEVEL' && (
          <div className="mt-4 flex flex-wrap gap-2" role="radiogroup" aria-label="Trình độ">
            {LEVELS.map((l) => (
              <button
                key={l}
                type="button"
                role="radio"
                aria-checked={(choice.level ?? 'B1') === l}
                onClick={() => onChoiceChange({ ...choice, level: l })}
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

        {choice.source === 'NOTEBOOK' && (
          <p className="mt-4 text-sm text-slate-500">
            Từ đến hạn ôn được hỏi trước. Kết quả sẽ cập nhật lịch ôn tập của các từ này.{' '}
            <Link href="/vocabulary/notebook" className="text-brand-600 hover:underline">
              Mở sổ tay
            </Link>
          </p>
        )}

        <Button size="lg" className="mt-5 min-h-[48px] w-full sm:w-auto" onClick={start} loading={starting} disabled={!ready}>
          Bắt đầu (60 giây)
        </Button>
      </Card>

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
                  <li key={b.sourceKey} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="truncate text-slate-700">{b.sourceLabel}</span>
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
