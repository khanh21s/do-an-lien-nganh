'use client';

import { RevenueChart } from '@/components/admin/RevenueChart';
import { StatCard, WidgetError } from '@/components/dashboard/StatCard';
import { formatVnd } from '@/components/subscription/format';
import { Card } from '@/components/ui/Card';
import { Spinner } from '@/components/ui/Spinner';
import { authApi } from '@/lib/endpoints/auth';
import { contentApi } from '@/lib/endpoints/content';
import { subscriptionApi } from '@/lib/endpoints/subscription';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';

const nf = (n: number | undefined) => (n === undefined ? '…' : n.toLocaleString('vi-VN'));

/** T14: admin overview — user, subscription and content totals plus 30-day revenue. */
export default function AdminOverviewPage() {
  const authStats = useQuery({ queryKey: qk.adminAuthStats, queryFn: authApi.adminStats });
  const subStats = useQuery({ queryKey: qk.adminSubscriptionStats, queryFn: subscriptionApi.adminStats });
  const lessons = useQuery({
    queryKey: qk.adminLessonCount,
    queryFn: () => contentApi.listLessons({ page: 1, limit: 1 }),
  });
  const decks = useQuery({
    queryKey: qk.adminDeckCount,
    queryFn: () => vocabularyApi.listDecks({ page: 1, limit: 1 }),
  });

  const a = authStats.data;
  const s = subStats.data;

  return (
    <div className="mx-auto w-full max-w-6xl">
      <h1 className="text-2xl font-bold text-slate-900">Tổng quan</h1>
      <p className="mt-1 text-sm text-slate-500">Số liệu không tính tài khoản quản trị.</p>

      <section aria-label="Người dùng" className="mt-6">
        {authStats.isError ? (
          <Card>
            <WidgetError message="Không tải được thống kê người dùng." onRetry={() => authStats.refetch()} />
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard
              icon="👥"
              tint="bg-brand-100"
              label="Người dùng"
              value={nf(a?.totalUsers)}
              hint={a ? `+${nf(a.newUsersLast7Days)} trong 7 ngày` : undefined}
              href="/admin/users"
            />
            <StatCard icon="✅" tint="bg-green-100" label="Đang hoạt động" value={nf(a?.activeUsers)} />
            <StatCard icon="🔒" tint="bg-red-100" label="Đã khoá" value={nf(a?.lockedUsers)} />
            <StatCard icon="🆕" tint="bg-amber-100" label="Mới trong 30 ngày" value={nf(a?.newUsersLast30Days)} />
          </div>
        )}
      </section>

      <section aria-label="Gói & doanh thu" className="mt-4">
        {subStats.isError ? (
          <Card>
            <WidgetError message="Không tải được thống kê gói & giao dịch." onRetry={() => subStats.refetch()} />
          </Card>
        ) : (
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <StatCard
              icon="💎"
              tint="bg-violet-100"
              label="Premium đang hoạt động"
              value={nf(s?.activeSubscribers)}
              href="/admin/plans"
            />
            <StatCard
              icon="💰"
              tint="bg-green-100"
              label="Doanh thu 30 ngày"
              value={s ? formatVnd(s.revenueLast30Days) : '…'}
              hint={s ? `Tổng: ${formatVnd(s.revenueTotal)}` : undefined}
            />
            <StatCard
              icon="🧾"
              tint="bg-brand-100"
              label="Giao dịch thành công"
              value={nf(s?.successfulTransactions)}
              href="/admin/transactions"
            />
            <StatCard icon="⏳" tint="bg-amber-100" label="Đang xử lý" value={nf(s?.pendingTransactions)} />
          </div>
        )}
      </section>

      <section aria-label="Nội dung" className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard
          icon="🎧"
          tint="bg-sky-100"
          label="Bài học"
          value={lessons.isError ? '—' : nf(lessons.data?.pagination.total)}
          href="/admin/lessons"
        />
        <StatCard
          icon="📝"
          tint="bg-emerald-100"
          label="Bộ từ vựng"
          value={decks.isError ? '—' : nf(decks.data?.pagination.total)}
          href="/admin/vocabulary"
        />
      </section>

      <Card className="mt-6">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className="font-semibold text-slate-900">Doanh thu 30 ngày gần nhất</h2>
          {s && <span className="text-sm text-slate-500">{formatVnd(s.revenueLast30Days)}</span>}
        </div>
        <p className="text-xs text-slate-500">Chỉ tính giao dịch thành công, không tính mã quà tặng.</p>
        <div className="mt-4">
          {subStats.isLoading ? (
            <div className="flex justify-center py-10">
              <Spinner />
            </div>
          ) : subStats.isError ? (
            <WidgetError message="Không tải được doanh thu." onRetry={() => subStats.refetch()} />
          ) : (
            <RevenueChart days={s?.revenueByDay ?? []} />
          )}
        </div>
      </Card>
    </div>
  );
}
