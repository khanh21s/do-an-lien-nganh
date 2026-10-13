/**
 * SHA-01: microphone capture with the Web Audio API (no MediaRecorder → identical output on
 * Chrome, Firefox and Safari). Raw Float32 samples are collected at the AudioContext's native
 * rate and turned into WAV PCM 16 kHz mono by `encodeRecording` (`lib/audio/wav.ts`).
 *
 * Strategy: AudioWorklet (Blob-URL module); falls back to the deprecated ScriptProcessorNode when
 * AudioWorklet is missing or fails to load (old Safari, some embedded WebViews).
 */
import { PCM_CAPTURE_PROCESSOR, PCM_CAPTURE_WORKLET_SOURCE } from './recorder-worklet';
import { encodeRecording, MAX_RECORDING_SECONDS, rmsLevel, WAV_MIME_TYPE } from './wav';

export type RecorderErrorCode =
  /** User or browser policy blocked the microphone (NotAllowedError / SecurityError). */
  | 'permission-denied'
  /** No microphone (NotFoundError / OverconstrainedError). */
  | 'no-device'
  /** Microphone exists but cannot be opened (NotReadableError: used by another app, OS block). */
  | 'busy'
  /** Page is not served over HTTPS/localhost, so `mediaDevices` is unavailable. */
  | 'insecure'
  /** Browser lacks getUserMedia / Web Audio. */
  | 'unsupported'
  | 'unknown';

export class RecorderError extends Error {
  constructor(
    public readonly code: RecorderErrorCode,
    message?: string
  ) {
    super(message ?? code);
    this.name = 'RecorderError';
  }
}

/** Maps a getUserMedia / Web Audio failure to a RecorderErrorCode. Pure (unit-tested). */
export function classifyMediaError(err: unknown): RecorderErrorCode {
  if (err instanceof RecorderError) return err.code;
  const name =
    typeof err === 'object' && err !== null && 'name' in err ? String((err as { name: unknown }).name) : '';
  switch (name) {
    case 'NotAllowedError':
    case 'PermissionDeniedError':
    case 'SecurityError':
      return 'permission-denied';
    case 'NotFoundError':
    case 'DevicesNotFoundError':
    case 'OverconstrainedError':
    case 'ConstraintNotSatisfiedError':
      return 'no-device';
    case 'NotReadableError':
    case 'TrackStartError':
    case 'AbortError':
      return 'busy';
    case 'TypeError':
      return 'unsupported';
    default:
      return 'unknown';
  }
}

export interface CapturedRecording {
  blob: Blob;
  /** Seconds of encoded audio (16 kHz samples / 16000). */
  durationSec: number;
}

export interface PcmCapture {
  /** Native rate of the AudioContext (e.g. 44100 / 48000). */
  readonly inputSampleRate: number;
  /** 'worklet' or 'script-processor'. */
  readonly engine: 'worklet' | 'script-processor';
  /** Seconds captured so far (native samples / native rate). */
  capturedSeconds(): number;
  /** Stops capture, releases the mic and returns the WAV recording. */
  stop(): Promise<CapturedRecording>;
  /** Stops capture and releases the mic, discarding audio. Safe to call more than once. */
  cancel(): void;
}

export interface StartCaptureOptions {
  /** Called with the RMS level (0–1) of each captured chunk, for a level meter. */
  onLevel?: (level: number) => void;
  maxSeconds?: number;
}

type AudioContextCtor = typeof AudioContext;

function getAudioContextCtor(): AudioContextCtor | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as { AudioContext?: AudioContextCtor; webkitAudioContext?: AudioContextCtor };
  return w.AudioContext ?? w.webkitAudioContext ?? null;
}

export function isRecordingSupported(): boolean {
  return (
    typeof navigator !== 'undefined' &&
    !!navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function' &&
    !!getAudioContextCtor()
  );
}

async function createWorkletNode(ctx: AudioContext, onChunk: (c: Float32Array) => void): Promise<AudioWorkletNode> {
  if (!ctx.audioWorklet || typeof AudioWorkletNode === 'undefined') throw new Error('AudioWorklet unavailable');
  const url = URL.createObjectURL(new Blob([PCM_CAPTURE_WORKLET_SOURCE], { type: 'application/javascript' }));
  try {
    await ctx.audioWorklet.addModule(url);
  } finally {
    URL.revokeObjectURL(url);
  }
  const node = new AudioWorkletNode(ctx, PCM_CAPTURE_PROCESSOR, {
    numberOfInputs: 1,
    numberOfOutputs: 1,
    outputChannelCount: [1],
    // Down-mix stereo mics to mono before the processor sees them.
    channelCount: 1,
    channelCountMode: 'explicit',
    channelInterpretation: 'speakers',
  });
  node.port.onmessage = (e: MessageEvent) => {
    if (e.data instanceof Float32Array) onChunk(e.data);
  };
  return node;
}

