'use client';

import { AddWordModal } from '@/components/vocab/AddWordModal';
import type { AddWordPrefill } from '@/components/vocab/addWordForm';
import { useState } from 'react';
import { WordInfoModal } from './WordInfoModal';
import { prefillFromEntry } from './wordInfo';

/**
 * P2-C click-a-word flow: dictionary sheet first, then (on "Thêm vào sổ tay / bộ thẻ") the
 * existing AddWordModal pre-filled from the lookup, with deck/group choice. Only one dialog is
 * open at a time. Render it once per page; `word` null = closed.
 */
export function WordLookup({
  word,
  example,
  exampleVi,
  onClose,
}: {
  word: string | null;
  /** The sentence the word was clicked in (becomes the card's example). */
  example?: string | null;
  exampleVi?: string | null;
  onClose: () => void;
}) {
  const [prefill, setPrefill] = useState<AddWordPrefill | null>(null);

  function close() {
    setPrefill(null);
    onClose();
  }

  return (
    <>
      <WordInfoModal
        word={prefill ? null : word}
        onClose={close}
        onAdd={(entry) => setPrefill(prefillFromEntry(entry, word ?? '', { example, exampleVi }))}
      />
      <AddWordModal open={word !== null && prefill !== null} onClose={close} prefill={prefill} chooseDeck />
    </>
  );
}
