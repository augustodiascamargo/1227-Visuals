package net.m1227.client.perf;

import java.util.Arrays;

/**
 * Small statistics helpers shared by the frame, input, network and benchmark monitors. Plain Java
 * (no Minecraft classes), so they are covered by unit tests.
 */
public final class Stats {
    private Stats() {}

    /**
     * Percentile of the values (0..100), nearest-rank on a sorted copy. Returns NaN when empty.
     * Sorting a copy keeps the caller's ring buffer in time order.
     */
    public static double percentile(float[] v, int n, double p, float[] scratch) {
        if (n <= 0) return Double.NaN;
        float[] s = scratch != null && scratch.length >= n ? scratch : new float[n];
        System.arraycopy(v, 0, s, 0, n);
        Arrays.sort(s, 0, n);
        return sortedPercentile(s, n, p);
    }

    /** Nearest-rank percentile of an already sorted array. */
    public static double sortedPercentile(float[] sorted, int n, double p) {
        if (n <= 0) return Double.NaN;
        int k = (int) Math.ceil(p / 100.0 * n) - 1;
        if (k < 0) k = 0;
        if (k >= n) k = n - 1;
        return sorted[k];
    }

    /**
     * "x% low" FPS as benchmark tools report it: the average frame time of the slowest x% of
     * frames, turned into FPS. sorted = frame times in ms, ascending.
     */
    public static double lowFps(float[] sorted, int n, double percent) {
        if (n <= 0) return Double.NaN;
        int k = Math.max(1, (int) Math.floor(n * percent / 100.0));
        double sum = 0;
        for (int i = n - k; i < n; i++) sum += sorted[i];
        double ms = sum / k;
        return ms <= 0 ? Double.NaN : 1000.0 / ms;
    }

    public static double mean(float[] v, int n) {
        if (n <= 0) return Double.NaN;
        double s = 0;
        for (int i = 0; i < n; i++) s += v[i];
        return s / n;
    }

    public static double stddev(float[] v, int n) {
        if (n <= 1) return Double.NaN;
        double m = mean(v, n), s = 0;
        for (int i = 0; i < n; i++) s += (v[i] - m) * (v[i] - m);
        return Math.sqrt(s / (n - 1));
    }

    /**
     * Interarrival jitter as defined in RFC 3550 (section 6.4.1): a running average of the
     * absolute difference between consecutive transit times, with gain 1/16.
     */
    public static final class Jitter {
        private double j;
        private double last = Double.NaN;
        private int samples;

        /** transitMs = arrival time minus send time (any constant offset cancels out). */
        public void add(double transitMs) {
            if (!Double.isNaN(last)) {
                double d = Math.abs(transitMs - last);
                j += (d - j) / 16.0;
                samples++;
            }
            last = transitMs;
        }

        /** NaN until there are at least 4 differences. */
        public double value() {
            return samples < 4 ? Double.NaN : j;
        }

        public int samples() {
            return samples;
        }

        public void reset() {
            j = 0;
            last = Double.NaN;
            samples = 0;
        }
    }

    /**
     * Time-stamped float samples in a fixed ring (no allocation after construction). Times are
     * monotonic nanoseconds (System.nanoTime()).
     */
    public static final class Series {
        public final float[] v;
        public final long[] t;
        private int head, size;

        public Series(int capacity) {
            v = new float[capacity];
            t = new long[capacity];
        }

        public void add(long nanos, float value) {
            v[head] = value;
            t[head] = nanos;
            head = (head + 1) % v.length;
            if (size < v.length) size++;
        }

        public int size() {
            return size;
        }

        public void clear() {
            head = 0;
            size = 0;
        }

        /** i = 0 is the oldest sample kept. */
        public float value(int i) {
            return v[(head - size + i + v.length * 2) % v.length];
        }

        public long time(int i) {
            return t[(head - size + i + v.length * 2) % v.length];
        }

        public float last() {
            return size == 0 ? Float.NaN : value(size - 1);
        }

        /** Copies the values newer than (now - windowNs) into out, returns how many (oldest first). */
        public int window(long now, long windowNs, float[] out) {
            int n = 0;
            for (int i = 0; i < size && n < out.length; i++) {
                if (now - time(i) <= windowNs) out[n++] = value(i);
            }
            return n;
        }
    }
}
