interface InputDisplayProps {
  text: string
}

const InputDisplay = ({ text }: InputDisplayProps) => {
  return (
    <div className="bg-gradient-to-r from-gray-800 to-gray-900 rounded-lg p-6 border-2 border-accent shadow-lg">
      <h2 className="text-gray-400 text-sm font-semibold mb-3 uppercase tracking-wider">Input Text</h2>
      <div className="min-h-24 bg-gray-900 rounded-lg p-4 border border-gray-700">
        <p className="text-white text-lg leading-relaxed break-words whitespace-pre-wrap">
          {text}
          {text !== '' && <span className="text-cursor">▌</span>}
        </p>
      </div>
      <div className="mt-3 flex justify-between items-center text-sm text-gray-400">
        <span>Characters: {text.length}</span>
        <span>Words: {text.trim().split(/\s+/).filter(w => w).length || 0}</span>
      </div>
    </div>
  )
}

export default InputDisplay
