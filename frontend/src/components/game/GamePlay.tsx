'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ShortcutHelp } from '@/components/practice/ShortcutHelp';
import { pronounce } from '@/components/vocab/pronounce';
import { useHotkeys } from '@/hooks/useHotkeys';
import { apiErrorMessage } from '@/lib/api';
import { gameApi } from '@/lib/endpoints/game';
import { MODE_INFO, comboMultiplier, formatClock, hintFactor, nextComboTier, posLabel } from '@/lib/game';
import { bindAction, primaryLabel } from '@/lib/shortcuts';
import type { GameAnswerResult, GameHint, GameQuestion, GameState, GameSummary } from '@/lib/types';
import { useShortcutKeys } from '@/store/shortcut-store';
import clsx from 'clsx';
import { useCallback, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';

/** Feedback of the previous answer, shown under the input while the next question is played. */
interface Feedback {
  result: GameAnswerResult;
  typed: string;
  /** Skipped automatically because the word's timer ran out. */
  timeout: boolean;
}

/** Per-word time limits of the server (GameMode), for the per-word bar. */
const QUESTION_LIMIT_MS: Record<string, number> = { SURVIVAL: 15_000, DAILY: 20_000 };

/**
 * One round. The server is authoritative for grading, score, lives and time; the local
 * countdowns are re-synced from the server after every answer.
 * - SPRINT: a 60-second round timer.
 * - SURVIVAL / DAILY: a per-word timer (`timeLeftMs`); when it runs out the word is skipped
 *   automatically (Survival: costs a life). Survival shows the lives, Daily "Câu x/10".
 * When the round is over the summary is fetched and handed to `onFinished`.
 */
export function GamePlay({
  initial,
  onFinished,
}: {
  initial: GameState;
  onFinished: (summary: GameSummary) => void;
}) {
  const keys = useShortcutKeys();
  const sessionId = initial.sessionId;
  const [question, setQuestion] = useState<GameQuestion | null>(initial.question);
  const [score, setScore] = useState(initial.score);
  const [combo, setCombo] = useState(initial.combo);
  const [input, setInput] = useState('');
  const [hint, setHint] = useState<GameHint | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [gain, setGain] = useState<{ points: number; key: number } | null>(null);
  const [busy, setBusy] = useState(false);
  const [deadline, setDeadline] = useState(() => Date.now() + initial.remainingMs);
  const [now, setNow] = useState(() => Date.now());
  const [lives, setLives] = useState(initial.lives);
  const [questionDeadline, setQuestionDeadline] = useState(() => Date.now() + (initial.question.timeLeftMs ?? 0));
  const [done, setDone] = useState(false);
  const finishing = useRef(false);
  const timeoutSent = useRef<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const mode = initial.mode;
  const timed = initial.durationSec > 0;
  const remaining = Math.max(0, deadline - now);
  const durationMs = initial.durationSec * 1000;
  const questionLimit = QUESTION_LIMIT_MS[mode] ?? 0;
  const questionRemaining = Math.max(0, questionDeadline - now);

  const finish = useCallback(async () => {
    if (finishing.current) return;
    finishing.current = true;
    try {
      onFinished(await gameApi.finish(sessionId));
    } catch (err) {
      finishing.current = false;
      toast.error(apiErrorMessage(err, 'Không thể kết thúc ván chơi.'));
    }
  }, [onFinished, sessionId]);

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 100);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    if ((done || (timed && remaining <= 0)) && !busy) void finish();
  }, [done, timed, remaining, busy, finish]);

  // per-word timer (Survival / Daily): skip the word when it runs out, once per word
  useEffect(() => {
    if (timed || !question || busy || questionRemaining > 0 || timeoutSent.current === question.seq) return;
    timeoutSent.current = question.seq;
    void send(true, true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [timed, question, busy, questionRemaining]);

  useEffect(() => {
    inputRef.current?.focus();
  }, [question?.seq]);

  async function send(skip: boolean, timeout = false) {
    if (busy || !question || finishing.current) return;
    const typed = input.trim();
    if (!skip && !typed) {
      inputRef.current?.focus();
      return;
    }
    setBusy(true);
    try {
      const r = await gameApi.answer(sessionId, skip ? { skip: true } : { answer: typed });
      setScore(r.score);
      setCombo(r.combo);
      setLives(r.lives);
      if (r.timeUp) {
        setDone(true);
        return;
      }
      setFeedback({ result: r, typed: skip ? '' : typed, timeout });
      if (r.points > 0) setGain({ points: r.points, key: r.score });
      setInput('');
      setHint(null);
      setDeadline(Date.now() + r.remainingMs);
      setQuestion(r.nextQuestion);
      if (r.nextQuestion?.timeLeftMs != null) setQuestionDeadline(Date.now() + r.nextQuestion.timeLeftMs);
      if (r.finished || !r.nextQuestion) setDone(true);
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không gửi được đáp án.'));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  }

  async function askHint() {
    if (busy || !question || (hint && hint.hintsUsed >= hint.maxHints)) return;
    try {
      setHint(await gameApi.hint(sessionId));
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Không lấy được gợi ý.'));
    } finally {
      inputRef.current?.focus();
    }
  }

  useHotkeys([
    ...bindAction(keys('game.hint'), { handler: () => void askHint(), allowInInput: true }),
    ...bindAction(keys('game.skip'), { handler: () => void send(true), allowInInput: true }),
  ]);

  const multiplier = comboMultiplier(combo);
  const tier = nextComboTier(combo);
  const hintsUsed = hint?.hintsUsed ?? 0;
  const shown = timed ? remaining : questionRemaining;
  const shownTotal = timed ? durationMs : questionLimit;
  const low = shown <= (timed ? 10_000 : 5_000);

  return (
    <div className="mx-auto max-w-xl">
      <p className="mb-2 text-center text-xs font-medium uppercase tracking-wide text-slate-400">
        {MODE_INFO[mode].icon} {mode === 'SPRINT' ? `${MODE_INFO[mode].title} · ${initial.sourceLabel}` : initial.sourceLabel}
      </p>
      {/* status bar: time, lives / progress, score, combo */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className={clsx('text-2xl font-bold tabular-nums', low ? 'text-red-600' : 'text-slate-900')}
            aria-label={timed ? 'Thời gian còn lại' : 'Thời gian cho từ này'}
          >
            ⏱ {formatClock(shown)}
          </div>
          {lives != null && (
            <div className="text-xl" aria-label={`Còn ${lives} mạng`} data-testid="game-lives">
              {Array.from({ length: 3 }, (_, i) => (
                <span key={i} aria-hidden className={i < lives ? '' : 'opacity-25 grayscale'}>
                  ❤️
                </span>
              ))}
            </div>
          )}
          {initial.questionCount != null && question && (
            <div className="text-sm font-semibold text-slate-500" data-testid="game-progress-count">
              {question.seq}/{initial.questionCount}
            </div>
          )}
        </div>
        <div className="relative text-right">
          <div className="text-xs uppercase tracking-wide text-slate-400">Điểm</div>
          <div className="text-2xl font-bold tabular-nums text-slate-900" data-testid="game-score">
            {score}
          </div>
          {gain && (
            <span key={gain.key} className="animate-float-up absolute -top-3 right-0 text-sm font-bold text-green-600" aria-hidden>
              +{gain.points}
            </span>
          )}
        </div>
      </div>
      <div
        className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100"
        role="progressbar"
        aria-label="Thời gian"
        aria-valuemin={0}
        aria-valuemax={shownTotal}
        aria-valuenow={shown}
      >
        <div
          className={clsx('h-full rounded-full transition-[width] duration-100', low ? 'bg-red-500' : 'bg-brand-500')}
          style={{ width: `${shownTotal > 0 ? Math.min(100, (shown / shownTotal) * 100) : 0}%` }}
        />
      </div>

      <div className="mt-3 flex min-h-[28px] items-center justify-between text-sm">
        <span className={clsx('font-semibold', combo >= 5 ? 'text-orange-600' : 'text-slate-500')} data-testid="game-combo">
          🔥 Combo {combo}
          {multiplier > 1 && <span className="ml-1 rounded bg-orange-100 px-1.5 py-0.5 text-orange-700">×{multiplier}</span>}
        </span>
        {tier && combo > 0 && (
          <span className="text-xs text-slate-400">
            còn {tier.remaining} câu nữa để ×{tier.multiplier}
          </span>
        )}
      </div>

      {question ? (
        <Card className="mt-3">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs text-slate-400">Câu {question.seq}</span>
            <span className="flex items-center gap-1.5">
              {question.retry && <Badge tone="amber">Ôn lại</Badge>}
              {question.level && <Badge tone="brand">{question.level}</Badge>}
              <Badge tone="slate">{question.basePoints} điểm</Badge>
            </span>
          </div>
          <p className="mt-4 text-center text-2xl font-bold text-slate-900 sm:text-3xl" data-testid="game-meaning">
            {question.meaning}
          </p>

          {hint && (
            <div className="mt-4 rounded-lg bg-amber-50 px-3 py-2 text-center text-sm text-amber-900" role="status">
              {hint.partOfSpeech && <span className="mr-2 italic">({posLabel(hint.partOfSpeech)})</span>}
              <span className="font-mono text-base tracking-wider" data-testid="game-mask">
                {hint.mask}
              </span>
              <span className="ml-2 text-xs text-amber-700">{hint.letterCount} chữ cái</span>
              {hint.example && <p className="mt-1 italic">“{hint.example}”</p>}
            </div>
          )}

          <form
            className="mt-4"
            onSubmit={(e) => {
              e.preventDefault();
              void send(false);
            }}
          >
            <label htmlFor="game-answer" className="sr-only">
              Từ tiếng Anh
            </label>
            <input
              id="game-answer"
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              placeholder="Gõ từ tiếng Anh rồi nhấn Enter"
              className="block min-h-[52px] w-full rounded-xl border border-slate-300 px-4 text-center text-xl focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-200"
            />
            <div className="mt-3 flex flex-wrap items-center justify-center gap-2">
              <Button type="submit" className="min-h-[44px]" disabled={busy}>
                Trả lời
              </Button>
              <Button
                type="button"
                variant="secondary"
                className="min-h-[44px]"
                onClick={() => void askHint()}
                disabled={busy || hintsUsed >= 3}
                title={`Gợi ý (${primaryLabel(keys('game.hint'))})`}
              >
                💡 Gợi ý {hintsUsed > 0 ? `${hintsUsed}/3` : ''}
                {hintsUsed < 3 && <span className="text-xs text-slate-400">×{hintFactor(hintsUsed + 1)}</span>}
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="min-h-[44px]"
                onClick={() => void send(true)}
                disabled={busy}
                title={`Bỏ qua (${primaryLabel(keys('game.skip'))})`}
              >
                Bỏ qua
              </Button>
            </div>
          </form>
        </Card>
      ) : (
        <Card className="mt-3 text-center text-slate-500">Đang tổng kết…</Card>
      )}

      {feedback && <AnswerFeedback feedback={feedback} />}

      <div className="mt-4 flex items-center justify-between">
        <Button variant="ghost" size="sm" onClick={() => void finish()}>
          Kết thúc sớm
        </Button>
        <ShortcutHelp scope="game" title="Phím tắt trò chơi" />
      </div>
    </div>
  );
}

function AnswerFeedback({ feedback }: { feedback: Feedback }) {
  const r = feedback.result;
  if (!r.result || !r.correctWord) return null;
  const tone = {
    CORRECT: 'border-green-200 bg-green-50 text-green-900',
    NEAR: 'border-amber-200 bg-amber-50 text-amber-900',
    WRONG: 'border-red-200 bg-red-50 text-red-900',
    SKIPPED: 'border-slate-200 bg-slate-50 text-slate-700',
  }[r.result];
  const title = {
    CORRECT: `✅ Chính xác! +${r.points}`,
    NEAR: `🟡 Gần đúng – sai 1 chữ cái. +${r.points}`,
    WRONG: '❌ Chưa đúng',
    SKIPPED: feedback.timeout ? '⏰ Hết giờ' : '⏭ Đã bỏ qua',
  }[r.result];
  const heading = r.timedOut ? '⏰ Quá thời gian – không được tính' : title;

  return (
    <div className={clsx('mt-3 rounded-xl border px-4 py-3 text-sm', tone)} role="status" data-testid="game-feedback">
      <div className="flex items-center justify-between gap-2">
        <span className="font-semibold">
          {heading}
          {r.lives != null && (r.result === 'WRONG' || r.result === 'SKIPPED') && <span className="ml-2 text-red-600">−1 ❤️</span>}
        </span>
        {r.result === 'CORRECT' && (
          <span className="text-xs">
            {r.multiplier > 1 && `🔥 ×${r.multiplier} `}
            {r.speedBonus > 0 && `⚡ +${r.speedBonus}`}
          </span>
        )}
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-2">
        {r.result !== 'CORRECT' && feedback.typed && (
          <span>
            Bạn gõ: <s>{feedback.typed}</s> →
          </span>
        )}
        <b className="text-base">{r.correctWord}</b>
        {r.ipa && <span className="text-slate-500">{r.ipa}</span>}
        {r.partOfSpeech && <span className="italic text-slate-500">({posLabel(r.partOfSpeech)})</span>}
        <button
          type="button"
          className="rounded px-1 hover:bg-black/5"
          aria-label={`Phát âm ${r.correctWord}`}
          onClick={() => pronounce(r.correctWord!, r.audioUrl)}
        >
          🔊
        </button>
      </div>
      {r.acceptedWord && (
        <p className="mt-1 text-xs">
          Bạn dùng từ đồng nghĩa “{r.acceptedWord}” – cũng được tính đúng.
        </p>
      )}
      {r.result !== 'CORRECT' && r.example && <p className="mt-1 text-xs italic">{r.example}</p>}
    </div>
  );
}