function createScriptProcessorNode(ctx: AudioContext, onChunk: (c: Float32Array) => void): ScriptProcessorNode {
  const node = ctx.createScriptProcessor(4096, 1, 1);
  node.onaudioprocess = (e: AudioProcessingEvent) => {
    // The buffer is reused by the browser → copy it.
    onChunk(new Float32Array(e.inputBuffer.getChannelData(0)));
  };
  return node;
}

/**
 * Opens the microphone and starts collecting samples. Must be called from a user gesture
 * (click / key press) so Safari allows the AudioContext to start.
 * @throws RecorderError
 */
export async function startPcmCapture(options: StartCaptureOptions = {}): Promise<PcmCapture> {
  const { onLevel, maxSeconds = MAX_RECORDING_SECONDS } = options;

  if (typeof window !== 'undefined' && window.isSecureContext === false) throw new RecorderError('insecure');
  const Ctor = getAudioContextCtor();
  if (!isRecordingSupported() || !Ctor) throw new RecorderError('unsupported');

  // Create the context synchronously inside the gesture (Safari), before awaiting the permission prompt.
  let ctx: AudioContext;
  try {
    ctx = new Ctor();
  } catch {
    throw new RecorderError('unsupported');
  }

  let stream: MediaStream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
  } catch (err) {
    ctx.close().catch(() => undefined);
    throw new RecorderError(classifyMediaError(err), err instanceof Error ? err.message : undefined);
  }

  const chunks: Float32Array[] = [];
  let capturedSamples = 0;
  let collecting = true;
  const maxSamples = Math.ceil((maxSeconds + 0.5) * ctx.sampleRate); // hard memory cap, trimmed later
  const onChunk = (c: Float32Array) => {
    if (!collecting || capturedSamples >= maxSamples) return;
    chunks.push(c);
    capturedSamples += c.length;
    onLevel?.(rmsLevel(c));
  };

  let source: MediaStreamAudioSourceNode | null = null;
  let node: AudioWorkletNode | ScriptProcessorNode | null = null;
  let sink: GainNode | null = null;
  let engine: PcmCapture['engine'] = 'worklet';
  let released = false;

  const release = () => {
    if (released) return;
    released = true;
    collecting = false;
    try {
      source?.disconnect();
      node?.disconnect();
      sink?.disconnect();
    } catch {
      // already disconnected
    }
    if (node && 'onaudioprocess' in node) node.onaudioprocess = null;
    if (node && 'port' in node) node.port.onmessage = null;
    stream.getTracks().forEach((t) => t.stop());
    ctx.close().catch(() => undefined);
  };

  try {
    if (ctx.state === 'suspended') await ctx.resume().catch(() => undefined);
    source = ctx.createMediaStreamSource(stream);
    // Processors only run when pulled by the destination; a muted gain keeps the mic off the speakers.
    sink = ctx.createGain();
    sink.gain.value = 0;
    sink.connect(ctx.destination);
    try {
      node = await createWorkletNode(ctx, onChunk);
    } catch {
      engine = 'script-processor';
      node = createScriptProcessorNode(ctx, onChunk);
    }
    source.connect(node);
    node.connect(sink);
  } catch (err) {
    release();
    throw new RecorderError('unsupported', err instanceof Error ? err.message : undefined);
  }

  const inputSampleRate = ctx.sampleRate;

  const flushWorklet = (): Promise<void> =>
    new Promise((resolve) => {
      if (!node || !('port' in node)) return resolve();
      const port = node.port;
      const timer = setTimeout(resolve, 250);
      port.onmessage = (e: MessageEvent) => {
        if (e.data === 'flushed') {
          clearTimeout(timer);
          resolve();
        } else if (e.data instanceof Float32Array) {
          onChunk(e.data);
        }
      };
      port.postMessage('flush');
    });

  return {
    inputSampleRate,
    engine,
    capturedSeconds: () => capturedSamples / inputSampleRate,
    async stop() {
      if (!released) {
        await flushWorklet();
        release();
      }
      const { wav, durationSec } = encodeRecording(chunks, inputSampleRate, maxSeconds);
      return { blob: new Blob([wav], { type: WAV_MIME_TYPE }), durationSec };
    },
    cancel: release,
  };
}
