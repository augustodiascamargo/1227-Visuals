package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.m1227.lyrics.LrcLib;
import net.m1227.lyrics.LrcParser;
import net.m1227.lyrics.Lyrics;
import net.m1227.lyrics.LyricsMatch;
import net.m1227.music.Fft;
import net.m1227.music.PcmTap;
import org.junit.Test;

/** Floating Lyrics without a game: LRC parsing, timing, matching, LRCLIB records, FFT, PCM ring. */
public class LyricsTest {
    @Test
    public void basicLrc() {
        Lyrics l = LrcParser.parse("[ti:Song]\n[ar:Artist]\n[al:Album]\n[00:12.40]first line\n[00:15.80]second line\n[01:02]third");
        assertTrue(l.synced);
        assertEquals("Song", l.title);
        assertEquals("Artist", l.artist);
        assertEquals("Album", l.album);
        assertEquals(3, l.lines.size());
        assertEquals(12400, l.lines.get(0).start);
        assertEquals(15800, l.lines.get(1).start);
        assertEquals(62000, l.lines.get(2).start);
        assertEquals("second line", l.lines.get(1).text);
    }

    @Test
    public void fractionsAndSeparators() {
        Lyrics l = LrcParser.parse("[00:01.5]a\n[00:02.45]b\n[00:03.123]c\n[00:04:20]d");
        assertEquals(1500, l.lines.get(0).start);
        assertEquals(2450, l.lines.get(1).start);
        assertEquals(3123, l.lines.get(2).start);
        assertEquals(4200, l.lines.get(3).start);
    }

    @Test
    public void offsetAndRepeatedTimes() {
        // positive offset = lyrics appear earlier
        Lyrics l = LrcParser.parse("[offset:+200]\n[00:10.00][00:30.00]chorus\n[00:20.00]verse");
        assertEquals(3, l.lines.size());
        assertEquals(9800, l.lines.get(0).start);
        assertEquals("verse", l.lines.get(1).text);
        assertEquals(29800, l.lines.get(2).start);
        assertEquals("chorus", l.lines.get(2).text);
        Lyrics neg = LrcParser.parse("[offset:-500]\n[00:01.00]x");
        assertEquals(1500, neg.lines.get(0).start);
    }

    @Test
    public void enhancedWords() {
        Lyrics l = LrcParser.parse("[00:05.00]<00:05.00>Olá <00:05.50>mundo <00:06.20>lindo");
        assertEquals("Olá mundo lindo", l.lines.get(0).text);
        assertEquals(3, l.lines.get(0).words.size());
        assertEquals(5500, l.lines.get(0).words.get(1).start);
        long[] t = l.wordTimes(0, l.lines.get(0).text.split(" "));
        assertEquals(6200, t[2]);
    }

    @Test
    public void accentsUtf8AndCp1252() {
        byte[] utf8 = "[00:01.00]Coração, ação e emoção".getBytes(StandardCharsets.UTF_8);
        assertEquals("Coração, ação e emoção", LrcParser.parse(LrcParser.decode(utf8)).lines.get(0).text);
        byte[] bom = new byte[utf8.length + 3];
        bom[0] = (byte) 0xEF;
        bom[1] = (byte) 0xBB;
        bom[2] = (byte) 0xBF;
        System.arraycopy(utf8, 0, bom, 3, utf8.length);
        assertEquals(1000, LrcParser.parse(LrcParser.decode(bom)).lines.get(0).start);
        byte[] cp = "[00:01.00]Coração".getBytes(java.nio.charset.Charset.forName("ISO-8859-1"));
        assertEquals("Coração", LrcParser.parse(LrcParser.decode(cp)).lines.get(0).text);
    }

    @Test
    public void plainLyricsAreNotSynced() {
        Lyrics l = LrcParser.parse("line one\nline two\n\nline three\n");
        assertFalse(l.synced);
        assertEquals(4, l.lines.size());
        assertEquals(-1, l.indexAt(5000));
        Lyrics p = LrcParser.plain("[00:01.00]a\n[00:02.00]b");
        assertFalse(p.synced);
        assertEquals("a", p.lines.get(0).text);
    }

