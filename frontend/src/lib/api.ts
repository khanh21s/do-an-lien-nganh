import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '@/store/auth-store';
import type { ApiErrorBody, AuthResponse } from './types';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000';

// ---------------------------------------------------------------------------------------------
// Single-flight refresh (AUTH-01)
// ---------------------------------------------------------------------------------------------

/**
 * Wraps an async function so that concurrent callers share one in-flight promise.
 * Once the promise settles, the next call starts a fresh run.
 */
export function createRefreshQueue<T>(fn: () => Promise<T>): () => Promise<T> {
  let inflight: Promise<T> | null = null;
  return () => {
    if (!inflight) {
      inflight = fn().finally(() => {
        inflight = null;
      });
    }
    return inflight;
  };
}

export interface AuthInterceptorDeps {
  getAccessToken: () => string | null;
  getRefreshToken: () => string | null;
  /** Calls `POST /auth/refresh-token` WITHOUT going through the intercepted instance. */
  refresh: (refreshToken: string) => Promise<AuthResponse>;
  onRefreshed: (res: AuthResponse) => void;
  /** Refresh impossible or failed: clear the session and send the user to /login. */
  onAuthFailure: () => void;
}

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

// Endpoints whose 401 must never trigger a refresh (credentials are wrong, or it IS the refresh).
const NO_REFRESH_PATHS = ['/auth/refresh-token', '/auth/login', '/auth/register'];

function isNoRefreshUrl(url: string | undefined): boolean {
  if (!url) return false;
  return NO_REFRESH_PATHS.some((p) => url.includes(p));
}

function sentBearer(config: InternalAxiosRequestConfig): string | null {
  const raw = config.headers?.Authorization;
  if (typeof raw !== 'string' || !raw.startsWith('Bearer ')) return null;
  return raw.slice('Bearer '.length);
}

/**
 * Attaches the Bearer request interceptor and the 401 → refresh → retry-once response interceptor.
 * Exported (with injectable deps) so the single-flight behaviour can be unit tested.
 */
export function attachAuthInterceptors(instance: AxiosInstance, deps: AuthInterceptorDeps): void {
  const refreshOnce = createRefreshQueue(async () => {
    const refreshToken = deps.getRefreshToken();
    if (!refreshToken) throw new Error('No refresh token');
    const res = await deps.refresh(refreshToken);
    deps.onRefreshed(res);
    return res.token;
  });

  instance.interceptors.request.use((config) => {
    const token = deps.getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    async (error: AxiosError<ApiErrorBody>) => {
      const config = error.config as RetriableConfig | undefined;
      if (error.response?.status !== 401 || !config || config._retry || isNoRefreshUrl(config.url)) {
        return Promise.reject(error);
      }

      const usedToken = sentBearer(config);
      // Anonymous request: nothing to refresh.
      if (!usedToken) return Promise.reject(error);

      config._retry = true;

      // Another request already refreshed the token while this one was in flight.
      const current = deps.getAccessToken();
      if (current && current !== usedToken) {
        return instance(config);
      }

      if (!deps.getRefreshToken()) {
        deps.onAuthFailure();
        return Promise.reject(error);
      }

      try {
        await refreshOnce();
      } catch {
        deps.onAuthFailure();
        return Promise.reject(error);
      }
      return instance(config);
    }
  );
}

/** Current path + query, used as the `next` param when bouncing to /login. */
export function currentPathForNext(): string {
  if (typeof window === 'undefined') return '/';
  return window.location.pathname + window.location.search;
}

export function loginUrlWithNext(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}

/**
 * Validates a `next` redirect target: only internal paths starting with a single `/` are allowed
 * (`//evil.com`, `/\evil.com`, absolute URLs are rejected).
 */
export function safeNextPath(next: string | null | undefined): string | null {
  if (!next) return null;
  if (!next.startsWith('/')) return null;
  if (next.startsWith('//') || next.startsWith('/\\')) return null;
  return next;
}

// ---------------------------------------------------------------------------------------------
// The app-wide axios instance
// ---------------------------------------------------------------------------------------------

export const api = axios.create({ baseURL: API_BASE_URL });

// Bare client for the refresh call so it never re-enters the interceptors above.
const refreshClient = axios.create({ baseURL: API_BASE_URL });

attachAuthInterceptors(api, {
  getAccessToken: () => useAuthStore.getState().token,
  getRefreshToken: () => useAuthStore.getState().refreshToken,
  refresh: (refreshToken) =>
    refreshClient.post<AuthResponse>('/auth/refresh-token', { refreshToken }).then((r) => r.data),
  onRefreshed: (res) => useAuthStore.getState().setTokens(res.token, res.refreshToken),
  onAuthFailure: () => {
    useAuthStore.getState().clearAuth();
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = loginUrlWithNext(currentPathForNext());
    }
  },
});

// Presigned S3 PUT URLs must NOT receive the gateway's Authorization/Bearer header,
// so this uses a bare axios call rather than the `api` instance above.
export async function uploadToPresignedUrl(url: string, blob: Blob, contentType: string): Promise<void> {
  await axios.put(url, blob, { headers: { 'Content-Type': contentType } });
}

// ---------------------------------------------------------------------------------------------
// Error helpers
// ---------------------------------------------------------------------------------------------

export function apiErrorStatus(err: unknown): number | null {
  if (axios.isAxiosError(err)) return err.response?.status ?? null;
  return null;
}

export function isNotFoundError(err: unknown): boolean {
  return apiErrorStatus(err) === 404;
}

export function apiErrorMessage(err: unknown, fallback = 'Đã có lỗi xảy ra, vui lòng thử lại.'): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body && typeof body === 'object' && typeof body.error === 'string' && body.error) return body.error;
    if (!err.response) return 'Không kết nối được máy chủ, vui lòng kiểm tra mạng.';
    if (err.response.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    if (err.response.status === 502 || err.response.status === 503) {
      return 'Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau.';
    }
  }
  return fallback;
}
