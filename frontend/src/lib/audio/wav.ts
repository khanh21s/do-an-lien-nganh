/**
 * SHA-01: pure helpers that turn captured Float32 microphone samples into a
 * WAV (RIFF, PCM 16-bit, mono, 16 kHz) payload accepted by practice-service
 * (`WavUtils.validate`) and Azure short-audio pronunciation assessment.
 *
 * No DOM dependencies, so everything here is unit-testable under Node.
 */

export const TARGET_SAMPLE_RATE = 16000;
export const WAV_HEADER_BYTES = 44;
export const WAV_MIME_TYPE = 'audio/wav';

/** Backend limits (practice-service `WavUtils`): 0.5–30 s. */
export const MIN_RECORDING_SECONDS = 0.5;
export const MAX_RECORDING_SECONDS = 30;

/** Concatenates captured chunks into one buffer. */
export function mergeChunks(chunks: readonly Float32Array[]): Float32Array {
  let total = 0;
  for (const c of chunks) total += c.length;
  const out = new Float32Array(total);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

/** Number of output samples produced by `downsample` for `inputLength` samples. */
export function resampledLength(inputLength: number, fromRate: number, toRate: number): number {
  return Math.round((inputLength * toRate) / fromRate);
}

/**
 * Resamples mono audio from `fromRate` to `toRate`.
 * - Downsampling averages every source sample that falls into the output sample's window
 *   (a box low-pass filter, enough to avoid harsh aliasing for speech).
 * - Upsampling (rare: 8 kHz devices) uses linear interpolation.
 */
export function downsample(
  input: Float32Array,
  fromRate: number,
  toRate: number = TARGET_SAMPLE_RATE
): Float32Array {
  if (!(fromRate > 0) || !(toRate > 0)) throw new Error('Sample rates must be positive');
  if (fromRate === toRate) return input.slice();

  const outLength = resampledLength(input.length, fromRate, toRate);
  const out = new Float32Array(outLength);
  const ratio = fromRate / toRate;

  if (ratio > 1) {
    for (let i = 0; i < outLength; i++) {
      const start = Math.floor(i * ratio);
      const end = Math.min(input.length, Math.max(start + 1, Math.floor((i + 1) * ratio)));
      let sum = 0;
      for (let j = start; j < end; j++) sum += input[j];
      out[i] = end > start ? sum / (end - start) : 0;
    }
  } else {
    const last = input.length - 1;
    for (let i = 0; i < outLength; i++) {
      const pos = i * ratio;
      const j = Math.floor(pos);
      const frac = pos - j;
      const a = input[Math.min(j, last)] ?? 0;
      const b = input[Math.min(j + 1, last)] ?? 0;
      out[i] = a + (b - a) * frac;
    }
  }
  return out;
}

/** Float [-1, 1] → signed 16-bit PCM (clamped). */
export function floatTo16BitPCM(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length);
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i] || 0));
    out[i] = s < 0 ? Math.round(s * 0x8000) : Math.round(s * 0x7fff);
  }
  return out;
}

function writeAscii(view: DataView, offset: number, text: string): void {
  for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
}

/**
 * Builds a canonical 44-byte-header WAV file (PCM format 1, little endian).
 * @param samples mono (or interleaved, with `channels`) 16-bit samples
 */
export function encodeWav(samples: Int16Array, sampleRate: number = TARGET_SAMPLE_RATE, channels = 1): ArrayBuffer {
  const bitsPerSample = 16;
  const blockAlign = channels * (bitsPerSample / 8);
  const byteRate = sampleRate * blockAlign;
  const dataBytes = samples.length * 2;
  const buffer = new ArrayBuffer(WAV_HEADER_BYTES + dataBytes);
  const view = new DataView(buffer);

  writeAscii(view, 0, 'RIFF');
  view.setUint32(4, 36 + dataBytes, true);
  writeAscii(view, 8, 'WAVE');
  writeAscii(view, 12, 'fmt ');
  view.setUint32(16, 16, true); // fmt chunk size
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, channels, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, blockAlign, true);
  view.setUint16(34, bitsPerSample, true);
  writeAscii(view, 36, 'data');
  view.setUint32(40, dataBytes, true);

  let offset = WAV_HEADER_BYTES;
  for (let i = 0; i < samples.length; i++, offset += 2) view.setInt16(offset, samples[i], true);
  return buffer;
}

export interface EncodedRecording {
  wav: ArrayBuffer;
  /** Seconds of audio actually encoded (after trimming to the max). */
  durationSec: number;
  sampleCount: number;
}

/**
 * Full pipeline: merge chunks → resample to 16 kHz → trim to `maxSeconds` → 16-bit PCM → WAV.
 */
export function encodeRecording(
  chunks: readonly Float32Array[],
  inputRate: number,
  maxSeconds: number = MAX_RECORDING_SECONDS
): EncodedRecording {
  const merged = mergeChunks(chunks);
  let resampled = downsample(merged, inputRate, TARGET_SAMPLE_RATE);
  const maxSamples = Math.floor(maxSeconds * TARGET_SAMPLE_RATE);
  if (resampled.length > maxSamples) resampled = resampled.subarray(0, maxSamples);
  const pcm = floatTo16BitPCM(resampled);
  return {
    wav: encodeWav(pcm, TARGET_SAMPLE_RATE, 1),
    durationSec: pcm.length / TARGET_SAMPLE_RATE,
    sampleCount: pcm.length,
  };
}

/** Root-mean-square level of a chunk (0–1), used for the input level meter. */
export function rmsLevel(chunk: Float32Array): number {
  if (!chunk.length) return 0;
  let sum = 0;
  for (let i = 0; i < chunk.length; i++) sum += chunk[i] * chunk[i];
  return Math.sqrt(sum / chunk.length);
}
