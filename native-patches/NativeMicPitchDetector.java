package com.keyboardkustom.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import androidx.core.content.ContextCompat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deteksi nada gitar LANGSUNG lewat AudioRecord (API Android native),
 * BUKAN lewat getUserMedia() di JavaScript/WebView.
 *
 * Kenapa dibuat terpisah dari WebView: getUserMedia() di WebView ternyata
 * selalu gagal (NotAllowedError) di sebagian HP meski izin RECORD_AUDIO
 * sudah "Diizinkan" di level sistem -- kemungkinan pembatasan WebView/OEM
 * yang di luar jangkauan kode. AudioRecord ini cuma butuh izin RECORD_AUDIO
 * biasa (yang sudah terbukti granted), tanpa lewat lapisan WebView sama sekali.
 *
 * Algoritma di bawah ini port dari fungsi autoCorrelate() + micLoop() di
 * index.html, dengan tuning tambahan supaya terasa lebih responsif/instan
 * (buffer lebih kecil, cooldown lebih pendek) — mendekati rasa tuner gitar.
 */
public class NativeMicPitchDetector {

    /** Dipanggil di UI thread setiap kali ada hasil dari thread rekaman. */
    public interface Listener {
        void onOnsetDetected();                 // Mulai kedengaran ada petikan
        void onPitchIndex(int idx, double freq); // Nada valid, ketemu indeks tuts-nya
        void onOutOfRange(double freq);          // Nada kedengaran tapi di luar jangkauan tuts
        void onUnclear();                        // Sinyal kedengaran tapi nadanya tidak jelas
    }

    private static final int SAMPLE_RATE = 44100;
    // 1536 sample @44.1kHz ~= 35ms per baca -- cukup untuk deteksi E2 (~82Hz, perlu
    // minimal ~538 sample per siklus) sambil tetap terasa cepat, tidak selambat 2048 (46ms).
    private static final int BUFFER_SAMPLES = 1536;
    private static final int BASE_MIDI = 40; // E2 -- HARUS sama persis dengan BASE_MIDI di index.html
    private static final int NOTE_COUNT = 44; // HARUS sama persis dengan NOTE_COUNT di index.html
    private static final long COOLDOWN_MS = 180; // dipersingkat dari 260ms biar bisa petik lebih cepat berturut-turut

    private final Context context;
    private final android.os.Handler mainHandler;
    private Listener listener;
    private AudioRecord audioRecord;
    private Thread recordThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NativeMicPitchDetector(Context context) {
        this.context = context;
        this.mainHandler = new android.os.Handler(context.getMainLooper());
    }

    public boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    public synchronized boolean start(Listener listener) {
        if (running.get()) return true;
        if (!hasPermission()) return false;

        this.listener = listener;

        int minBufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBufSize, BUFFER_SAMPLES * 4);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize);
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        running.set(true);
        audioRecord.startRecording();

        recordThread = new Thread(this::recordLoop, "GenjrengMicThread");
        recordThread.setPriority(Thread.MAX_PRIORITY);
        recordThread.start();
        return true;
    }

    public synchronized void stop() {
        running.set(false);
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
        recordThread = null;
    }

    private void recordLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

        final short[] rawBuf = new short[BUFFER_SAMPLES];
        final float[] floatBuf = new float[BUFFER_SAMPLES];

        boolean sampling = false;
        double smoothedRms = 0.001;
        long cooldownUntil = 0;

        while (running.get()) {
            int read = audioRecord.read(rawBuf, 0, BUFFER_SAMPLES);
            if (read <= 0) continue;

            double sumSq = 0;
            for (int i = 0; i < read; i++) {
                float v = rawBuf[i] / 32768f;
                floatBuf[i] = v;
                sumSq += (double) v * v;
            }
            double rms = Math.sqrt(sumSq / read);
            long now = System.currentTimeMillis();

            if (!sampling) {
                if (rms > 0.02 && rms > smoothedRms * 1.6 && now > cooldownUntil) {
                    sampling = true;
                    postOnset();
                }
            } else {
                double freq = autoCorrelate(floatBuf, read, SAMPLE_RATE);
                if (freq > 0) {
                    double midi = 69 + 12 * (Math.log(freq / 440.0) / Math.log(2));
                    int idx = (int) Math.round(midi) - BASE_MIDI;
                    if (idx >= 0 && idx < NOTE_COUNT) {
                        postPitchIndex(idx, freq);
                    } else {
                        postOutOfRange(freq);
                    }
                } else {
                    postUnclear();
                }
                sampling = false;
                cooldownUntil = now + COOLDOWN_MS;
            }

            smoothedRms = smoothedRms * 0.85 + rms * 0.15;
        }
    }

    /** Port dari fungsi autoCorrelate(buf, sampleRate) di index.html. */
    private static double autoCorrelate(float[] buf, int size, int sampleRate) {
        double rms = 0;
        for (int i = 0; i < size; i++) rms += (double) buf[i] * buf[i];
        rms = Math.sqrt(rms / size);
        if (rms < 0.012) return -1;

        int r1 = 0, r2 = size - 1;
        final double thres = 0.2;
        for (int i = 0; i < size / 2; i++) {
            if (Math.abs(buf[i]) < thres) { r1 = i; break; }
        }
        for (int i = 1; i < size / 2; i++) {
            if (Math.abs(buf[size - i]) < thres) { r2 = size - i; break; }
        }
        int n = r2 - r1;
        if (n < 8) return -1;

        float[] trimmed = new float[n];
        System.arraycopy(buf, r1, trimmed, 0, n);

        double[] c = new double[n];
        for (int lag = 0; lag < n; lag++) {
            double sum = 0;
            for (int i = 0; i < n - lag; i++) sum += trimmed[i] * trimmed[i + lag];
            c[lag] = sum;
        }

        int d = 0;
        while (d < n - 1 && c[d] > c[d + 1]) d++;

        double maxVal = -1;
        int maxPos = -1;
        for (int i = d; i < n; i++) {
            if (c[i] > maxVal) { maxVal = c[i]; maxPos = i; }
        }
        if (maxPos <= 0) return -1;

        double t0 = maxPos;
        double x1 = (maxPos - 1 >= 0) ? c[maxPos - 1] : 0;
        double x2 = c[maxPos];
        double x3 = (maxPos + 1 < n) ? c[maxPos + 1] : 0;
        double a = (x1 + x3 - 2 * x2) / 2;
        double b = (x3 - x1) / 2;
        if (a != 0) t0 = t0 - b / (2 * a);
        if (t0 <= 0) return -1;

        return sampleRate / t0;
    }

    private void postOnset() {
        if (listener == null) return;
        mainHandler.post(() -> { if (listener != null) listener.onOnsetDetected(); });
    }
    private void postPitchIndex(int idx, double freq) {
        if (listener == null) return;
        mainHandler.post(() -> { if (listener != null) listener.onPitchIndex(idx, freq); });
    }
    private void postOutOfRange(double freq) {
        if (listener == null) return;
        mainHandler.post(() -> { if (listener != null) listener.onOutOfRange(freq); });
    }
    private void postUnclear() {
        if (listener == null) return;
        mainHandler.post(() -> { if (listener != null) listener.onUnclear(); });
    }
}
