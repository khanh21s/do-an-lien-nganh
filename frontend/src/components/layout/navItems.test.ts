import { describe, expect, it } from 'vitest';
import {
  MAIN_NAV,
  MOBILE_TABS,
  allNavHrefs,
  findActiveHref,
  flattenNav,
  isPracticeRoute,
  localDateKey,
  moreDrawerEntries,
  shouldShowReviewReminder,
} from './navItems';

describe('findActiveHref (NAV-03)', () => {
  const hrefs = allNavHrefs();

  it('highlights the deepest matching item', () => {
    expect(findActiveHref('/vocabulary/notebook', hrefs)).toBe('/vocabulary/notebook');
    expect(findActiveHref('/vocabulary/review', hrefs)).toBe('/vocabulary/review');
    expect(findActiveHref('/vocabulary', hrefs)).toBe('/vocabulary');
    expect(findActiveHref('/vocabulary/decks/42', hrefs)).toBe('/vocabulary');
  });

  it('matches child routes but not lookalike prefixes', () => {
    expect(findActiveHref('/lessons/1/dictation/2', hrefs)).toBe('/lessons');
    expect(findActiveHref('/lessons/1/result', hrefs)).toBe('/lessons');
    expect(findActiveHref('/profiles', hrefs)).toBeNull();
    expect(findActiveHref(null, hrefs)).toBeNull();
  });

  it('has no admin entries in the learner navigation (T14)', () => {
    expect(hrefs.some((h) => h.startsWith('/admin'))).toBe(false);
    expect(findActiveHref('/admin/lessons', hrefs)).toBeNull();
  });
});

describe('navigation coverage (NAV-01)', () => {
  it('every (app) page is reachable from the tab bar or the drawer', () => {
    const reachable = new Set([
      ...MOBILE_TABS.map((t) => t.href),
      ...flattenNav(moreDrawerEntries()).map((l) => l.href),
    ]);
    for (const href of flattenNav(MAIN_NAV).map((l) => l.href)) {
      expect(reachable.has(href)).toBe(true);
    }
  });

  it('drawer has no duplicates of tabs and no admin entries', () => {
    const tabHrefs = MOBILE_TABS.map((t) => t.href);
    const drawer = flattenNav(moreDrawerEntries()).map((l) => l.href);
    expect(drawer.some((h) => tabHrefs.includes(h))).toBe(false);
    expect(drawer.some((h) => h.startsWith('/admin'))).toBe(false);
    expect(MOBILE_TABS).toHaveLength(4);
  });
});

describe('isPracticeRoute', () => {
  it('detects dictation/shadowing screens only', () => {
    expect(isPracticeRoute('/lessons/abc/dictation/s1')).toBe(true);
    expect(isPracticeRoute('/lessons/abc/shadowing/s1')).toBe(true);
    expect(isPracticeRoute('/lessons/abc')).toBe(false);
    expect(isPracticeRoute('/lessons/abc/result')).toBe(false);
    expect(isPracticeRoute('/dashboard')).toBe(false);
    expect(isPracticeRoute(null)).toBe(false);
  });
});

describe('shouldShowReviewReminder (NAV-07)', () => {
  const today = '2026-10-13';

  it('shows once per day on the dashboard when cards are due', () => {
    expect(shouldShowReviewReminder({ pathname: '/dashboard', dueCount: 3, lastShownDate: null, today })).toBe(true);
    expect(shouldShowReviewReminder({ pathname: '/dashboard', dueCount: 3, lastShownDate: '2026-10-12', today })).toBe(
      true
    );
    expect(shouldShowReviewReminder({ pathname: '/dashboard', dueCount: 3, lastShownDate: today, today })).toBe(false);
  });

  it('stays hidden elsewhere or with nothing due', () => {
    expect(shouldShowReviewReminder({ pathname: '/lessons', dueCount: 3, lastShownDate: null, today })).toBe(false);
    expect(shouldShowReviewReminder({ pathname: '/dashboard', dueCount: 0, lastShownDate: null, today })).toBe(false);
    expect(shouldShowReviewReminder({ pathname: '/dashboard', dueCount: undefined, lastShownDate: null, today })).toBe(
      false
    );
  });

  it('localDateKey formats the local calendar day', () => {
    expect(localDateKey(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05');
  });
});
