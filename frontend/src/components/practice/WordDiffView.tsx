import clsx from 'clsx';
import type { WordScore } from '@/lib/types';

export function WordDiffView({ words }: { words: WordScore[] }) {
  if (!words?.length) return null;
  return (
    <p className="flex flex-wrap gap-x-1.5 gap-y-2 text-lg leading-relaxed">
      {words
        .slice()
        .sort((a, b) => a.position - b.position)
        .map((w) => (
          <span
            key={w.position}
            className={clsx(
              'rounded px-1',
              w.isCorrect ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800 line-through decoration-2'
            )}
            title={w.isCorrect ? undefined : `Đáp án đúng: ${w.expected}`}
          >
            {w.word || w.expected}
          </span>
        ))}
    </p>
  );
}
