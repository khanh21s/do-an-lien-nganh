import { describe, expect, it } from 'vitest';
import type { Plan } from '@/lib/types';
import {
  bestValuePlanId,
  durationLabel,
  formatDate,
  formatVnd,
  planFeatures,
  premiumFeatures,
  pricePerMonth,
} from './format';

const plan = (id: string, price: number, durationDays: number, features: Plan['features'] = []): Plan => ({
  id,
  name: id,
  price,
  durationDays,
  features,
  priceDisplay: '',
});

describe('formatVnd', () => {
  it('formats the three Phase 1 prices', () => {
    expect(formatVnd(79000)).toBe('79.000đ');
    expect(formatVnd(169000)).toBe('169.000đ');
    expect(formatVnd(499000)).toBe('499.000đ');
  });
  it('handles small and large numbers', () => {
    expect(formatVnd(0)).toBe('0đ');
    expect(formatVnd(999)).toBe('999đ');
    expect(formatVnd(1849000)).toBe('1.849.000đ');
  });
});

describe('durationLabel', () => {
  it('maps plan durations to Vietnamese labels', () => {
    expect(durationLabel(30)).toBe('1 tháng');
    expect(durationLabel(90)).toBe('3 tháng');
    expect(durationLabel(365)).toBe('1 năm');
    expect(durationLabel(7)).toBe('7 ngày');
  });
});

describe('formatDate', () => {
  it('formats dd/mm/yyyy and tolerates bad input', () => {
    expect(formatDate('2027-01-05T10:00:00')).toBe('05/01/2027');
    expect(formatDate(null)).toBe('—');
    expect(formatDate('nope')).toBe('—');
  });
});

describe('plan helpers', () => {
  const plans = [
    plan('plan-1month', 79000, 30, ['A', 'B']),
    plan('plan-3month', 169000, 90, ['A', 'B', 'C']),
    plan('plan-1year', 499000, 365, ['A', 'D']),
  ];

  it('picks the cheapest monthly plan as best value', () => {
    expect(bestValuePlanId(plans)).toBe('plan-1year');
    expect(bestValuePlanId([plans[0]])).toBeNull();
  });

  it('computes the monthly equivalent', () => {
    expect(pricePerMonth(plans[1])).toBe(56000);
    expect(pricePerMonth(plans[2])).toBe(41000);
  });

  it('unions features in first-seen order', () => {
    expect(premiumFeatures(plans)).toEqual(['A', 'B', 'C', 'D']);
  });

  it('accepts non-array feature JSON', () => {
    expect(planFeatures(plan('x', 1, 30, null))).toEqual([]);
    expect(planFeatures(plan('x', 1, 30, { proDecks: true, ads: false, note: 'Hỗ trợ' }))).toEqual([
      'proDecks',
      'Hỗ trợ',
    ]);
  });
});