    @Test
    public void indexAtFollowsTheSong() {
        Lyrics l = LrcParser.parse("[00:08.00]one\n[00:12.00]two\n[00:16.00]\n[00:20.00]three");
        assertEquals(-1, l.indexAt(0));
        assertEquals(-1, l.indexAt(7999));
        assertEquals(0, l.indexAt(8000));
        assertEquals(1, l.indexAt(15999));
        assertEquals(2, l.indexAt(16000));
        assertTrue(l.lines.get(2).blank());
        assertEquals(3, l.nextText(2));
        assertEquals(3, l.indexAt(999999));
        // seeking backwards and forwards just gives the line for that time
        assertEquals(0, l.indexAt(9000));
        assertEquals(12000, l.endOf(0));
    }

    @Test
    public void estimatedWordTimesStayInsideTheLine() {
        Lyrics l = LrcParser.parse("[00:10.00]a quick little line\n[00:14.00]next");
        long[] t = l.wordTimes(0, "a quick little line".split(" "));
        assertEquals(10000, t[0]);
        for (int i = 1; i < t.length; i++) assertTrue(t[i] > t[i - 1] && t[i] < 14000);
    }

    @Test
    public void writeAndReadBack() {
        Lyrics l = LrcParser.parse("[ti:T]\n[ar:A]\n[00:01.20]um\n[00:02.30]dois");
        Lyrics back = LrcParser.parse(LrcParser.write(l));
        assertEquals(2, back.lines.size());
        assertEquals(1200, back.lines.get(0).start);
        assertEquals("dois", back.lines.get(1).text);
        assertEquals("T", back.title);
        assertEquals("[01:05.43]", LrcParser.stamp(65430));
    }

    @Test
    public void cleaning() {
        assertEquals("evidencias", LyricsMatch.clean("Evidências (Ao Vivo)"));
        assertEquals("bohemian rhapsody", LyricsMatch.clean("Bohemian Rhapsody - Remastered 2011"));
        assertEquals("song", LyricsMatch.clean("Song (Official Music Video)"));
        assertEquals("song", LyricsMatch.clean("Song feat. Someone"));
    }

    @Test
    public void matching() {
        double right = LyricsMatch.score("Bohemian Rhapsody - Remastered 2011", "Queen", "A Night at the Opera", 355,
                "Bohemian Rhapsody", "Queen", "A Night At The Opera", 354);
        assertTrue(right >= LyricsMatch.ACCEPT);
        // same title, different artist: not accepted
        double otherArtist = LyricsMatch.score("Halo", "Beyoncé", "", 261, "Halo", "Depeche Mode", "", 270);
        assertTrue(otherArtist < LyricsMatch.ACCEPT);
        // similar titles from the same artist, different songs
        double similar = LyricsMatch.score("Love Story", "Taylor Swift", "", 235, "Love Story (Taylor's Version)", "Taylor Swift", "", 235);
        assertTrue(similar >= LyricsMatch.ACCEPT); // same song, re-recorded: acceptable
        double different = LyricsMatch.score("Love Song", "Taylor Swift", "", 200, "Love Story", "Taylor Swift", "", 235);
        assertTrue(different < LyricsMatch.ACCEPT);
        // very different duration = different recording
        assertEquals(0.0, LyricsMatch.score("Song", "A", "", 100, "Song", "A", "", 200), 0.0001);
    }

    @Test
    public void splitTitles() {
        String[] a = LyricsMatch.splitArtistTitle("Imagine Dragons - Believer (Official Video)", "ImagineDragonsVEVO");
        assertEquals("Imagine Dragons", a[0]);
        assertEquals("Believer (Official Video)", a[1]);
        String[] b = LyricsMatch.splitArtistTitle("Believer", "ImagineDragonsVEVO");
        assertEquals("ImagineDragons", b[0]);
        String[] c = LyricsMatch.splitArtistTitle("Song", "Artist - Topic");
        assertEquals("Artist", c[0]);
    }

