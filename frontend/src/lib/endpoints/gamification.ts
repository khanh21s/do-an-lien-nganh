import { api } from '@/lib/api';
import type { CheckInResult, GamificationMe, XpLeaderboard, XpLeaderboardPeriod } from '@/lib/types';

/** P2-B app-wide gamification (vocabulary-service /vocabs/gamification). */
export const gamificationApi = {
  me: () => api.get<GamificationMe>('/vocabs/gamification/me').then((r) => r.data),

  checkIn: () => api.post<CheckInResult>('/vocabs/gamification/check-in').then((r) => r.data),

  leaderboard: (period: XpLeaderboardPeriod = 'week') =>
    api.get<XpLeaderboard>('/vocabs/gamification/leaderboard', { params: { period } }).then((r) => r.data),
};
