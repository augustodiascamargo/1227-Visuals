package net.m1227.lyrics;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes LRC lyrics.
 * <ul>
 * <li>Line times: [mm:ss], [mm:ss.x], [mm:ss.xx], [mm:ss.xxx] and [mm:ss:xx]; several times on
 * one line ([00:12.00][01:30.00]chorus) repeat the line.</li>
 * <li>Tags: [ar:], [ti:], [al:], [offset:+/-ms] (positive = lyrics earlier), others ignored.</li>
 * <li>Enhanced LRC word times: &lt;mm:ss.xx&gt;word &lt;mm:ss.xx&gt;word.</li>
 * <li>Text: UTF-8 (with or without BOM); files that are not valid UTF-8 are read as
 * Windows-1252, the usual encoding of older Portuguese LRC files.</li>
 * <li>No time tags at all: plain, unsynced lyrics.</li>
 * </ul>
 */
public final class LrcParser {
    private LrcParser() {}

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)\\]\\s*$");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");

    /** Decodes file bytes: UTF-8 if valid (BOM removed), otherwise Windows-1252. */
    public static String decode(byte[] data) {
        int off = data.length >= 3 && (data[0] & 255) == 0xEF && (data[1] & 255) == 0xBB && (data[2] & 255) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, off, data.length - off)).toString();
        } catch (CharacterCodingException e) {
            Charset cp = Charset.isSupported("windows-1252") ? Charset.forName("windows-1252") : StandardCharsets.ISO_8859_1;
            return new String(data, off, data.length - off, cp);
        }
    }

    static long ms(String m, String s, String frac) {
        long t = Long.parseLong(m) * 60000L + Long.parseLong(s) * 1000L;
        if (frac != null && !frac.isEmpty()) {
            // ".5" = 500 ms, ".45" = 450 ms, ".453" = 453 ms
            int v = Integer.parseInt(frac);
            if (frac.length() == 1) v *= 100;
            else if (frac.length() == 2) v *= 10;
            t += v;
        }
        return t;
    }

    public static Lyrics parse(String text) {
        if (text == null) text = "";
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        String ar = "", ti = "", al = "";
        long offset = 0;
        List<Lyrics.Line> timed = new ArrayList<Lyrics.Line>();
        List<Lyrics.Line> plain = new ArrayList<Lyrics.Line>();
        for (String raw : text.split("\\r?\\n|\\r")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                plain.add(new Lyrics.Line(-1, "", null));
                continue;
            }
            Matcher tag = TAG.matcher(line);
            if (tag.matches() && !TIME.matcher(line).lookingAt()) {
                String k = tag.group(1).toLowerCase(Locale.ROOT), v = tag.group(2).trim();
                if (k.equals("ar")) ar = v;
                else if (k.equals("ti")) ti = v;
                else if (k.equals("al")) al = v;
                else if (k.equals("offset")) {
                    try {
                        offset = Long.parseLong(v.replace("+", "").trim());
                    } catch (NumberFormatException ignored) {
                        // bad offset: ignore it
                    }
                }
                continue;
            }
            // leading time tags
            List<Long> times = new ArrayList<Long>();
            Matcher m = TIME.matcher(line);
            int pos = 0;
            while (m.find() && m.start() == pos) {
                times.add(ms(m.group(1), m.group(2), m.group(3)));
                pos = m.end();
            }
            String rest = line.substring(pos);
            if (times.isEmpty()) {
                plain.add(new Lyrics.Line(-1, rest, null));
                continue;
            }
            // Enhanced LRC word times
            List<Lyrics.Word> words = new ArrayList<Lyrics.Word>();
            Matcher w = WORD.matcher(rest);
            StringBuilder clean = new StringBuilder();
            int last = 0;
            long wordStart = -1;
            while (w.find()) {
                String seg = rest.substring(last, w.start());
                if (wordStart >= 0) addWords(words, wordStart, seg);
                clean.append(seg);
                wordStart = ms(w.group(1), w.group(2), w.group(3));
                last = w.end();
            }
            String tail = rest.substring(last);
            if (wordStart >= 0) addWords(words, wordStart, tail);
            clean.append(tail);
            String txt = clean.toString().replaceAll("\\s+", " ").trim();
            for (Long t : times) {
                List<Lyrics.Word> ws = new ArrayList<Lyrics.Word>();
                long shift = t - (words.isEmpty() ? t : words.get(0).start);
                // repeated lines keep their word rhythm relative to their own start
                for (Lyrics.Word x : words) ws.add(new Lyrics.Word(x.start + (times.size() > 1 ? shift : 0) - offset, x.text));
                timed.add(new Lyrics.Line(t - offset, txt, ws));
            }
        }
        Lyrics out;
        if (!timed.isEmpty()) {
            Collections.sort(timed, (a, b) -> Long.compare(a.start, b.start));
            for (Lyrics.Line l : timed) {
                if (l.start < 0) l.start = 0;
            }
            out = new Lyrics(timed, true);
        } else {
            // trim blank lines at both ends of plain text
            while (!plain.isEmpty() && plain.get(0).blank()) plain.remove(0);
            while (!plain.isEmpty() && plain.get(plain.size() - 1).blank()) plain.remove(plain.size() - 1);
            out = new Lyrics(plain, false);
        }
        out.artist = ar;
        out.title = ti;
        out.album = al;
        out.fileOffset = offset;
        return out;
    }

    /** One timed segment may hold several words ("<00:01.00>hello world"): they share the time. */
    private static void addWords(List<Lyrics.Word> out, long start, String seg) {
        for (String p : seg.trim().split("\\s+")) {
            if (!p.isEmpty()) out.add(new Lyrics.Word(start, p));
        }
    }

    /** Plain lyrics (no times) from text, e.g. LRCLIB's plainLyrics. */
    public static Lyrics plain(String text) {
        Lyrics l = parse(text == null ? "" : text.replaceAll("\\[\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?\\]", ""));
        return l.synced ? new Lyrics(l.lines, false) : l;
    }

    public static String stamp(long ms) {
        if (ms < 0) ms = 0;
        long m = ms / 60000L, s = (ms / 1000L) % 60L, cs = (ms % 1000L) / 10L;
        return String.format(Locale.ROOT, "[%02d:%02d.%02d]", m, s, cs);
    }

    /** Writes standard LRC (line times only; word times are written as Enhanced LRC). */
    public static String write(Lyrics l) {
        StringBuilder b = new StringBuilder();
        if (!l.title.isEmpty()) b.append("[ti:").append(l.title).append("]\n");
        if (!l.artist.isEmpty()) b.append("[ar:").append(l.artist).append("]\n");
        if (!l.album.isEmpty()) b.append("[al:").append(l.album).append("]\n");
        b.append("[by:1227 lyrics editor]\n");
        for (Lyrics.Line line : l.lines) {
            if (l.synced && line.start >= 0) b.append(stamp(line.start));
            if (!line.words.isEmpty() && l.synced) {
                for (int i = 0; i < line.words.size(); i++) {
                    Lyrics.Word w = line.words.get(i);
                    if (i > 0) b.append(' ');
                    b.append(stamp(w.start).replace('[', '<').replace(']', '>')).append(w.text);
                }
            } else {
                b.append(line.text == null ? "" : line.text);
            }
            b.append('\n');
        }
        return b.toString();
    }
}
