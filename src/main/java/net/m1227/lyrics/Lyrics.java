package net.m1227.lyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lyrics of one song: lines with their start time (ms) and, when the source has them, the start
 * time of every word (Enhanced LRC). Unsynced lyrics have every start at -1 and {@link #synced}
 * false: they are shown as plain text, never animated as if they were in time.
 */
public final class Lyrics {
    public static final class Word {
        public final long start;
        public final String text;

        public Word(long start, String text) {
            this.start = start;
            this.text = text;
        }
    }

    public static final class Line {
        /** Start in ms (already corrected by the file's [offset]), or -1 when unsynced. */
        public long start;
        public String text;
        /** Per-word times from Enhanced LRC; empty when the source only has line times. */
        public final List<Word> words;

        public Line(long start, String text, List<Word> words) {
            this.start = start;
            this.text = text;
            this.words = words == null ? Collections.<Word>emptyList() : words;
        }

        public boolean blank() {
            return text == null || text.trim().isEmpty();
        }
    }

    public final List<Line> lines;
    public final boolean synced;
    public String title = "", artist = "", album = "";
    /** Where the text came from ("file name.lrc", "LRCLIB #1234"...), shown to the user. */
    public String source = "";
    /** True when the source says the song is instrumental (no lyrics on purpose). */
    public boolean instrumental;
    /** The [offset:] tag of the file, already applied to the times (kept for saving). */
    public long fileOffset;

    public Lyrics(List<Line> lines, boolean synced) {
        this.lines = lines == null ? new ArrayList<Line>() : lines;
        this.synced = synced;
    }

    public boolean empty() {
        for (Line l : lines) {
            if (!l.blank()) return false;
        }
        return true;
    }

    /**
     * Index of the line playing at time ms: the last line whose start is not after ms, or -1
     * before the first line (and always -1 for unsynced lyrics). Binary search: lines are sorted.
     */
    public int indexAt(long ms) {
        if (!synced || lines.isEmpty() || ms < lines.get(0).start) return -1;
        int lo = 0, hi = lines.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lines.get(mid).start <= ms) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    /** End of line i: the next line's start, or start + 6 s for the last one. */
    public long endOf(int i) {
        if (i < 0 || i >= lines.size()) return 0L;
        if (i + 1 < lines.size()) return lines.get(i + 1).start;
        return lines.get(i).start + 6000L;
    }

    /** Next line after i that has text, or -1. */
    public int nextText(int i) {
        for (int k = i + 1; k < lines.size(); k++) {
            if (!lines.get(k).blank()) return k;
        }
        return -1;
    }

    /**
     * Word start times for line i: the real ones from Enhanced LRC, or an estimate that spreads
     * the words over the line by their length (marked as estimated by the caller).
     */
    public long[] wordTimes(int i, String[] words) {
        Line l = lines.get(i);
        long[] t = new long[words.length];
        if (l.words.size() == words.length) {
            for (int k = 0; k < words.length; k++) t[k] = l.words.get(k).start;
            return t;
        }
        long start = l.start, end = endOf(i);
        int total = 0;
        for (String w : words) total += w.length() + 1;
        // sing the line in at most ~70% of the gap, at no less than ~11 characters per second
        long span = Math.min((long) ((end - start) * 0.85), Math.max(600L, total * 90L));
        int acc = 0;
        for (int k = 0; k < words.length; k++) {
            t[k] = start + span * acc / Math.max(1, total);
            acc += words[k].length() + 1;
        }
        return t;
    }
}
