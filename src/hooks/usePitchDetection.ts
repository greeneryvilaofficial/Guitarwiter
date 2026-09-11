import { useDispatch } from 'react-redux'
import { useEffect, useState } from 'react'
import { setPitch, setNote, updatePerformanceMetrics } from '../store/appSlice'
import { detectPitch } from '../services/pitchDetection'
import type { AppDispatch } from '../store'

export const usePitchDetection = (isListening: boolean) => {
  const dispatch = useDispatch<AppDispatch>()
  const [audioContext, setAudioContext] = useState<AudioContext | null>(null)
  const [analyser, setAnalyser] = useState<AnalyserNode | null>(null)
  const [dataArray, setDataArray] = useState<Uint8Array | null>(null)
  const [startTime, setStartTime] = useState<number>(0)

  useEffect(() => {
    if (!isListening) return

    const initAudio = async () => {
      try {
        const context = new (window.AudioContext || (window as any).webkitAudioContext)()
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        const source = context.createMediaStreamSource(stream)
        const analyserNode = context.createAnalyser()
        analyserNode.fftSize = 4096
        analyserNode.smoothingTimeConstant = 0.8

        source.connect(analyserNode)
        setAudioContext(context)
        setAnalyser(analyserNode)
        setDataArray(new Uint8Array(analyserNode.frequencyBinCount))
        setStartTime(performance.now())
      } catch (error) {
        console.error('Error accessing microphone:', error)
      }
    }

    initAudio()
  }, [isListening])

  useEffect(() => {
    if (!isListening || !analyser || !dataArray) return

    let animationId: number
    const detectLoop = () => {
      analyser.getByteFrequencyData(dataArray)
      const frequency = detectPitch(dataArray, analyser.context.sampleRate)

      if (frequency > 0) {
        dispatch(setPitch(Math.round(frequency)))

        // Get note name from frequency
        const note = frequencyToNote(frequency)
        dispatch(setNote(note))
      }

      // Track latency
      const latency = performance.now() - startTime
      if (latency > 0 && latency < 100) {
        dispatch(updatePerformanceMetrics({ latency: Math.round(latency) }))
      }

      animationId = requestAnimationFrame(detectLoop)
    }

    detectLoop()

    return () => {
      cancelAnimationFrame(animationId)
      if (audioContext && audioContext.state !== 'closed') {
        audioContext.close()
      }
    }
  }, [isListening, analyser, dataArray, dispatch, startTime, audioContext])
}

function frequencyToNote(frequency: number): string {
  const notes = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']
  const noteNum = 12 * Math.log2(frequency / 16.35)
  const octave = Math.floor(noteNum / 12)
  const note = Math.round(noteNum % 12)
  return `${notes[note]}${octave}`
}
