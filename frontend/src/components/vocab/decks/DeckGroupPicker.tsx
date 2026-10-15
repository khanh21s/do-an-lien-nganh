'use client';

import { Input, Select } from '@/components/ui/Input';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { useQuery } from '@tanstack/react-query';

export interface DeckChoice {
  id: string;
  label: string;
  isNotebook?: boolean;
}

/** Sentinel group value: create a group named `newGroupName` (import only). */
export const NEW_GROUP = '__new__';

/**
 * P2-C: deck + group selects. Groups of the selected deck are loaded from GET /vocabs/decks/{id}.
 * `groupId` '' = no group; `NEW_GROUP` (when `allowNewGroup`) shows a name input.
 */
export function DeckGroupPicker({
  decks,
  deckId,
  groupId,
  onChange,
  allowNewGroup = false,
  newGroupName = '',
  onNewGroupNameChange,
  disabled,
  deckLabel = 'Lưu vào bộ thẻ',
}: {
  decks: DeckChoice[];
  deckId: string;
  groupId: string;
  onChange: (deckId: string, groupId: string) => void;
  allowNewGroup?: boolean;
  newGroupName?: string;
  onNewGroupNameChange?: (name: string) => void;
  disabled?: boolean;
  deckLabel?: string;
}) {
  const detail = useQuery({
    queryKey: qk.deckDetail(deckId),
    queryFn: () => vocabularyApi.getDeckDetail(deckId),
    enabled: !!deckId,
  });
  const groups = detail.data?.groups ?? [];

  return (
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <Select
        label={deckLabel}
        value={deckId}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value, '')}
        className="min-h-[44px]"
      >
        {decks.map((d) => (
          <option key={d.id} value={d.id}>
            {d.label}
          </option>
        ))}
      </Select>
      <div className="flex flex-col gap-2">
        <Select
          label="Nhóm"
          value={groupId}
          disabled={disabled || !deckId || detail.isLoading}
          onChange={(e) => onChange(deckId, e.target.value)}
          className="min-h-[44px]"
          hint={detail.isError ? 'Không tải được danh sách nhóm.' : undefined}
        >
          <option value="">Không thuộc nhóm nào</option>
          {groups.map((g) => (
            <option key={g.id} value={g.id}>
              {g.name} ({g.cardCount})
            </option>
          ))}
          {allowNewGroup && <option value={NEW_GROUP}>+ Tạo nhóm mới…</option>}
        </Select>
        {allowNewGroup && groupId === NEW_GROUP && (
          <Input
            aria-label="Tên nhóm mới"
            placeholder="Tên nhóm mới"
            value={newGroupName}
            maxLength={100}
            disabled={disabled}
            onChange={(e) => onNewGroupNameChange?.(e.target.value)}
            className="min-h-[44px]"
          />
        )}
      </div>
    </div>
  );
}
