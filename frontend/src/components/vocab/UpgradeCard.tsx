import { Card } from '@/components/ui/Card';
import Link from 'next/link';

/** VOC-08: shown when vocabulary-service answers 403 for a Pro deck. `message` = backend `error` text. */
export function UpgradeCard({ message, deckName }: { message?: string; deckName?: string }) {
  return (
    <Card className="mx-auto flex max-w-md flex-col items-center gap-3 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-pro-gradient text-2xl" aria-hidden>
        👑
      </div>
      <h2 className="text-lg font-semibold text-slate-900">
        {deckName ? `“${deckName}” là bộ từ Premium` : 'Bộ từ Premium'}
      </h2>
      <p className="text-sm text-slate-600">{message || 'Bộ từ Pro – nâng cấp Premium để học.'}</p>
      <div className="mt-1 flex flex-wrap justify-center gap-2">
        <Link
          href="/subscription"
          className="inline-flex min-h-[44px] items-center justify-center rounded-lg bg-pro-gradient px-5 py-2 text-sm font-semibold text-white shadow-sm hover:opacity-90"
        >
          Nâng cấp Premium
        </Link>
        <Link
          href="/vocabulary"
          className="inline-flex min-h-[44px] items-center justify-center rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Chọn bộ từ khác
        </Link>
      </div>
    </Card>
  );
}
