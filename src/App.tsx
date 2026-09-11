import { useState } from 'react'
import Keyboard from './components/Keyboard'
import PitchDetector from './components/PitchDetector'
import InputDisplay from './components/InputDisplay'
import PerformanceMonitor from './components/PerformanceMonitor'

function App() {
  const [isListening, setIsListening] = useState(false)
  const [currentText, setCurrentText] = useState('')
  const [showPerformance, setShowPerformance] = useState(false)

  return (
    <div className="min-h-screen bg-gradient-to-br from-gray-900 via-gray-800 to-black flex flex-col">
      {/* Header */}
      <header className="p-4 border-b border-gray-700">
        <div className="max-w-6xl mx-auto flex justify-between items-center">
          <div>
            <h1 className="text-3xl font-bold text-white">🎸 GuitarWiter</h1>
            <p className="text-gray-400 text-sm">Real-time Guitar-to-Keyboard Input</p>
          </div>
          <button
            onClick={() => setShowPerformance(!showPerformance)}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 text-white rounded-lg transition"
          >
            {showPerformance ? 'Hide' : 'Show'} Performance
          </button>
        </div>
      </header>

      {/* Main Content */}
      <main className="flex-1 max-w-6xl mx-auto w-full flex flex-col gap-6 p-6">
        {/* Performance Monitor */}
        {showPerformance && <PerformanceMonitor />}

        {/* Input Display */}
        <InputDisplay text={currentText} />

        {/* Pitch Detector */}
        <PitchDetector
          isListening={isListening}
          onListeningChange={setIsListening}
          onTextChange={setCurrentText}
          currentText={currentText}
        />

        {/* Keyboard */}
        <Keyboard
          onKeyPress={(text) => setCurrentText(currentText + text)}
          onDelete={() => setCurrentText(currentText.slice(0, -1))}
          onClear={() => setCurrentText('')}
        />
      </main>

      {/* Footer */}
      <footer className="p-4 border-t border-gray-700 text-center text-gray-400 text-sm">
        <p>GuitarWiter © 2024 | Powered by TunerPro Pitch Detection</p>
      </footer>
    </div>
  )
}

export default App
