import type { FlatPageResponse, PageResponse } from './types';

/**
 * T14: every Java service returns `{ data, pagination: { page, limit, total, totalPages } }`.
 * The admin endpoints of auth-service are new, so a flat `{ data, page, limit, total, totalPages }`
 * body is accepted too and normalised to the shared `PageResponse` shape.
 */
export function normalizePage<T>(raw: PageResponse<T> | FlatPageResponse<T> | null | undefined): PageResponse<T> {
  if (!raw || typeof raw !== 'object') {
    return { data: [], pagination: { page: 1, limit: 0, total: 0, totalPages: 0 } };
  }
  const data = Array.isArray(raw.data) ? raw.data : [];
  if ('pagination' in raw && raw.pagination) return { data, pagination: raw.pagination };
  const flat = raw as FlatPageResponse<T>;
  return {
    data,
    pagination: {
      page: flat.page ?? 1,
      limit: flat.limit ?? data.length,
      total: flat.total ?? data.length,
      totalPages: flat.totalPages ?? (data.length ? 1 : 0),
    },
  };
}
