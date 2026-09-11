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
 * Algoritma di bawah ini port dari fungsi yinDetect() + micLoop() di index.html,
 * dan HARUS dijaga tetap sinkron dengan versi JS itu supaya jalur native & jalur
 * WebView terasa sama persis buat user:
 *   - Deteksi pitch pakai algoritma YIN (bukan autokorelasi biasa lagi) --
 *     jauh lebih tahan salah pilih oktaf (mis. F2 kebaca F3) karena guitar
 *     sering punya harmonik ke-2 yang lebih kuat dari nada dasarnya sendiri,
 *     dan autokorelasi polos gampang kejebak kunci ke situ.
 *   - Nada di-commit langsung di bacaan PERTAMA yang meyakinkan (bukan nunggu
 *     beberapa bacaan sepakat dulu) -- YIN cukup andal buat itu, jadi gak
 *     perlu dipetik berkali-kali dulu baru kedeteksi.
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
    // 4096 sample @44.1kHz ~= 93ms per baca. HARUS sama persis dengan analyser.fftSize
    // di index.html. Dulu 1536 (35ms) biar terasa cepat, tapi itu cuma ~2.8 siklus
    // gelombang buat nada rendah kayak E2 (~82Hz) -- ketipisan data bikin YIN (atau
    // autokorelasi apa pun) gampang salah pilih oktaf. 4096 kasih ~7.6 siklus di
    // E2, jauh lebih andal. Latensinya tetap kerasa instan karena YIN langsung
    // commit di bacaan pertama yang yakin (lihat recordLoop()).
    private static final int BUFFER_SAMPLES = 4096;
    private static final int BASE_MIDI = 40; // E2 -- HARUS sama persis dengan BASE_MIDI di index.html
    private static final int NOTE_COUNT = 44; // HARUS sama persis dengan NOTE_COUNT di index.html
    private static final long COOLDOWN_MS = 180; // HARUS sama persis dengan cooldownUntil di index.html
    private static final int MAX_SAMPLE_TRIES = 4; // HARUS sama persis dengan batas percobaan di index.html
    private static final double YIN_THRESHOLD = 0.15; // HARUS sama persis dengan THRESHOLD di index.html

    // Rentang frekuensi yang masuk akal buat dicari (nada gitar yang dipetakan ke
    // tuts + sedikit margin). HARUS sama persis dengan MIN/MAX_VALID_FREQ di index.html.
    private static final double MIN_VALID_FREQ = 440.0 * Math.pow(2, (BASE_MIDI - 3 - 69) / 12.0);
    private static final double MAX_VALID_FREQ = 440.0 * Math.pow(2, (BASE_MIDI + NOTE_COUNT - 1 + 3 - 69) / 12.0);

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
                    postOnset();
                    // Sengaja TIDAK menganalisis buffer ini juga -- buffer di titik onset
                    // masih berisi transien awal petikan, bacaan baru dimulai dari buffer
                    // berikutnya biar gelombangnya sudah lebih stabil.
                }
            } else {
                PitchReading r = yinDetect(floatBuf, read, SAMPLE_RATE);
                sampleTries++;

                if (r != null) {
                    // YIN jauh lebih tahan salah oktaf dibanding autokorelasi biasa, jadi
                    // begitu dapat SATU bacaan yang meyakinkan langsung dikunci -- gak
                    // perlu dipetik berkali-kali dulu baru kedeteksi.
                    commit(freqToIdx(r.freq), r.freq);
                    sampling = false;
                    cooldownUntil = now + COOLDOWN_MS;
                } else if (sampleTries >= MAX_SAMPLE_TRIES) {
                    // Beberapa buffer berturut masih belum dapat bacaan yang jelas
                    // (mis. senar teredam / lebih dari satu senar bunyi bareng) --
                    // baru di sini nyerah.
                    postUnclear();
                    sampling = false;
                    cooldownUntil = now + COOLDOWN_MS;
                }
                // kalau belum yakin & masih ada jatah percobaan, lanjut ke buffer berikutnya
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

    /** Hasil satu bacaan yinDetect(): frekuensi + seberapa yakin/periodik sinyalnya. */
    private static final class PitchReading {
        final double freq;
        final double probability;
        PitchReading(double freq, double probability) {
            this.freq = freq;
            this.probability = probability;
        }
    }

    /**
     * Port dari fungsi yinDetect(buf, sampleRate) di index.html -- algoritma YIN
     * (De Cheveigne & Kawahara, 2002), dipakai juga di tuner-tuner gitar
     * profesional. Bedanya sama autokorelasi biasa: YIN pakai "cumulative mean
     * normalized difference function" yang jauh lebih tahan salah pilih oktaf
     * (mis. F2 kebaca F3 karena harmonik ke-2 gitar sering lebih kuat dari nada
     * dasarnya sendiri -- autokorelasi polos gampang kejebak di situ).
     * Pencarian dibatasi ke rentang frekuensi nada gitar (MIN/MAX_VALID_FREQ)
     * biar lebih cepat DAN lebih tegas (gak pernah mempertimbangkan periode
     * yang jelas di luar jangkauan gitar).
     */
    private static PitchReading yinDetect(float[] buf, int size, int sampleRate) {
        double rms = 0;
        for (int i = 0; i < size; i++) rms += (double) buf[i] * buf[i];
        rms = Math.sqrt(rms / size);
        if (rms < 0.012) return null;

        int minTau = Math.max(2, (int) Math.floor(sampleRate / MAX_VALID_FREQ));
        int maxTau = Math.min(size / 2 - 1, (int) Math.ceil(sampleRate / MIN_VALID_FREQ));
        if (maxTau <= minTau) return null;

        // Langkah 1: fungsi selisih d(tau), cuma dihitung untuk rentang tau yang relevan.
        double[] diff = new double[maxTau + 1];
        for (int tau = minTau; tau <= maxTau; tau++) {
            double sum = 0;
            for (int j = 0; j < size - maxTau; j++) {
                double d = buf[j] - buf[j + tau];
                sum += d * d;
            }
            diff[tau] = sum;
        }

        // Langkah 2: cumulative mean normalized difference function (CMNDF).
        double[] cmnd = new double[maxTau + 1];
        double runningSum = 0;
        cmnd[minTau] = 1;
        for (int tau = minTau + 1; tau <= maxTau; tau++) {
            runningSum += diff[tau];
            cmnd[tau] = diff[tau] * (tau - minTau) / (runningSum != 0 ? runningSum : 1e-9);
        }

        // Langkah 3: cari tau TERKECIL (frekuensi tertinggi valid) yang CMNDF-nya
        // sudah di bawah ambang -- ini yang bikin YIN menghindari salah pilih
        // oktaf ke bawah (keliru mengunci ke 2x periode/setengah frekuensi asli).
        int tauEstimate = -1;
        for (int tau = minTau + 1; tau <= maxTau; tau++) {
            if (cmnd[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++;
                tauEstimate = tau;
                break;
            }
        }
        if (tauEstimate == -1) return null;

        // Langkah 4: interpolasi parabola di sekitar tauEstimate biar presisi.
        int x0 = tauEstimate > minTau ? tauEstimate - 1 : tauEstimate;
        int x2 = tauEstimate < maxTau ? tauEstimate + 1 : tauEstimate;
        double betterTau = tauEstimate;
        if (x0 != tauEstimate && x2 != tauEstimate) {
            double s0 = cmnd[x0], s1 = cmnd[tauEstimate], s2 = cmnd[x2];
            double denom = 2 * (2 * s1 - s2 - s0);
            if (denom != 0) betterTau = tauEstimate + (s2 - s0) / denom;
        }
        if (betterTau <= 0) return null;

        double probability = 1 - cmnd[tauEstimate];
        return new PitchReading(sampleRate / betterTau, probability);
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
