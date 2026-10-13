import { describe, expect, it } from 'vitest';
import {
  downsample,
  encodeRecording,
  encodeWav,
  floatTo16BitPCM,
  mergeChunks,
  rmsLevel,
  TARGET_SAMPLE_RATE,
  WAV_HEADER_BYTES,
} from './wav';

function ascii(view: DataView, offset: number, length: number): string {
  let s = '';
  for (let i = 0; i < length; i++) s += String.fromCharCode(view.getUint8(offset + i));
  return s;
}

function sine(length: number, rate: number, freq = 440, amp = 0.5): Float32Array {
  const out = new Float32Array(length);
  for (let i = 0; i < length; i++) out[i] = amp * Math.sin((2 * Math.PI * freq * i) / rate);
  return out;
}

describe('encodeWav', () => {
  it('writes a canonical RIFF/WAVE PCM 16 kHz mono 16-bit header', () => {
    const samples = new Int16Array([0, 1000, -1000, 32767, -32768]);
    const buf = encodeWav(samples, 16000, 1);
    const view = new DataView(buf);

    expect(buf.byteLength).toBe(WAV_HEADER_BYTES + samples.length * 2);
    expect(ascii(view, 0, 4)).toBe('RIFF');
    expect(view.getUint32(4, true)).toBe(36 + samples.length * 2);
    expect(ascii(view, 8, 4)).toBe('WAVE');
    expect(ascii(view, 12, 4)).toBe('fmt ');
    expect(view.getUint32(16, true)).toBe(16);
    expect(view.getUint16(20, true)).toBe(1); // PCM
    expect(view.getUint16(22, true)).toBe(1); // mono
    expect(view.getUint32(24, true)).toBe(16000);
    expect(view.getUint32(28, true)).toBe(32000); // byte rate
    expect(view.getUint16(32, true)).toBe(2); // block align
    expect(view.getUint16(34, true)).toBe(16);
    expect(ascii(view, 36, 4)).toBe('data');
    expect(view.getUint32(40, true)).toBe(samples.length * 2);
  });

  it('stores samples little-endian after the header', () => {
    const samples = new Int16Array([0, 1000, -1000, 32767, -32768]);
    const view = new DataView(encodeWav(samples));
    for (let i = 0; i < samples.length; i++) {
      expect(view.getInt16(WAV_HEADER_BYTES + i * 2, true)).toBe(samples[i]);
    }
  });

  it('handles an empty recording', () => {
    const buf = encodeWav(new Int16Array(0));
    expect(buf.byteLength).toBe(WAV_HEADER_BYTES);
    expect(new DataView(buf).getUint32(40, true)).toBe(0);
  });
});

describe('floatTo16BitPCM', () => {
  it('maps and clamps [-1, 1] to int16', () => {
    const pcm = floatTo16BitPCM(new Float32Array([0, 1, -1, 2, -2, 0.5, NaN]));
    expect(Array.from(pcm)).toEqual([0, 32767, -32768, 32767, -32768, 16384, 0]);
  });
});

describe('downsample', () => {
  it('48 kHz → 16 kHz keeps the expected sample count (±1)', () => {
    for (const n of [48000, 48001, 47999, 128 * 375, 12345]) {
      const out = downsample(sine(n, 48000), 48000, 16000);
      expect(Math.abs(out.length - n / 3)).toBeLessThanOrEqual(1);
    }
  });

  it('44.1 kHz → 16 kHz keeps the duration', () => {
    const out = downsample(sine(44100, 44100), 44100, 16000);
    expect(Math.abs(out.length - 16000)).toBeLessThanOrEqual(1);
  });

  it('averages constant input without changing its level', () => {
    const out = downsample(new Float32Array(4800).fill(0.25), 48000, 16000);
    expect(out.length).toBe(1600);
    for (const v of out) expect(v).toBeCloseTo(0.25, 6);
  });

  it('upsamples 8 kHz → 16 kHz by interpolation', () => {
    const out = downsample(new Float32Array([0, 1, 0, -1]), 8000, 16000);
    expect(out.length).toBe(8);
    expect(out[1]).toBeCloseTo(0.5, 6);
  });

  it('returns a copy when the rate already matches', () => {
    const input = new Float32Array([0.1, 0.2]);
    const out = downsample(input, 16000, 16000);
    expect(Array.from(out)).toEqual(Array.from(input));
    expect(out).not.toBe(input);
  });
});

describe('encodeRecording', () => {
  it('produces a 16 kHz WAV whose data length matches the duration', () => {
    const rate = 48000;
    const chunks = Array.from({ length: 375 * 3 }, (_, i) => sine(128, rate, 440, 0.3 + (i % 2) * 0.01)); // 3 s
    const { wav, durationSec, sampleCount } = encodeRecording(chunks, rate);
    const view = new DataView(wav);
    expect(view.getUint32(24, true)).toBe(TARGET_SAMPLE_RATE);
    expect(view.getUint16(22, true)).toBe(1);
    expect(sampleCount).toBe(48000);
    expect(view.getUint32(40, true)).toBe(sampleCount * 2);
    expect(durationSec).toBeCloseTo(3, 3);
  });

  it('trims recordings to the maximum length', () => {
    const { durationSec, sampleCount } = encodeRecording([new Float32Array(48000 * 2)], 48000, 1);
    expect(sampleCount).toBe(16000);
    expect(durationSec).toBe(1);
  });
});

describe('helpers', () => {
  it('mergeChunks concatenates in order', () => {
    const out = mergeChunks([new Float32Array([1, 2]), new Float32Array([]), new Float32Array([3])]);
    expect(Array.from(out)).toEqual([1, 2, 3]);
  });

  it('rmsLevel of a constant signal is its magnitude', () => {
    expect(rmsLevel(new Float32Array(10).fill(-0.5))).toBeCloseTo(0.5, 6);
    expect(rmsLevel(new Float32Array(0))).toBe(0);
  });
});
