import { usePerformance } from '../hooks/usePerformance'
import { useSelector } from 'react-redux'
import type { RootState } from '../store'

const PerformanceMonitor = () => {
  const { fps, memoryUsage } = usePerformance()
  const { performanceMetrics } = useSelector((state: RootState) => state.app)

  return (
    <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
      <h2 className="text-xl font-bold text-white mb-4">📊 Performance Monitor</h2>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        {/* FPS */}
        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">FPS</p>
          <div className="flex items-baseline gap-2">
            <p className="text-2xl font-bold text-green-400">{fps}</p>
            <p className="text-gray-400 text-sm">fps</p>
          </div>
          <div className="mt-2 w-full bg-gray-600 rounded h-1">
            <div
              className="performance-bar bg-green-500 h-1 rounded transition-all"
              style={{ width: `${Math.min(fps / 60 * 100, 100)}%` }}
            />
          </div>
        </div>

        {/* Latency */}
        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Latency</p>
          <div className="flex items-baseline gap-2">
            <p className="text-2xl font-bold text-blue-400">{performanceMetrics.latency}</p>
            <p className="text-gray-400 text-sm">ms</p>
          </div>
          <div className="mt-2 w-full bg-gray-600 rounded h-1">
            <div
              className="performance-bar bg-blue-500 h-1 rounded transition-all"
              style={{ width: `${Math.max(0, 100 - performanceMetrics.latency / 2)}%` }}
            />
          </div>
        </div>

        {/* Memory Usage */}
        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Memory</p>
          <div className="flex items-baseline gap-2">
            <p className="text-2xl font-bold text-purple-400">{memoryUsage}</p>
            <p className="text-gray-400 text-sm">MB</p>
          </div>
          <div className="mt-2 w-full bg-gray-600 rounded h-1">
            <div
              className="performance-bar bg-purple-500 h-1 rounded transition-all"
              style={{ width: `${Math.min(memoryUsage / 100 * 100, 100)}%` }}
            />
          </div>
        </div>

        {/* CPU Usage */}
        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Status</p>
          <div className="flex items-baseline gap-2">
            <p className="text-2xl font-bold text-yellow-400">
              {fps > 50 ? '✓' : '⚠'}
            </p>
            <p className="text-gray-400 text-sm">
              {fps > 50 ? 'Optimal' : 'Monitor'}
            </p>
          </div>
          <div className="mt-2">
            <p className="text-xs text-gray-500">
              System running {fps > 50 ? 'smoothly' : 'at reduced performance'}
            </p>
          </div>
        </div>
      </div>

      {/* Performance Tips */}
      {fps < 50 && (
        <div className="mt-4 p-3 bg-yellow-900 border border-yellow-700 rounded-lg text-yellow-200 text-sm">
          💡 <strong>Tip:</strong> Close other applications or browser tabs to improve performance.
        </div>
      )}
    </div>
  )
}

export default PerformanceMonitor
