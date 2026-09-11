import { useEffect, useRef } from 'react'

interface KeyboardProps {
  onKeyPress: (char: string) => void
  onDelete: () => void
  onClear: () => void
}

const Keyboard = ({ onKeyPress, onDelete, onClear }: KeyboardProps) => {
  const touchStartRef = useRef<{ [key: string]: number }>({}) // Track touch hold time

  const rows = [
    ['Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'],
    ['A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'],
    ['Z', 'X', 'C', 'V', 'B', 'N', 'M'],
  ]

  const handleKeyDown = (char: string) => {
    onKeyPress(char.toLowerCase())
    // Visual feedback
    const element = document.getElementById(`key-${char}`)
    if (element) {
      element.classList.add('scale-95', 'bg-accent')
    }
  }

  const handleKeyUp = (char: string) => {
    const element = document.getElementById(`key-${char}`)
    if (element) {
      element.classList.remove('scale-95', 'bg-accent')
    }
  }

  const handleTouchStart = (char: string) => {
    touchStartRef.current[char] = Date.now()
    handleKeyDown(char)
  }

  const handleTouchEnd = (char: string) => {
    delete touchStartRef.current[char]
    handleKeyUp(char)
  }

  const handleDeleteStart = () => {
    touchStartRef.current['delete'] = Date.now()
  }

  const handleDeleteEnd = () => {
    delete touchStartRef.current['delete']
  }

  return (
    <div className="bg-gray-800 rounded-lg p-4 border border-gray-700">
      <h2 className="text-xl font-bold text-white mb-4">⌨️ Gboard-like Keyboard</h2>

      {/* Keyboard Layout - Responsive */}
      <div className="space-y-2 mb-4">
        {rows.map((row, rowIndex) => (
          <div key={rowIndex} className="flex gap-2 justify-center keyboard-row">
            {row.map((char) => (
              <button
                key={char}
                id={`key-${char}`}
                onMouseDown={() => handleKeyDown(char)}
                onMouseUp={() => handleKeyUp(char)}
                onMouseLeave={() => handleKeyUp(char)}
                onTouchStart={() => handleTouchStart(char)}
                onTouchEnd={() => handleTouchEnd(char)}
                className="key-button flex-1 min-w-12 py-3 px-2 bg-gray-700 hover:bg-gray-600 text-white font-semibold rounded-lg transition-all duration-75 active:scale-95 active:bg-accent md:min-w-auto"
              >
                {char}
              </button>
            ))}
          </div>
        ))}

        {/* Special Keys Row */}
        <div className="flex gap-2 justify-center keyboard-row">
          {/* Space Bar */}
          <button
            onMouseDown={() => handleKeyDown(' ')}
            onMouseUp={() => handleKeyUp(' ')}
            onMouseLeave={() => handleKeyUp(' ')}
            onTouchStart={() => handleTouchStart(' ')}
            onTouchEnd={() => handleTouchEnd(' ')}
            className="key-button flex-grow py-3 px-4 bg-gray-700 hover:bg-gray-600 text-white font-semibold rounded-lg transition-all duration-75 active:scale-95 active:bg-accent"
          >
            SPACE
          </button>

          {/* Delete Button */}
          <button
            onMouseDown={handleDeleteStart}
            onMouseUp={handleDeleteEnd}
            onMouseLeave={handleDeleteEnd}
            onTouchStart={handleDeleteStart}
            onTouchEnd={handleDeleteEnd}
            onClick={onDelete}
            className="key-button py-3 px-4 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-lg transition-all duration-75 active:scale-95 w-20"
          >
            ← Del
          </button>

          {/* Clear Button */}
          <button
            onClick={onClear}
            className="key-button py-3 px-4 bg-yellow-600 hover:bg-yellow-700 text-white font-semibold rounded-lg transition-all duration-75 active:scale-95 w-20"
          >
            Clear
          </button>
        </div>
      </div>

      {/* Info Text */}
      <p className="text-gray-400 text-xs text-center mt-4">
        Click keys or hold for accelerated delete. Responsive design for all devices.
      </p>
    </div>
  )
}

export default Keyboard
