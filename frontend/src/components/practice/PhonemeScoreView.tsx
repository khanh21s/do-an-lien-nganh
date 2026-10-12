import clsx from 'clsx';
import type { ShadowingWordScore } from '@/lib/types';

const STATUS_CLASSES: Record<string, string> = {
  green: 'bg-green-100 text-green-800',
  yellow: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-800',
};

export function PhonemeScoreView({ words }: { words: ShadowingWordScore[] }) {
  if (!words?.length) return null;
  return (
    <div className="flex flex-wrap gap-2">
      {words.map((w, i) => (
        <div
          key={`${w.word}-${i}`}
          className={clsx('rounded-lg px-3 py-1.5 text-center', STATUS_CLASSES[w.status] ?? 'bg-slate-100 text-slate-700')}
          title={w.phonemes?.map((p) => `${p.phoneme}: ${p.accuracy.toFixed(0)}%`).join(', ')}
        >
          <div className="font-medium">{w.word}</div>
          <div className="text-xs opacity-80">{w.wordAccuracy.toFixed(0)}%</div>
        </div>
      ))}
    </div>
  );
}
