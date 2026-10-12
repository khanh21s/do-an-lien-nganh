import { api } from '@/lib/api';
import type { CreateLessonInput, LessonDetail, LessonSummary, PageResponse, SentenceInput } from '@/lib/types';

export const contentApi = {
  listLessons: (params: { page?: number; limit?: number; cefrLevel?: string; topicTag?: string } = {}) =>
    api.get<PageResponse<LessonSummary>>('/content/lessons', { params }).then((r) => r.data),

  searchLessons: (q: string, params: { page?: number; limit?: number } = {}) =>
    api.get<PageResponse<LessonSummary>>('/content/lessons/search', { params: { q, ...params } }).then((r) => r.data),

  getLesson: (id: string) => api.get<LessonDetail>(`/content/lessons/${id}`).then((r) => r.data),

  createLesson: (data: CreateLessonInput) =>
    api.post<LessonDetail>('/content/lessons', data).then((r) => r.data),

  updateLesson: (id: string, data: Partial<CreateLessonInput>) =>
    api.put<LessonDetail>(`/content/lessons/${id}`, data).then((r) => r.data),

  deleteLesson: (id: string) => api.delete(`/content/lessons/${id}`).then((r) => r.data),

  addSentences: (lessonId: string, sentences: SentenceInput[]) =>
    api.post(`/content/lessons/${lessonId}/sentences`, { sentences }).then((r) => r.data),

  uploadLessonAudio: (lessonId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api
      .post(`/content/lessons/${lessonId}/upload-audio`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      .then((r) => r.data);
  },

  lessonAudioUrl: (lessonId: string) => `${api.defaults.baseURL}/content/lessons/${lessonId}/audio`,
  sentenceAudioUrl: (sentenceId: string) => `${api.defaults.baseURL}/content/sentences/${sentenceId}/audio`,
};
