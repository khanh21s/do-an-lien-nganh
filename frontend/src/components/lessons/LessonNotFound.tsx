import { ErrorState } from '@/components/ui/ErrorState';

/** "Không tìm thấy bài học" block used by `/lessons/[id]` (404/invalid id) and its `not-found.tsx`. */
export function LessonNotFound() {
  return (
    <ErrorState
      notFound
      title="Không tìm thấy bài học"
      message="Bài học không tồn tại hoặc đã bị xoá."
      backHref="/lessons"
      backLabel="Về danh sách bài học"
      className="mt-10"
    />
  );
}
