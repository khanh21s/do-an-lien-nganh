import { api } from '@/lib/api';
import type { GameAnswerResult, GameHint, GameState, GameStats, GameSummary, StartGameInput } from '@/lib/types';

/** T15 "Dịch nhanh": every answer is graded and scored by vocabulary-service. */
export const gameApi = {
  start: (data: StartGameInput) => api.post<GameState>('/vocabs/games/sessions', data).then((r) => r.data),

  hint: (sessionId: string) => api.post<GameHint>(`/vocabs/games/sessions/${sessionId}/hint`).then((r) => r.data),

  answer: (sessionId: string, data: { answer?: string; skip?: boolean }) =>
    api.post<GameAnswerResult>(`/vocabs/games/sessions/${sessionId}/answer`, data).then((r) => r.data),

  finish: (sessionId: string) => api.post<GameSummary>(`/vocabs/games/sessions/${sessionId}/finish`).then((r) => r.data),

  stats: () => api.get<GameStats>('/vocabs/games/me').then((r) => r.data),
};
