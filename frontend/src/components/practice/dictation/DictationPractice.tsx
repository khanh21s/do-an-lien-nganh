'use client';

import { PracticeHeader } from '@/components/practice/PracticeHeader';
import { SentencePlayer } from '@/components/practice/SentencePlayer';
import { DICTATION_SHORTCUTS, ShortcutHelp } from '@/components/practice/ShortcutHelp';
import { WordDiffView } from '@/components/practice/WordDiffView';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { AddWordModal } from '@/components/vocab/AddWordModal';
import { useHotkeys, type HotkeyBinding } from '@/hooks/useHotkeys';
import { usePracticeNav } from '@/hooks/usePracticeNav';
import { useSentenceAudio } from '@/hooks/useSentenceAudio';
import { apiErrorMessage } from '@/lib/api';
import {
  DIFFICULTY_WEIGHTS,
  blankMask,
  composeHardInput,
  composeUserInput,
  nextHintIndex,
  splitTyped,
  tokenize,
  unrevealedBlanks,
  wordCorrectness,
  wordsOf,
} from '@/lib/dictation';
import { practiceApi } from '@/lib/endpoints/practice';
import { addTimeSpent, ensureSession, incrementCounter } from '@/lib/practice-session';
import { qk } from '@/lib/queryKeys';
import type { DictationAttemptResult, Difficulty, LessonDetail, SentenceResponse } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import { AddableSentence } from './AddableSentence';
import { DifficultyPopover } from './DifficultyPopover';
import { playCorrectFeedback } from './feedback';
import { DICTATION_INPUT_ATTR, WordBlanks } from './WordBlanks';

const MODE = 'dictation' as const;
/** Cap per visit so a tab left open overnight does not distort "tổng thời gian". */
const MAX_VISIT_MS = 30 * 60 * 1000;

function isNativeActivation(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  return !!el?.closest?.('button, a, select, [role="radio"], [role="switch"]');
}

/**
 * One dictation sentence (DIC-02…DIC-09). Mount it with `key={sentence.id}` so every sentence starts
 * with a fresh answer, timer and audio state.
 */
