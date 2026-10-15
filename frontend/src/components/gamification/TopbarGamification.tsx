'use client';

import { formatCount } from '@/lib/gamification';
import Link from 'next/link';
import { useGainToasts, useGamificationMe } from './useGamification';

/** P2-B topbar chips: level and diamonds. Also the single place that toasts XP/diamond gains. */
export function TopbarGamification() {
  const { data: me } = useGamificationMe();
  useGainToasts(me);
  if (!me) return null;
  return (
    <Link
      href="/dashboard#gamification"
      className="flex h-9 items-center gap-1.5 rounded-full bg-violet-50 px-2.5 text-sm font-semibold text-violet-700"
      title={`Cấp ${me.level} · ${formatCount(me.xp)} XP · ${formatCount(me.diamonds)} kim cương`}
      aria-label={`Cấp ${me.level}, ${me.diamonds} kim cương`}
      data-testid="topbar-gamification"
    >
      <span>
        <span aria-hidden>🏆</span> {me.level}
      </span>
      <span className="hidden text-sky-700 min-[420px]:inline">
        <span aria-hidden>💎</span> {formatCount(me.diamonds)}
      </span>
    </Link>
  );
}
