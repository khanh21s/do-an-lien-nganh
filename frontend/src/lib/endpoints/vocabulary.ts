import { api } from '@/lib/api';
import type {
  CefrLevel,
  LearnedWord,
  PageResponse,
  ReviewQueueResponse,
  SubmitReviewResult,
  VocabCard,
  VocabDeck,
  VocabUserStats,
} from '@/lib/types';

export interface CardInput {
  word: string;
  meaning: string;
  example?: string;
  exampleVi?: string;
  ipa?: string;
  audioUrl?: string;
  imageUrl?: string;
}

export const vocabularyApi = {
  listDecks: (params: { page?: number; limit?: number; level?: CefrLevel; isProOnly?: boolean } = {}) =>
    api.get<PageResponse<VocabDeck>>('/vocabs/decks', { params }).then((r) => r.data),

  getDeckCards: (deckId: string, params: { page?: number; limit?: number } = {}) =>
    api.get<PageResponse<VocabCard>>(`/vocabs/decks/${deckId}/cards`, { params }).then((r) => r.data),

  createDeck: (data: { name: string; description?: string; level?: CefrLevel; isProOnly?: boolean }) =>
    api.post<VocabDeck>('/vocabs/decks', data).then((r) => r.data),

  deleteDeck: (deckId: string) => api.delete(`/vocabs/decks/${deckId}`).then((r) => r.data),

  addCards: (deckId: string, cards: CardInput[]) =>
    api.post(`/vocabs/decks/${deckId}/cards`, { cards }).then((r) => r.data),

  updateCard: (cardId: string, data: Partial<CardInput>) =>
    api.put(`/vocabs/cards/${cardId}`, data).then((r) => r.data),

  getReviewQueue: () => api.get<ReviewQueueResponse>('/vocabs/user/review-queue').then((r) => r.data),

  submitReview: (data: { userVocabId: string; quality: number }) =>
    api.post<SubmitReviewResult>('/vocabs/user/review', data).then((r) => r.data),

  getStats: () => api.get<VocabUserStats>('/vocabs/user/stats').then((r) => r.data),

  getLearned: (params: { page?: number; limit?: number; searchWord?: string } = {}) =>
    api.get<PageResponse<LearnedWord>>('/vocabs/user/learned', { params }).then((r) => r.data),

  addFromLesson: (cardId: string, source = 'deck') =>
    api.post('/vocabs/user/add-from-lesson', { cardId, source }).then((r) => r.data),

  removeFromNotebook: (userVocabId: string) => api.delete(`/vocabs/user/${userVocabId}`).then((r) => r.data),
};
