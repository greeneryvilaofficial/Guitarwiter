# 🎸 GuitarWiter - Guitar-to-Keyboard Input System

**Real-time pitch detection powered by TunerPro algorithm with zero-delay keyboard input**

## 🚀 Features

- **TunerPro Pitch Detection**: Accurate, low-latency pitch recognition
- **Zero-Delay Typing**: Instant keyboard input response
- **Gboard-like UI**: Responsive, modern keyboard interface
- **Hold-to-Delete**: Long-press acceleration for faster deletion
- **Cross-App Compatible**: Works with any application
- **Performance Optimized**: Service Worker, debouncing, GPU acceleration
- **Mobile Responsive**: Touch-optimized interface

## 📋 Tech Stack

- **Frontend**: React 18 + Tailwind CSS
- **Audio Processing**: Web Audio API + TunerPro Algorithm
- **State Management**: Redux Toolkit
- **Performance**: Service Worker, IndexedDB caching
- **Build Tool**: Vite
- **Testing**: Jest + React Testing Library

## 📁 Project Structure

```
guitarwiter/
├── public/
├── src/
│   ├── components/
│   │   ├── Keyboard.tsx
│   │   ├── PitchDetector.tsx
│   │   ├── InputDisplay.tsx
│   │   └── PerformanceMonitor.tsx
│   ├── services/
│   │   ├── audioService.ts
│   │   ├── pitchDetection.ts (TunerPro)
│   │   ├── keyboardInput.ts
│   │   └── performanceOptimizer.ts
│   ├── hooks/
│   │   ├── useAudio.ts
│   │   ├── usePitchDetection.ts
│   │   └── usePerformance.ts
│   ├── store/
│   │   └── appSlice.ts
│   ├── utils/
│   │   ├── constants.ts
│   │   └── helpers.ts
│   ├── styles/
│   │   └── globals.css
│   ├── App.tsx
│   └── main.tsx
├── public/
│   └── service-worker.js
├── package.json
├── vite.config.ts
├── tsconfig.json
└── tailwind.config.js
```

## 🔧 Installation

```bash
npm install
npm run dev
```

## 📚 Documentation

- [Architecture](./docs/ARCHITECTURE.md)
- [Pitch Detection](./docs/PITCH_DETECTION.md)
- [Performance Optimization](./docs/PERFORMANCE.md)
- [API Reference](./docs/API.md)

## 📝 License

MIT

---

**Made with ❤️ by Greeneryvilla**
