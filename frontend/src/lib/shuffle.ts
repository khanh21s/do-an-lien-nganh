/** A shuffled copy of `items` (Fisher–Yates); the input is left untouched. */
export function shuffle<T>(items: readonly T[]): T[] {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/** A fresh seed for server-side shuffled paging (`?seed=`). */
export function newShuffleSeed(): string {
  return Math.random().toString(36).slice(2, 10);
}
