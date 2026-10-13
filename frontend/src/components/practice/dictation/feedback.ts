/**
 * DIC-09: short "correct" chime (Web Audio, no asset) + light vibration where supported.
 * Every call is best-effort and silently ignored when the browser refuses.
 */
let ctx: AudioContext | null = null;

function audioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  try {
    const Ctor =
      window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!Ctor) return null;
    ctx = ctx ?? new Ctor();
    return ctx;
  } catch {
    return null;
  }
}

function tone(ac: AudioContext, freq: number, start: number, duration: number) {
  const osc = ac.createOscillator();
  const gain = ac.createGain();
  osc.type = 'sine';
  osc.frequency.value = freq;
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(0.15, start + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + duration);
  osc.connect(gain).connect(ac.destination);
  osc.start(start);
  osc.stop(start + duration + 0.02);
}

export function playCorrectFeedback(): void {
  try {
    const ac = audioContext();
    if (ac) {
      if (ac.state === 'suspended') void ac.resume();
      const t = ac.currentTime;
      tone(ac, 880, t, 0.12);
      tone(ac, 1318.5, t + 0.1, 0.18);
    }
  } catch {
    // ignore
  }
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') navigator.vibrate(40);
  } catch {
    // ignore
  }
}
