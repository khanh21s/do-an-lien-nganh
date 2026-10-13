# T13-FE – Users add their own words to the notebook: report

Branch `feat/T13-fe-add-word` (from `61d015f`). Built against the T13 API contract in `docs/tasks.md`; it has not been run against T13-BE.

## Done
- **Contract additions** (all inside `// --- T13 additions ---` blocks):
  - `types.ts`: `CreatePersonalWordInput`. `LearnedWord` gets optional `custom`, `ipa`, `partOfSpeech`, `example`, `exampleVi` through interface declaration merging, so the base interface is unchanged and older responses still type-check.
  - `endpoints/vocabulary.ts`: `createPersonalWord` (POST `/vocabs/user/words`) and `updatePersonalWord` (PUT `/vocabs/user/words/{userVocabId}`). Both return `LearnedWord`.
  - `queryKeys.ts`: an empty marker block. No new keys were needed.
- **`components/vocab/addWordForm.ts`** holds pure helpers, covered by 11 vitest tests:
  - `normalizeAddWordForm` trims text, collapses whitespace and turns empty strings into `null`. It also strips `/…/` or `[…]` from IPA.
  - `validateAddWordForm` checks that word and meaning are filled in, plus the contract length limits (100/500/100/50/500/500).
  - `initialFormValues` builds the starting form values.
  - `cleanClickedWord` turns `today?` into `today` and `"Hello,` into `Hello`. It leaves `don't` and `well-known` unchanged and turns `don’t` into `don't`.
  - `clickableWords` splits a sentence into the words that can be clicked.
- **`components/vocab/AddWordModal.tsx`** has create and edit modes.
  - Props: `open`, `onClose`, `prefill`, `editing`, `onSaved`.
  - Fields: Từ, Nghĩa, Phiên âm (IPA), Từ loại, Ví dụ, Nghĩa ví dụ. Từ and Nghĩa are required and get inline errors.
  - It is a `<form>`, so Enter in any field submits.
  - A backend `error` (for example 409 "Từ này đã có trong sổ tay") shows in an alert inside the dialog.
  - Fields and buttons are disabled while saving, and it cannot be closed while saving.
  - On success it shows the toast "Đã thêm vào sổ tay" or "Đã cập nhật" and invalidates `qk.learnedWordsAll`, `qk.vocabStats` and `qk.reviewQueue`.
- **Notebook page**:
  - A "+ Thêm từ" button.
  - A "Tự thêm" badge (purple) and a ✎ edit button, both only on words with `custom`.
  - IPA, part of speech and example / exampleVi are shown when present.
  - The empty state has a CTA "+ Thêm từ đầu tiên". When a search finds nothing, a "+ Thêm từ mới" button opens the modal with the search term as the word.
- **Dictation (after a sentence is submitted)**:
  - Each word of the answer sentence is a button (new `AddableSentence`), shown when the answer is visible. The answer is visible once the sentence is correct or after "Xem đáp án".
  - In `WordDiffView` (new optional `onWordClick` prop), words that are not masked are also buttons. Masked wrong words stay plain, so this does not reveal the answer.
  - Each button has `aria-label="Thêm '<word>' vào sổ tay"`, a hover/focus ring or highlight, and a dotted underline. A hint line says "Bấm vào một từ để thêm vào sổ tay".
  - The modal is prefilled with `word`, `example` = `sentence.text` and `exampleVi` = `sentence.translationVi`.
- **Shortcuts (checked)**:
  - `useHotkeys` returns early whenever `[role="dialog"][aria-modal="true"]` is in the DOM, and `Modal` renders those attributes. So Tab, Enter, `` ` `` and Ctrl+H do not fire while the modal is open, and Enter submits the form.
  - When the modal is closed, Enter on a focused word button clicks it, because the dictation Enter binding skips native buttons (`isNativeActivation`).
  - When the modal closes, focus goes back to the word button that opened it.

## Verification (Docker, architecture §1.1)
`npm ci && tsc --noEmit && next lint && vitest run`: tsc passed, lint reported no warnings, and vitest passed 145/145 tests in 15 files (including the 11 new ones). `next build` also passed.

## Contract assumptions / notes
- POST and PUT return a `LearnedWordResponse` object. The FE only uses it for `onSaved`, so another 2xx body would also work.
- The FE validates the same limits as the BE, but the BE stays the authority, and its `error` text is shown as-is.
- A word clicked in dictation keeps its case (a word at the start of a sentence stays capitalised), and the user can edit it before saving. Words from `WordDiffView` come from the BE grader, which lower-cases them.
- `WordDiffView` without `onWordClick` (as in history `AttemptDetailModal`) renders exactly as before.
