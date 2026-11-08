package net.m1227.audio;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import net.m1227.Mod1227;

/**
 * Plays hit sounds through Java Sound on its own output line, completely
 * independent of Minecraft's sound system and volume sliders.
 *
 * A single daemon thread mixes up to N voices into small 256-frame blocks
 * (~6 ms), so a hit is audible almost immediately and many fast hits never
 * stack into a loud mess: when the voice limit is reached the oldest voice
 * is dropped. Sounds are 16-bit mono 44.1 kHz; pitch is applied by
 * resampling once and caching the result.
 */
public final class HitSoundEngine implements Runnable {
    public static final HitSoundEngine INSTANCE = new HitSoundEngine();
    public static final float RATE = 44100f;
    private static final int BLOCK = 256;
    private static final int MAXV = 8;

    private final short[][] vData = new short[MAXV][];
    private final int[] vPos = new int[MAXV];
    private final float[] vGain = new float[MAXV];
    private int voices;

    private final Map<String, short[]> base = new HashMap<String, short[]>();
    private final Map<String, short[]> pitched = new HashMap<String, short[]>();
    private Thread thread;
    private volatile boolean broken;

    private HitSoundEngine() {}

    // ------------------------------------------------------------ public

    /** Queues a sound. volume 0..2, pitch 0.5..2. */
    public void play(String sound, float volume, float pitch, int maxVoices) {
        if (broken || volume <= 0f) return;
        short[] data = get(sound, pitch);
        if (data == null || data.length == 0) return;
        ensureThread();
        synchronized (this) {
            int limit = Math.max(1, Math.min(MAXV, maxVoices));
            while (voices >= limit) {
                int oldest = 0;
                for (int i = 1; i < voices; i++) {
                    if (vPos[i] > vPos[oldest]) oldest = i;
                }
                remove(oldest);
            }
            vData[voices] = data;
            vPos[voices] = 0;
            vGain[voices] = volume;
            voices++;
            notifyAll();
        }
    }

    /** True when no audio output could be opened (shown in the menu). */
    public boolean unavailable() {
        return broken;
    }

    /** Clears caches so edited custom files are read again. */
    public synchronized void reload() {
        base.clear();
        pitched.clear();
    }

    // ------------------------------------------------------------ caches

    private synchronized short[] get(String sound, float pitch) {
        float p = Math.round(Math.max(0.5f, Math.min(2f, pitch)) * 50f) / 50f;
        String key = sound + "|" + p;
        short[] out = pitched.get(key);
        if (out != null) return out;
        short[] src = base.get(sound);
        if (src == null) {
            src = load(sound);
            if (src == null) src = new short[0];
            base.put(sound, src);
        }
        out = p == 1f ? src : resample(src, p);
        if (pitched.size() > 48) pitched.clear();
        pitched.put(key, out);
        return out;
    }

    private static short[] load(String sound) {
        if (sound.startsWith(SoundBank.FILE_PREFIX)) {
            File f = new File(SoundBank.folder(), sound.substring(SoundBank.FILE_PREFIX.length()));
            return readAudio(f);
        }
        return SoundBank.synth(sound);
    }

    private static short[] resample(short[] src, float pitch) {
        int n = (int) (src.length / pitch);
        short[] out = new short[Math.max(0, n)];
        for (int i = 0; i < n; i++) {
            float pos = i * pitch;
            int a = Math.min(src.length - 1, (int) pos);
            float f = pos - a;
            int b = Math.min(src.length - 1, a + 1);
            out[i] = (short) (src[a] + (src[b] - src[a]) * f);
        }
        return out;
    }

    /** Reads a .wav/.aiff/.au into 16-bit mono 44.1 kHz (max 3 s). Returns null on failure. */
    static short[] readAudio(File f) {
        AudioInputStream in = null;
        try {
            in = AudioSystem.getAudioInputStream(f);
            AudioFormat src = in.getFormat();
            int ch = Math.max(1, src.getChannels());
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    ch, ch * 2, src.getSampleRate(), false);
            AudioInputStream conv = AudioSystem.getAudioInputStream(pcm, in);
            int maxFrames = (int) (pcm.getSampleRate() * 3f);
            byte[] buf = new byte[maxFrames * ch * 2];
            int read = 0, r;
            while (read < buf.length && (r = conv.read(buf, read, buf.length - read)) > 0) read += r;
            conv.close();
            int frames = read / (ch * 2);
            short[] mono = new short[frames];
            for (int i = 0; i < frames; i++) {
                int sum = 0;
                for (int c = 0; c < ch; c++) {
                    int o = (i * ch + c) * 2;
                    sum += (short) ((buf[o] & 0xFF) | (buf[o + 1] << 8));
                }
                mono[i] = (short) (sum / ch);
            }
            float ratio = pcm.getSampleRate() / RATE;
            return Math.abs(ratio - 1f) < 0.001f ? mono : resample(mono, ratio);
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not read hit sound " + f.getName() + ": " + ex);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
        }
    }

    // ------------------------------------------------------------- mixer

    private void remove(int i) {
        int l = voices - 1;
        vData[i] = vData[l];
        vPos[i] = vPos[l];
        vGain[i] = vGain[l];
        vData[l] = null;
        voices--;
    }

    private synchronized void ensureThread() {
        if (thread != null) return;
        thread = new Thread(this, "Mod1227-HitSounds");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        SourceDataLine line;
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, BLOCK * 2 * 8);
            line.start();
        } catch (Exception ex) {
            broken = true;
            Mod1227.LOGGER.warn("Hit sounds disabled, no audio output available: " + ex);
            return;
        }
        int[] mix = new int[BLOCK];
        byte[] out = new byte[BLOCK * 2];
        while (true) {
            synchronized (this) {
                while (voices == 0) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        line.close();
                        return;
                    }
                }
                Arrays.fill(mix, 0);
                for (int v = 0; v < voices; ) {
                    short[] d = vData[v];
                    int pos = vPos[v];
                    float g = vGain[v];
                    int n = Math.min(BLOCK, d.length - pos);
                    for (int i = 0; i < n; i++) mix[i] += (int) (d[pos + i] * g);
                    vPos[v] = pos + n;
                    if (vPos[v] >= d.length) remove(v);
                    else v++;
                }
            }
            for (int i = 0; i < BLOCK; i++) {
                int s = mix[i];
                if (s > 32767) s = 32767;
                else if (s < -32768) s = -32768;
                out[i * 2] = (byte) s;
                out[i * 2 + 1] = (byte) (s >> 8);
            }
            line.write(out, 0, out.length);
        }
    }
}
