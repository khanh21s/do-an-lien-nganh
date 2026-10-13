import { qk } from '@/lib/queryKeys';
import type { QueryClient } from '@tanstack/react-query';

/** After any lesson/sentence change: admin list, public lists/search and the lesson detail (LES-07). */
export function invalidateLessonCaches(queryClient: QueryClient, lessonId?: string) {
  queryClient.invalidateQueries({ queryKey: qk.adminLessonsAll });
  queryClient.invalidateQueries({ queryKey: qk.lessonsAll });
  queryClient.invalidateQueries({ queryKey: ['lesson-search'] });
  if (lessonId) queryClient.invalidateQueries({ queryKey: qk.lesson(lessonId) });
}

/** After any deck/card change: admin + public deck lists and card lists. */
export function invalidateVocabCaches(queryClient: QueryClient, deckId?: string) {
  queryClient.invalidateQueries({ queryKey: qk.adminDecks });
  queryClient.invalidateQueries({ queryKey: qk.decksAll });
  queryClient.invalidateQueries({ queryKey: deckId ? ['admin-cards', deckId] : qk.adminCardsAll });
  queryClient.invalidateQueries({ queryKey: deckId ? ['deck-cards', deckId] : qk.deckCardsAll });
}
