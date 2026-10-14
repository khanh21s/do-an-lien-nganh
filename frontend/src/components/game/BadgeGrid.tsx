import { Card } from '@/components/ui/Card';
import type { GameBadge } from '@/lib/types';
import clsx from 'clsx';

/** The badge catalogue (T16): earned ones in colour, the rest greyed out with how to get them. */
export function BadgeGrid({ badges }: { badges: GameBadge[] }) {
  const earned = badges.filter((b) => b.earnedAt).length;
  return (
    <Card>
      <h2 className="text-base font-semibold text-slate-900">
        Huy hiệu <span className="text-sm font-normal text-slate-500">({earned}/{badges.length})</span>
      </h2>
      <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-3" data-testid="badges">
        {badges.map((b) => (
          <li
            key={b.code}
            title={b.description}
            className={clsx(
              'flex flex-col items-center rounded-xl border px-1.5 py-2 text-center',
              b.earnedAt ? 'border-amber-200 bg-amber-50' : 'border-slate-200 bg-slate-50 opacity-60 grayscale'
            )}
            data-earned={b.earnedAt ? 'true' : 'false'}
          >
            <span className="text-2xl" aria-hidden>
              {b.icon}
            </span>
            <span className="mt-1 text-xs font-semibold text-slate-800">{b.title}</span>
            <span className="mt-0.5 text-[11px] leading-tight text-slate-500">{b.description}</span>
          </li>
        ))}
      </ul>
    </Card>
  );
}
