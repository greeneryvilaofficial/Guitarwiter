/**
 * Audio utilities for guitar pitch detection and synthesis
 */

export const initAudioContext = (): AudioContext => {
  const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
  return audioContext;
};

export const playTone = (
  audioContext: AudioContext,
  frequency: number,
  duration: number = 0.1
) => {
  const oscillator = audioContext.createOscillator();
  const gainNode = audioContext.createGain();

  oscillator.frequency.value = frequency;
  oscillator.type = 'sine';

  gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
  gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + duration);

  oscillator.connect(gainNode);
  gainNode.connect(audioContext.destination);

  oscillator.start();
  oscillator.stop(audioContext.currentTime + duration);
};

export const playSoundEffect = async (audioContext: AudioContext, type: 'click' | 'success' | 'error') => {
  const frequencies: Record<string, number> = {
    click: 800,
    success: 1200,
    error: 400,
  };

  playTone(audioContext, frequencies[type], 0.05);
};

/**
 * Convert cents difference to human-readable accuracy percentage
 */
export const centsToPitchAccuracy = (cents: number): number => {
  // 50 cents = 50% accuracy (±50 cents)
  // 0 cents = 100% accuracy (perfect pitch)
  return Math.max(0, 100 - Math.abs(cents) * 0.5);
};

/**
 * Get note name with octave from MIDI number
 */
export const getMidiNoteName = (midi: number): string => {
  const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
  const octave = Math.floor(midi / 12) - 1;
  const noteName = NOTE_NAMES[midi % 12];
  return `${noteName}${octave}`;
};

export default {
  initAudioContext,
  playTone,
  playSoundEffect,
  centsToPitchAccuracy,
  getMidiNoteName,
};