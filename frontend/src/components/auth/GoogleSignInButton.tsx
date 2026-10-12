'use client';

import { GoogleLogin, GoogleOAuthProvider } from '@react-oauth/google';
import toast from 'react-hot-toast';

const RAW_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
// .env.example ships every unconfigured secret as a "your_..." placeholder (shared with the
// backend's own GOOGLE_CLIENT_ID), so a plain truthiness check isn't enough to detect "not set".
const CLIENT_ID = RAW_CLIENT_ID && !RAW_CLIENT_ID.startsWith('your_') ? RAW_CLIENT_ID : null;

export function GoogleSignInButton({ onToken }: { onToken: (idToken: string) => void }) {
  if (!CLIENT_ID) return null;

  return (
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
  );
}
