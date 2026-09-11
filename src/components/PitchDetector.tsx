import { useState, useCallback, useEffect } from 'react'
import { useDispatch, useSelector } from 'react-redux'
import { deleteText, clearText } from '../store/appSlice'
import { usePitchDetection } from '../hooks/usePitchDetection'
import { performanceOptimizer } from '../services/performanceOptimizer'
import type { RootState, AppDispatch } from '../store'

interface PitchDetectorProps {
  isListening: boolean
  onListeningChange: (listening: boolean) => void
  onTextChange: (text: string) => void
  currentText?: string
}

const PitchDetector = ({
  isListening,
  onListeningChange,
  onTextChange,
}: PitchDetectorProps) => {
  const dispatch = useDispatch<AppDispatch>()
  const { currentPitch, currentNote } = useSelector((state: RootState) => state.app)
  const [isAccessing, setIsAccessing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [deleteCounter, setDeleteCounter] = useState(0)
  const [isHoldingDelete, setIsHoldingDelete] = useState(false)

  usePitchDetection(isListening)

  const handleToggleListening = useCallback(async () => {
    if (!isListening) {
      setIsAccessing(true)
      setError(null)
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
        stream.getTracks().forEach(track => track.stop())
        onListeningChange(true)
      } catch (err) {
        setError('Failed to access microphone')
        console.error('Microphone error:', err)
      } finally {
        setIsAccessing(false)
      }
    } else {
      onListeningChange(false)
      setDeleteCounter(0)
    }
  }, [isListening, onListeningChange])

  // Handle hold-to-delete acceleration
  useEffect(() => {
    if (!isHoldingDelete) {
      setDeleteCounter(0)
      return
    }

    let interval: NodeJS.Timeout
    const startTime = Date.now()

    const deleteWithAcceleration = () => {
      const elapsedTime = Date.now() - startTime
      // Accelerate deletion: start slow, then get faster
      const deleteSpeed = Math.max(50, 200 - elapsedTime / 20) // Minimum 50ms interval

      performanceOptimizer.measureFrame()
      dispatch(deleteText())
      setDeleteCounter(prev => prev + 1)

      interval = setTimeout(deleteWithAcceleration, deleteSpeed)
    }

    interval = setTimeout(deleteWithAcceleration, 200)

    return () => clearTimeout(interval)
  }, [isHoldingDelete, dispatch])

  return (
    <div className="bg-gray-800 rounded-lg p-6 border border-gray-700">
      <h2 className="text-xl font-bold text-white mb-4">🎙️ Pitch Detection</h2>

      {/* Status Section */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Status</p>
          <div className="flex items-center gap-2">
            <div
              className={`w-3 h-3 rounded-full ${
                isListening ? 'bg-green-500 animate-pulse' : 'bg-red-500'
              }`}
            />
            <p className="text-white font-semibold">
              {isListening ? 'Listening' : 'Idle'}
            </p>
          </div>
        </div>

        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Current Frequency</p>
          <p className="text-white font-semibold">
            {currentPitch ? `${currentPitch} Hz` : '--'}
          </p>
        </div>

        <div className="bg-gray-700 rounded-lg p-4">
          <p className="text-gray-400 text-sm mb-2">Detected Note</p>
          <p className="text-white font-semibold text-lg">
            {currentNote ? currentNote : '--'}
          </p>
        </div>
      </div>

      {/* Control Buttons */}
      <div className="flex gap-4 mb-6">
        <button
          onClick={handleToggleListening}
          disabled={isAccessing}
          className={`flex-1 py-3 px-4 rounded-lg font-semibold transition-all ${
            isListening
              ? 'bg-red-600 hover:bg-red-700 text-white'
              : 'bg-green-600 hover:bg-green-700 text-white'
          } disabled:opacity-50 disabled:cursor-not-allowed`}
        >
          {isAccessing ? 'Accessing Microphone...' : isListening ? 'Stop Listening' : 'Start Listening'}
        </button>
      </div>

      {/* Delete Controls */}
      <div className="bg-gray-700 rounded-lg p-4">
        <p className="text-gray-400 text-sm mb-3">Delete Controls</p>
        <div className="flex gap-3">
          <button
            onMouseDown={() => setIsHoldingDelete(true)}
            onMouseUp={() => setIsHoldingDelete(false)}
            onMouseLeave={() => setIsHoldingDelete(false)}
            onTouchStart={() => setIsHoldingDelete(true)}
            onTouchEnd={() => setIsHoldingDelete(false)}
            className="flex-1 py-2 px-3 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold transition-all active:scale-95"
          >
            Hold to Delete (x{deleteCounter})
          </button>
          <button
            onClick={() => {
              dispatch(clearText())
              onTextChange('')
            }}
            className="flex-1 py-2 px-3 bg-yellow-600 hover:bg-yellow-700 text-white rounded-lg font-semibold transition-all"
          >
            Clear All
          </button>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="mt-4 p-3 bg-red-900 border border-red-700 rounded-lg text-red-200">
          {error}
        </div>
      )}
    </div>
  )
}

export default PitchDetector
