import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export interface AuthUser {
  userId: string;
  email: string;
  cefrLevel: string;
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  refreshToken: string | null;
  hasHydrated: boolean;
  setAuth: (user: AuthUser, token: string, refreshToken: string) => void;
  /** Used by the refresh interceptor (AUTH-01): swaps tokens, keeps the user. */
  setTokens: (token: string, refreshToken: string) => void;
  clearAuth: () => void;
  setHasHydrated: (state: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      refreshToken: null,
      hasHydrated: false,
      setAuth: (user, token, refreshToken) => set({ user, token, refreshToken }),
      setTokens: (token, refreshToken) => set({ token, refreshToken }),
      clearAuth: () => set({ user: null, token: null, refreshToken: null }),
      setHasHydrated: (state) => set({ hasHydrated: state }),
    }),
    {
      name: 'parroto-auth',
      // `hasHydrated` is runtime-only state.
      partialize: (s) => ({ user: s.user, token: s.token, refreshToken: s.refreshToken }),
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    }
  )
);
