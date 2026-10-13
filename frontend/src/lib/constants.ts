import type { CefrLevel, Difficulty } from './types';

// X-08: shared FE constants. Wave 2 tasks import these instead of re-declaring them.

export const CEFR_LEVELS: readonly CefrLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export const CEFR_LABELS: Record<CefrLevel, string> = {
  A1: 'A1 – Mới bắt đầu',
  A2: 'A2 – Sơ cấp',
  B1: 'B1 – Trung cấp',
  B2: 'B2 – Trung cấp trên',
  C1: 'C1 – Cao cấp',
  C2: 'C2 – Thành thạo',
};

export const TOPIC_TAGS = [
  'daily',
  'travel',
  'work',
  'business',
  'health',
  'education',
  'technology',
  'culture',
  'ielts',
  'toeic',
  'ted',
  'news',
  'movie',
  'song',
] as const;

export type TopicTag = (typeof TOPIC_TAGS)[number];

export const TOPIC_LABELS: Record<TopicTag, string> = {
  daily: 'Đời sống hằng ngày',
  travel: 'Du lịch',
  work: 'Công việc',
  business: 'Kinh doanh',
  health: 'Sức khoẻ',
  education: 'Giáo dục',
  technology: 'Công nghệ',
  culture: 'Văn hoá',
  ielts: 'IELTS',
  toeic: 'TOEIC',
  ted: 'TED Talks',
  news: 'Tin tức',
  movie: 'Phim ảnh',
  song: 'Bài hát',
};

export const TOPIC_ICONS: Record<TopicTag, string> = {
  daily: '🏠',
  travel: '🧳',
  work: '💼',
  business: '📈',
  health: '🏥',
  education: '🏫',
  technology: '💻',
  culture: '🎭',
  ielts: '📝',
  toeic: '🎯',
  ted: '🎤',
  news: '📰',
  movie: '🎬',
  song: '🎵',
};

export const DEFAULT_TOPIC_ICON = '📚';

export function isTopicTag(value: string | null | undefined): value is TopicTag {
  return !!value && (TOPIC_TAGS as readonly string[]).includes(value);
}

/** Label for any stored `topicTag` (unknown tags are shown as-is). */
export function topicLabel(tag: string | null | undefined): string {
  if (!tag) return 'Khác';
  return isTopicTag(tag) ? TOPIC_LABELS[tag] : tag;
}

export function topicIcon(tag: string | null | undefined): string {
  return isTopicTag(tag) ? TOPIC_ICONS[tag] : DEFAULT_TOPIC_ICON;
}

export const DIFFICULTIES: readonly Difficulty[] = ['Easy', 'Normal', 'Hard'];

export const DIFFICULTY_LABELS: Record<Difficulty, string> = {
  Easy: 'Dễ',
  Normal: 'Vừa',
  Hard: 'Khó',
};

/** DIC-04: playback speed range 0.5–1.5. */
export const PLAYBACK_RATES: readonly number[] = [0.5, 0.75, 1, 1.25, 1.5];
export const DEFAULT_PLAYBACK_RATE = 1;
export const MIN_PLAYBACK_RATE = 0.5;
export const MAX_PLAYBACK_RATE = 1.5;
