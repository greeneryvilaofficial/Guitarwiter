import { createSlice, PayloadAction } from '@reduxjs/toolkit'

interface AppState {
  currentText: string
  isListening: boolean
  currentPitch: number | null
  currentNote: string | null
  performanceMetrics: {
    latency: number
    fps: number
    memoryUsage: number
    cpuUsage: number
  }
}

const initialState: AppState = {
  currentText: '',
  isListening: false,
  currentPitch: null,
  currentNote: null,
  performanceMetrics: {
    latency: 0,
    fps: 60,
    memoryUsage: 0,
    cpuUsage: 0,
  },
}

const appSlice = createSlice({
  name: 'app',
  initialState,
  reducers: {
    setText: (state, action: PayloadAction<string>) => {
      state.currentText = action.payload
    },
    addText: (state, action: PayloadAction<string>) => {
      state.currentText += action.payload
    },
    deleteText: (state) => {
      state.currentText = state.currentText.slice(0, -1)
    },
    clearText: (state) => {
      state.currentText = ''
    },
    setListening: (state, action: PayloadAction<boolean>) => {
      state.isListening = action.payload
    },
    setPitch: (state, action: PayloadAction<number | null>) => {
      state.currentPitch = action.payload
    },
    setNote: (state, action: PayloadAction<string | null>) => {
      state.currentNote = action.payload
    },
    updatePerformanceMetrics: (state, action: PayloadAction<Partial<AppState['performanceMetrics']>>) => {
      state.performanceMetrics = { ...state.performanceMetrics, ...action.payload }
    },
  },
})

export const {
  setText,
  addText,
  deleteText,
  clearText,
  setListening,
  setPitch,
  setNote,
  updatePerformanceMetrics,
} = appSlice.actions

export default appSlice.reducer