    @Test
    public void lrclibRecordsAndBestPick() throws Exception {
        java.lang.reflect.Method rec = LrcLib.class.getDeclaredMethod("record", com.google.gson.JsonObject.class);
        rec.setAccessible(true);
        LrcLib.Record good = (LrcLib.Record) rec.invoke(null, new JsonParser().parse(
                "{\"id\":7,\"trackName\":\"Believer\",\"artistName\":\"Imagine Dragons\",\"albumName\":\"Evolve\",\"duration\":204.0,"
                        + "\"instrumental\":false,\"plainLyrics\":\"x\",\"syncedLyrics\":\"[00:01.00]x\"}").getAsJsonObject());
        LrcLib.Record wrong = (LrcLib.Record) rec.invoke(null, new JsonParser().parse(
                "{\"id\":8,\"trackName\":\"Believer\",\"artistName\":\"Someone Else\",\"duration\":180,\"plainLyrics\":\"y\"}").getAsJsonObject());
        LrcLib.Record nulls = (LrcLib.Record) rec.invoke(null, new JsonParser().parse(
                "{\"id\":9,\"trackName\":\"Believer\",\"artistName\":\"Imagine Dragons\",\"plainLyrics\":null,\"syncedLyrics\":null}").getAsJsonObject());
        assertTrue(good.hasSynced());
        assertFalse(nulls.hasAny());
        List<LrcLib.Record> list = new ArrayList<LrcLib.Record>();
        list.add(wrong);
        list.add(nulls);
        list.add(good);
        assertEquals(7, LrcLib.best(list, "Believer", "Imagine Dragons", "Evolve", 204).id);
        List<LrcLib.Record> onlyWrong = new ArrayList<LrcLib.Record>();
        onlyWrong.add(wrong);
        assertNull(LrcLib.best(onlyWrong, "Believer", "Imagine Dragons", "", 204));
    }

    @Test
    public void fftFindsTheTone() {
        int n = 2048;
        float rate = 44100f, freq = 1000f;
        float[] s = new float[n];
        for (int i = 0; i < n; i++) s[i] = (float) Math.sin(2 * Math.PI * freq * i / rate);
        float[] mag = new float[n / 2];
        new Fft(n).magnitudes(s, mag);
        int peak = 0;
        for (int i = 1; i < mag.length; i++) if (mag[i] > mag[peak]) peak = i;
        float peakHz = peak * rate / n;
        assertTrue(Math.abs(peakHz - freq) < rate / n * 1.5f);
        assertTrue(mag[peak] > 0.7f && mag[peak] < 1.3f);
        float[] bands = new float[32];
        Fft.bands(mag, rate, 32, 35f, 16000f, bands);
        int best = 0;
        for (int i = 1; i < 32; i++) if (bands[i] > bands[best]) best = i;
        double f0 = 35 * Math.pow(16000 / 35.0, best / 32.0), f1 = 35 * Math.pow(16000 / 35.0, (best + 1) / 32.0);
        assertTrue(freq >= f0 * 0.9 && freq <= f1 * 1.1);
    }

    @Test
    public void pcmRingReadsWhatWasWritten() {
        PcmTap t = new PcmTap();
        t.reset(1000, 44100f);
        byte[] b = new byte[8];
        // two stereo frames: (1000, 1000) and (-1000, -1000), little endian
        b[0] = (byte) 0xE8;
        b[1] = 0x03;
        b[2] = (byte) 0xE8;
        b[3] = 0x03;
        b[4] = 0x18;
        b[5] = (byte) 0xFC;
        b[6] = 0x18;
        b[7] = (byte) 0xFC;
        t.write16(b, 0, 8, 2, false);
        assertEquals(1002, t.writeFrame());
        float[] out = new float[2];
        assertTrue(t.read(1002, 2, out));
        assertEquals(1000 / 32768f, out[0], 1e-6);
        assertEquals(-1000 / 32768f, out[1], 1e-6);
        float[] none = new float[4];
        assertFalse(t.read(50, 4, none));
        assertNotNull(none);
    }
}
