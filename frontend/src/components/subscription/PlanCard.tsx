import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import type { Plan } from '@/lib/types';
import clsx from 'clsx';
import { durationLabel, formatVnd, planFeatures, pricePerMonth } from './format';

/** One Premium plan (used by `/pricing` and `/subscription`). Prices always come from the BE plan. */
export function PlanCard({
  plan,
  highlight,
  actionLabel,
  onAction,
  loading,
  disabled,
  showFeatures = true,
}: {
  plan: Plan;
  highlight?: boolean;
  actionLabel: string;
  onAction: () => void;
  loading?: boolean;
  disabled?: boolean;
  showFeatures?: boolean;
}) {
  const features = showFeatures ? planFeatures(plan) : [];
  const monthly = pricePerMonth(plan);
  const isMonthly = plan.durationDays <= 31;

  return (
    <Card
      className={clsx(
        'relative flex flex-col',
        highlight && 'border-2 border-violet-400 shadow-md ring-1 ring-violet-200'
      )}
    >
      {highlight && (
        <Badge tone="purple" className="absolute -top-3 left-1/2 -translate-x-1/2 whitespace-nowrap">
          Tiết kiệm nhất
        </Badge>
      )}
      <h3 className="text-base font-semibold text-slate-900">{plan.name}</h3>
      <p className="text-sm text-slate-500">Premium {durationLabel(plan.durationDays)}</p>
      <p className="mt-3 text-3xl font-bold text-brand-600">{formatVnd(plan.price)}</p>
      <p className="mt-1 min-h-[1.25rem] text-xs text-slate-500">
        {isMonthly ? 'Thanh toán 1 lần' : `≈ ${formatVnd(monthly)}/tháng · thanh toán 1 lần`}
      </p>
      {features.length > 0 && (
        <ul className="mt-4 flex-1 space-y-1.5 text-sm text-slate-600">
          {features.map((f) => (
            <li key={f} className="flex gap-2">
              <span className="text-green-600" aria-hidden>
                ✓
              </span>
              <span>{f}</span>
            </li>
          ))}
        </ul>
      )}
      <Button
        variant={highlight ? 'pro' : 'primary'}
        className="mt-5 min-h-[44px] w-full"
        onClick={onAction}
        loading={loading}
        disabled={disabled}
      >
        {actionLabel}
      </Button>
    </Card>
  );
}
