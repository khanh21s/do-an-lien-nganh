import type { Metadata } from 'next';
import './globals.css';
import { Providers } from './providers';

export const metadata: Metadata = {
  title: 'Parroto — Học tiếng Anh qua Dictation & Shadowing',
  description: 'Luyện nghe chép chính tả, shadowing phát âm và từ vựng SRS cùng Parroto.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
