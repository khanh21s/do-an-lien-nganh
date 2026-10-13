import { api } from '@/lib/api';
import type {
  CreateLessonInput,
  LessonDetail,
  LessonSummary,
  ListLessonsParams,
  MessageResponse,
  PageParams,
  PageResponse,
  SentenceInput,
  SentenceResponse,
  UpdateLessonInput,
  UpdateSentenceInput,
  UploadAudioResult,
} from '@/lib/types';

export const contentApi = {
  listLessons: (params: ListLessonsParams = {}) =>
    api.get<PageResponse<LessonSummary>>('/content/lessons', { params }).then((r) => r.data),

  searchLessons: (q: string, params: PageParams = {}) =>
    api.get<PageResponse<LessonSummary>>('/content/lessons/search', { params: { q, ...params } }).then((r) => r.data),

  getLesson: (id: string) => api.get<LessonDetail>(`/content/lessons/${id}`).then((r) => r.data),

  createLesson: (data: CreateLessonInput) =>
    api.post<LessonDetail>('/content/lessons', data).then((r) => r.data),

  /** ADM-02: sends exactly `UpdateLessonRequest` (title, description, topicTag, cefrLevel). */
  updateLesson: (id: string, data: UpdateLessonInput) =>
    api.put<LessonDetail>(`/content/lessons/${id}`, data).then((r) => r.data),

  deleteLesson: (id: string) => api.delete<MessageResponse>(`/content/lessons/${id}`).then((r) => r.data),

  addSentences: (lessonId: string, sentences: SentenceInput[]) =>
    api.post<SentenceResponse[]>(`/content/lessons/${lessonId}/sentences`, { sentences }).then((r) => r.data),

  updateSentence: (lessonId: string, sentenceId: string, data: UpdateSentenceInput) =>
    api.put<SentenceResponse>(`/content/lessons/${lessonId}/sentences/${sentenceId}`, data).then((r) => r.data),

  deleteSentence: (lessonId: string, sentenceId: string) =>
    api.delete<MessageResponse>(`/content/lessons/${lessonId}/sentences/${sentenceId}`).then((r) => r.data),

  uploadLessonAudio: (lessonId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post<UploadAudioResult>(`/content/lessons/${lessonId}/upload-audio`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },

  /** Public streaming URLs (GET /content/** is public, usable directly in <audio src>). */
  lessonAudioUrl: (lessonId: string) => `${api.defaults.baseURL}/content/lessons/${lessonId}/audio`,
  sentenceAudioUrl: (sentenceId: string) => `${api.defaults.baseURL}/content/sentences/${sentenceId}/audio`,

  // --- T08 additions ---
  // --- end T08 ---

  // --- T12 additions ---
  // --- end T12 ---
};
