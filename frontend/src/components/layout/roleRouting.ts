/**
 * T14: role-based routing. Admins live in the admin console (`/admin/**`), learners in the
 * learning app. Pure functions so the decisions are unit tested without React.
 */

export const ADMIN_HOME = '/admin';
export const LEARNER_HOME = '/dashboard';

/** `/admin` itself or any child route (not `/administrator`). */
export function isAdminPath(path: string | null | undefined): boolean {
  if (!path) return false;
  const pathname = path.split(/[?#]/)[0];
  return pathname === ADMIN_HOME || pathname.startsWith(ADMIN_HOME + '/');
}

/**
 * Where to go after login/register (or when an already signed-in user opens /login).
 * - admin → `next` only when it points inside the admin console, otherwise `/admin`;
 * - user  → `next` unless it is an admin page (that would only bounce back), otherwise `/dashboard`.
 * `next` must already be validated with `safeNextPath`.
 */
export function postAuthPath(isAdmin: boolean, next: string | null | undefined): string {
  if (isAdmin) return next && isAdminPath(next) ? next : ADMIN_HOME;
  return next && !isAdminPath(next) ? next : LEARNER_HOME;
}

export type RouteArea = 'learner' | 'admin';

/**
 * Redirect target for a signed-in user whose role does not match the area, or null to stay.
 * `learner` = the (app) group plus the signed-in variant of /lessons and /pricing.
 */
export function roleRedirect(area: RouteArea, isAdmin: boolean): string | null {
  if (area === 'learner') return isAdmin ? ADMIN_HOME : null;
  return isAdmin ? null : LEARNER_HOME;
}
