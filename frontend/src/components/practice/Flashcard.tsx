'use client';

import { pronounce } from '@/components/vocab/pronounce';
import { Badge } from '@/components/ui/Badge';
import clsx from 'clsx';

/** Fields shared by `VocabCard` and `ReviewQueueItem` (VOC-07). */
export interface FlashcardContent {
  word: string;
  meaning: string;
  example: string | null;
  exampleVi: string | null;
  ipa: string | null;
  audioUrl: string | null;
  imageUrl: string | null;
  partOfSpeech: string | null;
}

// Hide the inactive face at the flip's midpoint; backface-visibility alone isn't reliable in Firefox.
const FACE_SWAP = 'transition-[opacity,visibility] duration-0 delay-200';
const FACE =
  '[grid-area:1/1] flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-slate-200 p-5 text-center shadow-sm [backface-visibility:hidden] sm:p-6';

function PronounceButton({ card }: { card: FlashcardContent }) {
  return (
    <button
      type="button"
      onClick={(e) => {
        e.stopPropagation();
        pronounce(card.word, card.audioUrl);
      }}
      className="flex h-11 w-11 items-center justify-center rounded-full text-xl text-brand-600 hover:bg-brand-50 hover:text-brand-700"
      aria-label={`Nghe phát âm "${card.word}"`}
      title="Nghe phát âm"
    >
      🔊
    </button>
  );
}

/**
 * Flip card showing every card field (VOC-07): image, word, part of speech, IPA, pronunciation
 * (audioUrl or en-US speech synthesis) on the front; meaning, example and its translation on the back.
 * Controlled: the parent owns `flipped` so keyboard shortcuts and buttons share the same state.
 * `front` replaces the default front face (used by the typing mode, VOC-09).
 */
export function Flashcard({
  card,
  flipped,
  onFlip,
  front,
  badge,
}: {
  card: FlashcardContent;
  flipped: boolean;
  onFlip: () => void;
  front?: React.ReactNode;
  badge?: React.ReactNode;
}) {
  return (
    <div className="[perspective:1500px]">
      <div
        className={clsx(
          'grid transition-transform duration-500 ease-out [transform-style:preserve-3d]',
          flipped && '[transform:rotateY(180deg)]'
        )}
      >
        {/* Front face */}
        <div
          onClick={front ? undefined : onFlip}
          className={clsx(FACE, 'bg-white', !front && 'cursor-pointer select-none', FACE_SWAP, flipped && 'invisible opacity-0')}
          aria-hidden={flipped}
        >
          {front ?? (
            <>
              {badge && <div className="mb-2">{badge}</div>}
              <div className="mx-auto flex h-24 w-24 items-center justify-center overflow-hidden rounded-2xl bg-gradient-to-br from-amber-200 to-brand-200 text-4xl sm:h-28 sm:w-28">
                {card.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={card.imageUrl} alt={card.word} className="h-full w-full object-cover" />
                ) : (
                  '📖'
                )}
              </div>
              <div className="mt-4 flex flex-wrap items-center justify-center gap-2">
                <h2 className="break-all text-3xl font-bold text-brand-700">{card.word}</h2>
                {card.partOfSpeech && <Badge tone="brand">{card.partOfSpeech}</Badge>}
              </div>
              <div className="mt-1 flex items-center justify-center gap-1">
                {card.ipa && <span className="text-slate-500">/{card.ipa.replace(/^\/|\/$/g, '')}/</span>}
                <PronounceButton card={card} />
              </div>
              <p className="mt-4 text-sm font-medium text-brand-600">
                Nhấn để lật thẻ <span className="hidden sm:inline">(Space / Enter)</span>
              </p>
            </>
          )}
        </div>

        {/* Back face */}
        <div
          onClick={onFlip}
          className={clsx(
            FACE,
            'cursor-pointer select-none bg-brand-50/40 [transform:rotateY(180deg)]',
            FACE_SWAP,
            !flipped && 'invisible opacity-0'
          )}
          aria-hidden={!flipped}
        >
          <div className="flex flex-wrap items-center justify-center gap-2">
            <h2 className="break-all text-2xl font-bold text-brand-700">{card.word}</h2>
            {card.partOfSpeech && <Badge tone="brand">{card.partOfSpeech}</Badge>}
            <PronounceButton card={card} />
          </div>
          {card.ipa && <p className="text-slate-500">/{card.ipa.replace(/^\/|\/$/g, '')}/</p>}

          <div className="mt-4 w-full max-w-md text-left">
            <p className="text-xs font-semibold uppercase text-slate-400">Nghĩa</p>
            <p className="mt-1 text-lg text-slate-800">{card.meaning}</p>

            {card.example && (
              <div className="mt-4 rounded-xl bg-white p-4">
                <p className="text-xs font-semibold uppercase text-slate-400">Ví dụ</p>
                <p className="mt-1 italic text-slate-700">&ldquo;{card.example}&rdquo;</p>
                {card.exampleVi && <p className="mt-1 text-sm text-slate-500">{card.exampleVi}</p>}
              </div>
            )}
          </div>
          <p className="mt-4 text-xs text-slate-400">Nhấn để lật lại</p>
        </div>
      </div>
    </div>
  );
}
