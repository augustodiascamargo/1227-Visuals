package net.m1227.music;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import net.m1227.Mod1227;

/**
 * Optional audio for the visualizer when the music plays outside 1227 (Spotify, YouTube...).
 *
 * True WASAPI loopback needs native code that Java 8 does not have, so this uses what Java
 * Sound can open: a recording device that carries what the computer plays, such as Windows'
 * "Stereo Mix" (Mixagem estéreo) or a virtual cable. Only devices with those names are listed:
 * microphones are never offered. It runs only while you have explicitly allowed it in the
 * visualizer settings and the visualizer is visible; the samples stay in memory for the
 * drawing and are never saved or sent anywhere.
 */
public final class CaptureSource {
    private CaptureSource() {}

    private static final String[] LOOPBACK = {"stereo mix", "mixagem est", "mezcla est", "what u hear", "loopback",
            "wave out", "cable output", "voicemeeter out", "monitor of"};
    private static volatile Thread thread;
    private static volatile boolean stop;
    private static volatile String device = "";
    private static volatile long lastWanted;
    private static volatile String status = "off";

    public static String status() {
        return status;
    }

    public static boolean active() {
        return thread != null && status.equals("capturing");
    }

    /** Names of recording devices that look like "what the computer plays". */
    public static List<String> devices() {
        List<String> out = new ArrayList<String>();
        try {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                String n = mi.getName().toLowerCase(Locale.ROOT);
                boolean loop = false;
                for (String k : LOOPBACK) loop |= n.contains(k);
                if (!loop) continue;
                Mixer mx = AudioSystem.getMixer(mi);
                if (mx.isLineSupported(new DataLine.Info(TargetDataLine.class, format())) && !out.contains(mi.getName())) {
                    out.add(mi.getName());
                }
            }
        } catch (Throwable t) {
            // no sound system
        }
        return out;
    }

    private static AudioFormat format() {
        return new AudioFormat(44100f, 16, 2, true, false);
    }

    /** Keeps capture running while asked (each frame the visualizer is drawn with capture allowed). */
    private static long failedAt;

    public static synchronized void want(String deviceName) {
        lastWanted = System.currentTimeMillis();
        if (thread != null && deviceName.equals(device)) return;
        // after a failure, wait before trying the same device again
        if (deviceName.equals(device) && System.currentTimeMillis() - failedAt < 15000L) return;
        stopNow();
        device = deviceName;
        stop = false;
        Thread t = new Thread(() -> run(deviceName), "1227-capture");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    public static synchronized void stopNow() {
        stop = true;
        Thread t = thread;
        thread = null;
        if (t != null) t.interrupt();
        status = "off";
    }

    private static void run(String name) {
        TargetDataLine line = null;
        try {
            Mixer.Info found = null;
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().equals(name)) found = mi;
            }
            if (found == null) {
                status = "device not found";
                failedAt = System.currentTimeMillis();
                return;
            }
            AudioFormat fmt = format();
            line = (TargetDataLine) AudioSystem.getMixer(found).getLine(new DataLine.Info(TargetDataLine.class, fmt));
            line.open(fmt, 4096 * 4);
            line.start();
            PcmTap.CAPTURE.reset(0, fmt.getFrameRate());
            status = "capturing";
            byte[] buf = new byte[4096];
            while (!stop && System.currentTimeMillis() - lastWanted < 3000L) {
                int n = line.read(buf, 0, buf.length);
                if (n > 0) PcmTap.CAPTURE.write16(buf, 0, n, 2, false);
            }
        } catch (Throwable t) {
            status = "could not open: " + t.getMessage();
            failedAt = System.currentTimeMillis();
            Mod1227.LOGGER.warn("1227 visualizer capture failed", t);
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (RuntimeException ignored) {
                    // closing
                }
            }
            synchronized (CaptureSource.class) {
                if (thread == Thread.currentThread()) {
                    thread = null;
                    if (status.equals("capturing")) status = "off";
                }
            }
        }
    }
}
