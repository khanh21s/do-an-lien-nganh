'use client';

import { useMounted } from '@/components/layout/AppShell';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { KbdCombo } from '@/components/ui/Kbd';
import { FullPageSpinner } from '@/components/ui/Spinner';
import { comboToLabels } from '@/hooks/useHotkeys';
import {
  MAX_COMBOS_PER_ACTION,
  SHORTCUT_ACTIONS,
  SHORTCUT_SCOPES,
  comboFromEvent,
  normalizeCombo,
  validateCombo,
  type ShortcutAction,
} from '@/lib/shortcuts';
import { useShortcutKeys, useShortcutStore } from '@/store/shortcut-store';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import toast from 'react-hot-toast';

/** Which combo is being recorded: `index` = the slot to replace, `null` = add a new one. */
interface Recording {
  actionId: string;
  index: number | null;
}

/** Customisable keyboard shortcuts (stored in this browser). */
export default function ShortcutsSettingsPage() {
  const mounted = useMounted(); // the store reads localStorage: render after mount to avoid a hydration mismatch
  const [recording, setRecording] = useState<Recording | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const overrides = useShortcutStore((s) => s.overrides);
  const { setCombos, resetAction, resetScope } = useShortcutStore.getState();
  const keys = useShortcutKeys();

  // While recording, capture the next key press before any page shortcut sees it.
  useEffect(() => {
    if (!recording) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.isComposing) return;
      e.preventDefault();
      e.stopPropagation();
      if (e.key === 'Escape') {
        setRecording(null);
        return;
      }
      const combo = comboFromEvent(e);
      if (!combo || !recording) return; // only modifiers so far
      const { actionId, index } = recording;
      const current = keys(actionId);
      const others = index === null ? current : current.filter((_, i) => i !== index);
      const error = validateCombo(actionId, combo, overrides, others);
      if (error) {
        setErrors((m) => ({ ...m, [actionId]: error }));
        return; // stay in recording mode so another key can be tried
      }
      const next = index === null ? [...current, combo] : current.map((c, i) => (i === index ? combo : c));
      setCombos(actionId, next);
      setErrors((m) => ({ ...m, [actionId]: '' }));
      setRecording(null);
      toast.success(`Đã gán ${comboToLabels(combo).join('+')}`);
    }
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [recording, overrides, keys, setCombos]);

  if (!mounted) return <FullPageSpinner />;

  function removeCombo(action: ShortcutAction, index: number) {
    const next = keys(action.id).filter((_, i) => i !== index);
    setCombos(action.id, next);
  }

  return (
    <div className="mx-auto max-w-3xl">
      <Link href="/profile" className="text-sm text-slate-500 hover:text-slate-700">
        ← Hồ sơ
      </Link>
      <h1 className="mt-2 text-2xl font-bold text-slate-900">Phím tắt</h1>
      <p className="mt-1 text-sm text-slate-600">
        Bấm <strong>Đổi</strong> hoặc <strong>+ Thêm phím</strong> rồi nhấn tổ hợp phím mong muốn (ví dụ{' '}
        <KbdCombo keys={['Ctrl', 'K']} />
        ). Nhấn <KbdCombo keys={['Esc']} /> để huỷ. Mỗi thao tác có tối đa {MAX_COMBOS_PER_ACTION} tổ hợp. Cài đặt được lưu
        trên trình duyệt này.
      </p>

      {SHORTCUT_SCOPES.map(({ scope, title, note }) => {
        const actions = SHORTCUT_ACTIONS.filter((a) => a.scope === scope);
        const customised = actions.some((a) => overrides[a.id]);
        return (
          <Card key={scope} className="mt-6">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="font-semibold text-slate-900">{title}</h2>
              <Button
                type="button"
                variant="ghost"
                disabled={!customised}
                onClick={() => {
                  resetScope(scope);
                  setErrors({});
                  toast.success(`Đã khôi phục phím mặc định cho ${title}`);
                }}
              >
                Khôi phục mặc định
              </Button>
            </div>
            {note && <p className="mt-1 text-xs text-slate-500">{note}</p>}
            <ul className="mt-3 divide-y divide-slate-100">
              {actions.map((action) => {
                const combos = keys(action.id);
                const isCustom = !!overrides[action.id];
                const recordingHere = recording?.actionId === action.id;
                return (
                  <li key={action.id} className="py-3" data-action={action.id}>
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <span className="text-sm font-medium text-slate-800">
                        {action.description}
                        {isCustom && <span className="ml-2 text-xs font-normal text-brand-600">(đã đổi)</span>}
                      </span>
                      <div className="flex flex-wrap items-center gap-2">
                        {combos.map((combo, i) => {
                          const active = recordingHere && recording?.index === i;
                          return (
                            <span
                              key={normalizeCombo(combo)}
                              className="inline-flex items-center gap-1 rounded-lg border border-slate-200 bg-white py-1 pl-2 pr-1"
                            >
                              {active ? (
                                <span className="animate-pulse text-xs text-brand-600">Nhấn phím…</span>
                              ) : (
                                <KbdCombo keys={comboToLabels(combo)} />
                              )}
                              <button
                                type="button"
                                onClick={() => setRecording(active ? null : { actionId: action.id, index: i })}
                                className="rounded px-1.5 py-0.5 text-xs text-brand-600 hover:bg-brand-50"
                                aria-label={`Đổi phím ${comboToLabels(combo).join('+')} của "${action.description}"`}
                              >
                                {active ? 'Huỷ' : 'Đổi'}
                              </button>
                              {combos.length > 1 && (
                                <button
                                  type="button"
                                  onClick={() => removeCombo(action, i)}
                                  className="rounded px-1.5 py-0.5 text-xs text-slate-400 hover:bg-slate-100 hover:text-red-600"
                                  aria-label={`Xoá phím ${comboToLabels(combo).join('+')} của "${action.description}"`}
                                >
                                  ✕
                                </button>
                              )}
                            </span>
                          );
                        })}
                        {combos.length < MAX_COMBOS_PER_ACTION &&
                          (recordingHere && recording?.index === null ? (
                            <button
                              type="button"
                              onClick={() => setRecording(null)}
                              className="animate-pulse rounded-lg border border-dashed border-brand-400 px-2 py-1.5 text-xs text-brand-600"
                            >
                              Nhấn phím… (Huỷ)
                            </button>
                          ) : (
                            <button
                              type="button"
                              onClick={() => setRecording({ actionId: action.id, index: null })}
                              className="rounded-lg border border-dashed border-slate-300 px-2 py-1.5 text-xs text-slate-500 hover:border-brand-400 hover:text-brand-600"
                              aria-label={`Thêm phím cho "${action.description}"`}
                            >
                              + Thêm phím
                            </button>
                          ))}
                        {isCustom && (
                          <button
                            type="button"
                            onClick={() => {
                              resetAction(action.id);
                              setErrors((m) => ({ ...m, [action.id]: '' }));
                            }}
                            className="rounded px-1.5 py-1 text-xs text-slate-500 hover:bg-slate-100"
                            aria-label={`Khôi phục phím mặc định của "${action.description}"`}
                          >
                            Mặc định
                          </button>
                        )}
                      </div>
                    </div>
                    {errors[action.id] && (
                      <p role="alert" className="mt-1.5 text-xs text-red-600">
                        {errors[action.id]}
                      </p>
                    )}
                  </li>
                );
              })}
            </ul>
          </Card>
        );
      })}
    </div>
  );
}
