// API contract types. Mirrors the Java DTO records in services/*/src/main/java/**/dto exactly
// (field names, `| null` for nullable values), plus the Phase 1 changes in docs/architecture.md §4.5.
//
// Wave 2 tasks: do NOT edit lines above the append-only regions at the bottom of this file.
// If a type is really missing, add it inside your own `// --- T0x additions ---` block.

export type CefrLevel = 'A1' | 'A2' | 'B1' | 'B2' | 'C1' | 'C2';
export type Difficulty = 'Easy' | 'Normal' | 'Hard';
export type PracticeMode = 'dictation' | 'shadowing';

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

/** Standard error body returned by every service and the gateway (architecture §5.1). */
export interface ApiErrorBody {
  error: string;
  status: number;
  timestamp: string;
  requestId?: string;
}

/** `MessageResponse` record shared by all services. */
export interface MessageResponse {
  message: string;
}

/** Common paging query params (`page` starts at 1, `limit` default 20, max 100). */
export interface PageParams {
  page?: number;
  limit?: number;
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

export interface RegisterInput {
  email: string;
  password: string;
  cefrLevel: CefrLevel;
}

export interface LoginInput {
  email: string;
  password: string;
}

export interface ProfileResponse {
  userId: string;
  email: string;
  cefrLevel: CefrLevel;
  /** 'local' | 'google' | 'apple' (string in BE). */
  authProvider: string;
  totalHoursLearned: number;
  totalWordsLearned: number;
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
  isAdmin: boolean;
}

export interface UpdateProfileInput {
  cefrLevel?: CefrLevel;
  avatarUrl?: string;
}

export interface ChangePasswordInput {
  currentPassword: string;
  newPassword: string;
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

export interface ListLessonsParams extends PageParams {
  cefrLevel?: CefrLevel | string;
  topicTag?: string;
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

/** Mirrors `UpdateLessonRequest` (ADM-02): only these 4 fields are accepted by the BE. */
export interface UpdateLessonInput {
  title?: string;
  description?: string;
  topicTag?: string;
  cefrLevel?: CefrLevel;
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

/** Body of `PUT /content/lessons/{id}/sentences/{sentenceId}` (NEW): every field optional. */
export interface UpdateSentenceInput {
  text?: string;
  ipa?: string;
  translationVi?: string;
  startMs?: number;
  endMs?: number;
  sentenceOrder?: number;
  audioUrl?: string;
}

export interface UploadAudioResult {
  audioUrl: string;
  duration: number | null;
}

// ---------- Practice ----------
export interface WordScore {
  position: number;
  word: string;
  isCorrect: boolean;
  expected: string;
}

export interface DictationAttemptInput {
  lessonId: string;
  sentenceId: string;
  /** Full sentence typed by the user (spec calls it `typedText`). */
  userInput: string;
  difficulty: Difficulty;
}

export interface DictationAttemptResult {
  attemptId: string;
  lessonId: string;
  sentenceId: string;
  accuracy: number;
  /** Always 0–100 (architecture §4.7). */
  score: number;
  totalWords: number;
  correctWords: number;
  wordScores: WordScore[];
  createdAt: string;
}

export type StorageProvider = 's3' | 'local';
export type SpeechScorer = 'azure' | 'mock';

export interface ShadowingUploadUrlInput {
  lessonId: string;
  sentenceId: string;
}

export interface ShadowingUploadUrlResponse {
  /** Presigned S3 URL, or a gateway-relative path `/practice/shadowing/recordings/{recordingId}` in local mode. */
  uploadUrl: string;
  recordingId: string;
  expiresIn: number;
  uploadMethod: 'PUT';
  /** true → upload through `api` (Bearer); false → bare PUT to the presigned URL. */
  uploadRequiresAuth: boolean;
  contentType: 'audio/wav';
  storage: StorageProvider;
}

export interface PhonemeScore {
  phoneme: string;
  accuracy: number;
}

export type ScoreStatus = 'green' | 'yellow' | 'red';

export interface ShadowingWordScore {
  word: string;
  phonemes: PhonemeScore[];
  wordAccuracy: number;
  status: ScoreStatus | string;
}

export interface ShadowingAttemptInput {
  lessonId: string;
  sentenceId: string;
  recordingId: string;
  /** Seconds. */
  duration?: number;
}

export interface ShadowingAttemptResult {
  attemptId: string;
  recordingUrl: string;
  score: number;
  accuracy: number;
  duration: number;
  wordScores: ShadowingWordScore[];
  createdAt: string;
  /** 'mock' → FE shows the "Chế độ demo – điểm mô phỏng" badge. */
  scorer: SpeechScorer;
}

export interface AttemptSummary {
  attemptId: string;
  lessonId: string;
  sentenceId: string;
  mode: PracticeMode | string;
  score: number;
  accuracy: number;
  difficulty: Difficulty | null;
  createdAt: string;
}

export interface AttemptDetail extends AttemptSummary {
  /** Dictation → `WordScore[]`, shadowing → `ShadowingWordScore[]` (raw JSON column). */
  wordScores: WordScore[] | ShadowingWordScore[] | null;
  /** Storage key, not a URL (architecture §4.2). Play it with `practiceApi.getRecordingBlob`. */
  audioRecordingUrl: string | null;
  /** Seconds. */
  duration: number | null;
}

export interface ListAttemptsParams extends PageParams {
  lessonId?: string;
  mode?: PracticeMode;
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

/** `GET /practice/stats/activity?days=` (NEW): exactly `days` items, oldest → newest, Asia/Ho_Chi_Minh. */
export interface ActivityDay {
  /** 'YYYY-MM-DD' */
  date: string;
  attempts: number;
  minutes: number;
}

/** `GET /practice/streak` */
export interface StreakResponse {
  currentStreak: number;
  lastPracticeDate: string | null;
}

/** `GET /practice/stats/trending-errors` */
export interface TrendingError {
  word: string;
  count: number;
  avgAccuracy: number;
}

/** `GET /practice/stats/by-lesson/{lessonId}` (mirrors `LessonStatsResponse`). */
export interface LessonStats {
  attempts: number;
  avgAccuracy: number;
  bestScore: number;
  lastAttempt: string | null;
}

/** `GET /practice/lessons/{lessonId}/attempts` (mirrors `LessonAttemptsResponse`). */
export interface LessonAttempts {
  attempts: AttemptSummary[];
  summary: {
    avgAccuracy: number;
    totalAttempts: number;
  };
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

export interface ListDecksParams extends PageParams {
  level?: CefrLevel;
  isProOnly?: boolean;
}

export interface CreateDeckInput {
  name: string;
  description?: string;
  level?: CefrLevel;
  isProOnly?: boolean;
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
  /** Set when the request carried a valid Bearer and the user has learned the card; anonymous → null. */
  userVocabId: string | null;
}

/** Mirrors `CardInput` (admin create cards). */
export interface CardInput {
  word: string;
  meaning: string;
  example?: string;
  exampleVi?: string;
  ipa?: string;
  audioUrl?: string;
  imageUrl?: string;
  partOfSpeech?: string;
}

/** Mirrors `UpdateCardRequest` (the word itself cannot be changed). */
export interface UpdateCardInput {
  meaning?: string;
  example?: string;
  exampleVi?: string;
  ipa?: string;
  audioUrl?: string;
  imageUrl?: string;
  partOfSpeech?: string;
}

export interface ReviewQueueItem {
  /** userVocabId */
  id: string;
  cardId: string;
  word: string;
  meaning: string;
  example: string | null;
  exampleVi: string | null;
  partOfSpeech: string | null;
  ipa: string | null;
  audioUrl: string | null;
  imageUrl: string | null;
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

/** Again = 1, Hard = 3, Good = 4, Easy = 5 (architecture §4.6); BE accepts 0–5. */
export type ReviewQuality = 0 | 1 | 2 | 3 | 4 | 5;

export interface SubmitReviewInput {
  userVocabId: string;
  /** 0–5, see `ReviewQuality`. */
  quality: number;
}

export interface SubmitReviewResult {
  userVocabId: string;
  nextReviewAt: string;
  easeFactor: number;
  interval: number;
  repetitions: number;
  message: string | null;
}

export interface AddFromLessonResult {
  userVocabId: string;
  cardId: string;
  word: string;
  meaning: string;
  nextReviewAt: string;
  status: string;
}

export interface LearnedWord {
  userVocabId: string;
  cardId: string;
  word: string;
  meaning: string;
  repetitions: number;
  accuracy: number;
}

export interface ListLearnedParams extends PageParams {
  searchWord?: string;
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
  /** JSON column; Phase 1 seed stores a Vietnamese string array. */
  features: string[] | Record<string, unknown> | null;
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
  // `autoRenew` intentionally omitted (architecture §4.5): the BE may still send it, FE never reads it.
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

export interface ActivationCode {
  id: string;
  /** Format `PROMO-XXXXX-XXXXX`. */
  code: string;
  planId: string;
  planName: string;
  usedByUserId: string | null;
  usedByEmail: string | null;
  usedAt: string | null;
  expiresAt: string;
  createdAt: string;
}

/** Body of `POST /subscriptions/admin/activation-codes` (NEW). */
export interface CreateActivationCodesInput {
  planId: string;
  /** 1–500 */
  quantity: number;
  /** ISO-8601; BE defaults to now + 365 days. */
  expiresAt?: string;
}

export interface CreateActivationCodesResult {
  codes: ActivationCode[];
}

export interface ListActivationCodesParams extends PageParams {
  used?: boolean;
}

// =====================================================================================
// Append-only regions for Wave 2 (merge order T07 → T12; keep both blocks on conflict).
// =====================================================================================

// --- T07 additions ---
// --- end T07 ---

// --- T08 additions ---
// --- end T08 ---

// --- T09 additions ---
// --- end T09 ---

// --- T10 additions ---
/**
 * SM-2 schedule fields the notebook shows when `GET /vocabs/user/learned` returns them
 * (not in `LearnedWordResponse` yet – requested from the vocabulary-service owner).
 */
export interface LearnedWordSchedule {
  nextReviewAt?: string | null;
  intervalDays?: number | null;
  easeFactor?: number | null;
}
// --- end T10 ---

// --- T11 additions ---
// --- end T11 ---

// --- T12 additions ---
/** Query params of `GET /vocabs/decks/{id}/cards` used by the admin card manager (ADM-05). */
export interface DeckCardsParams extends PageParams {
  searchWord?: string;
}
// --- end T12 ---

// --- T13 additions ---
/**
 * VOC-NB-ADD: `LearnedWordResponse` fields added by T13-BE (declaration merging keeps the base
 * interface untouched). Optional so responses without them still type-check.
 */
export interface LearnedWord {
  /** True when the card lives in the user's personal deck (a word the user added). */
  custom?: boolean;
  ipa?: string | null;
  partOfSpeech?: string | null;
  example?: string | null;
  exampleVi?: string | null;
}

/** Body of `POST /vocabs/user/words` and `PUT /vocabs/user/words/{userVocabId}` (`CreatePersonalWordRequest`). */
export interface CreatePersonalWordInput {
  word: string;
  meaning: string;
  ipa?: string | null;
  partOfSpeech?: string | null;
  example?: string | null;
  exampleVi?: string | null;
}
// --- end T13 ---
