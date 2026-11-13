package net.m1227.music;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import net.m1227.lyrics.LyricsMatch;
import net.m1227.module.MusicModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * One answer to "what is playing?" for the lyrics, the Now Playing widget and the visualizer.
 * In order:
 * <ol>
 * <li>the 1227 music player (exact position, from the sound card);</li>
 * <li>the Windows media controls through {@link MediaBridge} (Spotify, YouTube in a browser...),
 * with a position when the player publishes a timeline;</li>
 * <li>the window title of Spotify / a YouTube tab (title only: no position, so no sync).</li>
 * </ol>
 * Updated on the game thread every tick; window titles are read on a background thread.
 */
public final class NowPlaying {
    private NowPlaying() {}

    public enum Source { NONE, LOCAL, MEDIA, WINDOW }

    public static final class Info {
        public Source source = Source.NONE;
        /** "1227", "Spotify", "Chrome", "YouTube"... */
        public String app = "";
        public String title = "", artist = "", album = "";
        /** Seconds, 0 = unknown. */
        public float duration;
        public boolean playing;
        /** True when {@link #position()} follows the real playback (local player, or a published timeline). */
        public boolean timeline;
        /** Local file (for .lrc next to it), or null. */
        public File file;
        private MediaBridge.Info media;

        /** Identity of the song for caches ("artist|title|duration"). */
        public String key() {
            return LyricsMatch.clean(artist) + "|" + LyricsMatch.clean(title) + "|" + Math.round(duration);
        }

        /** Seconds into the song now, or -1 when unknown. */
        public float position() {
            if (source == Source.LOCAL) {
                MusicModule m = MusicModule.INSTANCE;
                return m == null ? -1f : m.player.position();
            }
            if (source == Source.MEDIA && media != null && timeline) return (float) (media.positionMs() / 1000.0);
            return -1f;
        }
    }

    private static Info info = new Info();
    private static boolean bridgeWanted;
    private static int coverSeqLoaded = -1;
    private static ResourceLocation mediaCover;
    private static DynamicTexture mediaTex;

    public static Info get() {
        return info;
    }

    /** Ask for the Windows media bridge on the next ticks (lyrics / widget / visualizer). */
    public static void wantBridge() {
        bridgeWanted = true;
    }

    /** Called every client tick (game thread). */
    public static void tick() {
        MediaBridge.tick();
        MusicModule m = MusicModule.INSTANCE;
        Info n = new Info();
        Track t = m == null ? null : m.player.current();
        if (t != null) {
            n.source = Source.LOCAL;
            n.app = "1227";
            n.title = t.title;
            n.artist = "Unknown artist".equals(t.artist) ? "" : t.artist;
            n.album = t.album;
            n.duration = t.seconds > 0 ? t.seconds : m.player.length();
            n.playing = !m.player.paused();
            n.timeline = true;
            n.file = t.file;
        } else {
            MediaBridge.Info b = null;
            if (bridgeWanted && m != null && m.windowsMedia.on()) b = MediaBridge.current();
            if (b != null && !b.title.isEmpty() && !"Closed".equals(b.state) && !"Stopped".equals(b.state)) {
                n.source = Source.MEDIA;
                n.app = b.appName();
                boolean browser = !n.app.equals("Spotify") && !n.app.equals("Media Player");
                if (browser && (b.artist.isEmpty() || b.title.contains(" - "))) {
                    String[] at = LyricsMatch.splitArtistTitle(b.title, b.artist);
                    n.artist = at[0];
                    n.title = at[1];
                } else {
                    n.title = b.title;
                    n.artist = b.artist.replaceAll("(?i)vevo$", "").replaceAll("(?i)\\s-\\stopic$", "").trim();
                }
                n.album = b.album;
                n.duration = b.timeline() ? (float) ((b.endMs - b.startMs) / 1000.0) : 0f;
                n.playing = b.playing();
                n.timeline = b.timeline();
                n.media = b;
                loadMediaCover(b.coverSeq);
            } else if (m != null) {
                String[] ext = m.external();
                if (ext != null) {
                    n.source = Source.WINDOW;
                    n.app = ext[0];
                    String[] at = LyricsMatch.splitArtistTitle(ext[1], "");
                    n.artist = at[0];
                    n.title = at[1];
                    n.playing = true;
                    n.timeline = false;
                }
            }
        }
        info = n;
        bridgeWanted = false;
    }

    /** Cover of the Windows media session (re-read when the bridge saves a new one), or null. */
    public static ResourceLocation mediaCover() {
        return info.source == Source.MEDIA ? mediaCover : null;
    }

    private static void loadMediaCover(final int seq) {
        if (seq == coverSeqLoaded || seq <= 0) return;
        coverSeqLoaded = seq;
        final File f = MediaBridge.coverFile();
        Thread th = new Thread(() -> {
            BufferedImage img = null;
            try {
                if (f.isFile()) img = ImageIO.read(f);
            } catch (Exception ignored) {
                // not an image the JDK can read
            }
            final BufferedImage fi = img == null ? null : MusicModule.square(img, 128);
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (mediaCover != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(mediaCover);
                mediaCover = null;
                mediaTex = null;
                if (fi != null) {
                    mediaTex = new DynamicTexture(fi);
                    mediaCover = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("m1227_media_cover", mediaTex);
                }
            });
        }, "1227-media-cover");
        th.setDaemon(true);
        th.start();
    }
}
