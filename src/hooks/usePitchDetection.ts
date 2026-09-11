import { useEffect, useRef, useState, useCallback } from 'react';
import { findNearestNote, generateNotes } from '../components/KeyboardCore';

interface PitchDetectionResult {
  frequency: number;
  note: string | null;
  confidence: number;
  isListening: boolean;
}

const BUFFER_SIZE = 4096;

export const usePitchDetection = (
  onNoteDetected?: (note: string, frequency: number) => void
): PitchDetectionResult => {
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const dataArrayRef = useRef<Uint8Array | null>(null);
  const [result, setResult] = useState<PitchDetectionResult>({
    frequency: 0,
    note: null,
    confidence: 0,
    isListening: false,
  });
  const animationIdRef = useRef<number | null>(null);
  const notesRef = useRef(generateNotes());

  const startListening = useCallback(async () => {
    try {
      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      audioContextRef.current = audioContext;

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const source = audioContext.createMediaStreamAudioSource(stream);

      const analyser = audioContext.createAnalyser();
      analyser.fftSize = BUFFER_SIZE * 2;
      analyserRef.current = analyser;

      source.connect(analyser);

      const dataArray = new Uint8Array(analyser.frequencyBinCount);
      dataArrayRef.current = dataArray;

      setResult((prev) => ({ ...prev, isListening: true }));
      analyzeFrequency();
    } catch (error) {
      console.error('Pitch detection error:', error);
      setResult((prev) => ({ ...prev, isListening: false }));
    }
  }, []);

  const stopListening = useCallback(() => {
    if (animationIdRef.current) {
      cancelAnimationFrame(animationIdRef.current);
      animationIdRef.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    setResult((prev) => ({ ...prev, isListening: false, frequency: 0, note: null }));
  }, []);

  const analyzeFrequency = useCallback(() => {
    if (!analyserRef.current || !dataArrayRef.current) return;

    analyserRef.current.getByteFrequencyData(dataArrayRef.current);
    const nyquist = (audioContextRef.current?.sampleRate || 44100) / 2;
    const binHz = nyquist / dataArrayRef.current.length;
    const threshold = 30;

    let maxBin = 0;
    let maxValue = 0;

    for (let i = 0; i < dataArrayRef.current.length; i++) {
      if (dataArrayRef.current[i] > maxValue) {
        maxValue = dataArrayRef.current[i];
        maxBin = i;
      }
    }

    if (maxValue < threshold) {
      setResult((prev) => ({ ...prev, frequency: 0, note: null, confidence: 0 }));
    } else {
      const frequency = maxBin * binHz;
      const note = findNearestNote(frequency, notesRef.current);
      const confidence = Math.min(100, (maxValue / 255) * 100);

      setResult({
        frequency,
        note: note?.name || null,
        confidence,
        isListening: true,
      });

      if (note && onNoteDetected) {
        onNoteDetected(note.name, frequency);
      }
    }

    animationIdRef.current = requestAnimationFrame(analyzeFrequency);
  }, [onNoteDetected]);

  useEffect(() => {
    return () => {
      stopListening();
    };
  }, [stopListening]);

  return { ...result, isListening: result.isListening };
};

export default usePitchDetection;