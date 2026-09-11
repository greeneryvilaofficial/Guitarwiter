// Performance optimization service

class PerformanceOptimizer {
  private metrics = {
    frameCount: 0,
    lastFrameTime: performance.now(),
    fps: 60,
    eventLatency: 0,
  }

  private observers: PerformanceObserver[] = []

  constructor() {
    this.initializePerformanceObservers()
  }

  private initializePerformanceObservers() {
    if ('PerformanceObserver' in window) {
      try {
        const observer = new PerformanceObserver((list) => {
          for (const entry of list.getEntries()) {
            if (entry.duration > 50) {
              console.warn(`Slow operation detected: ${entry.name} (${Math.round(entry.duration)}ms)`)
            }
          }
        })
        observer.observe({ entryTypes: ['measure', 'navigation'] })
        this.observers.push(observer)
      } catch (e) {
        console.log('PerformanceObserver not fully supported')
      }
    }
  }

  measureFrame() {
    const now = performance.now()
    this.metrics.frameCount++

    if (now - this.metrics.lastFrameTime >= 1000) {
      this.metrics.fps = this.metrics.frameCount
      this.metrics.frameCount = 0
      this.metrics.lastFrameTime = now
    }
  }

  getFPS(): number {
    return this.metrics.fps
  }

  recordEventLatency(latency: number) {
    this.metrics.eventLatency = latency
  }

  getMetrics() {
    return { ...this.metrics }
  }

  // Optimize by reducing unnecessary renders
  static batchUpdates(callback: () => void) {
    if ('unstable_batchedUpdates' in window) {
      (window as any).unstable_batchedUpdates(callback)
    } else {
      callback()
    }
  }

  // Clean up observers
  destroy() {
    this.observers.forEach(obs => obs.disconnect())
  }
}

export const performanceOptimizer = new PerformanceOptimizer()
