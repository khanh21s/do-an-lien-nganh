/**
 * Single source of truth for app navigation (spec 3.9). Sidebar, MobileTabBar and MoreDrawer all
 * render from these definitions, so a route only has to be added here once.
 * Kept free of React/API imports so the helpers can be unit tested in a node environment.
 */

export type NavBadge = 'reviewDue';

export interface NavLeaf {
  kind: 'leaf';
  href: string;
  label: string;
  /** Short label for the mobile tab bar. */
  shortLabel?: string;
  icon: string;
  badge?: NavBadge;
}

export interface NavGroup {
  kind: 'group';
  key: string;
  label: string;
  icon: string;
  children: NavLeaf[];
}

export type NavEntry = NavLeaf | NavGroup;

function leaf(href: string, label: string, icon: string, extra: Partial<NavLeaf> = {}): NavLeaf {
  return { kind: 'leaf', href, label, icon, ...extra };
}

export const NAV_DASHBOARD = leaf('/dashboard', 'Dashboard', '🏠', { shortLabel: 'Tổng quan' });
export const NAV_LESSONS = leaf('/lessons', 'Bài học', '📚');
export const NAV_DECKS = leaf('/vocabulary', 'Bộ từ', '🗂️');
export const NAV_REVIEW = leaf('/vocabulary/review', 'Ôn tập', '🧠', { badge: 'reviewDue' });
export const NAV_NOTEBOOK = leaf('/vocabulary/notebook', 'Sổ tay', '📓');
export const NAV_HISTORY = leaf('/practice/history', 'Lịch sử luyện tập', '📈', { shortLabel: 'Lịch sử' });
export const NAV_PREMIUM = leaf('/subscription', 'Gói Premium', '💎');
export const NAV_PROFILE = leaf('/profile', 'Hồ sơ', '👤');

export const MAIN_NAV: NavEntry[] = [
  NAV_DASHBOARD,
  NAV_LESSONS,
  { kind: 'group', key: 'vocabulary', label: 'Từ vựng', icon: '🔤', children: [NAV_DECKS, NAV_REVIEW, NAV_NOTEBOOK] },
  NAV_HISTORY,
  NAV_PREMIUM,
  NAV_PROFILE,
];

/** ADM-01: only rendered when `profile.isAdmin`. */
export const ADMIN_NAV: NavGroup = {
  kind: 'group',
  key: 'admin',
  label: 'Quản trị',
  icon: '🛠️',
  children: [
    leaf('/admin/lessons', 'Bài học', '🎧'),
    leaf('/admin/vocabulary', 'Từ vựng', '📝'),
    leaf('/admin/activation-codes', 'Mã kích hoạt', '🎟️'),
  ],
};

/** NAV-01: the four fixed tabs; the fifth tab ("Thêm") opens the MoreDrawer. */
export const MOBILE_TABS: NavLeaf[] = [NAV_DASHBOARD, NAV_LESSONS, NAV_REVIEW, NAV_HISTORY];

/** Entries shown in the "Thêm" drawer: everything not already a tab (admin appended when allowed). */
export function moreDrawerEntries(isAdmin: boolean): NavEntry[] {
  const tabHrefs = new Set(MOBILE_TABS.map((t) => t.href));
  const entries: NavEntry[] = [];
  for (const entry of MAIN_NAV) {
    if (entry.kind === 'leaf') {
      if (!tabHrefs.has(entry.href)) entries.push(entry);
    } else {
      const children = entry.children.filter((c) => !tabHrefs.has(c.href));
      if (children.length) entries.push({ ...entry, children });
    }
  }
  if (isAdmin) entries.push(ADMIN_NAV);
  return entries;
}

export function flattenNav(entries: NavEntry[]): NavLeaf[] {
  return entries.flatMap((e) => (e.kind === 'leaf' ? [e] : e.children));
}

/** `href` matches `pathname` itself or any child route (`/lessons` ↔ `/lessons/1/dictation/2`). */
export function matchesRoute(pathname: string, href: string): boolean {
  return pathname === href || pathname.startsWith(href.endsWith('/') ? href : href + '/');
}

/**
 * NAV-03: the active item is the longest href matching the current route, so
 * `/vocabulary/notebook` highlights "Sổ tay" (not "Bộ từ" at `/vocabulary`), while
 * `/vocabulary/decks/42` still highlights "Bộ từ".
 */
export function findActiveHref(pathname: string | null | undefined, hrefs: string[]): string | null {
  if (!pathname) return null;
  let best: string | null = null;
  for (const href of hrefs) {
    if (matchesRoute(pathname, href) && (best === null || href.length > best.length)) best = href;
  }
  return best;
}

/** All navigable hrefs (used to resolve the active item across sidebar sections). */
export function allNavHrefs(isAdmin: boolean): string[] {
  const entries = isAdmin ? [...MAIN_NAV, ADMIN_NAV] : MAIN_NAV;
  return flattenNav(entries).map((l) => l.href);
}

const PRACTICE_ROUTE = /^\/lessons\/[^/]+\/(dictation|shadowing)(\/|$)/;

/** NAV-01: practice screens hide the mobile tab bar to maximise the working area. */
export function isPracticeRoute(pathname: string | null | undefined): boolean {
  return !!pathname && PRACTICE_ROUTE.test(pathname);
}

/** Local calendar date `YYYY-MM-DD` (the popup limit is per local day). */
export function localDateKey(date: Date = new Date()): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** NAV-07: show the "time to review" popup on /dashboard at most once per day, only when cards are due. */
export function shouldShowReviewReminder(args: {
  pathname: string | null | undefined;
  dueCount: number | null | undefined;
  lastShownDate: string | null | undefined;
  today: string;
}): boolean {
  return args.pathname === '/dashboard' && (args.dueCount ?? 0) > 0 && args.lastShownDate !== args.today;
}
