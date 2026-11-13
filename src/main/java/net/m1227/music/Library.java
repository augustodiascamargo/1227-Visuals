package net.m1227.music;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

/** Local music: every .ogg and .wav under config/1227-music (sub-folders = albums). */
public final class Library {
    private Library() {}

    public static File folder() {
        return new File(net.m1227.core.ConfigManager.dir(), "1227-music");
    }

    public static List<Track> scan() {
        List<Track> out = new ArrayList<Track>();
        File root = folder();
        if (!root.isDirectory()) root.mkdirs();
        walk(root, out, 0);
        Collections.sort(out, (a, b) -> (a.artist + a.title).compareToIgnoreCase(b.artist + b.title));
        return out;
    }

    private static void walk(File dir, List<Track> out, int depth) {
        File[] fs = dir.listFiles();
        if (fs == null || depth > 4) return;
        File cover = null;
        for (File f : fs) {
            String n = f.getName().toLowerCase();
            if (n.equals("cover.png") || n.equals("cover.jpg") || n.equals("folder.jpg") || n.equals("folder.png")) cover = f;
        }
        for (File f : fs) {
            if (f.isDirectory()) {
                walk(f, out, depth + 1);
                continue;
            }
            String n = f.getName().toLowerCase();
            if (n.endsWith(".ogg") || n.endsWith(".wav")) {
                Track t = new Track(f, cover);
                t.seconds = duration(f);
                out.add(t);
                if (out.size() >= 5000) return;
            }
        }
    }

    /** Length without decoding: WAV from its header, Ogg Vorbis from the last page's granule position. */
    public static float duration(File f) {
        try {
            String n = f.getName().toLowerCase();
            if (n.endsWith(".wav")) {
                AudioFileFormat ff = AudioSystem.getAudioFileFormat(f);
                return ff.getFrameLength() / ff.getFormat().getFrameRate();
            }
            try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
                // sample rate: identification header in the first page ("\1vorbis" + version(4) + channels(1) + rate(4))
                byte[] head = new byte[256];
                r.readFully(head, 0, (int) Math.min(256, r.length()));
                int idx = indexOf(head, new byte[]{1, 'v', 'o', 'r', 'b', 'i', 's'}, 0);
                if (idx < 0) return 0f;
                int rate = (head[idx + 12] & 255) | (head[idx + 13] & 255) << 8 | (head[idx + 14] & 255) << 16 | (head[idx + 15] & 255) << 24;
                long len = r.length();
                int tail = (int) Math.min(len, 65536);
                byte[] end = new byte[tail];
                r.seek(len - tail);
                r.readFully(end);
                int last = -1, from = 0, at;
                while ((at = indexOf(end, new byte[]{'O', 'g', 'g', 'S'}, from)) >= 0) {
                    last = at;
                    from = at + 4;
                }
                if (last < 0 || last + 14 > end.length || rate <= 0) return 0f;
                long granule = 0;
                for (int i = 7; i >= 0; i--) granule = (granule << 8) | (end[last + 6 + i] & 255);
                return granule / (float) rate;
            }
        } catch (Exception e) {
            return 0f;
        }
    }

    static int indexOf(byte[] a, byte[] p, int from) {
        outer:
        for (int i = from; i <= a.length - p.length; i++) {
            for (int j = 0; j < p.length; j++) {
                if (a[i + j] != p[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
