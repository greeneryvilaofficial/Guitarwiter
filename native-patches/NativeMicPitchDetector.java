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
 * index.html, dan HARUS dijaga tetap sinkron dengan versi JS itu supaya
 * jalur native & jalur WebView terasa sama persis buat user:
 *   - autoCorrelate() memakai NSDF (Normalized Square Difference Function,
 *     inti dari McLeod Pitch Method -- algoritma tuner gitar profesional),
 *     yang jauh lebih tahan salah oktaf dan tetap kunci ke nada yang benar
 *     walau senar dipetik agak fals/tajam (melenceng sedikit dari frekuensi
 *     standarnya). "clarity" (0..1, puncak NSDF terpilih) dipakai buat
 *     menolak bacaan yang gak cukup jernih -- biar noise/dengung/senar
 *     teredam gak nyasar jadi huruf.
 *   - Nada baru di-commit (dikirim ke listener) setelah DUA bacaan berturut-turut
 *     sepakat di nada yang sama, bukan cuma satu bacaan. Ini yang bikin hasil
 *     jauh lebih akurat, dan karena satu buffer AudioRecord di sini cuma ~35ms
 *     (lihat BUFFER_SAMPLES), dua bacaan berturut-turut cuma nambah latensi
 *     sekitar itu juga -- tetap kerasa instan seperti tuner gitar.
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
    private static final long COOLDOWN_MS = 220; // HARUS sama persis dengan cooldownUntil di index.html
    private static final double CLARITY_THRESHOLD = 0.82; // HARUS sama persis dengan ambang clarity di index.html
    private static final int MAX_SAMPLE_TRIES = 6; // HARUS sama persis dengan batas percobaan di index.html

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
        int sampleTries = 0;
        // Menyimpan bacaan (freq + clarity) selama satu sesi "sampling", dibersihkan
        // tiap kali onset baru terdeteksi. Sejajar dengan `sampleReadings` di index.html.
        final java.util.List<PitchReading> readings = new java.util.ArrayList<>();

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
                    sampleTries = 0;
                    readings.clear();
                    postOnset();
                    // Sengaja TIDAK menganalisis buffer ini juga -- buffer di titik onset
                    // masih berisi transien awal petikan, bacaan baru dimulai dari buffer
                    // berikutnya (~35ms lagi) biar gelombangnya sudah lebih stabil.
                }
            } else {
                PitchReading r = autoCorrelate(floatBuf, read, SAMPLE_RATE);
                sampleTries++;
                if (r != null) readings.add(r);

                boolean committed = false;
                if (readings.size() >= 2) {
                    PitchReading a = readings.get(readings.size() - 2);
                    PitchReading b = readings.get(readings.size() - 1);
                    // Seperti tuner gitar sungguhan: yang dibandingkan MIDI mentah (cents)
                    // dengan toleransi ~60 cents, BUKAN hasil pembulatan tiap bacaan --
                    // jadi petikan yang agak sharp/flat tetap dianggap nada yang sama.
                    // Baru di akhir hasil rata-ratanya dibulatkan sekali ke nada terdekat.
                    double midiA = freqToMidi(a.freq);
                    double midiB = freqToMidi(b.freq);
                    if (Math.abs(midiA - midiB) <= 0.6) {
                        double avgFreq = (a.freq + b.freq) / 2.0;
                        commit(freqToIdx(avgFreq), avgFreq);
                        committed = true;
                    }
                }

                if (!committed && sampleTries >= MAX_SAMPLE_TRIES) {
                    // Sudah dicoba beberapa kali tapi gak pernah dapat dua bacaan yang sepakat.
                    // Daripada diam saja, pakai bacaan dengan clarity tertinggi kalau ada.
                    if (!readings.isEmpty()) {
                        PitchReading best = readings.get(0);
                        for (PitchReading cand : readings) {
                            if (cand.clarity > best.clarity) best = cand;
                        }
                        commit(freqToIdx(best.freq), best.freq);
                    } else {
                        postUnclear();
                    }
                    committed = true;
                }

                if (committed) {
                    sampling = false;
                    cooldownUntil = now + COOLDOWN_MS;
                }
            }

            smoothedRms = smoothedRms * 0.85 + rms * 0.15;
        }
    }

    private static double freqToMidi(double freq) {
        return 69 + 12 * (Math.log(freq / 440.0) / Math.log(2));
    }

    private static int freqToIdx(double freq) {
        return (int) Math.round(freqToMidi(freq)) - BASE_MIDI;
    }

    private void commit(int idx, double freq) {
        if (idx >= 0 && idx < NOTE_COUNT) {
            postPitchIndex(idx, freq);
        } else {
            postOutOfRange(freq);
        }
    }

    /** Hasil satu bacaan autoCorrelate(): frekuensi + seberapa jernih/periodik sinyalnya. */
    private static final class PitchReading {
        final double freq;
        final double clarity;
        PitchReading(double freq, double clarity) {
            this.freq = freq;
            this.clarity = clarity;
        }
    }

    /**
     * Port dari fungsi autoCorrelate(buf, sampleRate) di index.html.
     *
     * Algoritma: NSDF (Normalized Square Difference Function), inti dari
     * McLeod Pitch Method -- algoritma yang sungguhan dipakai tuner gitar
     * profesional. Dibanding autokorelasi polos (versi lama), NSDF menormalkan
     * energi di tiap lag sehingga puncaknya jauh lebih stabil & tahan salah
     * oktaf, dan tetap kunci ke periode yang benar walau senar dipetik agak
     * fals/tajam (melenceng sedikit dari frekuensi standarnya). "clarity"
     * (0..1, nilai puncak NSDF terpilih) tetap dipakai buat nyaring bacaan
     * yang meragukan -- di bawah CLARITY_THRESHOLD biasanya noise, senar
     * teredam, atau lebih dari satu senar berbunyi bareng.
     */
    private static PitchReading autoCorrelate(float[] buf, int size, int sampleRate) {
        double rms = 0;
        for (int i = 0; i < size; i++) rms += (double) buf[i] * buf[i];
        rms = Math.sqrt(rms / size);
        if (rms < 0.012) return null;

        int r1 = 0, r2 = size - 1;
        final double thres = 0.2;
        for (int i = 0; i < size / 2; i++) {
            if (Math.abs(buf[i]) < thres) { r1 = i; break; }
        }
        for (int i = 1; i < size / 2; i++) {
            if (Math.abs(buf[size - i]) < thres) { r2 = size - i; break; }
        }
        int trimmedLen = r2 - r1;
        if (trimmedLen < 8) return null;

        // Batasi jangkauan frekuensi yang dicari -- sedikit lebih lebar dari
        // nada gitar aslinya (55Hz-1300Hz vs jangkauan tuts ~82Hz-988Hz) supaya
        // senar yang dipetik agak melenceng turun/naik dari standar tetap
        // kedeteksi, sekaligus membatasi biaya komputasi NSDF.
        int minLag = Math.max(2, sampleRate / 1300);
        int maxLagWanted = (int) Math.ceil(sampleRate / 55.0);
        int n = Math.min(trimmedLen, maxLagWanted * 3);
        int maxLag = Math.min(maxLagWanted, n - 1);
        if (n < 8 || maxLag <= minLag + 1) return null;

        float[] trimmed = new float[n];
        System.arraycopy(buf, r1, trimmed, 0, n);

        double[] nsdf = new double[maxLag + 1];
        for (int lag = 0; lag <= maxLag; lag++) {
            double acf = 0, m = 0;
            for (int i = 0; i < n - lag; i++) {
                double a = trimmed[i], b = trimmed[i + lag];
                acf += a * b;
                m += a * a + b * b;
            }
            nsdf[lag] = m > 0 ? (2 * acf / m) : 0;
        }

        // Cari semua puncak lokal di jangkauan lag yang diminati, lalu ambil
        // yang PERTAMA (lag terkecil) yang tingginya minimal 90% dari puncak
        // tertinggi -- trik utama McLeod buat menghindari salah oktaf, karena
        // puncak fundamental biasanya hampir sama tinggi dengan puncak
        // harmoniknya tapi muncul lebih dulu (lag lebih kecil).
        java.util.List<Integer> peaks = new java.util.ArrayList<>();
        for (int lag = minLag + 1; lag < maxLag; lag++) {
            if (nsdf[lag] > nsdf[lag - 1] && nsdf[lag] >= nsdf[lag + 1] && nsdf[lag] > 0) {
                peaks.add(lag);
            }
        }
        if (peaks.isEmpty()) return null;

        double globalMax = -Double.MAX_VALUE;
        for (int p : peaks) if (nsdf[p] > globalMax) globalMax = nsdf[p];
        if (globalMax <= 0) return null;

        final double K = 0.9; // ambang "key maximum"
        int chosen = peaks.get(0);
        for (int p : peaks) {
            if (nsdf[p] >= K * globalMax) { chosen = p; break; }
        }

        double clarity = nsdf[chosen];
        if (clarity < CLARITY_THRESHOLD) return null;

        // Interpolasi parabola di sekitar puncak terpilih buat presisi sub-sample.
        double t0 = chosen;
        double x1 = (chosen - 1 >= 0) ? nsdf[chosen - 1] : 0;
        double x2 = nsdf[chosen];
        double x3 = (chosen + 1 <= maxLag) ? nsdf[chosen + 1] : 0;
        double a = (x1 + x3 - 2 * x2) / 2;
        double b = (x3 - x1) / 2;
        if (a != 0) t0 = t0 - b / (2 * a);
        if (t0 <= 0) return null;

        return new PitchReading(sampleRate / t0, clarity);
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
