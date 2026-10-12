'use client';

import { useState } from 'react';

export function AudioPlayer({ src, className }: { src: string; className?: string }) {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <p className="rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-500">
        🔇 Audio chưa khả dụng (chưa cấu hình lưu trữ audio thật).
      </p>
    );
  }

  return (
    <audio controls preload="none" className={className ?? 'w-full'} onError={() => setFailed(true)}>
      <source src={src} />
      Trình duyệt của bạn không hỗ trợ phát audio.
    </audio>
  );
}
