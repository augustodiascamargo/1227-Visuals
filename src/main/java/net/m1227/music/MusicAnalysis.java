package net.m1227.music;

import net.m1227.module.MusicModule;

/**
 * Shared, once-per-frame analysis of the music you are hearing, for the visualizer, the music
 * atmosphere and the lyrics glow: 64 spectrum bands (smoothed), the waveform, overall energy,
 * bass level and beat detection.
 *
 * Sources, in order: the 1227 player (exact samples at the sound card's position), then the
 * optional capture device ({@link CaptureSource}, only when you turned it on), otherwise
 * nothing (levels fall to zero; the visualizer can show its labelled "simulated" mode).
 */
public final class MusicAnalysis {
    public static final MusicAnalysis INSTANCE = new MusicAnalysis();
    public static final int BANDS = 64, N = 2048;

    public enum Source { NONE, LOCAL, CAPTURE }

    private final Fft fft = new Fft(N);
    private final float[] samples = new float[N], mag = new float[N / 2], raw = new float[BANDS];
    public final float[] bands = new float[BANDS];
    /** The last 512 samples, -1..1, for the waveform modes. */
    public final float[] wave = new float[512];
    public float energy, bass, beat;
    private float bassAvg;
    private long lastFrame, lastBeat;
    private Source source = Source.NONE;

    private MusicAnalysis() {}

    public Source source() {
        return source;
    }

    /** Runs the analysis at most once per frame (call from the render thread). */
    public void update() {
        long now = System.nanoTime();
        if (now - lastFrame < 8_000_000L) return;
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        boolean got = false;
        float rate = 44100f;
        MusicModule m = MusicModule.INSTANCE;
        if (m != null && m.player.current() != null && !m.player.paused()) {
            long f = m.player.playedFrame();
            if (f >= 0 && PcmTap.LOCAL.read(f, N, samples)) {
                got = true;
                rate = PcmTap.LOCAL.sampleRate();
                source = Source.LOCAL;
            }
        }
        if (!got && CaptureSource.active()) {
            long f = PcmTap.CAPTURE.writeFrame() - 1024; // what was heard ~23 ms ago
            if (PcmTap.CAPTURE.read(f, N, samples)) {
                got = true;
                rate = PcmTap.CAPTURE.sampleRate();
                source = Source.CAPTURE;
            }
        }
        if (!got) {
            source = Source.NONE;
            for (int i = 0; i < BANDS; i++) bands[i] = Math.max(0f, bands[i] - dt * 2.5f);
            for (int i = 0; i < wave.length; i++) wave[i] *= 0.8f;
            energy = Math.max(0f, energy - dt * 2f);
            bass = Math.max(0f, bass - dt * 2f);
            beat = Math.max(0f, beat - dt * 4f);
            return;
        }
        System.arraycopy(samples, N - wave.length, wave, 0, wave.length);
        fft.magnitudes(samples, mag);
        Fft.bands(mag, rate, BANDS, 35f, 16000f, raw);
        float e = 0f;
        for (int i = 0; i < BANDS; i++) {
            // fast attack, slower release: bars jump with the beat and fall smoothly
            float v = raw[i];
            bands[i] = v > bands[i] ? bands[i] + (v - bands[i]) * Math.min(1f, dt * 30f) : Math.max(v, bands[i] - dt * 1.6f);
            e += bands[i];
        }
        energy = e / BANDS;
        float b = 0f;
        for (int i = 0; i < 8; i++) b += raw[i];
        b /= 8f;
        bass += (b - bass) * Math.min(1f, dt * 20f);
        bassAvg += (bass - bassAvg) * Math.min(1f, dt * 1.5f);
        beat = Math.max(0f, beat - dt * 4f);
        if (bass > bassAvg * 1.18f + 0.04f && bass > 0.35f && now - lastBeat > 250_000_000L) {
            lastBeat = now;
            beat = 1f;
        }
    }

    /** Smoothed value of the band nearest to t (0 = lowest, 1 = highest). */
    public float band(float t) {
        int i = Math.max(0, Math.min(BANDS - 1, Math.round(t * (BANDS - 1))));
        return bands[i];
    }
}
