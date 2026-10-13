/**
 * T14: navigation of the separate admin console (`app/(admin)/admin/**`). Reuses the learner
 * nav model/helpers; kept free of React imports so it can be unit tested.
 */

import { flattenNav, type NavEntry, type NavLeaf } from '@/components/layout/navItems';

function leaf(href: string, label: string, icon: string): NavLeaf {
  return { kind: 'leaf', href, label, icon };
}

export const ADMIN_CONSOLE_NAV: NavEntry[] = [
  leaf('/admin', 'Tổng quan', '📊'),
  leaf('/admin/users', 'Người dùng', '👥'),
  leaf('/admin/lessons', 'Bài học', '🎧'),
  leaf('/admin/vocabulary', 'Từ vựng', '📝'),
  {
    kind: 'group',
    key: 'billing',
    label: 'Gói & giao dịch',
    icon: '💳',
    children: [leaf('/admin/plans', 'Gói Premium', '💎'), leaf('/admin/transactions', 'Giao dịch', '🧾')],
  },
  leaf('/admin/activation-codes', 'Mã kích hoạt', '🎟️'),
  leaf('/admin/account', 'Tài khoản', '🔐'),
];

export function adminNavHrefs(): string[] {
  return flattenNav(ADMIN_CONSOLE_NAV).map((l) => l.href);
}
