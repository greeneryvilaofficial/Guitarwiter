import React, { useState, useEffect, useRef } from 'react';
import '../styles/keyboard.css';

interface KeyboardProps {
  onKeyPress?: (char: string, note: string) => void;
  theme?: 'dark' | 'light' | 'blue' | 'purple' | 'green';
  mode?: 'letters' | 'symbols' | 'emoji';
}

const Keyboard: React.FC<KeyboardProps> = ({
  onKeyPress,
  theme = 'dark',
  mode: initialMode = 'letters',
}) => {
  const [mode, setMode] = useState(initialMode);
  const [shiftActive, setShiftActive] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const keyboardRef = useRef<HTMLDivElement>(null);

  const NOTE_NAMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
  const BASE_MIDI = 40; // E2

  const handleKeyDown = (char: string, noteName: string) => {
    if (onKeyPress) {
      onKeyPress(char, noteName);
    }
  };

  return (
    <div className={`keyboard-wrapper theme-${theme}`} ref={keyboardRef}>
      <div className="kb-float-wrap">
        <div className="keyboard" id="keyboard">
          {/* Keyboard will be rendered here */}
        </div>
        <div className="emoji-panel" id="emojiPanel">
          {/* Emoji panel will be rendered here */}
        </div>
      </div>
    </div>
  );
};

export default Keyboard;