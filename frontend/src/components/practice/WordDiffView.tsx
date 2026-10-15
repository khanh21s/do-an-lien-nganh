import clsx from 'clsx';
import { cleanClickedWord } from '@/components/vocab/addWordForm';
import type { WordScore } from '@/lib/types';

/**
 * Word-by-word dictation result (DIC-09). `words` comes from the BE grader, where `word` is the
 * expected (normalised) word: correct words are green, wrong / missing / revealed words are red.
 */
export function WordDiffView({
  words,
  className,
  showLegend = true,
  hideWrong = false,
  onWordClick,
}: {
  words: WordScore[];
  className?: string;
  showLegend?: boolean;
  /** Mask wrong words (e.g. "•••") so the learner can retry without seeing the answer. */
  hideWrong?: boolean;
  /** T13: makes every visible (unmasked) word a button, e.g. to add it to the notebook. */
  onWordClick?: (word: string) => void;
}) {
  if (!words?.length) return null;
  const sorted = words.slice().sort((a, b) => a.position - b.position);
  const wrong = sorted.filter((w) => !w.isCorrect).length;

  return (
    <div className={className}>
      <p className="flex flex-wrap gap-x-1.5 gap-y-2 text-lg leading-relaxed" aria-label="Kết quả từng từ">
        {sorted.map((w) => {
          const masked = !w.isCorrect && hideWrong;
          const label = w.expected || w.word;
          const clickWord = onWordClick && !masked ? cleanClickedWord(label) : '';
          const content = (
            <>
              {masked ? '•'.repeat(Math.min(8, Math.max(2, label.length))) : label}
              {!w.isCorrect && <span className="sr-only"> (sai)</span>}
            </>
          );
          const cls = clsx(
              'rounded px-1.5',
              w.isCorrect
                ? 'bg-green-100 text-green-800'
                : 'bg-red-100 text-red-800 underline decoration-red-400 decoration-wavy underline-offset-4'
          );
          const title = w.isCorrect ? 'Đúng' : hideWrong ? 'Sai' : `Sai – đáp án: ${label}`;
          return clickWord ? (
            <button
              key={w.position}
              type="button"
              onClick={() => onWordClick?.(clickWord)}
              aria-label={`Tra từ '${clickWord}'`}
              title={`${title} · Bấm để tra từ`}
              className={clsx(
                cls,
                'cursor-pointer transition-shadow hover:ring-2 hover:ring-brand-400 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500'
              )}
            >
              {content}
            </button>
          ) : (
            <span key={w.position} className={cls} title={title}>
              {content}
            </span>
          );
        })}
      </p>
      {showLegend && (
        <p className="mt-2 text-xs text-slate-500">
          <span className="mr-3 inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-green-200" aria-hidden /> Đúng
          </span>
          <span className="inline-flex items-center gap-1">
            <span className="h-2.5 w-2.5 rounded-sm bg-red-200" aria-hidden /> Sai / bị lộ ({wrong})
          </span>
        </p>
      )}
    </div>
  );
}
