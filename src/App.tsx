import { useState } from 'react'
import Keyboard from './components/Keyboard'
import './App.css'

function App() {
  const [theme, setTheme] = useState<'dark' | 'light' | 'blue' | 'purple' | 'green'>('dark')
  const [mode, setMode] = useState<'letters' | 'symbols' | 'emoji'>('letters')
  const [detectedNote, setDetectedNote] = useState<string | null>(null)

  const handleKeyPress = (char: string, note: string) => {
    console.log(`Pressed: ${char} (Note: ${note})`)
    setDetectedNote(note)
  }

  return (
    <div className={`app theme-${theme}`}>
      <div className="app-header">
        <h1>🎸 GuitarWiter Keyboard</h1>
        <p>Petik nada gitar untuk mengetik</p>
      </div>
      
      <div className="controls">
        <div className="control-group">
          <label>Tema:</label>
          <select value={theme} onChange={(e) => setTheme(e.target.value as any)}>
            <option value="dark">Gelap</option>
            <option value="light">Terang</option>
            <option value="blue">Biru</option>
            <option value="purple">Ungu</option>
            <option value="green">Hijau</option>
          </select>
        </div>
        
        <div className="control-group">
          <label>Mode:</label>
          <select value={mode} onChange={(e) => setMode(e.target.value as any)}>
            <option value="letters">Huruf</option>
            <option value="symbols">Simbol</option>
            <option value="emoji">Emoji</option>
          </select>
        </div>
      </div>

      {detectedNote && (
        <div className="detected-note">
          Nada terdeteksi: <strong>{detectedNote}</strong>
        </div>
      )}

      <Keyboard theme={theme} mode={mode} onKeyPress={handleKeyPress} />
    </div>
  )
}

export default App