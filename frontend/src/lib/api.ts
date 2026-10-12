import axios, { AxiosError } from 'axios';
import { useAuthStore } from '@/store/auth-store';
import type { ApiErrorBody } from './types';

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000',
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const hadToken = !!useAuthStore.getState().token;
    if (error.response?.status === 401 && hadToken) {
      useAuthStore.getState().clearAuth();
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// Presigned S3 PUT URLs must NOT receive the gateway's Authorization/Bearer header,
// so this uses a bare axios call rather than the `api` instance above.
export async function uploadToPresignedUrl(url: string, blob: Blob, contentType: string): Promise<void> {
  await axios.put(url, blob, { headers: { 'Content-Type': contentType } });
}

export function apiErrorMessage(err: unknown, fallback = 'Đã có lỗi xảy ra, vui lòng thử lại.'): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    if (body?.error) return body.error;
    if (err.response?.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
    if (err.response?.status === 502 || err.response?.status === 503) {
      return 'Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau.';
    }
  }
  return fallback;
}
