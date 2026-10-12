import { api } from '@/lib/api';
import type { AuthResponse, CefrLevel, ProfileResponse } from '@/lib/types';

export const authApi = {
  register: (data: { email: string; password: string; cefrLevel: CefrLevel }) =>
    api.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  loginGoogle: (googleToken: string) =>
    api.post<AuthResponse>('/auth/login-google', { googleToken }).then((r) => r.data),

  loginApple: (appleToken: string) =>
    api.post<AuthResponse>('/auth/login-apple', { appleToken }).then((r) => r.data),

  refreshToken: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh-token', { refreshToken }).then((r) => r.data),

  logout: () => api.post('/auth/logout').then((r) => r.data),

  getProfile: () => api.get<ProfileResponse>('/auth/profile').then((r) => r.data),

  updateProfile: (data: { cefrLevel?: CefrLevel; avatarUrl?: string }) =>
    api.put<ProfileResponse>('/auth/profile', data).then((r) => r.data),

  changePassword: (data: { currentPassword: string; newPassword: string }) =>
    api.put('/auth/change-password', data).then((r) => r.data),

  deleteAccount: () => api.delete('/auth/account').then((r) => r.data),
};
