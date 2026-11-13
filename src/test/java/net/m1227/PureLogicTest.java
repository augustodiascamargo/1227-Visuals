package net.m1227;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.m1227.client.Reflect;
import net.m1227.music.Links;
import net.m1227.music.Track;
import net.m1227.vfx.SkyPreset;
import org.junit.Test;

/** Logic that does not need a running game: links, track names, skies, reflection helper. */
public class PureLogicTest {
    @Test
    public void spotifyLinks() {
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M",
                Links.spotifyUri("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"));
        assertEquals("spotify:album:1ATL5GLyefJaxhQzSPVrLX", Links.spotifyUri("https://open.spotify.com/intl-pt/album/1ATL5GLyefJaxhQzSPVrLX"));
        assertEquals("spotify:track:abc123", Links.spotifyUri("spotify:track:abc123"));
        assertNull(Links.spotifyUri("https://evil.example/open.spotify.com/playlist/x"));
        assertNull(Links.spotifyUri("spotify:track:../../etc"));
    }

    @Test
    public void youtubeLinks() {
        assertNotNull(Links.youtubeUrl("https://www.youtube.com/playlist?list=PL123"));
        assertNotNull(Links.youtubeUrl("https://music.youtube.com/watch?v=abc&list=PL1"));
        assertNotNull(Links.youtubeUrl("https://youtu.be/dQw4w9WgXcQ"));
        assertNull(Links.youtubeUrl("https://www.youtube.com/playlist?list=PL1\"><script>"));
        assertNull(Links.youtubeUrl("javascript:alert(1)"));
    }

    @Test
    public void trackNameParsing() {
        Track t = new Track(new File("music/Album X/Some Artist - Great_Song.ogg"), null);
        assertEquals("Some Artist", t.artist);
        assertEquals("Great Song", t.title);
        assertEquals("Album X", t.album);
        Track u = new Track(new File("loose.wav"), null);
        assertEquals("loose", u.title);
        assertEquals("1:05", Track.fmt(65));
    }

    @Test
    public void every58SkiesHaveUniqueNamesAndACollection() {
        assertEquals(58, SkyPreset.ALL.length);
        Set<String> names = new HashSet<String>(Arrays.asList(SkyPreset.names()));
        assertEquals(SkyPreset.ALL.length, names.size());
        Set<String> cols = new HashSet<String>(Arrays.asList("Night", "Sunset", "Space", "Aurora", "Neon"));
        for (SkyPreset s : SkyPreset.ALL) assertTrue(s.name, cols.contains(SkyPreset.collection(s)));
        assertEquals(SkyPreset.ALL[0], SkyPreset.byName("no such sky"));
    }

    static final class Sample {
        private final String a = "first";
        private final String b = "second";
        private final Integer n = 7;
    }

    @Test
    public void reflectReadsFieldsByTypeAndPosition() {
        Sample s = new Sample();
        assertEquals("first", Reflect.get(s, Sample.class, String.class, 0));
        assertEquals("second", Reflect.get(s, Sample.class, String.class, 1));
        assertEquals(Integer.valueOf(7), Reflect.get(s, Sample.class, Integer.class, 0));
        assertNull(Reflect.get(s, Sample.class, String.class, 2));
    }
}
