'use client';

import { Button } from '@/components/ui/Button';
import { useRef, useState } from 'react';

interface RecorderProps {
  onRecorded: (blob: Blob, durationSec: number) => void;
  disabled?: boolean;
}

const MIME_TYPE = 'audio/webm';

export function Recorder({ onRecorded, disabled }: RecorderProps) {
  const [recording, setRecording] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const startedAtRef = useRef<number>(0);

  async function startRecording() {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream, { mimeType: MIME_TYPE });
      chunksRef.current = [];
      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunksRef.current.push(e.data);
      };
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: MIME_TYPE });
        const durationSec = (Date.now() - startedAtRef.current) / 1000;
        setPreviewUrl(URL.createObjectURL(blob));
        onRecorded(blob, durationSec);
        stream.getTracks().forEach((t) => t.stop());
      };
      mediaRecorderRef.current = recorder;
      startedAtRef.current = Date.now();
      recorder.start();
      setRecording(true);
    } catch {
      setError('Không thể truy cập micro. Vui lòng cấp quyền và thử lại.');
    }
  }

  function stopRecording() {
    mediaRecorderRef.current?.stop();
    setRecording(false);
  }

  return (
    <div className="flex flex-col items-center gap-3">
      <Button
        type="button"
        variant={recording ? 'danger' : 'primary'}
        size="lg"
        disabled={disabled}
        onClick={recording ? stopRecording : startRecording}
      >
        {recording ? '⏹ Dừng ghi âm' : '🎙 Bắt đầu ghi âm'}
      </Button>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {previewUrl && !recording && (
        <audio controls src={previewUrl} className="w-full max-w-sm">
          Trình duyệt của bạn không hỗ trợ phát audio.
        </audio>
      )}
    </div>
  );
}
