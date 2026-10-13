import { api } from '@/lib/api';
import type {
  AdminAuthStats,
  AdminUser,
  AuthResponse,
  ChangePasswordInput,
  ListAdminUsersParams,
  LoginInput,
  MessageResponse,
  PageResponse,
  ProfileResponse,
  RegisterInput,
  UpdateAdminUserInput,
  UpdateProfileInput,
} from '@/lib/types';

export const authApi = {
  register: (data: RegisterInput) => api.post<AuthResponse>('/auth/register', data).then((r) => r.data),

  login: (data: LoginInput) => api.post<AuthResponse>('/auth/login', data).then((r) => r.data),

  loginGoogle: (googleToken: string) =>
    api.post<AuthResponse>('/auth/login-google', { googleToken }).then((r) => r.data),

  loginApple: (appleToken: string) =>
    api.post<AuthResponse>('/auth/login-apple', { appleToken }).then((r) => r.data),

  refreshToken: (refreshToken: string) =>
    api.post<AuthResponse>('/auth/refresh-token', { refreshToken }).then((r) => r.data),

  /** Revokes the access token (Bearer) and, when given, the refresh token too. */
  logout: (refreshToken?: string | null) =>
    api
      .post<MessageResponse>('/auth/logout', refreshToken ? { refreshToken } : undefined)
      .then((r) => r.data),

  getProfile: () => api.get<ProfileResponse>('/auth/profile').then((r) => r.data),

  updateProfile: (data: UpdateProfileInput) =>
    api.put<ProfileResponse>('/auth/profile', data).then((r) => r.data),

  changePassword: (data: ChangePasswordInput) =>
    api.put<MessageResponse>('/auth/change-password', data).then((r) => r.data),

  deleteAccount: () => api.delete<MessageResponse>('/auth/account').then((r) => r.data),

  // --- T11 additions ---
  // --- end T11 ---

  // --- T14 additions ---
  /** Admin: users (admins included), `q` matches email, newest first. */
  adminListUsers: (params: ListAdminUsersParams = {}) =>
    api
      .get<PageResponse<AdminUser>>('/auth/admin/users', { params })
      .then((r) => r.data),

  adminGetUser: (id: string) => api.get<AdminUser>(`/auth/admin/users/${id}`).then((r) => r.data),

  /** Admin: lock (`isActive=false`) or unlock a user. Locking an admin/self → 400; unlocking is allowed. */
  adminUpdateUser: (id: string, data: UpdateAdminUserInput) =>
    api.patch<AdminUser>(`/auth/admin/users/${id}`, data).then((r) => r.data),

  adminStats: () => api.get<AdminAuthStats>('/auth/admin/stats').then((r) => r.data),
  // --- end T14 ---
};
