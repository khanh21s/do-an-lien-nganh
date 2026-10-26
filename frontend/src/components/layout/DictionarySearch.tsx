'use client';

import { WordLookup } from '@/components/vocab/dictionary/WordLookup';
import { vocabularyApi } from '@/lib/endpoints/vocabulary';
import { qk } from '@/lib/queryKeys';
import { readLocalStorage, storageKeys, writeLocalStorage } from '@/lib/storageKeys';
import type { DictionarySuggestion } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';

/** Inline dictionary search with fast, local-card suggestions. */
export function DictionarySearch({ className }: { className?: string }) {
  const [query, setQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');
  const [word, setWord] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [history, setHistory] = useState<string[]>([]);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  const suggestionsQuery = useQuery({
    queryKey: qk.dictionarySuggestions(debouncedQuery.toLowerCase()),
    queryFn: () => vocabularyApi.suggestDictionaryWords(debouncedQuery),
    enabled: open && debouncedQuery.length >= 2,
    staleTime: 60_000,
    gcTime: 5 * 60_000,
    retry: false,
  });
  const suggestions = suggestionsQuery.data ?? [];
  const isCurrentQuery = query.trim().toLowerCase() === debouncedQuery.toLowerCase();
  const showSuggestions = open && isCurrentQuery && debouncedQuery.length >= 2;

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed) {
      saveHistory(trimmed);
      setOpen(false);
      setWord(trimmed);
    }
  }

  function chooseSuggestion(suggestion: DictionarySuggestion) {
    setQuery(suggestion.word);
    saveHistory(suggestion.word);
    setOpen(false);
    setActiveIndex(-1);
    setWord(suggestion.word);
  }

  function saveHistory(value: string) {
    const normalized = value.trim();
    const next = [normalized, ...readLocalStorage<string[]>(storageKeys.dictionaryHistory, [])
      .filter((item) => item.toLowerCase() !== normalized.toLowerCase())].slice(0, 8);
    setHistory(next);
    writeLocalStorage(storageKeys.dictionaryHistory, next);
  }

  function chooseHistory(value: string) {
    saveHistory(value);
    setQuery(value);
    setOpen(false);
    setWord(value);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setOpen(false);
      setActiveIndex(-1);
    } else if (event.key === 'ArrowDown' && showSuggestions && suggestions.length) {
      event.preventDefault();
      setActiveIndex((current) => (current + 1) % suggestions.length);
    } else if (event.key === 'ArrowUp' && showSuggestions && suggestions.length) {
      event.preventDefault();
      setActiveIndex((current) => (current <= 0 ? suggestions.length - 1 : current - 1));
    } else if (event.key === 'Enter' && activeIndex >= 0 && suggestions[activeIndex]) {
      event.preventDefault();
      chooseSuggestion(suggestions[activeIndex]);
    }
  }

  return (
    <div
      ref={rootRef}
      className={clsx('relative min-w-0', className)}
      onBlur={(event) => {
        if (!rootRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false);
      }}
    >
      <form onSubmit={onSubmit} role="search" className="flex min-w-0 items-center gap-1.5">
        <label htmlFor="dictionary-search" className="sr-only">
          Tra từ điển
        </label>
        <div className="flex h-10 min-w-0 flex-1 items-center gap-2 rounded-lg border border-slate-300 bg-slate-50 px-2.5 focus-within:border-brand-500 focus-within:bg-white focus-within:ring-2 focus-within:ring-brand-100 sm:px-3">
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" className="h-4 w-4 shrink-0 text-slate-400">
            <circle cx="10.8" cy="10.8" r="6.3" stroke="currentColor" strokeWidth="1.8" />
            <path d="m15.5 15.5 4.1 4.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <input
            id="dictionary-search"
            type="search"
            value={query}
            onFocus={() => {
              setHistory(readLocalStorage<string[]>(storageKeys.dictionaryHistory, []));
              setOpen(true);
            }}
            onChange={(event) => {
              setQuery(event.target.value);
              setOpen(true);
              setActiveIndex(-1);
            }}
            onKeyDown={onKeyDown}
            placeholder="Tra từ hoặc cụm từ tiếng Anh..."
            autoComplete="off"
            role="combobox"
            aria-autocomplete="list"
            aria-expanded={showSuggestions && (suggestions.length > 0 || suggestionsQuery.isFetching)}
            aria-controls="dictionary-suggestions"
            aria-activedescendant={activeIndex >= 0 ? `dictionary-suggestion-${activeIndex}` : undefined}
            className="h-full min-w-0 flex-1 bg-transparent text-sm text-slate-900 outline-none placeholder:text-slate-400"
          />
        </div>
        <button
          type="submit"
          disabled={!query.trim()}
          aria-label="Tra từ"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-brand-600 text-sm font-semibold text-white transition-colors hover:bg-brand-700 disabled:cursor-not-allowed disabled:bg-slate-300 sm:w-auto sm:px-3"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" className="h-4 w-4 sm:hidden">
            <circle cx="10.8" cy="10.8" r="6.3" stroke="currentColor" strokeWidth="1.8" />
            <path d="m15.5 15.5 4.1 4.1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
          <span className="hidden sm:inline">Tra từ</span>
        </button>
      </form>

      {showSuggestions && (suggestions.length > 0 || suggestionsQuery.isFetching || !suggestionsQuery.isError) && (
        <ul
          id="dictionary-suggestions"
          role="listbox"
          aria-label="Gợi ý từ vựng"
          className="absolute left-0 right-0 top-full z-[80] mt-2 max-h-80 overflow-y-auto rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl"
        >
          {suggestionsQuery.isFetching && suggestions.length === 0 ? (
            <li className="px-3 py-2 text-sm text-slate-500" role="status">Đang tìm gợi ý...</li>
          ) : suggestions.length > 0 ? suggestions.map((suggestion, index) => (
            <li key={suggestion.word} role="presentation">
              <button
                id={`dictionary-suggestion-${index}`}
                type="button"
                role="option"
                aria-selected={activeIndex === index}
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => chooseSuggestion(suggestion)}
                className={clsx(
                  'flex w-full flex-col items-start rounded-lg px-3 py-2 text-left transition-colors',
                  activeIndex === index ? 'bg-brand-50' : 'hover:bg-slate-50',
                )}
              >
                <span className="flex w-full items-center justify-between gap-3">
                  <span className="font-semibold text-slate-900">{suggestion.word}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    {suggestion.correction && <span className="text-xs text-brand-700">Có phải bạn muốn tìm</span>}
                    {suggestion.partsOfSpeech.length > 0 && (
                      <span className="text-xs text-slate-500">{suggestion.partsOfSpeech.join(', ')}</span>
                    )}
                  </span>
                </span>
                {suggestion.meaningsVi.length > 0 && (
                  <span className="mt-0.5 line-clamp-2 text-sm text-slate-600">{suggestion.meaningsVi.join('; ')}</span>
                )}
              </button>
            </li>
          )) : (
            <li>
              <button
                type="button"
                onMouseDown={(event) => event.preventDefault()}
                onClick={() => {
                  const trimmed = query.trim();
                  saveHistory(trimmed);
                  setOpen(false);
                  setWord(trimmed);
                }}
                className="w-full rounded-lg px-3 py-2 text-left text-sm text-slate-600 hover:bg-slate-50"
              >Tra “{query.trim()}” để xem bản dịch tiếng Việt</button>
            </li>
          )}
        </ul>
      )}
      {open && !query.trim() && history.length > 0 && (
        <div className="absolute left-0 right-0 top-full z-[80] mt-2 rounded-xl border border-slate-200 bg-white p-1.5 shadow-xl">
          <div className="flex items-center justify-between px-3 py-1.5">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-400">Tra cứu gần đây</span>
            <button
              type="button"
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => {
                setHistory([]);
                writeLocalStorage(storageKeys.dictionaryHistory, []);
              }}
              className="text-xs text-slate-500 hover:text-slate-800"
            >Xóa lịch sử</button>
          </div>
          <ul role="listbox" aria-label="Tra cứu gần đây">
            {history.map((item, index) => (
              <li key={`${item}-${index}`}>
                <button
                  type="button"
                  role="option"
                  aria-selected={false}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => chooseHistory(item)}
                  className="w-full rounded-lg px-3 py-2 text-left text-sm text-slate-700 hover:bg-slate-50"
                >{item}</button>
              </li>
            ))}
          </ul>
        </div>
      )}
      <WordLookup word={word} onClose={() => setWord(null)} />
    </div>
  );
}
