import { api } from '@/lib/api';
import type {
  AddFromLessonResult,
  CardInput,
  CreateDeckInput,
  LearnedWord,
  ListDecksParams,
  ListLearnedParams,
  MessageResponse,
  PageParams,
  PageResponse,
  ReviewQueueResponse,
  SubmitReviewInput,
  SubmitReviewResult,
  UpdateCardInput,
  VocabCard,
  VocabDeck,
  VocabUserStats,
} from '@/lib/types';

// Re-exported for existing imports (`import { type CardInput } from '@/lib/endpoints/vocabulary'`).
export type { CardInput, UpdateCardInput } from '@/lib/types';

export const vocabularyApi = {
  listDecks: (params: ListDecksParams = {}) =>
    api.get<PageResponse<VocabDeck>>('/vocabs/decks', { params }).then((r) => r.data),

  getDeckCards: (deckId: string, params: PageParams = {}) =>
    api.get<PageResponse<VocabCard>>(`/vocabs/decks/${deckId}/cards`, { params }).then((r) => r.data),

  createDeck: (data: CreateDeckInput) => api.post<VocabDeck>('/vocabs/decks', data).then((r) => r.data),

  deleteDeck: (deckId: string) => api.delete<MessageResponse>(`/vocabs/decks/${deckId}`).then((r) => r.data),

  addCards: (deckId: string, cards: CardInput[]) =>
    api.post<VocabCard[]>(`/vocabs/decks/${deckId}/cards`, { cards }).then((r) => r.data),

  updateCard: (cardId: string, data: UpdateCardInput) =>
    api.put<VocabCard>(`/vocabs/cards/${cardId}`, data).then((r) => r.data),

  deleteCard: (cardId: string) => api.delete<MessageResponse>(`/vocabs/cards/${cardId}`).then((r) => r.data),

  getReviewQueue: () => api.get<ReviewQueueResponse>('/vocabs/user/review-queue').then((r) => r.data),

  submitReview: (data: SubmitReviewInput) =>
    api.post<SubmitReviewResult>('/vocabs/user/review', data).then((r) => r.data),

  getStats: () => api.get<VocabUserStats>('/vocabs/user/stats').then((r) => r.data),

  getLearned: (params: ListLearnedParams = {}) =>
    api.get<PageResponse<LearnedWord>>('/vocabs/user/learned', { params }).then((r) => r.data),

  addFromLesson: (cardId: string, source = 'deck') =>
    api.post<AddFromLessonResult>('/vocabs/user/add-from-lesson', { cardId, source }).then((r) => r.data),

  removeFromNotebook: (userVocabId: string) =>
    api.delete<MessageResponse>(`/vocabs/user/${userVocabId}`).then((r) => r.data),

  // --- T10 additions ---
  // --- end T10 ---

  // --- T12 additions ---
  /** ADM-05: deck cards with optional `searchWord` filter (same endpoint as `getDeckCards`). */
  searchDeckCards: (deckId: string, params: import('@/lib/types').DeckCardsParams = {}) =>
    api.get<PageResponse<VocabCard>>(`/vocabs/decks/${deckId}/cards`, { params }).then((r) => r.data),
  // --- end T12 ---

  // --- T13 additions ---
  /** VOC-NB-ADD: add the user's own word to the notebook (201; 409 when it is already there). */
  createPersonalWord: (data: import('@/lib/types').CreatePersonalWordInput) =>
    api.post<LearnedWord>('/vocabs/user/words', data).then((r) => r.data),

  /** Edit a word the user added (403 for deck words, 409 when it collides with another word). */
  updatePersonalWord: (userVocabId: string, data: import('@/lib/types').CreatePersonalWordInput) =>
    api.put<LearnedWord>(`/vocabs/user/words/${userVocabId}`, data).then((r) => r.data),
  // --- end T13 ---
};
