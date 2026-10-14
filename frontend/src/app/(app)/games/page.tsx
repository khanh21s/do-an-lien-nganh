'use client';

import { GameLobby } from '@/components/game/GameLobby';
import { GamePlay } from '@/components/game/GamePlay';
import { GameSummary } from '@/components/game/GameSummary';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import { apiErrorMessage } from '@/lib/api';
import { gameApi } from '@/lib/endpoints/game';
import { qk } from '@/lib/queryKeys';
import { storageKeys } from '@/lib/storageKeys';
import type { GameState, GameSummary as Summary, StartGameInput } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import toast from 'react-hot-toast';

type Phase = { kind: 'lobby' } | { kind: 'play'; state: GameState } | { kind: 'summary'; summary: Summary };

/** T15 "Dịch nhanh": lobby → 60-second round → summary (→ replay with the same source). */
export default function GamesPage() {
  const queryClient = useQueryClient();
  const [choice, setChoice] = useLocalStorage<StartGameInput>(storageKeys.gameSource, { source: 'DECK' });
  const [lastStart, setLastStart] = useState<StartGameInput | null>(null);
  const [phase, setPhase] = useState<Phase>({ kind: 'lobby' });

  const startMutation = useMutation({
    mutationFn: gameApi.start,
    onSuccess: (state, input) => {
      setLastStart(input);
      setPhase({ kind: 'play', state });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể bắt đầu ván chơi.')),
  });

  function start(input: StartGameInput) {
    setChoice({ ...choice, ...input });
    startMutation.mutate(input);
  }

  if (phase.kind === 'play') {
    return (
      <GamePlay
        key={phase.state.sessionId}
        initial={phase.state}
        onFinished={(summary) => {
          queryClient.invalidateQueries({ queryKey: qk.gameStats });
          if (summary.srsUpdated > 0) {
            queryClient.invalidateQueries({ queryKey: qk.vocabStats });
            queryClient.invalidateQueries({ queryKey: qk.reviewQueue });
            queryClient.invalidateQueries({ queryKey: qk.learnedWordsAll });
          }
          setPhase({ kind: 'summary', summary });
        }}
      />
    );
  }

  if (phase.kind === 'summary') {
    return (
      <GameSummary
        summary={phase.summary}
        replaying={startMutation.isPending}
        onReplay={() => lastStart && startMutation.mutate(lastStart)}
        onChangeSource={() => setPhase({ kind: 'lobby' })}
      />
    );
  }

  return <GameLobby choice={choice} onChoiceChange={setChoice} onStart={start} starting={startMutation.isPending} />;
}
