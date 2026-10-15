import type { practiceApi } from '@/lib/endpoints/practice';
import type { ShadowingAttemptResult } from '@/lib/types';

export type ShadowingSubmitApi = Pick<
  typeof practiceApi,
  'requestShadowingUploadUrl' | 'uploadRecording' | 'submitShadowing'
>;

export type SubmitStage = 'uploading' | 'scoring';

/** Failure of one stage; `recordingId` is set when the upload already succeeded. */
export class ShadowingSubmitError extends Error {
  constructor(
    public readonly stage: SubmitStage,
    public readonly recordingId: string | null,
    public readonly original: unknown
  ) {
    super(`Shadowing ${stage} failed`);
    this.name = 'ShadowingSubmitError';
  }
}

export interface SubmitRecordingInput {
  lessonId: string;
  sentenceId: string;
  blob: Blob;
  durationSec: number;
  /** Recording already uploaded by a previous try → skip straight to scoring ("Gửi lại"). */
  uploadedRecordingId?: string | null;
  onStage?: (stage: SubmitStage) => void;
  /** P2-A2: grade this review-queue item. */
  reviewId?: string;
}

/**
 * SHA-02: request upload target → PUT the WAV (Bearer in local mode, bare presigned PUT in S3 mode;
 * see `practiceApi.uploadRecording`) → score. The same blob can be retried after any failure.
 */
export async function submitRecording(
  api: ShadowingSubmitApi,
  { lessonId, sentenceId, blob, durationSec, uploadedRecordingId, onStage, reviewId }: SubmitRecordingInput
): Promise<{ result: ShadowingAttemptResult; recordingId: string }> {
  let recordingId = uploadedRecordingId ?? null;

  if (!recordingId) {
    onStage?.('uploading');
    try {
      const target = await api.requestShadowingUploadUrl({ lessonId, sentenceId });
      await api.uploadRecording(target, blob);
      recordingId = target.recordingId;
    } catch (err) {
      throw new ShadowingSubmitError('uploading', null, err);
    }
  }

  onStage?.('scoring');
  try {
    const result = await api.submitShadowing({
      lessonId,
      sentenceId,
      recordingId,
      duration: Math.round(durationSec * 10) / 10,
      ...(reviewId ? { reviewId } : {}),
    });
    return { result, recordingId };
  } catch (err) {
    throw new ShadowingSubmitError('scoring', recordingId, err);
  }
}
