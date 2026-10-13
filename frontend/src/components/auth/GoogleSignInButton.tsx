'use client';

import { GoogleLogin, GoogleOAuthProvider } from '@react-oauth/google';
import toast from 'react-hot-toast';

const RAW_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
// .env.example ships every unconfigured secret as a "your_..." placeholder (shared with the
// backend's own GOOGLE_CLIENT_ID), so a plain truthiness check isn't enough to detect "not set".
const CLIENT_ID = RAW_CLIENT_ID && !RAW_CLIENT_ID.startsWith('your_') ? RAW_CLIENT_ID : null;

export const isGoogleLoginConfigured = CLIENT_ID !== null;

/**
 * AUTH-04 (FE): renders nothing (not even the "hoặc" divider) when Google login is not configured.
 * If the BE still answers 503 "Google login chưa cấu hình", the caller shows it via `apiErrorMessage`.
 */
export function GoogleSignInButton({
  onToken,
  withDivider = false,
}: {
  onToken: (idToken: string) => void;
  withDivider?: boolean;
}) {
  if (!CLIENT_ID) return null;

  return (
    <>
      {withDivider && (
        <div className="my-4 flex items-center gap-2 text-xs text-slate-400">
          <div className="h-px flex-1 bg-slate-200" />
          hoặc
          <div className="h-px flex-1 bg-slate-200" />
        </div>
      )}
      <GoogleOAuthProvider clientId={CLIENT_ID}>
        <div className="flex justify-center">
          <GoogleLogin
            onSuccess={(credential) => {
              if (credential.credential) onToken(credential.credential);
            }}
            onError={() => toast.error('Đăng nhập Google thất bại.')}
          />
        </div>
      </GoogleOAuthProvider>
    </>
  );
}
