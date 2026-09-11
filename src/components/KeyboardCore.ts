/**
 * KeyboardCore.ts - Pure TypeScript keyboard logic
 * Handles note detection, key mapping, and pitch-to-fret conversion
 */

export interface Note {
  idx: number;
  midi: number;
  freq: number;
  name: string;
  string?: number;
  fret?: number;
}

export interface KeyDef {
  type: 'letter' | 'shift' | 'backspace' | 'emoji-toggle' | 'num-toggle' | 'literal' | 'enter';
  char?: string;
  label?: string;
  cornerNum?: number;
  big?: boolean;
}

const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
const BASE_MIDI = 40; // E2 (open low E string)

const TUNING = [
  { string: 6, open: 'E', midi: 40 },
  { string: 5, open: 'A', midi: 45 },
  { string: 4, open: 'D', midi: 50 },
  { string: 3, open: 'G', midi: 55 },
  { string: 2, open: 'B', midi: 59 },
  { string: 1, open: 'e', midi: 64 },
];

const MAX_FRET = 20;

export const KEY_DEFS: KeyDef[] = [
  ...['1', '2', '3', '4', '5', '6', '7', '8', '9', '0'].map((ch) => ({ type: 'letter' as const, char: ch })),
  ...['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'].map((ch, i) => ({
    type: 'letter' as const,
    char: ch,
    cornerNum: (i + 1) % 10,
  })),
  ...['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'].map((ch) => ({ type: 'letter' as const, char: ch })),
  { type: 'shift' as const },
  ...['z', 'x', 'c', 'v', 'b', 'n', 'm'].map((ch) => ({ type: 'letter' as const, char: ch })),
  { type: 'backspace' as const },
  { type: 'num-toggle' as const },
  { type: 'emoji-toggle' as const },
  { type: 'literal' as const, char: ',', label: ',' },
  { type: 'literal' as const, char: ' ', label: 'spasi', big: true },
  { type: 'literal' as const, char: '.', label: '.' },
  { type: 'enter' as const },
];

/**
 * Find which string/fret combination matches a MIDI note
 */
function findFret(midi: number): { string: number; open: string; fret: number } | null {
  let best: { string: number; open: string; fret: number } | null = null;
  TUNING.forEach((t) => {
    const fret = midi - t.midi;
    if (fret >= 0 && fret <= MAX_FRET) {
      if (!best || fret < best.fret) {
        best = { string: t.string, open: t.open, fret };
      }
    }
  });
  return best;
}

/**
 * Generate all notes for keyboard keys with pitch info
 */
export function generateNotes(): Note[] {
  const notes: Note[] = [];
  for (let i = 0; i < KEY_DEFS.length; i++) {
    const midi = BASE_MIDI + i;
    const freq = 440 * Math.pow(2, (midi - 69) / 12);
    const name = NOTE_NAMES[((midi % 12) + 12) % 12] + (Math.floor(midi / 12) - 1);
    const pos = findFret(midi);
    notes.push({
      idx: i,
      midi,
      freq,
      name,
      string: pos ? pos.string : undefined,
      fret: pos ? pos.fret : undefined,
    });
  }
  return notes;
}

/**
 * Find nearest note by frequency (for pitch detection)
 */
export function findNearestNote(frequency: number, notes: Note[]): Note | null {
  if (notes.length === 0) return null;
  
  let nearest = notes[0];
  let minDiff = Math.abs(notes[0].freq - frequency);

  for (let i = 1; i < notes.length; i++) {
    const diff = Math.abs(notes[i].freq - frequency);
    if (diff < minDiff) {
      minDiff = diff;
      nearest = notes[i];
    }
  }

  // Only return if difference is within reasonable range (roughly ±100 cents)
  const centsDiff = 1200 * Math.log2(frequency / nearest.freq);
  return Math.abs(centsDiff) < 100 ? nearest : null;
}

/**
 * Convert MIDI note to frequency
 */
export function midiToFreq(midi: number): number {
  return 440 * Math.pow(2, (midi - 69) / 12);
}

/**
 * Convert frequency to MIDI note
 */
export function freqToMidi(freq: number): number {
  return Math.round(69 + 12 * Math.log2(freq / 440));
}

export default {
  generateNotes,
  findNearestNote,
  midiToFreq,
  freqToMidi,
  KEY_DEFS,
};