package net.m1227.music;

/**
 * A copy of the most recent decoded audio (mono, -1..1), kept so the visualizer can look at
 * exactly the samples you are hearing. The player writes every chunk it sends to the sound
 * card, tagged with its absolute frame number; readers ask for the window that ends at the
 * frame the sound card is playing right now. Lock-free: one writer (the music thread), any
 * number of readers (the render thread); a torn read only costs one visualizer frame.
 *
 * Also used by the optional capture source (a recording device such as Stereo Mix), which
 * writes here instead when it is active.
 */
public final class PcmTap {
    public static final PcmTap LOCAL = new PcmTap();
    public static final PcmTap CAPTURE = new PcmTap();

    private static final int SIZE = 1 << 16, MASK = SIZE - 1;
    private final float[] ring = new float[SIZE];
    /** Absolute frame number of the next sample to be written. */
    private volatile long writeFrame, firstFrame;
    private volatile float sampleRate = 44100f;

    public void reset(long frame, float rate) {
        firstFrame = frame;
        writeFrame = frame;
        sampleRate = rate;
    }

    public float sampleRate() {
        return sampleRate;
    }

    public long writeFrame() {
        return writeFrame;
    }

    /**
     * Adds 16-bit PCM (interleaved channels) as mono samples. Bytes from off for len, in the
     * given byte order.
     */
    public void write16(byte[] b, int off, int len, int channels, boolean bigEndian) {
        int frameBytes = 2 * Math.max(1, channels);
        long w = writeFrame;
        for (int i = off; i + frameBytes <= off + len; i += frameBytes) {
            float sum = 0f;
            for (int c = 0; c < channels; c++) {
                int j = i + c * 2;
                int s = bigEndian ? (b[j] << 8) | (b[j + 1] & 255) : (b[j + 1] << 8) | (b[j] & 255);
                sum += s / 32768f;
            }
            ring[(int) (w & MASK)] = sum / channels;
            w++;
        }
        writeFrame = w;
    }

    /**
     * Copies the n samples that end at frame endFrame into out (zeros where the data is not
     * there any more or not yet). Returns false when nothing usable was found.
     */
    public boolean read(long endFrame, int n, float[] out) {
        long w = writeFrame, first = Math.max(firstFrame, w - SIZE);
        long from = endFrame - n;
        boolean any = false;
        for (int i = 0; i < n; i++) {
            long f = from + i;
            if (f < first || f >= w) {
                out[i] = 0f;
            } else {
                out[i] = ring[(int) (f & MASK)];
                any = true;
            }
        }
        return any;
    }
}
