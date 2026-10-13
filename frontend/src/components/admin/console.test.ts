import { describe, expect, it } from 'vitest';
import { flattenNav } from '@/components/layout/navItems';
import { ADMIN_CONSOLE_NAV, adminNavHrefs } from './adminNav';
import { findActiveHref } from '@/components/layout/navItems';
import {
  buildRevenueChart,
  buildTransactionParams,
  buildUserListParams,
  deckDraftToUpdate,
  deckToDraft,
  formatCompactVnd,
  lockAction,
  niceCeil,
  parseFeatures,
  planDraftToInput,
  planToggleInput,
  premiumByUser,
  shortDate,
  validateDateRange,
  validateDeckDraft,
  validatePlanDraft,
  EMPTY_PLAN_DRAFT,
} from './console';
import type { AdminPlan } from '@/lib/types';

const G = { width: 300, height: 120, padLeft: 40, padRight: 10, padTop: 10, padBottom: 20 };

describe('admin console nav', () => {
  it('has every console page and resolves the deepest active item', () => {
    const hrefs = flattenNav(ADMIN_CONSOLE_NAV).map((l) => l.href);
    for (const h of [
      '/admin',
      '/admin/users',
      '/admin/lessons',
      '/admin/vocabulary',
      '/admin/plans',
      '/admin/transactions',
      '/admin/activation-codes',
      '/admin/account',
    ]) {
      expect(hrefs).toContain(h);
    }
    expect(findActiveHref('/admin', adminNavHrefs())).toBe('/admin');
    expect(findActiveHref('/admin/vocabulary/abc', adminNavHrefs())).toBe('/admin/vocabulary');
    expect(findActiveHref('/admin/lessons/1', adminNavHrefs())).toBe('/admin/lessons');
  });
});

describe('filters → query params', () => {
  it('users: trims q, omits it when blank, keeps status', () => {
    expect(buildUserListParams({ page: 2, q: '  an@x ', status: 'locked' })).toEqual({
      page: 2,
      limit: 20,
      status: 'locked',
      q: 'an@x',
    });
    expect(buildUserListParams({ page: 0, q: '   ', status: 'all' })).toEqual({ page: 1, limit: 20, status: 'all' });
  });

  it('transactions: only sends the filters that are set', () => {
    expect(
      buildTransactionParams({ page: 1, status: 'all', method: '', q: '', from: '', to: '' })
    ).toEqual({ page: 1, limit: 20, status: 'all' });
    expect(
      buildTransactionParams({
        page: 3,
        status: 'success',
        method: 'vnpay',
        q: ' TX1 ',
        from: '2026-10-01',
        to: '2026-10-13',
      })
    ).toEqual({ page: 3, limit: 20, status: 'success', method: 'vnpay', q: 'TX1', from: '2026-10-01', to: '2026-10-13' });
    expect(buildTransactionParams({ page: 1, status: 'all', method: '', q: '', from: '2026-1', to: '' })).not.toHaveProperty(
      'from'
    );
  });

  it('validates the date range', () => {
    expect(validateDateRange('', '')).toBeNull();
    expect(validateDateRange('2026-10-01', '')).toBeNull();
    expect(validateDateRange('2026-10-01', '2026-10-01')).toBeNull();
    expect(validateDateRange('2026-10-02', '2026-10-01')).toMatch(/trước/);
  });
});

describe('lockAction', () => {
  it('disables locking admins and self, allows unlocking', () => {
    expect(lockAction({ id: 'u1', isActive: true, isAdmin: false }, 'me')).toMatchObject({
      label: 'Khoá',
      nextActive: false,
      disabled: false,
    });
    expect(lockAction({ id: 'a1', isActive: true, isAdmin: true }, 'me').disabled).toBe(true);
    expect(lockAction({ id: 'me', isActive: true, isAdmin: false }, 'me').disabled).toBe(true);
    expect(lockAction({ id: 'a1', isActive: false, isAdmin: true }, 'me')).toMatchObject({
      label: 'Mở khoá',
      nextActive: true,
      disabled: false,
    });
  });

  it('premiumByUser indexes by userId', () => {
    const m = premiumByUser([{ userId: 'u1', premium: true, planId: 'p', endAt: null }]);
    expect(m.get('u1')?.premium).toBe(true);
    expect(premiumByUser(undefined).size).toBe(0);
  });
});

