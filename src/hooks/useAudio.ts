import { useEffect, useRef } from 'react'

export const useAudio = (isListening: boolean) => {
  const audioContextRef = useRef<AudioContext | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const analyserRef = useRef<AnalyserNode | null>(null)

  useEffect(() => {
    if (!isListening) return

    const initAudio = async () => {
      try {
        const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
        audioContextRef.current = audioContext

        const stream = await navigator.mediaDevices.getUserMedia({
          audio: {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: false,
          },
        })
        streamRef.current = stream

        const source = audioContext.createMediaStreamSource(stream)
        const analyser = audioContext.createAnalyser()
        analyser.fftSize = 4096
        analyser.smoothingTimeConstant = 0.8

        source.connect(analyser)
        analyserRef.current = analyser
      } catch (error) {
        console.error('Audio initialization error:', error)
      }
    }

    initAudio()

    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop())
      }
      if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
        audioContextRef.current.close()
      }
    }
  }, [isListening])

  return {
    audioContext: audioContextRef.current,
    analyser: analyserRef.current,
  }
}
