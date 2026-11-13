package net.m1227.music;

import java.io.File;

/** One local song. Title/artist come from the file name ("Artist - Title.ogg"), album from its folder. */
public final class Track {
    public final File file;
    public final String title, artist, album;
    /** cover.png / cover.jpg / folder.jpg next to the file, or null. */
    public final File cover;
    /** Length in seconds (0 = unknown yet). */
    public float seconds;

    public Track(File file, File cover) {
        this.file = file;
        this.cover = cover;
        String n = file.getName();
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replace('_', ' ').trim();
        int dash = n.indexOf(" - ");
        if (dash > 0) {
            artist = n.substring(0, dash).trim();
            title = n.substring(dash + 3).trim();
        } else {
            artist = "Unknown artist";
            title = n;
        }
        File parent = file.getParentFile();
        album = parent == null ? "" : parent.getName();
    }

    public boolean matches(String q) {
        if (q == null || q.isEmpty()) return true;
        String l = q.toLowerCase();
        return title.toLowerCase().contains(l) || artist.toLowerCase().contains(l) || album.toLowerCase().contains(l);
    }

    public String time() {
        return fmt(seconds);
    }

    public static String fmt(float s) {
        if (s <= 0f) return "--:--";
        int t = (int) s;
        return (t / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", t % 60);
    }
}
