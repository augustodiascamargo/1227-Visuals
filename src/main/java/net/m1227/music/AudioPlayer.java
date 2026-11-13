package net.m1227.music;

import java.io.File;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import net.m1227.Mod1227;

/**
 * Plays one local track on its own thread (keeps playing in menus and in game). Ogg Vorbis is
 * decoded with the JOrbis codec Minecraft already ships (paulscode CodecJOrbis); WAV goes
 * through Java Sound. Volume is applied to the samples, so it works on every sound card.
 */
public final class AudioPlayer {
    public interface Listener {
        void finished(Track t);
    }

    private volatile Thread thread;
    private volatile boolean paused, stop;
    private volatile float volume = 0.6f;
    private volatile float position, length;
    private volatile Track current;
    private final Listener listener;

    public AudioPlayer(Listener l) {
        this.listener = l;
    }

    public Track current() {
        return current;
    }

    public boolean playing() {
        return thread != null && !paused;
    }

    public boolean paused() {
        return paused;
    }

    public float position() {
        return position;
    }

    public float length() {
        return length;
    }

    public void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    public void togglePause() {
        if (thread != null) paused = !paused;
    }

    public synchronized void stop() {
        stop = true;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
            try {
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        current = null;
        position = 0f;
    }

    public synchronized void play(final Track t, final float startSec) {
        stop();
        stop = false;
        paused = false;
        current = t;
        position = startSec;
        length = t.seconds;
        Thread th = new Thread(() -> run(t, startSec), "1227-music");
        th.setDaemon(true);
        thread = th;
        th.start();
    }

    /** Pulls decoded PCM in chunks: either from a Java Sound stream or from the Ogg codec. */
    private interface Source {
        AudioFormat format();

        byte[] next() throws Exception;

        void close();
    }

    private static Source open(File f) throws Exception {
        if (f.getName().toLowerCase().endsWith(".wav")) {
            AudioInputStream in = AudioSystem.getAudioInputStream(f);
            AudioFormat src = in.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, src.getChannels(),
                    src.getChannels() * 2, src.getSampleRate(), false);
            final AudioInputStream s = AudioSystem.getAudioInputStream(pcm, in);
            return new Source() {
                final byte[] buf = new byte[16384];

                public AudioFormat format() {
                    return pcm;
                }

                public byte[] next() throws Exception {
                    int n = s.read(buf);
                    if (n <= 0) return null;
                    byte[] out = new byte[n];
                    System.arraycopy(buf, 0, out, 0, n);
                    return out;
                }

                public void close() {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                        // closing
                    }
                }
            };
        }
        final paulscode.sound.codecs.CodecJOrbis codec = new paulscode.sound.codecs.CodecJOrbis();
        if (!codec.initialize(f.toURI().toURL())) throw new Exception("can't decode " + f.getName());
        return new Source() {
            public AudioFormat format() {
                return codec.getAudioFormat();
            }

            public byte[] next() {
                if (codec.endOfStream()) return null;
                paulscode.sound.SoundBuffer b = codec.read();
                return b == null ? null : b.audioData;
            }

            public void close() {
                codec.cleanup();
            }
        };
    }

    private void run(Track t, float startSec) {
        Source src = null;
        SourceDataLine line = null;
        boolean ended = false;
        try {
            src = open(t.file);
            AudioFormat fmt = src.format();
            line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            line.open(fmt, (int) (fmt.getFrameRate() * fmt.getFrameSize() * 0.25f));
            line.start();
            float bytesPerSec = fmt.getFrameRate() * fmt.getFrameSize();
            long skip = (long) (startSec * bytesPerSec);
            skip -= skip % fmt.getFrameSize();
            long played = 0;
            boolean big = fmt.isBigEndian();
            byte[] chunk;
            while (!stop && (chunk = src.next()) != null) {
                int off = 0;
                if (skip > 0) {
                    int s = (int) Math.min(skip, chunk.length);
                    skip -= s;
                    off = s;
                    if (off >= chunk.length) continue;
                }
                applyVolume(chunk, off, chunk.length - off, big);
                while (off < chunk.length && !stop) {
                    while (paused && !stop) {
                        line.stop();
                        Thread.sleep(40);
                        if (!paused) line.start();
                    }
                    int n = Math.min(4096, chunk.length - off);
                    n -= n % fmt.getFrameSize();
                    if (n <= 0) break;
                    line.write(chunk, off, n);
                    off += n;
                    played += n;
                    position = startSec + played / bytesPerSec;
                }
            }
            if (!stop) {
                line.drain();
                ended = true;
            }
        } catch (InterruptedException ie) {
            // stopped
        } catch (Throwable ex) {
            Mod1227.LOGGER.warn("Music: could not play " + t.file.getName(), ex);
            ended = !stop; // skip broken files
        } finally {
            if (line != null) {
                line.stop();
                line.close();
            }
            if (src != null) src.close();
        }
        if (ended && !stop && thread == Thread.currentThread()) {
            thread = null;
            listener.finished(t);
        }
    }

    /** 16-bit PCM gain, with a perceptual (squared) curve. */
    private void applyVolume(byte[] b, int off, int len, boolean big) {
        float g = volume * volume;
        if (g >= 0.999f) return;
        for (int i = off; i + 1 < off + len; i += 2) {
            int s = big ? (b[i] << 8) | (b[i + 1] & 255) : (b[i + 1] << 8) | (b[i] & 255);
            s = (int) (s * g);
            if (big) {
                b[i] = (byte) (s >> 8);
                b[i + 1] = (byte) s;
            } else {
                b[i] = (byte) s;
                b[i + 1] = (byte) (s >> 8);
            }
        }
    }
}
