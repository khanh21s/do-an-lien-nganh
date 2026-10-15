'use client';

import { ConfirmModal } from '@/components/admin/ConfirmModal';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { apiErrorMessage } from '@/lib/api';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import type { VocabGroup } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import toast from 'react-hot-toast';
import { moveGroup, validateGroupName } from './myDecks';

/**
 * P2-C: create / rename / reorder / delete the groups of one deck (personal: owner, public: admin;
 * the server enforces it). Deleting a group keeps its cards, ungrouped.
 */
export function GroupManager({ deckId, groups }: { deckId: string; groups: VocabGroup[] }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ id: string; name: string } | null>(null);
  const [toDelete, setToDelete] = useState<VocabGroup | null>(null);
  const sorted = [...groups].sort((a, b) => a.sortOrder - b.sortOrder);

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: qk.deckDetail(deckId) });
    void queryClient.invalidateQueries({ queryKey: ['deck-cards', deckId] });
    void queryClient.invalidateQueries({ queryKey: qk.myDecks });
  }

  const create = useMutation({
    mutationFn: (n: string) => vocabularyApi.createGroup(deckId, { name: n }),
    onSuccess: () => {
      setName('');
      toast.success('Đã tạo nhóm');
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không tạo được nhóm.')),
  });
  const rename = useMutation({
    mutationFn: ({ id, n }: { id: string; n: string }) => vocabularyApi.updateGroup(deckId, id, { name: n }),
    onSuccess: () => {
      setEditing(null);
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không đổi được tên nhóm.')),
  });
  const reorder = useMutation({
    mutationFn: (ids: string[]) => vocabularyApi.reorderGroups(deckId, ids),
    onSuccess: refresh,
    onError: (err) => toast.error(apiErrorMessage(err, 'Không sắp xếp được nhóm.')),
  });
  const remove = useMutation({
    mutationFn: (id: string) => vocabularyApi.deleteGroup(deckId, id),
    onSuccess: () => {
      setToDelete(null);
      toast.success('Đã xoá nhóm');
      refresh();
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Không xoá được nhóm.')),
  });

  function submitNew(e: React.FormEvent) {
    e.preventDefault();
    const err = validateGroupName(name, groups);
    setNameError(err);
    if (!err) create.mutate(name.trim().replace(/\s+/g, ' '));
  }

  const busy = reorder.isPending;

  return (
    <Card className="p-4">
      <h2 className="font-semibold text-slate-900">Nhóm trong bộ thẻ</h2>
      <p className="mt-1 text-xs text-slate-500">Chia bộ thẻ thành các phần nhỏ (Unit 1, Chủ đề…) để học theo từng nhóm.</p>

      <form onSubmit={submitNew} className="mt-3 flex items-start gap-2">
        <div className="flex-1">
          <Input
            aria-label="Tên nhóm mới"
            placeholder="Tên nhóm mới"
            value={name}
            maxLength={110}
            error={nameError ?? undefined}
            onChange={(e) => {
              setName(e.target.value);
              setNameError(null);
            }}
            className="min-h-[44px] w-full"
          />
        </div>
        <Button type="submit" className="min-h-[44px]" loading={create.isPending}>
          + Thêm
        </Button>
      </form>

      {sorted.length === 0 ? (
        <p className="mt-3 text-sm text-slate-400">Chưa có nhóm nào.</p>
      ) : (
        <ul className="mt-3 flex flex-col divide-y divide-slate-100">
          {sorted.map((g, i) => (
            <li key={g.id} className="flex flex-wrap items-center gap-2 py-2">
              {editing?.id === g.id ? (
                <form
                  className="flex flex-1 items-center gap-2"
                  onSubmit={(e) => {
                    e.preventDefault();
                    const err = validateGroupName(editing.name, groups, g.id);
                    if (err) return void toast.error(err);
                    rename.mutate({ id: g.id, n: editing.name.trim() });
                  }}
                >
                  <Input
                    aria-label="Tên nhóm"
                    value={editing.name}
                    autoFocus
                    onChange={(e) => setEditing({ id: g.id, name: e.target.value })}
                    className="min-h-[44px] w-full"
                  />
                  <Button type="submit" size="sm" className="min-h-[44px]" loading={rename.isPending}>
                    Lưu
                  </Button>
                  <Button type="button" size="sm" variant="ghost" className="min-h-[44px]" onClick={() => setEditing(null)}>
                    Huỷ
                  </Button>
                </form>
              ) : (
                <>
                  <span className="min-w-0 flex-1 break-words text-sm font-medium text-slate-800">
                    {g.name} <span className="font-normal text-slate-400">· {g.cardCount} thẻ</span>
                  </span>
                  <div className="flex shrink-0 gap-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="min-h-[44px] min-w-[44px]"
                      aria-label={`Đưa nhóm ${g.name} lên`}
                      disabled={i === 0 || busy}
                      onClick={() => reorder.mutate(moveGroup(groups, g.id, -1))}
                    >
                      ↑
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="min-h-[44px] min-w-[44px]"
                      aria-label={`Đưa nhóm ${g.name} xuống`}
                      disabled={i === sorted.length - 1 || busy}
                      onClick={() => reorder.mutate(moveGroup(groups, g.id, 1))}
                    >
                      ↓
                    </Button>
                    <Button size="sm" variant="secondary" className="min-h-[44px]" onClick={() => setEditing({ id: g.id, name: g.name })}>
                      Đổi tên
                    </Button>
                    <Button size="sm" variant="danger" className="min-h-[44px]" onClick={() => setToDelete(g)}>
                      Xoá
                    </Button>
                  </div>
                </>
              )}
            </li>
          ))}
        </ul>
      )}

      <ConfirmModal
        open={!!toDelete}
        title="Xoá nhóm?"
        message={
          <>
            Nhóm <strong>{toDelete?.name}</strong> sẽ bị xoá. {toDelete?.cardCount ?? 0} thẻ trong nhóm vẫn được giữ lại trong bộ
            (không thuộc nhóm nào).
          </>
        }
        loading={remove.isPending}
        onConfirm={() => toDelete && remove.mutate(toDelete.id)}
        onClose={() => setToDelete(null)}
      />
    </Card>
  );
}
