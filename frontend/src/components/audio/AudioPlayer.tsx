'use client';

import clsx from 'clsx';
import { useEffect, useState } from 'react';

/**
 * Plain `<audio controls>` with a fallback when the file cannot be loaded (LES-06).
 * `src` is set directly on `<audio>` (not on a `<source>` child) so its `error` event fires reliably.
 */
export function AudioPlayer({
  src,
  className,
  preload = 'none',
  fallbackText = 'Audio chưa khả dụng',
}: {
  src: string;
  className?: string;
  preload?: 'none' | 'metadata' | 'auto';
  fallbackText?: string;
}) {
  const [failed, setFailed] = useState(false);

  // A new source gets a fresh chance.
  useEffect(() => setFailed(false), [src]);

  if (failed) {
    return (
      <p role="status" className={clsx('rounded-lg bg-slate-100 px-3 py-2 text-sm text-slate-500', className)}>
        🔇 {fallbackText}
      </p>
    );
  }

  return (
    <audio
      key={src}
      src={src}
      controls
      preload={preload}
      className={className ?? 'w-full'}
      onError={() => setFailed(true)}
    >
      Trình duyệt của bạn không hỗ trợ phát audio.
    </audio>
  );
}
