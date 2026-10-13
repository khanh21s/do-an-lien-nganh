'use client';

import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';

/** Destructive-action confirmation (replaces `window.confirm` in admin screens). */
export function ConfirmModal({
  open,
  title,
  message,
  confirmLabel = 'Xoá',
  loading,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: React.ReactNode;
  confirmLabel?: string;
  loading?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Modal open={open} onClose={loading ? () => undefined : onClose} title={title} className="max-w-md">
      <div className="text-sm text-slate-600">{message}</div>
      <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button variant="secondary" onClick={onClose} disabled={loading} className="min-h-[44px]">
          Huỷ
        </Button>
        <Button variant="danger" onClick={onConfirm} loading={loading} className="min-h-[44px]">
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  );
}
