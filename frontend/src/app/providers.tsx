'use client';

import { apiErrorStatus } from '@/lib/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { Toaster } from 'react-hot-toast';

// Client errors that will not change on retry (architecture §6.2: 404 is never retried).
const NO_RETRY_STATUS = new Set([400, 401, 403, 404, 422]);

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (failureCount, error) => {
              const status = apiErrorStatus(error);
              if (status !== null && NO_RETRY_STATUS.has(status)) return false;
              return failureCount < 1;
            },
            refetchOnWindowFocus: false,
            staleTime: 30_000,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      {children}
      <Toaster position="top-center" toastOptions={{ duration: 3500 }} />
    </QueryClientProvider>
  );
}
