package net.m1227.module;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.music.AudioPlayer;
import net.m1227.music.External;
import net.m1227.music.Library;
import net.m1227.music.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Music player: your own files (Ogg Vorbis / WAV in config/1227-music) played inside the game,
 * plus control of a Spotify or YouTube playlist playing in its own app (see External). The
 * player keeps going in menus and in game; the window opens with a key or from the menu.
 */
public final class MusicModule extends Module {
    public static MusicModule INSTANCE;

    public final Setting.Num volume;
    public final Setting.Bool shuffle;
    public final Setting.Mode repeat;
    public final Setting.Text spotifyLink, youtubeLink;
    /** Read Spotify / YouTube through the Windows media controls (title, artist, timeline). */
    public final Setting.Bool windowsMedia;
    private final Setting.Key openKey, playKey, nextKey, prevKey;

    public final AudioPlayer player = new AudioPlayer(this::finished);
    private List<Track> library = new ArrayList<Track>();
    private final List<Track> queue = new ArrayList<Track>();
    private int queuePos = -1;
    private final List<Track> history = new ArrayList<Track>();
    private final Random rng = new Random();

    public MusicModule() {
        super("Music Player", "Your music files, plus Spotify / YouTube playlist control.", Category.MUSIC, true);
        INSTANCE = this;
        add(new Setting.Action("Open music player", () -> Minecraft.getMinecraft().displayGuiScreen(new net.m1227.ui.MusicScreen())));
        volume = add(new Setting.Num("Volume", 0.6, 0.0, 1.0, 0.01, ""));
        shuffle = add(new Setting.Bool("Shuffle", false));
        repeat = add(new Setting.Mode("Repeat", "All", "Off", "All", "One"));
        spotifyLink = add(new Setting.Text("Spotify playlist link", "", 200, "https://open.spotify.com/playlist/..."));
        youtubeLink = add(new Setting.Text("YouTube playlist link", "", 200, "https://www.youtube.com/playlist?list=..."));
        openKey = add(new Setting.Key("Open player key", org.lwjgl.input.Keyboard.KEY_M));
        playKey = add(new Setting.Key("Play / pause key", 0));
        nextKey = add(new Setting.Key("Next track key", 0));
        prevKey = add(new Setting.Key("Previous track key", 0));
        windowsMedia = add(new Setting.Bool("Read Spotify / YouTube from Windows media controls", true));
        add(new Setting.Action("Open music folder", () -> openFolder(Library.folder())));
        add(new Setting.Action("Rescan music folder", this::rescan));
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    public void openMusicFolder() {
        openFolder(Library.folder());
    }

    private static void openFolder(final File d) {
        d.mkdirs();
        Thread t = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().open(d);
            } catch (Throwable ignored) {
                // no desktop integration
            }
        }, "1227-open-folder");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------- library / queue

    private boolean scanned;

    public List<Track> library() {
        if (!scanned) rescan();
        return library;
    }

    /** Re-reads the folder without a notification (each time the player window opens). */
    public void rescanSilent() {
        scanned = true;
        library = Library.scan();
    }

    public void rescan() {
        rescanSilent();
        Notifier.post(Notifier.Kind.SYSTEM, "Music", library.size() + " song(s) in your music folder", Notifier.GOOD, 1800);
    }

    public List<Track> queue() {
        return queue;
    }

    public List<Track> history() {
        return history;
    }

    /** Plays `list[index]` and makes `list` the queue (shuffled after it if shuffle is on). */
    public void playFrom(List<Track> list, int index) {
        if (list.isEmpty() || index < 0 || index >= list.size()) return;
        queue.clear();
        queue.addAll(list);
        Track first = queue.get(index);
        if (shuffle.on()) {
            queue.remove(index);
            Collections.shuffle(queue, rng);
            queue.add(0, first);
            queuePos = 0;
        } else {
            queuePos = index;
        }
        start(first);
    }

    private void start(Track t) {
        player.setVolume(volume.f());
        player.play(t, 0f);
        history.remove(t);
        history.add(0, t);
        while (history.size() > 50) history.remove(history.size() - 1);
    }

    public void togglePause() {
        if (player.current() != null) player.togglePause();
        else if (queuePos >= 0 && queuePos < queue.size()) start(queue.get(queuePos));
        else if (!library().isEmpty()) playFrom(library, 0);
    }

    public void next() {
        if (queue.isEmpty()) return;
        queuePos = (queuePos + 1) % queue.size();
        start(queue.get(queuePos));
    }

    public void previous() {
        if (queue.isEmpty()) return;
        if (player.position() > 3f) {
            start(queue.get(Math.max(0, queuePos))); // restart the song first, like most players
            return;
        }
        queuePos = (queuePos - 1 + queue.size()) % queue.size();
        start(queue.get(queuePos));
    }

    public void seek(float seconds) {
        Track t = player.current();
        if (t != null) player.play(t, Math.max(0f, seconds));
    }

    private int failStreak;

    /** Called on the player thread when a song ends. */
    private void finished(Track t, boolean failed) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (failed) {
                failStreak++;
                Notifier.post(Notifier.Kind.SYSTEM, "Music", "Could not play " + t.title, Notifier.BAD, 2500);
                // several broken files in a row (or no sound device): stop instead of skipping forever
                if (failStreak >= 3 || queue.size() <= 1) {
                    failStreak = 0;
                    return;
                }
            } else {
                failStreak = 0;
            }
            if (repeat.is("One") && !failed) {
                start(t);
            } else if (queuePos + 1 < queue.size()) {
                queuePos++;
                start(queue.get(queuePos));
            } else if (repeat.is("All") && !queue.isEmpty()) {
                queuePos = 0;
                start(queue.get(0));
            }
        });
    }

    // ------------------------------------------------------------- Spotify / YouTube

    private volatile String[] external;
    private volatile long externalAt;

    private volatile Thread externalPoll;

    /**
     * {source, title} of what Spotify / YouTube is playing (window titles), or null. Read about
     * once a second on a background thread while something keeps asking; never on the render
     * thread.
     */
    public String[] external() {
        externalAt = System.currentTimeMillis();
        if (externalPoll == null && External.available()) {
            Thread t = new Thread(() -> {
                try {
                    while (System.currentTimeMillis() - externalAt < 10000L) {
                        external = External.nowPlaying();
                        Thread.sleep(1000L);
                    }
                } catch (InterruptedException ignored) {
                    // stopping
                } finally {
                    external = null;
                    externalPoll = null;
                }
            }, "1227-window-titles");
            t.setDaemon(true);
            externalPoll = t;
            t.start();
        }
        return external;
    }

    /** Media keys go to Spotify / the browser when no local song is loaded. */
    public boolean externalActive() {
        return player.current() == null && external() != null;
    }

    // ------------------------------------------------------------- covers

    private final Map<String, ResourceLocation> covers = new java.util.LinkedHashMap<String, ResourceLocation>(16, 0.75f, true);
    private final java.util.Set<String> loading = new java.util.HashSet<String>();
    private final Map<String, String[]> embeds = new HashMap<String, String[]>();

    /**
     * Texture of a local cover file, or null while it loads (read and scaled on a background
     * thread, uploaded on the game thread). At most 48 are kept; the least recently used one is
     * freed from the GPU when a new one comes in.
     */
    public ResourceLocation cover(File f) {
        if (f == null) return null;
        final String k = f.getAbsolutePath();
        if (covers.containsKey(k)) return covers.get(k);
        if (loading.add(k)) {
            final File file = f;
            Thread t = new Thread(() -> {
                java.awt.image.BufferedImage img = null;
                try {
                    java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(file);
                    if (raw != null) img = square(raw, 128);
                } catch (Exception ignored) {
                    // unreadable image
                }
                final java.awt.image.BufferedImage fi = img;
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    loading.remove(k);
                    putCover(k, fi == null ? null : upload("m1227_cover", fi));
                });
            }, "1227-cover");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    private void putCover(String k, ResourceLocation r) {
        while (covers.size() >= 48) {
            String oldest = covers.keySet().iterator().next();
            ResourceLocation old = covers.remove(oldest);
            if (old != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(old);
        }
        covers.put(k, r);
    }

    /** {title, coverTexture-key} of a playlist link from oEmbed (fetched once in the background). */
    public String[] embed(final String link) {
        if (link == null || link.trim().isEmpty()) return null;
        if (embeds.containsKey(link)) return embeds.get(link);
        embeds.put(link, null);
        Thread t = new Thread(() -> {
            String[] info = External.oembed(link);
            if (info == null) return;
            java.awt.image.BufferedImage img = null;
            try {
                if (info[1] != null && info[1].startsWith("https://")) {
                    java.net.URLConnection c = new java.net.URL(info[1]).openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    try (java.io.InputStream in = c.getInputStream()) {
                        img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(External.readAll(in, 4 << 20)));
                    }
                }
            } catch (Exception ignored) {
                // cover is optional
            }
            final java.awt.image.BufferedImage fimg = img;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ResourceLocation r = fimg == null ? null : upload("m1227_playlist", square(fimg, 128));
                embeds.put(link, new String[]{info[0], r == null ? null : r.toString()});
                if (r != null) putCover("embed:" + link, r);
            });
        }, "1227-oembed");
        t.setDaemon(true);
        t.start();
        return null;
    }

    public ResourceLocation embedCover(String link) {
        return covers.get("embed:" + link);
    }

    /** Centre square of an image, scaled to s x s. */
    public static java.awt.image.BufferedImage square(java.awt.image.BufferedImage src, int s) {
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int side = Math.min(src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, s, s, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2,
                (src.getWidth() + side) / 2, (src.getHeight() + side) / 2, null);
        g.dispose();
        return out;
    }

    private static ResourceLocation upload(String name, java.awt.image.BufferedImage img) {
        return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(name, new DynamicTexture(img));
    }

    // ------------------------------------------------------------- keys & volume

    private float lastVolume = -1f;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (volume.f() != lastVolume) {
            lastVolume = volume.f();
            player.setVolume(lastVolume);
        }
    }

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (!org.lwjgl.input.Keyboard.getEventKeyState() || Minecraft.getMinecraft().currentScreen != null) return;
        int k = org.lwjgl.input.Keyboard.getEventKey();
        if (k == 0) return;
        if (k == openKey.code()) Minecraft.getMinecraft().displayGuiScreen(new net.m1227.ui.MusicScreen());
        else if (k == playKey.code()) playPause();
        else if (k == nextKey.code()) skip(true);
        else if (k == prevKey.code()) skip(false);
    }

    /** Play/pause for whatever is playing: the local player, or Spotify/YouTube via media keys. */
    public void playPause() {
        if (externalActive() || (player.current() == null && library().isEmpty())) External.mediaKey(External.PLAY_PAUSE);
        else togglePause();
    }

    public void skip(boolean forward) {
        if (externalActive()) External.mediaKey(forward ? External.NEXT : External.PREVIOUS);
        else if (forward) next();
        else previous();
    }
}
