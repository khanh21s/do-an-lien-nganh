'use client';

import { readLocalStorage, writeLocalStorage } from '@/lib/storageKeys';
import { useCallback, useEffect, useRef, useState } from 'react';

const SYNC_EVENT = 'parroto:local-storage';

interface SyncDetail {
  key: string;
}

/**
 * SSR-safe localStorage state (architecture §6.6). The first render uses `initialValue`
 * (so server and client markup match); the stored value is loaded right after mount.
 * Instances using the same key stay in sync (same tab via a custom event, other tabs via `storage`).
 *
 * Returns `[value, setValue, hydrated]`; `hydrated` is false until the stored value has been read.
 */
export function useLocalStorage<T>(
  key: string,
  initialValue: T
): readonly [T, (value: T | ((prev: T) => T)) => void, boolean] {
  const initialRef = useRef(initialValue);
  const [value, setValue] = useState<T>(initialValue);
  const [hydrated, setHydrated] = useState(false);
  const valueRef = useRef(value);
  valueRef.current = value;

  useEffect(() => {
    setValue(readLocalStorage(key, initialRef.current));
    setHydrated(true);

    function reload() {
      setValue(readLocalStorage(key, initialRef.current));
    }
    function onStorage(e: StorageEvent) {
      if (e.key === key) reload();
    }
    function onSync(e: Event) {
      if ((e as CustomEvent<SyncDetail>).detail?.key === key) reload();
    }
    window.addEventListener('storage', onStorage);
    window.addEventListener(SYNC_EVENT, onSync);
    return () => {
      window.removeEventListener('storage', onStorage);
      window.removeEventListener(SYNC_EVENT, onSync);
    };
  }, [key]);

  const set = useCallback(
    (next: T | ((prev: T) => T)) => {
      const resolved = typeof next === 'function' ? (next as (prev: T) => T)(valueRef.current) : next;
      valueRef.current = resolved;
      setValue(resolved);
      writeLocalStorage(key, resolved);
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent<SyncDetail>(SYNC_EVENT, { detail: { key } }));
      }
    },
    [key]
  );

  return [value, set, hydrated] as const;
}
