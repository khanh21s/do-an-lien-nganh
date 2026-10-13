'use client';

import { KbdCombo } from '@/components/ui/Kbd';
import { Modal } from '@/components/ui/Modal';
import { comboToLabels, useHotkeys } from '@/hooks/useHotkeys';
import { SHORTCUT_ACTIONS, type ShortcutScope } from '@/lib/shortcuts';
import { useShortcutKeys } from '@/store/shortcut-store';
import clsx from 'clsx';
import Link from 'next/link';
import { useState } from 'react';

/**
 * "?" button (and `?` key) listing the shortcuts of the current screen — the user's own
 * combos when customised (see /profile/shortcuts), otherwise the defaults from `lib/shortcuts`.
 */
export function ShortcutHelp({
  scope,
  title = 'Phím tắt',
  className,
}: {
  scope: ShortcutScope;
  title?: string;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  const keys = useShortcutKeys();
  const actions = SHORTCUT_ACTIONS.filter((a) => a.scope === scope);

  useHotkeys([{ combo: '?', handler: () => setOpen(true) }]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-label="Xem phím tắt"
        title="Phím tắt (?)"
        className={clsx(
          'flex h-11 w-11 items-center justify-center rounded-full border border-slate-300 bg-white text-base font-semibold text-slate-600 hover:bg-slate-50',
          className
        )}
      >
        ?
      </button>
      <Modal open={open} onClose={() => setOpen(false)} title={title}>
        <ul className="divide-y divide-slate-100">
          {actions.map((a) => (
            <li key={a.id} className="flex items-center justify-between gap-4 py-2.5">
              <span className="text-sm text-slate-700">{a.description}</span>
              <span className="flex flex-wrap items-center justify-end gap-2">
                {keys(a.id).map((c, i) => (
                  <span key={c} className="inline-flex items-center gap-2">
                    {i > 0 && <span className="text-xs text-slate-400">hoặc</span>}
                    <KbdCombo keys={comboToLabels(c)} />
                  </span>
                ))}
              </span>
            </li>
          ))}
        </ul>
        <p className="mt-4 border-t border-slate-100 pt-3 text-right text-sm">
          <Link
            href="/profile/shortcuts"
            onClick={() => setOpen(false)}
            className="font-medium text-brand-600 hover:underline"
          >
            Tuỳ chỉnh phím tắt →
          </Link>
        </p>
      </Modal>
    </>
  );
}
