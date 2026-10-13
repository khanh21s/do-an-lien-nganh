import { describe, expect, it, vi } from 'vitest';
import type { ShadowingAttemptResult, ShadowingUploadUrlResponse } from '@/lib/types';
import { ShadowingSubmitError, submitRecording, type ShadowingSubmitApi } from './submit';

const target: ShadowingUploadUrlResponse = {
  uploadUrl: '/practice/shadowing/recordings/r1',
  recordingId: 'r1',
  expiresIn: 900,
  uploadMethod: 'PUT',
  uploadRequiresAuth: true,
  contentType: 'audio/wav',
  storage: 'local',
};

const result = { attemptId: 'a1', scorer: 'mock', wordScores: [] } as unknown as ShadowingAttemptResult;

function makeApi(overrides: Partial<ShadowingSubmitApi> = {}) {
  return {
    requestShadowingUploadUrl: vi.fn().mockResolvedValue(target),
    uploadRecording: vi.fn().mockResolvedValue(undefined),
    submitShadowing: vi.fn().mockResolvedValue(result),
    ...overrides,
  } satisfies ShadowingSubmitApi;
}

const blob = new Blob([new Uint8Array(44)], { type: 'audio/wav' });

describe('submitRecording', () => {
  it('requests a target, uploads the blob and scores it', async () => {
    const api = makeApi();
    const stages: string[] = [];
    const out = await submitRecording(api, {
      lessonId: 'L',
      sentenceId: 'S',
      blob,
      durationSec: 3.04,
      onStage: (s) => stages.push(s),
    });
    expect(api.uploadRecording).toHaveBeenCalledWith(target, blob);
    expect(api.submitShadowing).toHaveBeenCalledWith({ lessonId: 'L', sentenceId: 'S', recordingId: 'r1', duration: 3 });
    expect(out).toEqual({ result, recordingId: 'r1' });
    expect(stages).toEqual(['uploading', 'scoring']);
  });

  it('reports an upload failure without a recordingId', async () => {
    const api = makeApi({ uploadRecording: vi.fn().mockRejectedValue(new Error('offline')) });
    const err = await submitRecording(api, { lessonId: 'L', sentenceId: 'S', blob, durationSec: 1 }).catch((e) => e);
    expect(err).toBeInstanceOf(ShadowingSubmitError);
    expect(err.stage).toBe('uploading');
    expect(err.recordingId).toBeNull();
    expect(api.submitShadowing).not.toHaveBeenCalled();
  });

  it('keeps the recordingId when scoring fails, and retry skips the upload', async () => {
    const api = makeApi({ submitShadowing: vi.fn().mockRejectedValueOnce(new Error('502')).mockResolvedValue(result) });
    const err = await submitRecording(api, { lessonId: 'L', sentenceId: 'S', blob, durationSec: 1 }).catch((e) => e);
    expect(err.stage).toBe('scoring');
    expect(err.recordingId).toBe('r1');

    const out = await submitRecording(api, {
      lessonId: 'L',
      sentenceId: 'S',
      blob,
      durationSec: 1,
      uploadedRecordingId: err.recordingId,
    });
    expect(out.result).toBe(result);
    expect(api.requestShadowingUploadUrl).toHaveBeenCalledTimes(1);
    expect(api.uploadRecording).toHaveBeenCalledTimes(1);
  });
});
