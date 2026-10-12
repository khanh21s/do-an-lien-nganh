'use client';

import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import type { VocabCard, VocabDeck } from '@/lib/types';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import clsx from 'clsx';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

const TOPIC_ICONS = ['👪', '🏫', '💼', '🍜', '🧳', '🏥', '🛍️', '🎉', '⚽', '🎬', '💻', '🌦️'];
function iconFor(index: number) {
  return TOPIC_ICONS[index % TOPIC_ICONS.length];
}

const GRADE_OPTIONS = [
  { quality: 0, label: 'Học lại', hint: '10 phút', className: 'bg-red-500 hover:bg-red-600' },
  { quality: 2, label: 'Khó', hint: '1-3 ngày', className: 'bg-amber-500 hover:bg-amber-600' },
  { quality: 3, label: 'Tốt', hint: '1-4 ngày', className: 'bg-green-500 hover:bg-green-600' },
  { quality: 5, label: 'Dễ', hint: '1-7 ngày', className: 'bg-brand-500 hover:bg-brand-600' },
];

export default function DeckDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const [index, setIndex] = useState(0);
  const [flipped, setFlipped] = useState(false);
  // cardId -> userVocabId for words already in the user's notebook, filled in as we discover/add them,
  // so re-grading an already-saved word submits a real SM-2 review instead of hitting a 409.
  const [knownUserVocabIds, setKnownUserVocabIds] = useState<Record<string, string>>({});

  const { data: decks } = useQuery({ queryKey: ['decks'], queryFn: () => vocabularyApi.listDecks({ limit: 50 }) });
  const { data, isLoading, error } = useQuery({
    queryKey: ['deck-cards', params.id],
    queryFn: () => vocabularyApi.getDeckCards(params.id, { limit: 100 }),
    retry: false,
  });
  const { data: learned } = useQuery({
    queryKey: ['learned-map'],
    queryFn: () => vocabularyApi.getLearned({ limit: 500 }),
  });

  useEffect(() => {
    setIndex(0);
    setFlipped(false);
  }, [params.id]);

  const cards = data?.data ?? [];
  const current = cards[index];
  const currentDeck = decks?.data.find((d) => d.id === params.id);

  function userVocabIdFor(cardId: string): string | undefined {
    return knownUserVocabIds[cardId] ?? learned?.data.find((w) => w.cardId === cardId)?.userVocabId;
  }

  const gradeMutation = useMutation({
    mutationFn: async ({ card, quality }: { card: VocabCard; quality: number }) => {
      let userVocabId = userVocabIdFor(card.id);
      try {
        if (!userVocabId) {
          const added = await vocabularyApi.addFromLesson(card.id, 'deck');
          userVocabId = added.userVocabId;
          setKnownUserVocabIds((m) => ({ ...m, [card.id]: added.userVocabId }));
        }
        await vocabularyApi.submitReview({ userVocabId: userVocabId!, quality });
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 409) {
          // Rare race (e.g. added from another tab) — resync and let the user retry.
          queryClient.invalidateQueries({ queryKey: ['learned-map'] });
          throw new Error('Dữ liệu vừa được đồng bộ lại, vui lòng chấm điểm lại từ này.');
        }
        throw err;
      }
    },
    onSuccess: () => {
      setIndex((i) => i + 1);
      setFlipped(false);
      queryClient.invalidateQueries({ queryKey: ['vocab-stats'] });
      queryClient.invalidateQueries({ queryKey: ['learned-map'] });
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không thể lưu kết quả.')),
  });

  if (isLoading) return <FullPageSpinner />;

  if (error) {
    return (
      <Card className="mx-auto max-w-md text-center">
        <p className="text-slate-700">{apiErrorMessage(error, 'Không thể tải bộ từ vựng này.')}</p>
        <p className="mt-2 text-sm text-slate-500">Bộ từ vựng này có thể yêu cầu gói Premium.</p>
        <Link href="/subscription">
          <Button className="mt-4">Xem gói Premium</Button>
        </Link>
      </Card>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[320px_1fr]">
      <TopicList decks={decks?.data ?? []} activeId={params.id} onSelect={(id) => router.push(`/vocabulary/decks/${id}`)} />

      {!current ? (
        <Card className="flex flex-col items-center justify-center text-center">
          <p className="text-lg font-semibold text-slate-900">🎉 Bạn đã học hết bộ từ này!</p>
          <p className="mt-2 text-sm text-slate-500">Chọn một bộ từ vựng khác để tiếp tục, hoặc ôn lại từ đã lưu.</p>
          <div className="mt-4 flex flex-wrap justify-center gap-3">
            <Link href="/vocabulary/review">
              <Button>Ôn tập từ đã lưu</Button>
            </Link>
            <Link href="/vocabulary">
              <Button variant="secondary">Chọn bộ từ khác</Button>
            </Link>
          </div>
        </Card>
      ) : (
        <StudyCard
          card={current}
          index={index}
          total={cards.length}
          deckName={currentDeck?.name}
          flipped={flipped}
          onFlip={() => setFlipped((f) => !f)}
          onGrade={(quality) => gradeMutation.mutate({ card: current, quality })}
          grading={gradeMutation.isPending}
        />
      )}
    </div>
  );
}

