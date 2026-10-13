import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { attachAuthInterceptors, createRefreshQueue, safeNextPath, type AuthInterceptorDeps } from './api';
import type { AuthResponse } from './types';

function authResponse(token: string, refreshToken: string): AuthResponse {
  return { userId: 'u1', email: 'a@b.c', cefrLevel: 'B1', token, refreshToken, expiresIn: 3600 };
}

/** Fake server: 200 when the Bearer equals `validToken()`, otherwise 401. Records every call. */
function fakeAdapter(validToken: () => string) {
  const calls: { url?: string; auth: string | null }[] = [];
  const adapter: AxiosAdapter = async (config: InternalAxiosRequestConfig) => {
    const raw = config.headers?.Authorization;
    const auth = typeof raw === 'string' ? raw : null;
    calls.push({ url: config.url, auth });
    // Let all concurrent requests reach the adapter before any of them resolves.
    await new Promise((r) => setTimeout(r, 5));
    if (auth === `Bearer ${validToken()}`) {
      return { data: { ok: config.url }, status: 200, statusText: 'OK', headers: {}, config };
    }
    const response = {
      data: { error: 'Unauthorized', status: 401, timestamp: '' },
      status: 401,
      statusText: 'Unauthorized',
      headers: {},
      config,
    };
    throw new AxiosError('401', AxiosError.ERR_BAD_REQUEST, config, null, response);
  };
  return { adapter, calls };
}

function setup(opts: { refreshFails?: boolean; refreshToken?: string | null } = {}) {
  const state = {
    token: 'old-access' as string | null,
    refreshToken: (opts.refreshToken === undefined ? 'refresh-1' : opts.refreshToken) as string | null,
  };
  const serverToken = { value: 'new-access' };
  const { adapter, calls } = fakeAdapter(() => serverToken.value);
  const instance = axios.create({ baseURL: 'http://test', adapter });

  const deps: AuthInterceptorDeps = {
    getAccessToken: () => state.token,
    getRefreshToken: () => state.refreshToken,
    refresh: vi.fn(async (rt: string) => {
      await new Promise((r) => setTimeout(r, 10));
      if (opts.refreshFails) throw new Error('refresh failed');
      expect(rt).toBe('refresh-1');
      return authResponse('new-access', 'refresh-2');
    }),
    onRefreshed: vi.fn((res: AuthResponse) => {
      state.token = res.token;
      state.refreshToken = res.refreshToken;
    }),
    onAuthFailure: vi.fn(() => {
      state.token = null;
      state.refreshToken = null;
    }),
  };
  attachAuthInterceptors(instance, deps);
  return { instance, deps, state, calls };
}

describe('createRefreshQueue', () => {
  it('shares one in-flight promise between concurrent callers and resets afterwards', async () => {
    let n = 0;
    const fn = vi.fn(async () => {
      await new Promise((r) => setTimeout(r, 5));
      return ++n;
    });
    const run = createRefreshQueue(fn);
    const results = await Promise.all([run(), run(), run()]);
    expect(results).toEqual([1, 1, 1]);
    expect(fn).toHaveBeenCalledTimes(1);
    expect(await run()).toBe(2);
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it('resets after a rejection', async () => {
    const fn = vi.fn().mockRejectedValueOnce(new Error('x')).mockResolvedValueOnce('ok');
    const run = createRefreshQueue(fn);
    await expect(run()).rejects.toThrow('x');
    await expect(run()).resolves.toBe('ok');
  });
});

describe('attachAuthInterceptors (AUTH-01)', () => {
  it('3 concurrent 401s → refresh called exactly once, all 3 retried with the new token', async () => {
    const { instance, deps, state, calls } = setup();

    const results = await Promise.all([instance.get('/a'), instance.get('/b'), instance.get('/c')]);

    expect(results.map((r) => r.status)).toEqual([200, 200, 200]);
    expect(results.map((r) => (r.data as { ok: string }).ok).sort()).toEqual(['/a', '/b', '/c']);
    expect(deps.refresh).toHaveBeenCalledTimes(1);
    expect(deps.onRefreshed).toHaveBeenCalledTimes(1);
    expect(deps.onAuthFailure).not.toHaveBeenCalled();
    expect(state).toEqual({ token: 'new-access', refreshToken: 'refresh-2' });

    // 3 original attempts with the old token + 3 retries with the new one.
    expect(calls).toHaveLength(6);
    expect(calls.filter((c) => c.auth === 'Bearer old-access')).toHaveLength(3);
    expect(calls.filter((c) => c.auth === 'Bearer new-access')).toHaveLength(3);
  });

  it('refresh failure → onAuthFailure, original errors rejected, no retry', async () => {
    const { instance, deps, state, calls } = setup({ refreshFails: true });

    const settled = await Promise.allSettled([instance.get('/a'), instance.get('/b')]);

    expect(settled.every((s) => s.status === 'rejected')).toBe(true);
    expect(deps.refresh).toHaveBeenCalledTimes(1);
    expect(deps.onAuthFailure).toHaveBeenCalled();
    expect(state).toEqual({ token: null, refreshToken: null });
    expect(calls).toHaveLength(2);
  });

  it('no refresh token → onAuthFailure without calling refresh', async () => {
    const { instance, deps } = setup({ refreshToken: null });
    await expect(instance.get('/a')).rejects.toBeInstanceOf(AxiosError);
    expect(deps.refresh).not.toHaveBeenCalled();
    expect(deps.onAuthFailure).toHaveBeenCalledTimes(1);
  });

  it('a retried request that 401s again is not refreshed a second time', async () => {
    const { instance, deps } = setup();
    // Server never accepts any token.
    const strict = fakeAdapter(() => 'nothing-valid');
    instance.defaults.adapter = strict.adapter;
    await expect(instance.get('/a')).rejects.toBeInstanceOf(AxiosError);
    expect(deps.refresh).toHaveBeenCalledTimes(1);
    // Original request + exactly one retry.
    expect(strict.calls.map((c) => c.auth)).toEqual(['Bearer old-access', 'Bearer new-access']);
  });

  it('does not refresh for /auth/login 401 or anonymous requests', async () => {
    const { instance, deps, state } = setup();
    await expect(instance.post('/auth/login', {})).rejects.toBeInstanceOf(AxiosError);
    state.token = null;
    await expect(instance.get('/x')).rejects.toBeInstanceOf(AxiosError);
    expect(deps.refresh).not.toHaveBeenCalled();
    expect(deps.onAuthFailure).not.toHaveBeenCalled();
  });
});

describe('safeNextPath (AUTH-05)', () => {
  it('accepts internal paths only', () => {
    expect(safeNextPath('/vocabulary/review')).toBe('/vocabulary/review');
    expect(safeNextPath('/lessons/1?mode=dictation')).toBe('/lessons/1?mode=dictation');
    expect(safeNextPath('//evil.com')).toBeNull();
    expect(safeNextPath('/\\evil.com')).toBeNull();
    expect(safeNextPath('https://evil.com')).toBeNull();
    expect(safeNextPath('evil')).toBeNull();
    expect(safeNextPath('')).toBeNull();
    expect(safeNextPath(null)).toBeNull();
  });
});
