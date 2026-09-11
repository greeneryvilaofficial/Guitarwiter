import { useEffect, useState } from 'react'

export const usePerformance = () => {
  const [metrics, setMetrics] = useState({
    fps: 60,
    memoryUsage: 0,
    latency: 0,
  })

  useEffect(() => {
    let lastTime = performance.now()
    let frames = 0
    let animationId: number

    const measurePerformance = () => {
      frames++
      const currentTime = performance.now()
      const delta = currentTime - lastTime

      if (delta >= 1000) {
        const fps = Math.round(frames * 1000 / delta)
        setMetrics(prev => ({ ...prev, fps }))
        frames = 0
        lastTime = currentTime
      }

      // Memory usage (if available)
      if ((performance as any).memory) {
        const memUsage = Math.round((performance as any).memory.usedJSHeapSize / 1048576)
        setMetrics(prev => ({ ...prev, memoryUsage: memUsage }))
      }

      animationId = requestAnimationFrame(measurePerformance)
    }

    animationId = requestAnimationFrame(measurePerformance)
    return () => cancelAnimationFrame(animationId)
  }, [])

  return metrics
}
