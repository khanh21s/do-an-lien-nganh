'use client';

import { Flashcard, type FlashcardContent } from '@/components/practice/Flashcard';
import { Button } from '@/components/ui/Button';
import { RatingButtons } from '@/components/vocab/RatingButtons';
import { pronounce } from '@/components/vocab/pronounce';
import { useHotkeys } from '@/hooks/useHotkeys';
import { bindAction } from '@/lib/shortcuts';
import { useShortcutKeys } from '@/store/shortcut-store';
import { SRS_RATINGS, isTypedAnswerCorrect, maskWord, suggestedQuality, type SrsState } from '@/lib/srs';
import { useRef, useState } from 'react';

interface TypingResult {
  input: string;
  correct: boolean;
  /** The back (answer) had already been shown before this attempt. */
  afterPeek: boolean;
}

/**
 * One flashcard + rating buttons + keyboard shortcuts, shared by `decks/[id]` and `review` (VOC-02).
 * Shortcuts (customisable, defaults Space/Enter flip; 1–4 rate when flipped, otherwise they only flip).
 * Typing mode (VOC-09): front shows the meaning + masked example, Enter checks the typed word;
 * correct → Good is suggested, wrong → Again.
 *
 * Render with `key={cardId}` so the flip/typing state resets for every card.
 */
export function StudyCard({
  card,
  srsState,
  onRate,
  busy,
  typingMode,
  badge,
}: {
  card: FlashcardContent;
  srsState: SrsState | null | undefined;
  onRate: (quality: number) => void;
  busy: boolean;
  typingMode: boolean;
  badge?: React.ReactNode;
}) {
  const [flipped, setFlipped] = useState(false);
  const [input, setInput] = useState('');
  const [result, setResult] = useState<TypingResult | null>(null);
  // Set once the back (answer) has been shown, so a retry after peeking is still suggested as "Again".
  const [peeked, setPeeked] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function flip() {
    // Typing mode: flipping back to the front brings the input back so the word can be typed again.
    if (flipped && typingMode && result) {
      setResult(null);
      setInput('');
    }
    if (!flipped) setPeeked(true);
    setFlipped((f) => !f);
  }

  function rate(quality: number) {
    if (busy) return;
    onRate(quality);
  }

  function check(given: string) {
    const correct = isTypedAnswerCorrect(given, card.word);
    setResult({ input: given, correct, afterPeek: peeked });
    setPeeked(true);
    setFlipped(true);
    inputRef.current?.blur();
    if (correct) pronounce(card.word, card.audioUrl);
  }

  const keys = useShortcutKeys();
  useHotkeys([
    ...bindAction(keys('flashcard.flip'), { handler: flip }),
    ...SRS_RATINGS.flatMap((r) =>
      bindAction(keys(`flashcard.${r.name}`), { handler: () => (flipped ? rate(r.quality) : flip()) })
    ),
  ]);

  const typingFront =
    typingMode && !result ? (
      <form
        className="flex w-full max-w-md flex-col items-center gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (input.trim()) check(input);
        }}
      >
        {badge}
        <p className="text-xs font-semibold uppercase text-slate-400">Gõ từ tiếng Anh có nghĩa</p>
        <p className="text-xl font-semibold text-slate-900">{card.meaning}</p>
        {card.partOfSpeech && <p className="text-sm text-slate-500">({card.partOfSpeech})</p>}
        {card.example && (
          <p className="text-sm italic text-slate-600">&ldquo;{maskWord(card.example, card.word)}&rdquo;</p>
        )}
        <input
          ref={inputRef}
          autoFocus
          value={input}
          onChange={(e) => setInput(e.target.value)}
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          aria-label="Nhập từ tiếng Anh"
          placeholder="Nhập từ rồi nhấn Enter…"
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-center text-lg outline-none focus:border-brand-500 focus:ring-1 focus:ring-brand-500"
        />
        <div className="flex flex-wrap justify-center gap-2">
          <Button type="submit" disabled={!input.trim()} className="min-h-[44px]">
            Kiểm tra
          </Button>
          <Button type="button" variant="secondary" className="min-h-[44px]" onClick={() => check('')}>
            Không biết
          </Button>
        </div>
      </form>
    ) : undefined;

  return (
    <div>
      <Flashcard card={card} flipped={flipped} onFlip={flip} front={typingFront} badge={badge} />

      {result && (
        <div
          role="status"
          className={
            result.correct
              ? 'mt-3 rounded-xl bg-green-50 px-4 py-2 text-center text-sm text-green-700'
              : 'mt-3 rounded-xl bg-red-50 px-4 py-2 text-center text-sm text-red-700'
          }
        >
          {result.correct && result.afterPeek ? (
            'Chính xác! Bạn đã xem đáp án trước đó – gợi ý: chấm "Học lại".'
          ) : result.correct ? (
            'Chính xác! Gợi ý: chấm "Tốt".'
          ) : (
            <>
              {result.input ? <>Bạn gõ &ldquo;{result.input}&rdquo; – </> : null}
              đáp án là <strong>{card.word}</strong>. Gợi ý: chấm &ldquo;Học lại&rdquo;.
            </>
          )}
        </div>
      )}

      {flipped ? (
        <RatingButtons
          state={srsState}
          onRate={rate}
          disabled={busy}
          suggestedQuality={result ? suggestedQuality(result.correct && !result.afterPeek) : null}
        />
      ) : (
        !typingFront && (
          <div className="pt-4 text-center">
            <Button type="button" variant="secondary" className="min-h-[44px]" onClick={flip}>
              Lật thẻ
            </Button>
          </div>
        )
      )}
    </div>
  );
}
