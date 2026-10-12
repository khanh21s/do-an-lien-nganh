import { api } from '@/lib/api';
import type {
  AttemptDetail,
  AttemptSummary,
  DictationAttemptResult,
  Difficulty,
  PageResponse,
  PracticeStats,
  ShadowingAttemptResult,
  ShadowingUploadUrlResponse,
} from '@/lib/types';

export const practiceApi = {
  submitDictation: (data: { lessonId: string; sentenceId: string; userInput: string; difficulty: Difficulty }) =>
    api.post<DictationAttemptResult>('/practice/dictation/attempt', data).then((r) => r.data),

  requestShadowingUploadUrl: (data: { lessonId: string; sentenceId: string }) =>
    api.post<ShadowingUploadUrlResponse>('/practice/shadowing/request-upload-url', data).then((r) => r.data),

  submitShadowing: (data: { lessonId: string; sentenceId: string; recordingId: string; duration?: number }) =>
    api.post<ShadowingAttemptResult>('/practice/shadowing/attempt', data).then((r) => r.data),

  listAttempts: (params: { page?: number; limit?: number; lessonId?: string; mode?: string } = {}) =>
    api.get<PageResponse<AttemptSummary>>('/practice/attempts', { params }).then((r) => r.data),

  getAttempt: (id: string) => api.get<AttemptDetail>(`/practice/attempts/${id}`).then((r) => r.data),

  getStats: () => api.get<PracticeStats>('/practice/stats').then((r) => r.data),
};
