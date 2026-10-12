// Mirrors the Java DTO records in services/*/src/main/java/**/dto exactly.

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';
export type Difficulty = 'Easy' | 'Normal' | 'Hard';

export interface PageInfo {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export interface PageResponse<T> {
  data: T[];
  pagination: PageInfo;
}

export interface ApiErrorBody {
  error: string;
  status: number;
  timestamp: string;
  requestId?: string;
}

// ---------- Auth ----------
export interface AuthResponse {
  userId: string;
  email: string;
  cefrLevel: CefrLevel;
  token: string;
  refreshToken: string;
  expiresIn: number;
}

export interface ProfileResponse {
  userId: string;
  email: string;
  cefrLevel: CefrLevel;
  authProvider: string;
  totalHoursLearned: number;
  totalWordsLearned: number;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  isAdmin: boolean;
}

// ---------- Content ----------
export interface LessonSummary {
  id: string;
  title: string;
  sourceUrl: string | null;
  cefrLevel: CefrLevel;
  topicTag: string | null;
  description: string | null;
  duration: number | null;
  durationMinutes: number | null;
  sentenceCount: number;
  createdAt: string;
}

export interface SentenceResponse {
  id: string;
  text: string;
  ipa: string | null;
  translationVi: string | null;
  sentenceOrder: number;
  startMs: number;
  endMs: number;
  audioUrl: string | null;
  imageUrl: string | null;
}

export interface LessonDetail {
  id: string;
  title: string;
  sourceUrl: string | null;
  cefrLevel: CefrLevel;
  topicTag: string | null;
  description: string | null;
  audioUrl: string | null;
  duration: number | null;
  createdAt: string;
  updatedAt: string;
  sentences: SentenceResponse[];
}

export interface CreateLessonInput {
  title: string;
  sourceUrl?: string;
  cefrLevel: CefrLevel;
  topicTag?: string;
  description?: string;
  audioUrl: string;
  duration?: number;
}

export interface SentenceInput {
  text: string;
  ipa?: string;
  translationVi?: string;
  startMs: number;
  endMs: number;
  audioUrl?: string;
  imageUrl?: string;
}

// ---------- Practice ----------
export interface DictationAttemptResult {
  attemptId: string;
  lessonId: string;
  sentenceId: string;
  accuracy: number;
  score: number;
  totalWords: number;
  correctWords: number;
  wordScores: WordScore[];
  createdAt: string;
}

export interface WordScore {
  position: number;
  word: string;
  isCorrect: boolean;
  expected: string;
}

export interface ShadowingUploadUrlResponse {
  uploadUrl: string;
  recordingId: string;
  expiresIn: number;
}

export interface PhonemeScore {
  phoneme: string;
  accuracy: number;
}

export interface ShadowingWordScore {
  word: string;
  phonemes: PhonemeScore[];
  wordAccuracy: number;
  status: 'green' | 'yellow' | 'red' | string;
}

export interface ShadowingAttemptResult {
  attemptId: string;
  recordingUrl: string;
  score: number;
  accuracy: number;
  duration: number;
  wordScores: ShadowingWordScore[];
  createdAt: string;
}

export interface AttemptSummary {
  attemptId: string;
  lessonId: string;
  sentenceId: string;
  mode: string;
  score: number;
  accuracy: number;
}

export interface AttemptDetail extends AttemptSummary {
  wordScores: unknown;
  audioRecordingUrl: string | null;
  duration: number | null;
  difficulty: string | null;
}

export interface PracticeStats {
  totalAttempts: number;
  totalHoursLearned: number;
  dictationAccuracy: number;
  shadowingAccuracy: number;
  currentStreak: number;
  longestStreak: number;
  lastPracticeDate: string | null;
  lessonsCompleted: number;
  averageScore: number;
  accuracy: { weekly: number[]; dates: string[] } | null;
}

// ---------- Vocabulary ----------
export interface VocabDeck {
  id: string;
  name: string;
  description: string | null;
  level: CefrLevel | null;
  isProOnly: boolean;
  cardCount: number;
  createdAt: string;
}

export interface VocabCard {
  id: string;
  word: string;
  meaning: string;
  example: string | null;
  exampleVi: string | null;
  ipa: string | null;
  audioUrl: string | null;
  imageUrl: string | null;
  partOfSpeech: string | null;
}

export interface ReviewQueueItem {
  id: string;
  cardId: string;
  word: string;
  meaning: string;
  example: string | null;
  ipa: string | null;
  lastReviewAt: string | null;
  nextReviewAt: string;
  repetitions: number;
  easeFactor: number;
  intervalDays: number;
}

export interface ReviewQueueResponse {
  queue: ReviewQueueItem[];
  totalDueCards: number;
  totalCards: number;
}

export interface SubmitReviewResult {
  userVocabId: string;
  nextReviewAt: string;
  easeFactor: number;
  interval: number;
  repetitions: number;
  message: string | null;
}

export interface LearnedWord {
  userVocabId: string;
  cardId: string;
  word: string;
  meaning: string;
  repetitions: number;
  accuracy: number;
}

export interface VocabUserStats {
  totalLearned: number;
  dueToday: number;
  dueThisWeek: number;
  bySRS: { new: number; learning: number; review: number };
  accuracy: number;
}

// ---------- Subscription ----------
export interface Plan {
  id: string;
  name: string;
  durationDays: number;
  price: number;
  features: Record<string, unknown> | unknown[] | null;
  priceDisplay: string;
}

export interface SubscriptionInfo {
  id: string;
  planId: string;
  planName: string;
  startAt: string;
  endAt: string;
  status: string;
  daysRemaining: number;
  autoRenew: boolean;
}

export interface UserSubscription {
  subscription: SubscriptionInfo | null;
  isPremium: boolean;
}

export interface CreatePaymentResult {
  transactionId: string;
  paymentUrl: string;
  expiresIn: number;
  amount: number;
}

export interface Transaction {
  id: string;
  amount: number;
  method: string;
  status: string;
  createdAt: string;
  planId: string | null;
}