export function DictationPractice({
  lesson,
  sentence,
  difficulty,
  onDifficultyChange,
}: {
  lesson: LessonDetail;
  sentence: SentenceResponse;
  difficulty: Difficulty;
  onDifficultyChange: (d: Difficulty) => void;
}) {
  const lessonId = lesson.id;
  const queryClient = useQueryClient();
  const nav = usePracticeNav(lessonId, MODE, lesson.sentences, sentence.id);
  const audio = useSentenceAudio({
    lessonId,
    sentence,
    lessonAudioUrl: lesson.audioUrl,
    mode: MODE,
    autoPlay: true,
  });

  const tokens = useMemo(() => tokenize(sentence.text), [sentence.text]);
  const words = useMemo(() => wordsOf(tokens), [tokens]);
  const isHard = difficulty === 'Hard';
  const mask = useMemo(() => blankMask(words.length, difficulty), [words.length, difficulty]);

  const [typed, setTyped] = useState<string[]>(() => words.map(() => ''));
  const [hardText, setHardText] = useState('');
  const [revealed, setRevealed] = useState<Set<number>>(() => new Set());
  const [focused, setFocused] = useState<number | null>(null);
  const [result, setResult] = useState<DictationAttemptResult | null>(null);
  const [correctness, setCorrectness] = useState<(boolean | null)[] | null>(null);
  const [showAnswer, setShowAnswer] = useState(false);
  // T13: word picked from the answer / feedback → AddWordModal (page shortcuts pause while it is open).
  const [wordToAdd, setWordToAdd] = useState<string | null>(null);
  const closeAddWord = useCallback(() => setWordToAdd(null), []);
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const isCorrect = result?.accuracy === 100;

  // Time spent on this sentence (DIC-08 → result screen).
  useEffect(() => {
    ensureSession(lessonId, MODE);
    const start = Date.now();
    return () => {
      addTimeSpent(lessonId, MODE, sentence.id, Math.min(Date.now() - start, MAX_VISIT_MS));
    };
  }, [lessonId, sentence.id]);

  const focusFirstBlank = useCallback(() => {
    if (isHard) {
      textareaRef.current?.focus();
      return;
    }
    const first = mask.findIndex((c, i) => c === 'blank' && !revealed.has(i));
    if (first >= 0) inputRefs.current[first]?.focus();
  }, [isHard, mask, revealed]);

  // Fresh answer whenever the difficulty changes.
  const firstRender = useRef(true);
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
    } else {
      setTyped(words.map(() => ''));
      setHardText('');
      setRevealed(new Set());
      setResult(null);
      setCorrectness(null);
      setShowAnswer(false);
    }
    const id = window.setTimeout(focusFirstBlank, 0);
    return () => window.clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- reset only on difficulty change
  }, [difficulty]);

  const mutation = useMutation({
    mutationFn: (userInput: string) =>
      practiceApi.submitDictation({ lessonId, sentenceId: sentence.id, userInput, difficulty }),
    onSuccess: (res) => {
      setResult(res);
      setCorrectness(isHard ? null : wordCorrectness(tokens, res.wordScores));
      if (res.accuracy === 100) playCorrectFeedback();
      // Shared numbers (architecture §6.2) + dashboard/topbar (T07, T11 requests).
      for (const queryKey of [
        qk.practiceStats,
        qk.streak,
        qk.lessonAttempts(lessonId),
        qk.lessonStats(lessonId),
        qk.activityAll,
        qk.trendingErrors,
        qk.attemptsAll,
      ]) {
        void queryClient.invalidateQueries({ queryKey });
      }
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể chấm điểm, vui lòng thử lại.')),
  });

  const hasAnswer = isHard
    ? splitTyped(hardText).length > 0 || revealed.size > 0
    : revealed.size > 0 || typed.some((v, i) => mask[i] === 'blank' && v.trim() !== '');

  const submit = useCallback(() => {
    if (mutation.isPending) return;
    if (!hasAnswer) {
      toast.error('Hãy nhập những gì bạn nghe được.');
      focusFirstBlank();
      return;
    }
    const userInput = isHard
      ? composeHardInput(hardText, words.length, revealed)
      : composeUserInput(tokens, mask, typed, revealed);
    incrementCounter(lessonId, MODE, sentence.id, 'submits');
    mutation.mutate(userInput);
  }, [mutation, hasAnswer, focusFirstBlank, isHard, hardText, words.length, revealed, tokens, mask, typed, lessonId, sentence.id]);

  const reveal = useCallback(
    (indices: number[]) => {
      if (indices.length === 0) return;
      setRevealed((prev) => {
        const next = new Set(prev);
        indices.forEach((i) => next.add(i));
        return next;
      });
      incrementCounter(lessonId, MODE, sentence.id, 'hints', indices.length);
    },
    [lessonId, sentence.id]
  );

  const hint = useCallback(() => {
    if (isCorrect) return;
    const hardMask = isHard ? blankMask(words.length, 'Hard') : mask;
    const typedNow = isHard ? splitTyped(hardText) : typed;
    const idx = nextHintIndex(words, hardMask, typedNow, revealed, isHard ? null : focused);
    if (idx < 0) {
      toast('Không còn từ nào để gợi ý.', { icon: '💡' });
      return;
    }
    reveal([idx]);
    if (!isHard) {
      // Move on to the next editable blank.
      const nextBlank = mask.findIndex((c, i) => i > idx && c === 'blank' && !revealed.has(i));
      if (nextBlank >= 0) window.setTimeout(() => inputRefs.current[nextBlank]?.focus(), 0);
    }
  }, [isCorrect, isHard, words, mask, hardText, typed, revealed, focused, reveal]);

  const revealAll = useCallback(() => {
    if (isCorrect) return;
    reveal(unrevealedBlanks(isHard ? blankMask(words.length, 'Hard') : mask, revealed));
  }, [isCorrect, isHard, words.length, mask, revealed, reveal]);

  const onType = useCallback((i: number, value: string) => {
    setTyped((prev) => {
      const next = [...prev];
      next[i] = value;
      return next;
    });
    setCorrectness((prev) => {
      if (!prev || prev[i] === null) return prev;
      const next = [...prev];
      next[i] = null;
      return next;
    });
  }, []);

  // DIC-05: spec §3.3 shortcuts (ignored while a modal is open — handled by useHotkeys).
  const bindings: HotkeyBinding[] = [
    {
      combo: 'Tab',
      allowInInput: true,
      preventDefault: false,
      handler: (e) => {
        const t = e.target as HTMLElement | null;
        const inAnswer = !t || t === document.body || !!t.hasAttribute?.(DICTATION_INPUT_ATTR);
        if (!inAnswer) return; // normal focus navigation on buttons/links
        e.preventDefault();
        audio.toggle();
      },
    },
    { combo: 'Ctrl+Space', allowInInput: true, handler: () => audio.toggle() },
    { combo: '`', allowInInput: true, handler: () => audio.replay() },
    { combo: 'Ctrl+R', allowInInput: true, handler: () => audio.replay() },
    {
      combo: 'Enter',
      allowInInput: true,
      preventDefault: false,
      handler: (e) => {
        if (isNativeActivation(e.target)) return;
        e.preventDefault();
        if (isCorrect) nav.goNextOrFinish();
        else submit();
      },
    },
    { combo: 'Shift+ArrowRight', allowInInput: true, handler: () => nav.goNext() },
    { combo: 'Mod+ArrowRight', allowInInput: true, handler: () => nav.goNext() },
    { combo: 'Shift+ArrowLeft', allowInInput: true, handler: () => nav.goPrev() },
    { combo: 'Mod+ArrowLeft', allowInInput: true, handler: () => nav.goPrev() },
    { combo: 'Ctrl+H', allowInInput: true, handler: () => hint() },
  ];
  useHotkeys(bindings);

  const hardHint = isHard && revealed.size > 0;
  const maxScore = Math.round(DIFFICULTY_WEIGHTS[difficulty] * 100);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <PracticeHeader
        lessonId={lessonId}
        lessonTitle={lesson.title}
        mode={MODE}
        sentences={lesson.sentences}
        currentId={sentence.id}
        right={
          <div className="flex items-center gap-2">
            <DifficultyPopover value={difficulty} onChange={onDifficultyChange} />
            <ShortcutHelp shortcuts={DICTATION_SHORTCUTS} title="Phím tắt Dictation" />
          </div>
        }
      />

      <Card className="flex flex-col gap-5 p-4 sm:p-5">
        <SentencePlayer audio={audio} />

        <div>
          <p className="mb-2 text-sm font-medium text-slate-600">
            {isHard ? 'Gõ lại toàn bộ câu bạn nghe được' : 'Điền các từ còn thiếu'}
          </p>
          {isHard ? (
            <textarea
              ref={textareaRef}
              {...{ [DICTATION_INPUT_ATTR]: '' }}
              value={hardText}
              onChange={(e) => setHardText(e.target.value)}
              rows={3}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              aria-label="Câu bạn nghe được"
              placeholder="Nhập câu bạn nghe được…"
              className="w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-lg outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
            />
          ) : (
            <WordBlanks
              tokens={tokens}
              mask={mask}
              typed={typed}
              revealed={revealed}
              correctness={correctness}
              inputRefs={inputRefs}
              onType={onType}
              onFocusWord={setFocused}
            />
          )}
          {hardHint && (
            <p className="mt-2 break-words text-sm text-amber-700">
              💡 Gợi ý:{' '}
              {words.map((w, i) => (
                <span key={i} className={clsx('mr-1', revealed.has(i) ? 'font-semibold' : 'text-slate-400')}>
                  {revealed.has(i) ? w : '___'}
                </span>
              ))}
            </p>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {isCorrect ? (
            <Button variant="success" onClick={nav.goNextOrFinish} className="min-h-[44px]">
              {nav.isLast ? 'Hoàn thành bài' : 'Câu kế →'} <span className="text-xs opacity-80">(Enter)</span>
            </Button>
          ) : (
            <Button onClick={submit} loading={mutation.isPending} className="min-h-[44px]">
              {result ? 'Nộp lại' : 'Kiểm tra'} <span className="text-xs opacity-80">(Enter)</span>
            </Button>
          )}
          <Button variant="secondary" onClick={hint} disabled={isCorrect} className="min-h-[44px]" title="Ctrl+H">
            💡 Gợi ý 1 từ
          </Button>
          <Button variant="ghost" onClick={revealAll} disabled={isCorrect} className="min-h-[44px]">
            Hiện tất cả
          </Button>
          <Button variant="ghost" onClick={nav.goNextOrFinish} className="min-h-[44px]">
            {nav.isLast ? 'Bỏ qua & kết thúc' : 'Bỏ qua câu'}
          </Button>
        </div>
        {!isHard && !result && (
          <p className="-mt-2 text-xs text-slate-400">
            Space: ô kế · Backspace ở ô trống: ô trước · Ctrl+H hoặc nút Gợi ý: lộ từ ở ô đang chọn (tính là sai)
          </p>
        )}
      </Card>

      {result && (
        <Card
          className={clsx('flex flex-col gap-4 p-4 sm:p-5', isCorrect ? 'border-green-300' : 'border-amber-300')}
          aria-live="polite"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className={clsx('text-lg font-semibold', isCorrect ? 'text-green-700' : 'text-amber-700')}>
              {isCorrect ? '🎉 Chính xác!' : 'Chưa đúng, thử lại'}
            </p>
            <div className="text-right">
              <p className="text-2xl font-bold text-brand-600">
                {Number(result.score.toFixed(1))}
                <span className="text-sm font-normal text-slate-400">/{maxScore}</span>
              </p>
              <p className="text-xs text-slate-500">
                Chính xác {Number(result.accuracy.toFixed(1))}% · {result.correctWords}/{result.totalWords} từ
              </p>
            </div>
          </div>
          <WordDiffView words={result.wordScores} hideWrong={!isCorrect && !showAnswer} onWordClick={setWordToAdd} />
          {!isCorrect && !showAnswer && (
            <button
              type="button"
              onClick={() => setShowAnswer(true)}
              className="-mt-2 self-start text-sm text-brand-600 hover:underline"
            >
              Xem đáp án
            </button>
          )}
          <div className="rounded-xl bg-slate-50 p-3 text-sm">
            {(isCorrect || showAnswer) && <AddableSentence text={sentence.text} onPick={setWordToAdd} />}
            {sentence.ipa && <p className="mt-1 text-slate-500">/{sentence.ipa.replace(/^\/|\/$/g, '')}/</p>}
            {sentence.translationVi && <p className="mt-1 text-slate-600">🇻🇳 {sentence.translationVi}</p>}
          </div>
          <p className="-mt-2 text-xs text-slate-400">💡 Bấm vào một từ để thêm vào sổ tay.</p>
        </Card>
      )}

      <AddWordModal
        open={wordToAdd !== null}
        onClose={closeAddWord}
        prefill={
          wordToAdd !== null
            ? { word: wordToAdd, example: sentence.text, exampleVi: sentence.translationVi ?? null }
            : null
        }
      />
    </div>
  );
}
