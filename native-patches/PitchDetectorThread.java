import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

public class PitchDetectorThread extends Thread {
    private static final int SAMPLE_RATE = 44100;
    
    // Baca mic setiap 1024 sampel (~23ms latensi = sangat responsif)
    private static final int READ_BUFFER_SIZE = 1024; 
    // Tapi tetap butuh sejarah 4096 sampel ke belakang untuk akurasi nada rendah (senar E)
    private static final int ANALYSIS_BUFFER_SIZE = 4096; 
    
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    private WebView webView;
    private Handler mainHandler;

    private float[] analysisBuffer = new float[ANALYSIS_BUFFER_SIZE];
    private float previousRms = 0;
    private long cooldownUntil = 0;

    public PitchDetectorThread(WebView webView) {
        this.webView = webView;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void run() {
        int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_FLOAT);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                Math.max(READ_BUFFER_SIZE * 4, minBufferSize));

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) return;

        audioRecord.startRecording();
        isRecording = true;
        float[] readBuffer = new float[READ_BUFFER_SIZE];

        while (isRecording) {
            int readResult = audioRecord.read(readBuffer, 0, READ_BUFFER_SIZE, AudioRecord.READ_BLOCKING);
            if (readResult > 0) {
                processAudioStream(readBuffer);
            }
        }
        
        audioRecord.stop();
        audioRecord.release();
    }

    private void processAudioStream(float[] newAudio) {
        // 1. Geser sejarah audio lama ke kiri, masukkan audio baru ke kanan (Ring Buffer)
        System.arraycopy(analysisBuffer, READ_BUFFER_SIZE, analysisBuffer, 0, ANALYSIS_BUFFER_SIZE - READ_BUFFER_SIZE);
        System.arraycopy(newAudio, 0, analysisBuffer, ANALYSIS_BUFFER_SIZE - READ_BUFFER_SIZE, READ_BUFFER_SIZE);

        long now = System.currentTimeMillis();
        float currentRms = calculateRms(newAudio);

        if (now < cooldownUntil) {
            previousRms = currentRms;
            return;
        }

        // 2. ONSET DETECTION: Mendeteksi momen spesifik saat senar dipetik (Transient)
        // Syarat: Volume cukup terdengar (>0.015) DAN lonjakan volume mendadak (1.5x lipat dari sebelumnya)
        if (currentRms > 0.015f && currentRms > (previousRms * 1.5f)) {
            
            // 3. Petikan terdeteksi! Langsung jalankan algoritma YIN ke seluruh buffer
            float freq = detectPitchYin(analysisBuffer, SAMPLE_RATE);
            
            if (freq > 0) {
                // Konversi Hz ke MIDI
                float midi = (float) (69 + 12 * (Math.log(freq / 440.0) / Math.log(2)));
                
                // Kirim langsung ke WebView (Javascript)
                mainHandler.post(() -> {
                    if (webView != null) {
                        webView.evaluateJavascript("window.triggerNoteFromJava(" + midi + ");", null);
                    }
                });
                
                // Cooldown dipersingkat menjadi 120ms agar bisa bermain melodi cepat
                cooldownUntil = now + 120; 
            }
        }
        previousRms = currentRms;
    }

    private float calculateRms(float[] buffer) {
        float sum = 0;
        for (float v : buffer) sum += v * v;
        return (float) Math.sqrt(sum / buffer.length);
    }

    // 4. Algoritma YIN (Cepat & Akurat)
    private float detectPitchYin(float[] buffer, int sampleRate) {
        int yinBufferLength = buffer.length / 2;
        float[] yinBuffer = new float[yinBufferLength];

        // Langkah A: Difference function
        for (int t = 1; t < yinBufferLength; t++) {
            for (int i = 0; i < yinBufferLength; i++) {
                float delta = buffer[i] - buffer[i + t];
                yinBuffer[t] += delta * delta;
            }
        }

        // Langkah B: Cumulative mean normalized difference
        yinBuffer[0] = 1;
        float runningSum = 0;
        for (int t = 1; t < yinBufferLength; t++) {
            runningSum += yinBuffer[t];
            yinBuffer[t] *= t / runningSum;
        }

        // Langkah C: Absolute threshold (Cari titik minimum pertama)
        int tau = -1;
        float threshold = 0.15f; // Toleransi noise
        for (int t = 1; t < yinBufferLength; t++) {
            if (yinBuffer[t] < threshold) {
                while (t + 1 < yinBufferLength && yinBuffer[t + 1] < yinBuffer[t]) {
                    t++;
                }
                tau = t;
                break;
            }
        }

        // Jika tidak ketemu di bawah threshold, cari nilai minimum mutlak
        if (tau == -1) {
            float minVal = Float.MAX_VALUE;
            for (int t = 1; t < yinBufferLength; t++) {
                if (yinBuffer[t] < minVal) {
                    minVal = yinBuffer[t];
                    tau = t;
                }
            }
            // Jika sinyal terlalu acak (bukan nada musik), batalkan
            if (minVal > 0.4f) return -1; 
        }

        return (float) sampleRate / tau;
    }

    public void stopRecording() {
        isRecording = false;
    }
}