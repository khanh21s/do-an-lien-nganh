import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import Link from 'next/link';

const FEATURES = [
  { icon: '✍️', tint: 'bg-brand-100', title: 'Dictation', desc: 'Nghe và chép chính tả từng câu, chấm điểm theo từng từ.' },
  { icon: '🗣️', tint: 'bg-rose-100', title: 'Shadowing', desc: 'Luyện phát âm theo audio bản ngữ, chấm điểm âm vị bằng AI.' },
  { icon: '🧠', tint: 'bg-emerald-100', title: 'Từ vựng SRS', desc: 'Ôn tập từ vựng thông minh theo thuật toán lặp lại ngắt quãng SM-2.' },
  { icon: '👑', tint: 'bg-violet-100', title: 'Premium', desc: 'Mở khoá bộ từ vựng nâng cao và tính năng chuyên sâu.' },
];

export default function LandingPage() {
  return (
    <main className="min-h-screen bg-gradient-to-b from-brand-50 to-white">
      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
        <span className="text-xl font-bold text-brand-600">Parroto</span>
        <nav className="flex items-center gap-4">
          <Link href="/lessons" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Bài học
          </Link>
          <Link href="/pricing" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Bảng giá
          </Link>
          <Link href="/login" className="text-sm font-medium text-slate-600 hover:text-slate-900">
            Đăng nhập
          </Link>
          <Link href="/register">
            <Button size="sm">Bắt đầu miễn phí</Button>
          </Link>
        </nav>
      </header>

      <section className="mx-auto max-w-4xl px-6 py-20 text-center">
        <h1 className="text-4xl font-extrabold tracking-tight text-slate-900 sm:text-5xl">
          Học tiếng Anh qua <span className="text-brand-600">Dictation</span> &amp;{' '}
          <span className="text-brand-600">Shadowing</span>
        </h1>
        <p className="mx-auto mt-4 max-w-2xl text-lg text-slate-600">
          Luyện nghe, luyện phát âm và ghi nhớ từ vựng lâu dài với hệ thống ôn tập thông minh — theo tốc độ của riêng bạn.
        </p>
        <div className="mt-8 flex items-center justify-center gap-4">
          <Link href="/register">
            <Button size="lg" variant="pro">
              Đăng ký ngay
            </Button>
          </Link>
          <Link href="/lessons">
            <Button size="lg" variant="secondary">
              Khám phá bài học
            </Button>
          </Link>
        </div>
      </section>

      <section className="mx-auto grid max-w-5xl grid-cols-1 gap-6 px-6 pb-24 sm:grid-cols-2 lg:grid-cols-4">
        {FEATURES.map((f) => (
          <Card key={f.title} className="text-center transition-shadow hover:shadow-md">
            <span className={`mx-auto flex h-12 w-12 items-center justify-center rounded-xl text-2xl ${f.tint}`}>
              {f.icon}
            </span>
            <h3 className="mt-3 font-semibold text-slate-900">{f.title}</h3>
            <p className="mt-1 text-sm text-slate-500">{f.desc}</p>
          </Card>
        ))}
      </section>
    </main>
  );
}
