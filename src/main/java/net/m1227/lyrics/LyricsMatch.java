package net.m1227.lyrics;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a lyrics result is really the song that is playing. Titles and artists are
 * compared after cleaning (accents, case, punctuation, "(Official Video)", "feat. ...",
 * "- Remastered 2011", "VEVO", " - Topic"...), by shared words; the duration must agree when
 * both are known. A wrong song is worse than no lyrics, so the bar is high.
 */
public final class LyricsMatch {
    private LyricsMatch() {}

    /** Minimum score for an automatic pick. */
    public static final double ACCEPT = 0.72;

    public static String clean(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT);
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        // bracketed extras: (official video), [lyrics], (feat. x), (ao vivo)...
        t = t.replaceAll("[(\\[{][^)\\]}]*[)\\]}]", " ");
        t = t.replaceAll("\\b(feat|ft|featuring|part)\\.?\\s.*$", " ");
        t = t.replaceAll("\\s-\\s(remaster(ed)?|live|ao vivo|radio edit|single version|official).*$", " ");
        t = t.replaceAll("\\b(official (music )?video|official audio|lyric video|lyrics?|clipe oficial|video oficial|hd|4k|mv)\\b", " ");
        t = t.replaceAll("vevo$|\\s-\\stopic$", " ");
        t = t.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        return t;
    }

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        for (String w : clean(s).split(" ")) {
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    /** 0..1: how much of the shorter name is contained in the longer one (by words). */
    public static double similarity(String a, String b) {
        Set<String> x = words(a), y = words(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        String ca = clean(a), cb = clean(b);
        if (ca.equals(cb)) return 1;
        int common = 0;
        for (String w : x) {
            if (y.contains(w)) common++;
        }
        double containment = common / (double) Math.min(x.size(), y.size());
        double jaccard = common / (double) (x.size() + y.size() - common);
        return 0.65 * containment + 0.35 * jaccard;
    }

    /**
     * Score of a candidate (0..1). durations in seconds, 0 or less = unknown. More than 8 s apart
     * is a different recording: score 0.
     */
    public static double score(String title, String artist, String album, double duration,
                               String cTitle, String cArtist, String cAlbum, double cDuration) {
        if (duration > 0 && cDuration > 0 && Math.abs(duration - cDuration) > 8) return 0;
        double t = similarity(title, cTitle);
        boolean artistKnown = artist != null && !clean(artist).isEmpty() && !clean(artist).equals("unknown artist");
        double a = artistKnown ? similarity(artist, cArtist) : 0.6;
        // channel names on YouTube are often just the artist inside the title
        if (artistKnown && a < 0.5 && clean(title).contains(clean(cArtist)) && !clean(cArtist).isEmpty()) a = 0.8;
        double al = album == null || clean(album).isEmpty() ? 0.5 : similarity(album, cAlbum);
        double s = 0.6 * t + 0.32 * a + 0.08 * al;
        if (duration > 0 && cDuration > 0) {
            double d = Math.abs(duration - cDuration);
            s += d <= 2 ? 0.05 : -0.04 * (d - 2);
        }
        if (t < 0.5) s = Math.min(s, 0.5);
        if (artistKnown && a < 0.34) s = Math.min(s, 0.55);
        return Math.max(0, Math.min(1, s));
    }

    /**
     * Splits "Artist - Title" (window titles, YouTube video names) into {artist, title}; when
     * there is no separator the artist is the fallback (e.g. a channel name).
     */
    public static String[] splitArtistTitle(String text, String fallbackArtist) {
        if (text == null) return new String[]{fallbackArtist == null ? "" : fallbackArtist, ""};
        String[] seps = {" - ", " – ", " — ", " | "};
        for (String sep : seps) {
            int i = text.indexOf(sep);
            if (i > 0) return new String[]{text.substring(0, i).trim(), text.substring(i + sep.length()).trim()};
        }
        String fa = fallbackArtist == null ? "" : fallbackArtist.replaceAll("(?i)vevo$", "").replaceAll("(?i)\\s-\\stopic$", "").trim();
        return new String[]{fa, text.trim()};
    }
}
