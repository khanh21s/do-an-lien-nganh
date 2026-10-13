/**
 * AudioWorklet processor source (SHA-01), loaded through a Blob URL so no extra static file or
 * bundler config is needed. It copies the first input channel into ~2048-sample Float32 chunks
 * and posts them to the main thread. Posting the string `'flush'` sends the partial buffer,
 * replies `'flushed'` and stops the processor.
 *
 * Kept as plain ES2017 (no TS syntax): it runs inside the AudioWorkletGlobalScope as-is.
 */
export const PCM_CAPTURE_PROCESSOR = 'parroto-pcm-capture';

export const PCM_CAPTURE_WORKLET_SOURCE = `
class PcmCaptureProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.buf = new Float32Array(2048);
    this.len = 0;
    this.active = true;
    this.port.onmessage = (e) => {
      if (e.data === 'flush') {
        this.flush();
        this.active = false;
        this.port.postMessage('flushed');
      }
    };
  }
  flush() {
    if (this.len > 0) {
      this.port.postMessage(this.buf.slice(0, this.len));
      this.len = 0;
    }
  }
  process(inputs) {
    if (!this.active) return false;
    const input = inputs[0];
    const ch = input && input[0];
    if (ch) {
      for (let i = 0; i < ch.length; i++) {
        this.buf[this.len++] = ch[i];
        if (this.len === this.buf.length) {
          this.port.postMessage(this.buf.slice());
          this.len = 0;
        }
      }
    }
    return true;
  }
}
registerProcessor('${PCM_CAPTURE_PROCESSOR}', PcmCaptureProcessor);
`;