describe('revenue chart scaling', () => {
  it('niceCeil rounds up to 1/2/2.5/5 × 10^k', () => {
    expect(niceCeil(0)).toBe(0);
    expect(niceCeil(79_000)).toBe(100_000);
    expect(niceCeil(169_000)).toBe(200_000);
    expect(niceCeil(210_000)).toBe(250_000);
    expect(niceCeil(499_000)).toBe(500_000);
    expect(niceCeil(500_000)).toBe(500_000);
    expect(niceCeil(1_200_000)).toBe(2_000_000);
  });

  it('scales bars inside the plot area with the tallest under the nice max', () => {
    const days = [
      { date: '2026-10-11', amount: 0 },
      { date: '2026-10-12', amount: 79_000 },
      { date: '2026-10-13', amount: 169_000 },
    ];
    const chart = buildRevenueChart(days, G);
    const innerH = G.height - G.padTop - G.padBottom;
    const baseY = G.padTop + innerH;
    expect(chart.max).toBe(200_000);
    expect(chart.total).toBe(248_000);
    expect(chart.bars).toHaveLength(3);
    expect(chart.bars[0].height).toBe(0);
    expect(chart.bars[2].height).toBeCloseTo((169_000 / 200_000) * innerH);
    for (const b of chart.bars) {
      expect(b.y + b.height).toBeCloseTo(baseY);
      expect(b.x).toBeGreaterThanOrEqual(G.padLeft);
      expect(b.x + b.width).toBeLessThanOrEqual(G.width - G.padRight + 1e-9);
    }
    expect(chart.ticks.map((t) => t.value)).toEqual([0, 50_000, 100_000, 150_000, 200_000]);
    expect(chart.ticks[chart.ticks.length - 1].y).toBeCloseTo(G.padTop);
  });

  it('handles an all-zero or empty series without NaN', () => {
    const zero = buildRevenueChart(
      Array.from({ length: 30 }, (_, i) => ({ date: `2026-09-${String(i + 1).padStart(2, '0')}`, amount: 0 })),
      G
    );
    expect(zero.max).toBe(0);
    expect(zero.bars.every((b) => b.height === 0 && Number.isFinite(b.x))).toBe(true);
    expect(zero.ticks).toHaveLength(1);
    expect(buildRevenueChart([], G).bars).toEqual([]);
    expect(buildRevenueChart([{ date: 'x', amount: -5 }], G).bars[0].height).toBe(0);
  });

  it('formats axis labels and dates', () => {
    expect(formatCompactVnd(0)).toBe('0');
    expect(formatCompactVnd(250_000)).toBe('250k');
    expect(formatCompactVnd(1_500_000)).toBe('1,5tr');
    expect(formatCompactVnd(2_000_000_000)).toBe('2tỷ');
    expect(shortDate('2026-10-03')).toBe('03/10');
  });
});

describe('plan form', () => {
  it('validates like subscription-service', () => {
    expect(validatePlanDraft(EMPTY_PLAN_DRAFT, { isNew: true })).toMatchObject({
      id: expect.any(String),
      name: expect.any(String),
      price: expect.any(String),
    });
    const ok = { id: 'premium-2y', name: 'Premium 2 năm', durationDays: '730', price: '899000', features: '', isActive: true };
    expect(validatePlanDraft(ok, { isNew: true })).toEqual({});
    expect(validatePlanDraft({ ...ok, id: 'Bad ID' }, { isNew: true }).id).toBeDefined();
    expect(validatePlanDraft({ ...ok, id: 'Bad ID' }, { isNew: false }).id).toBeUndefined();
    expect(validatePlanDraft({ ...ok, price: '999' }, { isNew: false }).price).toBeDefined();
    expect(validatePlanDraft({ ...ok, durationDays: '3651' }, { isNew: false }).durationDays).toBeDefined();
    expect(validatePlanDraft({ ...ok, durationDays: '1.5' }, { isNew: false }).durationDays).toBeDefined();
  });

  it('converts drafts and toggles', () => {
    expect(parseFeatures(' A \n\n B\n')).toEqual(['A', 'B']);
    expect(
      planDraftToInput({ id: ' p1 ', name: ' N ', durationDays: '30', price: '79000', features: 'x\ny', isActive: false })
    ).toEqual({ id: 'p1', name: 'N', durationDays: 30, price: 79000, features: ['x', 'y'], isActive: false });
    const plan: AdminPlan = {
      id: 'p',
      name: 'P',
      durationDays: 30,
      price: 79000,
      features: null,
      isActive: true,
      activeSubscribers: 2,
      createdAt: null,
      updatedAt: null,
    };
    expect(planToggleInput(plan)).toEqual({ name: 'P', durationDays: 30, price: 79000, features: [], isActive: false });
  });
});

describe('deck form', () => {
  it('maps blanks to null and validates the name', () => {
    expect(deckDraftToUpdate({ name: ' D ', description: '  ', level: '', isProOnly: true })).toEqual({
      name: 'D',
      description: null,
      level: null,
      isProOnly: true,
    });
    expect(deckToDraft({ name: 'D', description: null, level: 'B1', isProOnly: false })).toEqual({
      name: 'D',
      description: '',
      level: 'B1',
      isProOnly: false,
    });
    expect(validateDeckDraft({ name: ' ', description: '', level: '', isProOnly: false })).not.toBeNull();
    expect(validateDeckDraft({ name: 'x'.repeat(256), description: '', level: '', isProOnly: false })).not.toBeNull();
    expect(validateDeckDraft({ name: 'ok', description: '', level: '', isProOnly: false })).toBeNull();
  });
});
