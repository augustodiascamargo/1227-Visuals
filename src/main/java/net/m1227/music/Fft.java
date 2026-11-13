package net.m1227.music;

/**
 * In-place radix-2 FFT and a log-spaced band reducer for the visualizer. Pure Java, allocation
 * free after construction (one instance per size).
 */
public final class Fft {
    public final int n;
    private final float[] re, im, window, cos, sin;
    private final int[] rev;

    public Fft(int n) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("size must be a power of two");
        this.n = n;
        re = new float[n];
        im = new float[n];
        window = new float[n];
        cos = new float[n / 2];
        sin = new float[n / 2];
        rev = new int[n];
        int bits = Integer.numberOfTrailingZeros(n);
        for (int i = 0; i < n; i++) {
            rev[i] = Integer.reverse(i) >>> (32 - bits);
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1))); // Hann
        }
        for (int i = 0; i < n / 2; i++) {
            cos[i] = (float) Math.cos(-2 * Math.PI * i / n);
            sin[i] = (float) Math.sin(-2 * Math.PI * i / n);
        }
    }

    /**
     * Magnitudes of the first n/2 bins of the windowed samples (length n), written to mag
     * (length n/2), normalised so a full-scale sine is about 1.
     */
    public void magnitudes(float[] samples, float[] mag) {
        for (int i = 0; i < n; i++) {
            re[rev[i]] = samples[i] * window[i];
            im[rev[i]] = 0f;
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1, step = n / size;
            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < half; j++) {
                    int k = j * step;
                    float wr = cos[k], wi = sin[k];
                    int a = i + j, b = a + half;
                    float tr = re[b] * wr - im[b] * wi, ti = re[b] * wi + im[b] * wr;
                    re[b] = re[a] - tr;
                    im[b] = im[a] - ti;
                    re[a] += tr;
                    im[a] += ti;
                }
            }
        }
        float norm = 4f / n; // Hann window halves the amplitude
        for (int i = 0; i < n / 2; i++) mag[i] = (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]) * norm;
    }

    /**
     * Groups bins into count bands spaced logarithmically from fLow to fHigh Hz (like the ear),
     * each the peak of its bins converted to a 0..1 level over a ~60 dB range.
     */
    public static void bands(float[] mag, float sampleRate, int count, float fLow, float fHigh, float[] out) {
        int bins = mag.length;
        float binHz = sampleRate / (bins * 2f);
        for (int b = 0; b < count; b++) {
            float f0 = (float) (fLow * Math.pow(fHigh / fLow, b / (double) count));
            float f1 = (float) (fLow * Math.pow(fHigh / fLow, (b + 1) / (double) count));
            int i0 = Math.max(1, (int) (f0 / binHz)), i1 = Math.max(i0 + 1, (int) Math.ceil(f1 / binHz));
            float peak = 0f;
            for (int i = i0; i < Math.min(bins, i1); i++) peak = Math.max(peak, mag[i]);
            float db = (float) (20 * Math.log10(peak + 1e-6));
            out[b] = Math.max(0f, Math.min(1f, (db + 60f) / 60f));
        }
    }
}
