import { api, uploadToPresignedUrl } from '@/lib/api';
import type {
  ActivityDay,
  AttemptDetail,
  AttemptSummary,
  DictationAttemptInput,
  DictationAttemptResult,
  LessonAttempts,
  LessonStats,
  ListAttemptsParams,
  MessageResponse,
  PageResponse,
  PracticeStats,
  SentenceReviewItem,
  SentenceReviewStats,
  ShadowingAttemptInput,
  ShadowingAttemptResult,
  ShadowingUploadUrlInput,
  ShadowingUploadUrlResponse,
  StreakResponse,
  TrendingError,
} from '@/lib/types';

export const practiceApi = {
  submitDictation: (data: DictationAttemptInput) =>
    api.post<DictationAttemptResult>('/practice/dictation/attempt', data).then((r) => r.data),

  requestShadowingUploadUrl: (data: ShadowingUploadUrlInput) =>
    api.post<ShadowingUploadUrlResponse>('/practice/shadowing/request-upload-url', data).then((r) => r.data),

  /**
   * Uploads a WAV recording to the target returned by `requestShadowingUploadUrl` (architecture §6.1):
   * local mode → authenticated PUT through the gateway; S3 mode → bare PUT to the presigned URL.
   */
  uploadRecording: async (target: ShadowingUploadUrlResponse, blob: Blob): Promise<void> => {
    const contentType = target.contentType || 'audio/wav';
    if (target.uploadRequiresAuth) {
      await api.put(target.uploadUrl, blob, { headers: { 'Content-Type': contentType } });
    } else {
      await uploadToPresignedUrl(target.uploadUrl, blob, contentType);
    }
  },

  submitShadowing: (data: ShadowingAttemptInput) =>
    api.post<ShadowingAttemptResult>('/practice/shadowing/attempt', data).then((r) => r.data),

  /** SHA-05: fetches the owner's recording with Bearer; wrap with `URL.createObjectURL`. */
  getRecordingBlob: (attemptId: string) =>
    api.get<Blob>(`/practice/shadowing/${attemptId}/recording`, { responseType: 'blob' }).then((r) => r.data),

  listAttempts: (params: ListAttemptsParams = {}) =>
    api.get<PageResponse<AttemptSummary>>('/practice/attempts', { params }).then((r) => r.data),

  getAttempt: (id: string) => api.get<AttemptDetail>(`/practice/attempts/${id}`).then((r) => r.data),

  getLessonAttempts: (lessonId: string) =>
    api.get<LessonAttempts>(`/practice/lessons/${lessonId}/attempts`).then((r) => r.data),

  getStats: () => api.get<PracticeStats>('/practice/stats').then((r) => r.data),

  getLessonStats: (lessonId: string) =>
    api.get<LessonStats>(`/practice/stats/by-lesson/${lessonId}`).then((r) => r.data),

  /** `days` 1–365 (default 90); returns exactly `days` items, oldest first. */
  getActivity: (days = 90) =>
    api.get<ActivityDay[]>('/practice/stats/activity', { params: { days } }).then((r) => r.data),

  getTrendingErrors: () => api.get<TrendingError[]>('/practice/stats/trending-errors').then((r) => r.data),

  getStreak: () => api.get<StreakResponse>('/practice/streak').then((r) => r.data),

  // --- T08 additions ---
  // --- end T08 ---

  // --- T09 additions ---
  // --- end T09 ---

  // --- T11 additions ---
  // --- end T11 ---

  // --- P2-A ---
  /** Due sentences (oldest first) with sentence + lesson data; `limit` 1–100 (default 20). */
  getReviewQueue: (limit?: number) =>
    api
      .get<SentenceReviewItem[]>('/practice/review/queue', { params: limit ? { limit } : undefined })
      .then((r) => r.data),

  getReviewStats: () => api.get<SentenceReviewStats>('/practice/review/stats').then((r) => r.data),

  deleteReviewItem: (id: string) => api.delete<MessageResponse>(`/practice/review/${id}`).then((r) => r.data),
  // --- end P2-A ---
};
