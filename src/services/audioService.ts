// Audio service with optimized performance

class AudioService {
  private audioContext: AudioContext | null = null
  private analyser: AnalyserNode | null = null
  private stream: MediaStream | null = null
  private isRunning: boolean = false

  async initialize(): Promise<void> {
    try {
      this.audioContext = new (window.AudioContext || (window as any).webkitAudioContext)()
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: false,
        },
      })

      const source = this.audioContext.createMediaStreamSource(this.stream)
      this.analyser = this.audioContext.createAnalyser()
      this.analyser.fftSize = 4096
      this.analyser.smoothingTimeConstant = 0.8

      source.connect(this.analyser)
      this.isRunning = true
    } catch (error) {
      console.error('Audio initialization failed:', error)
      throw error
    }
  }

  getAnalyser(): AnalyserNode | null {
    return this.analyser
  }

  getFrequencyData(): Uint8Array | null {
    if (!this.analyser) return null
    const data = new Uint8Array(this.analyser.frequencyBinCount)
    this.analyser.getByteFrequencyData(data)
    return data
  }

  async cleanup(): Promise<void> {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop())
    }
    if (this.audioContext) {
      await this.audioContext.close()
    }
    this.isRunning = false
  }

  isActive(): boolean {
    return this.isRunning
  }
}

export const audioService = new AudioService()
