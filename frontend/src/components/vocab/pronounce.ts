'use client';

/** Speaks `word` with the browser's en-US voice. Returns false when speech synthesis is unavailable. */
export function speakWithSynthesis(word: string): boolean {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return false;
  try {
    const synth = window.speechSynthesis;
    synth.cancel();
    const utterance = new SpeechSynthesisUtterance(word);
    utterance.lang = 'en-US';
    const voice = synth.getVoices().find((v) => v.lang === 'en-US') ?? synth.getVoices().find((v) => v.lang.startsWith('en'));
    if (voice) utterance.voice = voice;
    synth.speak(utterance);
    return true;
  } catch {
    return false;
  }
}

/** VOC-07: play the card's `audioUrl`; fall back to speech synthesis if there is none or it fails. */
export function pronounce(word: string, audioUrl?: string | null): void {
  if (audioUrl) {
    try {
      const audio = new Audio(audioUrl);
      audio.play().catch(() => speakWithSynthesis(word));
      return;
    } catch {
      // fall through
    }
  }
  speakWithSynthesis(word);
}
