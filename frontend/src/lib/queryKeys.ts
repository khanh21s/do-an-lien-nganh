import type {
  ListActivationCodesParams,
  ListAttemptsParams,
  ListDecksParams,
  ListLearnedParams,
  ListLessonsParams,
  PageParams,
} from './types';

/**
 * Central react-query keys (architecture §6.2). The first segment keeps the existing kebab-case
 * strings so caches shared between pages keep working; invalidate with the bare prefix
 * (e.g. `{ queryKey: qk.lessonAttemptsAll }`) or the exact key.
 */
export const qk = {
  // Auth
  profile: ['profile'] as const,

  // Practice
  practiceStats: ['practice-stats'] as const,
  streak: ['practice-streak'] as const,
  activityAll: ['practice-activity'] as const,
  activity: (days: number) => ['practice-activity', days] as const,
  trendingErrors: ['practice-trending-errors'] as const,
  attemptsAll: ['attempts'] as const,
  attempts: (params: ListAttemptsParams = {}) => ['attempts', params] as const,
  attempt: (id: string) => ['attempt', id] as const,
  lessonAttemptsAll: ['lesson-attempts'] as const,
  lessonAttempts: (lessonId: string) => ['lesson-attempts', lessonId] as const,
  lessonStatsAll: ['lesson-stats'] as const,
  lessonStats: (lessonId: string) => ['lesson-stats', lessonId] as const,
  recording: (attemptId: string) => ['recording', attemptId] as const,

  // Content
  lessonsAll: ['lessons'] as const,
  lessons: (params: ListLessonsParams = {}) => ['lessons', params] as const,
  lessonSearch: (q: string, params: PageParams = {}) => ['lesson-search', q, params] as const,
  lesson: (id: string) => ['lesson', id] as const,

  // Vocabulary
  vocabStats: ['vocab-stats'] as const,
  decksAll: ['decks'] as const,
  decks: (params: ListDecksParams = {}) => ['decks', params] as const,
  deckCardsAll: ['deck-cards'] as const,
  deckCards: (deckId: string, params: PageParams = {}) => ['deck-cards', deckId, params] as const,
  reviewQueue: ['review-queue'] as const,
  learnedWordsAll: ['learned-words'] as const,
  learnedWords: (params: ListLearnedParams = {}) => ['learned-words', params] as const,

  // Subscription
  userSubscription: ['user-subscription'] as const,
  plans: ['plans'] as const,
  transactionsAll: ['transactions'] as const,
  transactions: (page: number) => ['transactions', page] as const,

  // Admin
  adminLessonsAll: ['admin-lessons'] as const,
  adminLessons: (page: number) => ['admin-lessons', page] as const,
  adminDecks: ['admin-decks'] as const,
  adminCardsAll: ['admin-cards'] as const,
  adminCards: (deckId: string, params: PageParams = {}) => ['admin-cards', deckId, params] as const,
  activationCodesAll: ['activation-codes'] as const,
  activationCodes: (params: ListActivationCodesParams = {}) => ['activation-codes', params] as const,

  // --- T07 additions ---
  // --- end T07 ---

  // --- T08 additions ---
  // --- end T08 ---

  // --- T09 additions ---
  // --- end T09 ---

  // --- T10 additions ---
  /** `useInfiniteQuery` pages of a deck (limit 50); prefix stays `deck-cards`. */
  deckCardsInfinite: (deckId: string) => ['deck-cards', deckId, 'infinite'] as const,
  // --- end T10 ---

  // --- T11 additions ---
  // --- end T11 ---

  // --- T12 additions ---
  // --- end T12 ---
};
