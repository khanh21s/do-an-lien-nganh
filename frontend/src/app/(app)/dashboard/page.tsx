'use client';

import { Card } from '@/components/ui/Card';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { practiceApi } from '@/lib/endpoints/practice';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { useAuthStore } from '@/store/auth-store';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['practice-stats'],
    queryFn: practiceApi.getStats,
  });
  const { data: vocabStats, isLoading: vocabLoading } = useQuery({
    queryKey: ['vocab-stats'],
    queryFn: vocabularyApi.getStats,
  });

  if (statsLoading || vocabLoading) return <FullPageSpinner />;

  return (
    <div>
      <h1 className="text-2xl font-bold text-slate-900">Chào mừng trở lại, {user?.email}!</h1>
      <p className="mt-1 text-slate-500">Đây là tổng quan tiến trình học tập của bạn.</p>

      <div className="mt-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard icon="🔥" tint="bg-amber-100" label="Chuỗi ngày học" value={`${stats?.currentStreak ?? 0} ngày`} />
        <StatCard icon="🏆" tint="bg-violet-100" label="Chuỗi dài nhất" value={`${stats?.longestStreak ?? 0} ngày`} />
        <StatCard icon="📚" tint="bg-brand-100" label="Bài học đã hoàn thành" value={stats?.lessonsCompleted ?? 0} />
        <StatCard icon="⭐" tint="bg-emerald-100" label="Điểm trung bình" value={(stats?.averageScore ?? 0).toFixed(1)} />
        <StatCard icon="✍️" tint="bg-brand-100" label="Độ chính xác Dictation" value={`${(stats?.dictationAccuracy ?? 0).toFixed(1)}%`} />
        <StatCard icon="🗣️" tint="bg-rose-100" label="Độ chính xác Shadowing" value={`${(stats?.shadowingAccuracy ?? 0).toFixed(1)}%`} />
        <StatCard icon="💎" tint="bg-brand-100" label="Từ cần ôn hôm nay" value={vocabStats?.dueToday ?? 0} />
        <StatCard icon="🗂️" tint="bg-emerald-100" label="Tổng từ đã học" value={vocabStats?.totalLearned ?? 0} />
      </div>

      <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <QuickLink href="/lessons" label="Tiếp tục học bài" icon="📚" tint="from-brand-200 to-brand-100" />
        <QuickLink href="/vocabulary/review" label="Ôn tập từ vựng" icon="🧠" tint="from-violet-200 to-brand-100" />
        <QuickLink href="/practice/history" label="Xem lịch sử luyện tập" icon="📈" tint="from-emerald-200 to-brand-100" />
      </div>
    </div>
  );
}

function StatCard({ icon, tint, label, value }: { icon: string; tint: string; label: string; value: string | number }) {
  return (
    <Card>
      <span className={`flex h-9 w-9 items-center justify-center rounded-lg text-lg ${tint}`}>{icon}</span>
      <p className="mt-3 text-sm text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold text-slate-900">{value}</p>
    </Card>
  );
}

function QuickLink({ href, label, icon, tint }: { href: string; label: string; icon: string; tint: string }) {
  return (
    <Link href={href}>
      <Card className="flex items-center gap-3 transition-shadow hover:shadow-md">
        <span className={`flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br text-xl ${tint}`}>{icon}</span>
        <span className="font-medium text-slate-800">{label}</span>
      </Card>
    </Link>
  );
}
