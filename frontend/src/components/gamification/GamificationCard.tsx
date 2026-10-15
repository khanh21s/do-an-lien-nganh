'use client';

import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import {
  XP_RULES,
  XP_RULES_NOTE,
  checkInHint,
  checkInLabel,
  formatCount,
  levelPercent,
  missionPercent,
  missionsDone,
} from '@/lib/gamification';
import type { GamificationMission } from '@/lib/types';
import clsx from 'clsx';
import Link from 'next/link';
import { useState } from 'react';
import { useCheckIn, useGamificationMe } from './useGamification';

/** P2-B dashboard card: level bar, XP, diamonds, daily check-in and the 3 daily missions. Hidden on error. */
export function GamificationCard() {
  const { data: me } = useGamificationMe();
  const checkIn = useCheckIn();
  const [showRules, setShowRules] = useState(false);
  if (!me) return null;
  const done = missionsDone(me.missions);

  return (
    <Card id="gamification" className="scroll-mt-20" data-testid="gamification-card">
      <div className="grid gap-5 md:grid-cols-2">
        <div className="flex flex-col gap-4">
          <div>
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-sm font-semibold text-slate-900">
                <span aria-hidden>🏆 </span>Cấp {me.level}
              </span>
              <span className="text-xs tabular-nums text-slate-500">
                {formatCount(me.levelXp)}/{formatCount(me.levelXpNeeded)} XP
              </span>
            </div>
            <Bar value={levelPercent(me)} className="bg-violet-500" label={`Tiến độ cấp ${me.level}`} />
            <p className="mt-1 text-xs text-slate-500">
              Tổng {formatCount(me.xp)} XP · còn {formatCount(me.levelXpNeeded - me.levelXp)} XP để lên cấp {me.level + 1}
            </p>
          </div>

          <div className="grid grid-cols-3 gap-2 text-center">
            <Stat label="XP hôm nay" value={`+${formatCount(me.todayXp)}`} tint="text-violet-700" />
            <Stat label="XP tuần này" value={formatCount(me.weekXp)} tint="text-brand-700" />
            <Stat label="Kim cương" value={`💎 ${formatCount(me.diamonds)}`} tint="text-sky-700" />
          </div>

          <div className="flex flex-col gap-2 rounded-xl bg-amber-50 p-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <p className="text-sm font-medium text-amber-900">
                <span aria-hidden>📅 </span>Điểm danh hằng ngày
                {me.checkIn.streak > 0 && <span className="ml-1 text-xs text-amber-700">· chuỗi {me.checkIn.streak} ngày</span>}
              </p>
              <p className="text-xs text-amber-800">{checkInHint(me.checkIn)}</p>
            </div>
            <Button
              variant={me.checkIn.checkedInToday ? 'secondary' : 'warning'}
              className="min-h-[44px] shrink-0"
              disabled={me.checkIn.checkedInToday}
              loading={checkIn.isPending}
              onClick={() => checkIn.mutate()}
              data-testid="check-in"
            >
              {checkInLabel(me.checkIn)}
            </Button>
          </div>
        </div>

        <div>
          <div className="flex items-baseline justify-between gap-2">
            <h2 className="text-sm font-semibold text-slate-900">
              <span aria-hidden>🎯 </span>Nhiệm vụ hôm nay
            </h2>
            <span className="text-xs text-slate-500">
              {done}/{me.missions.length} xong · +{me.missions[0]?.reward ?? 30} 💎 mỗi nhiệm vụ
            </span>
          </div>
          <ul className="mt-2 flex flex-col gap-2" data-testid="missions">
            {me.missions.map((m) => (
              <MissionRow key={m.code} mission={m} />
            ))}
          </ul>
          <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
            <button
              type="button"
              className="min-h-[44px] text-xs text-slate-500 underline-offset-2 hover:underline"
              onClick={() => setShowRules((s) => !s)}
              aria-expanded={showRules}
            >
              Cách nhận XP
            </button>
            <Link href="/games?board=xp-week" className="min-h-[44px] content-center text-sm font-medium text-brand-700 hover:underline">
              Bảng xếp hạng XP tuần →
            </Link>
          </div>
          {showRules && (
            <div className="rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <ul className="flex flex-col gap-1">
                {XP_RULES.map((r) => (
                  <li key={r.label} className="flex justify-between gap-2">
                    <span>{r.label}</span>
                    <span className="font-medium text-slate-800">{r.xp}</span>
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-slate-500">{XP_RULES_NOTE}</p>
            </div>
          )}
        </div>
      </div>
    </Card>
  );
}

function MissionRow({ mission }: { mission: GamificationMission }) {
  return (
    <li
      className={clsx('rounded-xl border p-3', mission.completed ? 'border-green-200 bg-green-50' : 'border-slate-100')}
      data-testid={`mission-${mission.code}`}
    >
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="min-w-0 truncate text-slate-800">
          <span aria-hidden>{mission.icon} </span>
          {mission.title}
        </span>
        <span className={clsx('shrink-0 text-xs font-semibold', mission.completed ? 'text-green-700' : 'text-sky-700')}>
          {mission.completed ? '✓ Đã nhận' : `+${mission.reward} 💎`}
        </span>
      </div>
      <div className="mt-2 flex items-center gap-2">
        <Bar
          value={missionPercent(mission)}
          className={mission.completed ? 'bg-green-500' : 'bg-brand-500'}
          label={mission.title}
          inline
        />
        <span className="w-12 shrink-0 text-right text-xs tabular-nums text-slate-500">
          {mission.progress}/{mission.target}
        </span>
      </div>
    </li>
  );
}

function Stat({ label, value, tint }: { label: string; value: string; tint: string }) {
  return (
    <div className="rounded-xl bg-slate-50 px-2 py-2">
      <p className={clsx('truncate text-base font-bold tabular-nums', tint)}>{value}</p>
      <p className="text-[11px] text-slate-500">{label}</p>
    </div>
  );
}

function Bar({ value, className, label, inline }: { value: number; className: string; label: string; inline?: boolean }) {
  return (
    <div
      className={clsx('h-2.5 w-full overflow-hidden rounded-full bg-slate-100', !inline && 'mt-2')}
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
