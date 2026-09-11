// Zero-delay keyboard input service
// Handles keyboard events with minimal latency

type KeyboardEventListener = (key: string) => void

class KeyboardInputService {
  private listeners: Set<KeyboardEventListener> = new Set()
  private pressedKeys: Set<string> = new Set()
  private lastEventTime: number = 0

  constructor() {
    this.setupListeners()
  }

  private setupListeners() {
    // Prevent bubbling and use capture phase for faster event handling
    document.addEventListener('keydown', this.handleKeyDown.bind(this), true)
    document.addEventListener('keyup', this.handleKeyUp.bind(this), true)
  }

  private handleKeyDown(event: KeyboardEvent) {
    const key = event.key
    if (!this.pressedKeys.has(key)) {
      this.pressedKeys.add(key)
      this.emit(key)
      this.lastEventTime = performance.now()
    }
  }

  private handleKeyUp(event: KeyboardEvent) {
    const key = event.key
    this.pressedKeys.delete(key)
  }

  private emit(key: string) {
    this.listeners.forEach(listener => {
      // Use requestAnimationFrame for zero-delay processing
      requestAnimationFrame(() => listener(key))
    })
  }

  public subscribe(listener: KeyboardEventListener): () => void {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  public simulateKeyPress(char: string) {
    const event = new KeyboardEvent('keydown', {
      key: char,
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(event)
  }

  public getLastEventLatency(): number {
    return performance.now() - this.lastEventTime
  }
}

export const keyboardInputService = new KeyboardInputService()

// Pitch to character mapping for guitar input
const PITCH_TO_CHAR_MAP: Record<number, string> = {
  // E string (base frequencies in Hz)
  82: 'e',   // E2
  110: 'a',  // A2
  147: 'd',  // D3
  196: 'g',  // G3
  247: 'b',  // B3
  330: 'e',  // E4
  // Add more mappings as needed
}

export function pitchToCharacter(frequency: number): string | null {
  // Find closest pitch match within tolerance
  const tolerance = 5 // Hz
  for (const [freq, char] of Object.entries(PITCH_TO_CHAR_MAP)) {
    if (Math.abs(parseFloat(freq) - frequency) < tolerance) {
      return char
    }
  }
  return null
}

// Debounce function for reducing excessive input
export function debounce<T extends (...args: any[]) => any>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: NodeJS.Timeout
  return function executedFunction(...args: Parameters<T>) {
    const later = () => {
      clearTimeout(timeout)
      func(...args)
    }
    clearTimeout(timeout)
    timeout = setTimeout(later, wait)
  }
}
