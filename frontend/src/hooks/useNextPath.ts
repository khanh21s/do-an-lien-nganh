'use client';

import { safeNextPath } from '@/lib/api';
import { useSearchParams } from 'next/navigation';

export const DEFAULT_AFTER_LOGIN = '/dashboard';

/**
 * Reads `?next=` (AUTH-05). Only internal paths are honoured; anything else → `/dashboard`.
 * Uses `useSearchParams`, so the calling component must sit inside a `<Suspense>` boundary.
 */
export function useNextPath(): { next: string; rawNext: string | null } {
  const params = useSearchParams();
  const rawNext = safeNextPath(params.get('next'));
  return { next: rawNext ?? DEFAULT_AFTER_LOGIN, rawNext };
}
