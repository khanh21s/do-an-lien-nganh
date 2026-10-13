'use client';

import { Button } from '@/components/ui/Button';
import type { RecorderErrorCode } from '@/lib/audio/recorder';
import { useEffect, useState } from 'react';

type BrowserKind = 'chrome' | 'edge' | 'firefox' | 'safari' | 'other';

function detectBrowser(ua: string): BrowserKind {
  if (/Edg\//.test(ua)) return 'edge';
  if (/Firefox\/|FxiOS/.test(ua)) return 'firefox';
  if (/Chrome\/|CriOS/.test(ua)) return 'chrome';
  if (/Safari\//.test(ua)) return 'safari';
  return 'other';
}

const PERMISSION_STEPS: Record<BrowserKind, string[]> = {
  chrome: [
    'Bấm biểu tượng ổ khoá / cài đặt trang bên trái thanh địa chỉ.',
    'Ở mục "Micrô", chọn "Cho phép".',
    'Tải lại trang rồi bấm ghi âm lại.',
  ],
  edge: [
    'Bấm biểu tượng ổ khoá bên trái thanh địa chỉ → "Quyền cho trang web này".',
    'Đặt "Micrô" thành "Cho phép".',
    'Tải lại trang rồi bấm ghi âm lại.',
  ],
  firefox: [
    'Bấm biểu tượng micrô bị gạch trên thanh địa chỉ.',
    'Bỏ chặn "Sử dụng micrô" (nút ✕ cạnh quyền bị chặn).',
    'Tải lại trang, bấm ghi âm và chọn "Cho phép".',
  ],
  safari: [
    'Mở menu Safari → "Cài đặt cho trang web này…" (Settings for This Website).',
    'Đặt "Micrô" thành "Cho phép".',
    'Trên iPhone/iPad: Cài đặt → Safari → Micrô → Cho phép, rồi tải lại trang.',
  ],
  other: [
    'Mở phần quyền của trang web trong trình duyệt (thường ở biểu tượng cạnh thanh địa chỉ).',
    'Cho phép trang này dùng micrô.',
    'Tải lại trang rồi thử lại.',
  ],
};

const TITLES: Record<RecorderErrorCode, string> = {
  'permission-denied': 'Trình duyệt đang chặn micrô',
  'no-device': 'Không tìm thấy micrô',
  busy: 'Không mở được micrô',
  insecure: 'Cần kết nối an toàn để ghi âm',
  unsupported: 'Trình duyệt không hỗ trợ ghi âm',
  unknown: 'Không ghi âm được',
};

const MESSAGES: Record<Exclude<RecorderErrorCode, 'permission-denied'>, string> = {
  'no-device': 'Hãy cắm tai nghe có micrô hoặc kiểm tra thiết bị thu âm trong cài đặt hệ thống, rồi thử lại.',
  busy: 'Micrô có thể đang được ứng dụng khác sử dụng (Zoom, Meet…) hoặc bị hệ điều hành chặn. Hãy đóng ứng dụng đó hoặc kiểm tra quyền micrô của trình duyệt trong cài đặt hệ thống.',
  insecure: 'Ghi âm chỉ hoạt động khi trang chạy qua HTTPS hoặc localhost.',
  unsupported: 'Hãy dùng phiên bản mới của Chrome, Firefox, Edge hoặc Safari.',
  unknown: 'Đã có lỗi khi mở micrô. Vui lòng thử lại.',
};

/** SHA-03: guidance shown when the microphone cannot be used. */
export function MicPermissionHelp({ code, onRetry }: { code: RecorderErrorCode; onRetry?: () => void }) {
  const [browser, setBrowser] = useState<BrowserKind>('other');
  useEffect(() => {
    setBrowser(detectBrowser(navigator.userAgent));
  }, []);

  return (
    <div role="alert" className="w-full max-w-md rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
      <p className="font-semibold">🎙 {TITLES[code]}</p>
      {code === 'permission-denied' ? (
        <>
          <p className="mt-1">Để luyện Shadowing, hãy cho phép trang này dùng micrô:</p>
          <ol className="mt-2 list-decimal space-y-1 pl-5">
            {PERMISSION_STEPS[browser].map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>
          <p className="mt-2 text-xs text-amber-800">
            macOS: kiểm tra thêm Cài đặt hệ thống → Quyền riêng tư & Bảo mật → Micrô. Windows: Cài đặt → Quyền riêng tư → Micrô.
          </p>
        </>
      ) : (
        <p className="mt-1">{MESSAGES[code]}</p>
      )}
      {onRetry && code !== 'unsupported' && code !== 'insecure' && (
        <Button type="button" variant="secondary" size="sm" className="mt-3 min-h-[44px]" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}