function TopicList({
  decks,
  activeId,
  onSelect,
}: {
  decks: VocabDeck[];
  activeId: string;
  onSelect: (id: string) => void;
}) {
  return (
    <Card className="h-fit p-4">
      <p className="mb-3 px-1 text-xs font-semibold uppercase tracking-wide text-slate-400">Danh sách chủ đề</p>
      <div className="space-y-2">
        {decks.map((deck, i) => {
          const active = deck.id === activeId;
          return (
            <button
              key={deck.id}
              onClick={() => onSelect(deck.id)}
              className={clsx(
                'flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors',
                active ? 'bg-ink-800 text-white' : 'bg-slate-50 text-slate-700 hover:bg-slate-100'
              )}
            >
              <span
                className={clsx(
                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg',
                  active ? 'bg-white/15' : 'bg-white'
                )}
              >
                {iconFor(i)}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate text-sm font-semibold">{deck.name}</span>
                <span className={clsx('text-xs', active ? 'text-white/70' : 'text-slate-400')}>{deck.cardCount} thẻ</span>
              </span>
              {deck.isProOnly && <Badge tone="amber">PRO</Badge>}
            </button>
          );
        })}
        {decks.length === 0 && <p className="px-1 text-sm text-slate-500">Chưa có bộ từ vựng nào.</p>}
      </div>
    </Card>
  );
}

function StudyCard({
  card,
  index,
  total,
  deckName,
  flipped,
  onFlip,
  onGrade,
  grading,
}: {
  card: VocabCard;
  index: number;
  total: number;
  deckName?: string;
  flipped: boolean;
  onFlip: () => void;
  onGrade: (quality: number) => void;
  grading: boolean;
}) {
  return (
    <Card className="overflow-hidden p-0">
      <div className="border-b border-slate-100 px-6 py-4">
        <div className="flex items-center justify-between">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-400">
            Tiến độ{deckName ? ` — ${deckName}` : ''}
          </p>
          <p className="text-sm font-semibold text-slate-600">
            {index + 1}/{total} thẻ
          </p>
        </div>
        <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full rounded-full bg-brand-500 transition-all"
            style={{ width: `${total ? ((index + 1) / total) * 100 : 0}%` }}
          />
        </div>
      </div>

      <div className="p-6 [perspective:1500px]">
        <div
          className={clsx(
            'relative min-h-[340px] transition-transform duration-500 ease-out [transform-style:preserve-3d]',
            flipped && '[transform:rotateY(180deg)]'
          )}
        >
          {/* Front face */}
          <div
            onClick={onFlip}
            className="absolute inset-0 flex cursor-pointer select-none flex-col items-center justify-center text-center [backface-visibility:hidden]"
          >
            <div className="mx-auto flex h-28 w-28 items-center justify-center overflow-hidden rounded-2xl bg-gradient-to-br from-amber-200 to-brand-200 text-5xl">
              {card.imageUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={card.imageUrl} alt={card.word} className="h-full w-full object-cover" />
              ) : (
                '📖'
              )}
            </div>

            <div className="mt-5 flex flex-wrap items-center justify-center gap-2">
              <h2 className="text-3xl font-bold text-brand-700">{card.word}</h2>
              {card.partOfSpeech && <Badge tone="brand">{card.partOfSpeech}</Badge>}
            </div>

            <div className="mt-2 flex items-center justify-center gap-2">
              {card.ipa && <span className="text-slate-500">/{card.ipa}/</span>}
              {card.audioUrl && (
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    new Audio(card.audioUrl as string).play();
                  }}
                  className="text-brand-600 hover:text-brand-700"
                  aria-label="Nghe phát âm"
                >
                  🔊
                </button>
              )}
            </div>

            <p className="mt-6 text-sm font-medium text-brand-600">👆 Nhấn để lật thẻ và xem nghĩa</p>
          </div>

          {/* Back face */}
          <div className="absolute inset-0 flex flex-col [backface-visibility:hidden] [transform:rotateY(180deg)]">
            <div onClick={onFlip} className="flex-1 cursor-pointer select-none overflow-y-auto text-center">
              <div className="flex flex-wrap items-center justify-center gap-2">
                <h2 className="text-xl font-bold text-brand-700">{card.word}</h2>
                {card.partOfSpeech && <Badge tone="brand">{card.partOfSpeech}</Badge>}
              </div>

              <div className="mx-auto mt-4 max-w-md text-left">
                <p className="text-xs font-semibold uppercase text-slate-400">Định nghĩa</p>
                <p className="mt-1 text-slate-800">{card.meaning}</p>

                {card.example && (
                  <div className="mt-4 rounded-xl bg-slate-50 p-4">
                    <p className="text-xs font-semibold uppercase text-slate-400">Ví dụ</p>
                    <p className="mt-1 italic text-slate-700">&ldquo;{card.example}&rdquo;</p>
                    {card.exampleVi && <p className="mt-1 text-sm text-slate-500">{card.exampleVi}</p>}
                  </div>
                )}
              </div>
              <p className="mt-4 text-xs text-slate-400">👆 Nhấn để lật lại</p>
            </div>

            <div>
              <p className="pb-2 pt-4 text-center text-sm text-slate-500">Bạn nhớ từ này ở mức nào?</p>
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                {GRADE_OPTIONS.map((opt) => (
                  <button
                    key={opt.quality}
                    disabled={grading}
                    onClick={() => onGrade(opt.quality)}
                    className={clsx(
                      'flex flex-col items-center gap-0.5 rounded-xl px-3 py-3 text-sm font-semibold text-white transition-colors disabled:opacity-60',
                      opt.className
                    )}
                  >
                    {opt.label}
                    <span className="text-xs font-normal opacity-90">{opt.hint}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </Card>
  );
}
