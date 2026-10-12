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
      clearAuth: () => set({ user: null, token: null, refreshToken: null }),
      setHasHydrated: (state) => set({ hasHydrated: state }),
    }),
    {
      name: 'parroto-auth',
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);
      },
    }
  )
);
