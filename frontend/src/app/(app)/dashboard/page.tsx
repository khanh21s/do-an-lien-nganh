'use client';

import { ActivityHeatmap } from '@/components/dashboard/ActivityHeatmap';
import { ContinueLearning } from '@/components/dashboard/ContinueLearning';
import { GameWidget } from '@/components/dashboard/GameWidget';
import { SentenceReviewCard } from '@/components/dashboard/SentenceReviewCard';
import { formatHours, formatPercent } from '@/components/dashboard/dashboard-data';
import { StatCard, WidgetError } from '@/components/dashboard/StatCard';
import { TrendingErrors } from '@/components/dashboard/TrendingErrors';
import { WeeklyAccuracyChart } from '@/components/dashboard/WeeklyAccuracyChart';
import { Card } from '@/components/ui/Card';
import { ErrorState } from '@/components/ui/ErrorState';
import { FullPageSpinner, Spinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

const ACTIVITY_DAYS = 90;

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);

  // Core numbers: without them the page shows ErrorState (AUTH-08 pattern, architecture §6.3).
  const statsQuery = useQuery({ queryKey: qk.practiceStats, queryFn: practiceApi.getStats });
  // `/practice/stats` may be cached up to 1h by the BE; `/practice/streak` is live (PRA-07).
  const streakQuery = useQuery({ queryKey: qk.streak, queryFn: practiceApi.getStreak });
  const vocabQuery = useQuery({ queryKey: qk.vocabStats, queryFn: vocabularyApi.getStats });
  const activityQuery = useQuery({
    queryKey: qk.activity(ACTIVITY_DAYS),
    queryFn: () => practiceApi.getActivity(ACTIVITY_DAYS),
  });
  const trendingQuery = useQuery({ queryKey: qk.trendingErrors, queryFn: practiceApi.getTrendingErrors });

  if (statsQuery.isLoading) return <FullPageSpinner />;
  if (statsQuery.isError) {
    return (
      <ErrorState
        className="mt-10"
        error={statsQuery.error}
        title="Không tải được tổng quan"
        onRetry={() => {
          statsQuery.refetch();
          streakQuery.refetch();
          activityQuery.refetch();
          trendingQuery.refetch();
          vocabQuery.refetch();
        }}
      />
    );
  }
  const stats = statsQuery.data;
  if (!stats) return <ErrorState notFound />;

  const currentStreak = streakQuery.data?.currentStreak ?? stats.currentStreak;
  // Stats may lag behind the live streak; the longest streak can never be below the current one.
  const longestStreak = Math.max(stats.longestStreak, currentStreak);
  const vocab = vocabQuery.data;
  const vocabValue = (n: number | undefined) => (vocabQuery.isLoading ? '…' : vocabQuery.isError ? '—' : n ?? 0);
  const vocabHint = vocabQuery.isError ? 'Không tải được số liệu từ vựng' : undefined;
  const weekly = stats.accuracy?.weekly ?? [];
  const dates = stats.accuracy?.dates ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="break-words text-2xl font-bold text-slate-900">Chào mừng trở lại{user?.email ? `, ${user.email}` : ''}!</h1>
        <p className="mt-1 text-slate-500">Đây là tổng quan tiến trình học tập của bạn.</p>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <StatCard
          icon="🔥"
          tint="bg-amber-100"
          label="Chuỗi ngày học"
          value={`${currentStreak} ngày`}
          hint={`Dài nhất: ${longestStreak} ngày`}
        />
        <StatCard icon="⏱️" tint="bg-sky-100" label="Tổng thời gian học" value={formatHours(stats.totalHoursLearned)} />
        <StatCard icon="📚" tint="bg-brand-100" label="Bài đã hoàn thành" value={stats.lessonsCompleted} />
        <StatCard icon="⭐" tint="bg-emerald-100" label="Điểm trung bình" value={(Math.round(stats.averageScore * 10) / 10).toString()} />
        <StatCard icon="✍️" tint="bg-brand-100" label="Độ chính xác Dictation" value={formatPercent(stats.dictationAccuracy)} />
        <StatCard icon="🗣️" tint="bg-rose-100" label="Độ chính xác Shadowing" value={formatPercent(stats.shadowingAccuracy)} />
        <StatCard icon="🗂️" tint="bg-emerald-100" label="Số từ đã học" value={vocabValue(vocab?.totalLearned)} hint={vocabHint} />
        <StatCard
          icon="🧠"
          tint="bg-violet-100"
          label="Thẻ cần ôn hôm nay"
          value={vocabValue(vocab?.dueToday)}
          hint={vocabHint ?? 'Ôn ngay →'}
          href="/vocabulary/review"
        />
      </div>

      <SentenceReviewCard />

      <GameWidget />

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="font-semibold text-slate-900">Độ chính xác theo tuần</h2>
          <div className="mt-3">
            <WeeklyAccuracyChart weekly={weekly} dates={dates} />
          </div>
        </Card>
        <ContinueLearning />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <h2 className="font-semibold text-slate-900">Hoạt động {ACTIVITY_DAYS} ngày qua</h2>
          <div className="mt-2">
            {activityQuery.isLoading ? (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            ) : activityQuery.isError ? (
              <WidgetError message="Không tải được dữ liệu hoạt động." onRetry={() => activityQuery.refetch()} />
            ) : (
              <ActivityHeatmap data={activityQuery.data ?? []} days={ACTIVITY_DAYS} />
            )}
          </div>
        </Card>
        <Card>
          <h2 className="font-semibold text-slate-900">Từ hay sai</h2>
          <div className="mt-3">
            {trendingQuery.isLoading ? (
              <div className="flex justify-center py-8">
                <Spinner />
              </div>
            ) : trendingQuery.isError ? (
              <WidgetError message="Không tải được danh sách từ hay sai." onRetry={() => trendingQuery.refetch()} />
            ) : (
              <TrendingErrors errors={trendingQuery.data ?? []} />
            )}
          </div>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:gap-4">
        <QuickLink href="/lessons" label="Khám phá bài học" icon="📚" tint="from-brand-200 to-brand-100" />
        <QuickLink href="/vocabulary/review" label="Ôn tập từ vựng" icon="🧠" tint="from-violet-200 to-brand-100" />
        <QuickLink href="/practice/history" label="Lịch sử luyện tập" icon="📈" tint="from-emerald-200 to-brand-100" />
      </div>
    </div>
  );
}

function QuickLink({ href, label, icon, tint }: { href: string; label: string; icon: string; tint: string }) {
  return (
    <Link href={href} className="block rounded-2xl focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500">
      <Card className="flex min-h-[44px] items-center gap-3 transition-shadow hover:shadow-md">
        <span className={`flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br text-xl ${tint}`} aria-hidden>
          {icon}
        </span>
        <span className="font-medium text-slate-800">{label}</span>
      </Card>
    </Link>
  );
}
