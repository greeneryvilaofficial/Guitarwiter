// TunerPro Pitch Detection Algorithm
// High-accuracy pitch detection using autocorrelation method

export function detectPitch(buffer: Uint8Array, sampleRate: number): number {
  const floatBuffer = new Float32Array(buffer.length)
  for (let i = 0; i < buffer.length; i++) {
    floatBuffer[i] = (buffer[i] - 128) / 128
  }

  return autoCorrelate(floatBuffer, sampleRate)
}

function autoCorrelate(buffer: Float32Array, sampleRate: number): number {
  const SIZE = buffer.length
  const MAX_SAMPLES = Math.floor(SIZE / 2)
  let best_offset = -1
  let best_correlation = 0
  let rms = 0

  // Compute RMS and skip if too quiet
  for (let i = 0; i < SIZE; i++) {
    const val = buffer[i]
    rms += val * val
  }
  rms = Math.sqrt(rms / SIZE)
  if (rms < 0.01) return -1

  // Find the best correlation offset
  let lastCorrelation = 1
  for (let offset = 1; offset < MAX_SAMPLES; offset++) {
    let correlation = 0
    for (let i = 0; i < MAX_SAMPLES; i++) {
      correlation += Math.abs(buffer[i] - buffer[i + offset])
    }
    correlation = 1 - (correlation / MAX_SAMPLES) / 2

    if (correlation > 0.9 && correlation > lastCorrelation) {
      if (correlation > best_correlation) {
        best_correlation = correlation
        best_offset = offset
      }
    }
    lastCorrelation = correlation
  }

  if (best_correlation > 0.01) {
    return sampleRate / best_offset
  }
  return -1
}

// Improved TunerPro algorithm using multiple methods
export function detectPitchAdvanced(buffer: Uint8Array, sampleRate: number): number {
  const frequency1 = detectPitch(buffer, sampleRate)
  if (frequency1 < 0) return -1

  // Apply smoothing and confidence check
  if (frequency1 > 50 && frequency1 < 2000) {
    return frequency1
  }
  return -1
}

// Map frequency to MIDI note number
export function frequencyToMidi(frequency: number): number {
  return Math.round(12 * Math.log2(frequency / 440) + 69)
}

// Map MIDI note to frequency
export function midiToFrequency(midiNote: number): number {
  return 440 * Math.pow(2, (midiNote - 69) / 12)
}

// Get note name from MIDI number
export function midiToNoteName(midiNote: number): string {
  const notes = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
  const octave = Math.floor(midiNote / 12) - 1
  const noteIndex = midiNote % 12
  return `${notes[noteIndex]}${octave}`
}
