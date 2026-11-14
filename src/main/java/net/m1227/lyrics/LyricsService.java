package net.m1227.lyrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.m1227.Mod1227;
import net.m1227.core.ConfigManager;
import net.m1227.module.FloatingLyricsModule;
import net.m1227.music.NowPlaying;

/**
 * Finds the lyrics of whatever is playing, in this order:
 * <ol>
 * <li>a .lrc (or .txt) file with the same name next to the local song;</li>
 * <li>config/1227-lyrics/"Artist - Title".lrc or "Title".lrc (your own files, any source);</li>
 * <li>the cache in config/1227/lyrics-cache (earlier downloads, your manual picks, and
 * "not found" answers for 3 days so the same song is not asked again);</li>
 * <li>LRCLIB, if online search is on: exact lookup, then a search scored by
 * {@link LyricsMatch}; a result that does not clearly match is not shown, but is offered
 * in the lyrics screen for you to choose.</li>
 * </ol>
 * All file and network work happens on one background thread; the game thread only reads the
 * finished result. Per-song sync offsets are saved in the cache entry.
 */
public final class LyricsService {
    private LyricsService() {}

    public enum State { IDLE, SEARCHING, FOUND, NOT_FOUND, INSTRUMENTAL, ERROR }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "1227-lyrics");
        t.setDaemon(true);
        return t;
    });

    private static volatile String key = "";
    private static volatile State state = State.IDLE;
    private static volatile Lyrics lyrics;
    private static volatile String message = "";
    private static volatile List<LrcLib.Record> candidates = Collections.emptyList();
    private static volatile long songOffset;
    private static volatile int requestId;
    private static NowPlaying.Info song;

    public static State state() {
        return state;
    }

    public static Lyrics lyrics() {
        return state == State.FOUND ? lyrics : null;
    }

    public static String message() {
        return message;
    }

    public static List<LrcLib.Record> candidates() {
        return candidates;
    }

    public static NowPlaying.Info song() {
        return song;
    }

    /** Per-song offset in ms (positive = lyrics later), saved with the song's cache entry. */
    public static long songOffset() {
        return songOffset;
    }

    public static void nudge(long ms) {
        songOffset = Math.max(-30000L, Math.min(30000L, songOffset + ms));
        final String k = key;
        final long off = songOffset;
        WORKER.submit(() -> {
            JsonObject e = readCache(k);
            if (e == null) e = new JsonObject();
            e.addProperty("userOffset", off);
            writeCache(k, e);
        });
    }

    /** Called every client tick: starts a lookup when the song changes. */
    public static void tick() {
        FloatingLyricsModule m = FloatingLyricsModule.INSTANCE;
        // World Lyrics uses the same lyrics, so either module keeps the search running
        if (m == null || !(m.isEnabled() || net.m1227.module.WorldLyricsModule.active())) return;
        NowPlaying.wantBridge();
        NowPlaying.Info np = NowPlaying.get();
        if (np.source == NowPlaying.Source.NONE || np.title.isEmpty()) {
            if (!key.isEmpty()) {
                key = "";
                state = State.IDLE;
                lyrics = null;
                candidates = Collections.<LrcLib.Record>emptyList();
            }
            song = np;
            return;
        }
        song = np;
        String k = np.key();
        // the duration of an external song can appear a moment after its title: same song
        if (k.equals(key) || (sameSong(k, key))) return;
        key = k;
        search(np, m.onlineSearch(), false);
    }

    private static boolean sameSong(String a, String b) {
        int ia = a.lastIndexOf('|'), ib = b.lastIndexOf('|');
        if (ia < 0 || ib < 0) return false;
        if (!a.substring(0, ia).equals(b.substring(0, ib))) return false;
        String da = a.substring(ia + 1), db = b.substring(ib + 1);
        return da.equals("0") || db.equals("0") || Math.abs(Long.parseLong(da) - Long.parseLong(db)) <= 2;
    }

    /** Looks again (forgets the cached "not found"). */
    public static void retry() {
        NowPlaying.Info np = song;
        FloatingLyricsModule m = FloatingLyricsModule.INSTANCE;
        if (np == null || np.title.isEmpty() || m == null) return;
        final String k = np.key();
        WORKER.submit(() -> {
            JsonObject e = readCache(k);
            if (e != null) {
                e.remove("status");
                e.remove("lrc");
                writeCache(k, e);
            }
        });
        search(np, m.onlineSearch(), true);
    }

    /** Uses a candidate the user picked (saved for this song). */
    public static void choose(final LrcLib.Record r) {
        final NowPlaying.Info np = song;
        if (np == null || r == null) return;
        final String k = np.key();
        final int id = ++requestId;
        WORKER.submit(() -> {
            JsonObject e = readCache(k);
            if (e == null) e = new JsonObject();
            store(e, r);
            e.addProperty("chosen", true);
            writeCache(k, e);
            if (id == requestId) publish(fromRecord(r), State.FOUND, "Chosen by you: LRCLIB #" + r.id);
        });
    }

    /** Manual text search for the lyrics screen; results go to candidates(). */
    public static void manualSearch(final String text) {
        final int id = ++requestId;
        state = State.SEARCHING;
        message = "Searching \"" + text + "\"...";
        WORKER.submit(() -> {
            try {
                List<LrcLib.Record> list = LrcLib.searchText(text);
                if (id != requestId) return;
                candidates = list;
                state = lyrics != null ? State.FOUND : State.NOT_FOUND;
                message = list.isEmpty() ? "No results for \"" + text + "\"" : list.size() + " result(s): pick one";
            } catch (Exception ex) {
                if (id != requestId) return;
                state = lyrics != null ? State.FOUND : State.ERROR;
                message = ex.getMessage();
            }
        });
    }

    /** Replaces the current lyrics (from the editor) without searching. */
    public static void useEdited(Lyrics l) {
        if (l == null) return;
        requestId++;
        publish(l, State.FOUND, l.source);
    }

    private static void search(final NowPlaying.Info np, final boolean online, final boolean force) {
        final int id = ++requestId;
        state = State.SEARCHING;
        lyrics = null;
        message = "Looking for lyrics...";
        candidates = Collections.<LrcLib.Record>emptyList();
        songOffset = 0;
        final String k = np.key();
        WORKER.submit(() -> {
            try {
                find(np, k, online, force, id);
            } catch (Throwable t) {
                if (id != requestId) return;
                state = State.ERROR;
                message = "Lyrics error: " + t.getMessage();
                Mod1227.LOGGER.warn("1227 lyrics lookup failed", t);
            }
        });
    }

    private static void publish(Lyrics l, State s, String msg) {
        lyrics = l;
        state = s;
        message = msg;
    }

    // ------------------------------------------------------------- lookup (worker thread)

    private static void find(NowPlaying.Info np, String k, boolean online, boolean force, int id) throws Exception {
        // 1. next to the local file
        if (np.file != null) {
            Lyrics l = readLocal(np.file);
            if (l != null) {
                if (id == requestId) {
                    applyOffset(k);
                    publish(l, State.FOUND, "File: " + l.source);
                }
                return;
            }
        }
        // 2. lyrics folder
        Lyrics own = readOwnFolder(np);
        if (own != null) {
            if (id == requestId) {
                applyOffset(k);
                publish(own, State.FOUND, "File: " + own.source);
            }
            return;
        }
        // 3. cache
        JsonObject e = readCache(k);
        if (e != null && id == requestId && e.has("userOffset")) songOffset = e.get("userOffset").getAsLong();
        if (e != null && e.has("status") && !force) {
            String st = e.get("status").getAsString();
            long at = e.has("at") ? e.get("at").getAsLong() : 0;
            if (st.equals("found") && e.has("lrc")) {
                Lyrics l = e.has("synced") && e.get("synced").getAsBoolean() ? LrcParser.parse(e.get("lrc").getAsString())
                        : LrcParser.plain(e.get("lrc").getAsString());
                l.source = e.has("source") ? e.get("source").getAsString() : "cache";
                if (id == requestId) publish(l, State.FOUND, l.source + (l.synced ? "" : " (not synced)"));
                return;
            }
            if (st.equals("instrumental")) {
                if (id == requestId) publish(null, State.INSTRUMENTAL, "Instrumental");
                return;
            }
            if (st.equals("none") && System.currentTimeMillis() - at < 3L * 24 * 3600 * 1000) {
                if (id == requestId) publish(null, State.NOT_FOUND, "No lyrics found (searched recently)");
                return;
            }
        }
        if (!online) {
            if (id == requestId) publish(null, State.NOT_FOUND, "No local lyrics (online search is off)");
            return;
        }
        // 4. LRCLIB
        String title = np.title, artist = np.artist, album = np.album;
        double dur = np.duration;
        LrcLib.Record hit = null;
        if (!artist.isEmpty() && dur > 0) {
            hit = LrcLib.get(title, artist, album, dur);
            if (hit != null && LyricsMatch.score(title, artist, album, dur, hit.trackName, hit.artistName, hit.albumName, hit.duration) < LyricsMatch.ACCEPT) {
                hit = null;
            }
        }
        List<LrcLib.Record> list = new ArrayList<LrcLib.Record>();
        if (hit == null) {
            if (id != requestId) return;
            list = LrcLib.search(LyricsMatch.clean(title).isEmpty() ? title : title, artist);
            if (list.isEmpty() && !artist.isEmpty()) list = LrcLib.search(title, "");
            hit = LrcLib.best(list, title, artist, album, dur);
        }
        if (id != requestId) return;
        candidates = list;
        JsonObject entry = e == null ? new JsonObject() : e;
        entry.addProperty("at", System.currentTimeMillis());
        if (hit == null) {
            entry.addProperty("status", "none");
            writeCache(k, entry);
            publish(null, State.NOT_FOUND, list.isEmpty() ? "No lyrics found" : "No exact match: open the lyrics screen to choose");
            return;
        }
        if (hit.instrumental && !hit.hasAny()) {
            entry.addProperty("status", "instrumental");
            writeCache(k, entry);
            publish(null, State.INSTRUMENTAL, "Instrumental");
            return;
        }
        store(entry, hit);
        writeCache(k, entry);
        publish(fromRecord(hit), State.FOUND, "LRCLIB #" + hit.id + (hit.hasSynced() ? "" : " (not synced)"));
    }

    private static void applyOffset(String k) {
        JsonObject e = readCache(k);
        if (e != null && e.has("userOffset")) songOffset = e.get("userOffset").getAsLong();
    }

    private static void store(JsonObject e, LrcLib.Record r) {
        e.addProperty("status", "found");
        e.addProperty("at", System.currentTimeMillis());
        e.addProperty("synced", r.hasSynced());
        e.addProperty("lrc", r.hasSynced() ? r.syncedLyrics : r.plainLyrics == null ? "" : r.plainLyrics);
        e.addProperty("source", "LRCLIB #" + r.id);
        e.addProperty("id", r.id);
    }

    static Lyrics fromRecord(LrcLib.Record r) {
        Lyrics l = r.hasSynced() ? LrcParser.parse(r.syncedLyrics) : LrcParser.plain(r.plainLyrics);
        l.source = "LRCLIB #" + r.id;
        l.title = r.trackName;
        l.artist = r.artistName;
        l.album = r.albumName;
        l.instrumental = r.instrumental;
        return l;
    }

    // ------------------------------------------------------------- files

    public static File ownFolder() {
        File d = new File(ConfigManager.dir(), "1227-lyrics");
        d.mkdirs();
        return d;
    }

    private static File cacheDir() {
        File d = new File(ConfigManager.dir(), "1227/lyrics-cache");
        d.mkdirs();
        return d;
    }

    static Lyrics readLocal(File audio) {
        String n = audio.getName();
        int dot = n.lastIndexOf('.');
        String base = dot > 0 ? n.substring(0, dot) : n;
        File dir = audio.getParentFile();
        for (String ext : new String[]{".lrc", ".LRC", ".txt"}) {
            File f = new File(dir, base + ext);
            Lyrics l = readFile(f);
            if (l != null) return l;
        }
        return null;
    }

    /** Where the editor saves lyrics for this song. */
    public static File saveTarget(NowPlaying.Info np) {
        if (np.file != null) {
            String n = np.file.getName();
            int dot = n.lastIndexOf('.');
            return new File(np.file.getParentFile(), (dot > 0 ? n.substring(0, dot) : n) + ".lrc");
        }
        String name = (np.artist.isEmpty() ? "" : np.artist + " - ") + np.title;
        return new File(ownFolder(), safe(name) + ".lrc");
    }

    static String safe(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static Lyrics readOwnFolder(NowPlaying.Info np) {
        File d = ownFolder();
        String[] names = {(np.artist.isEmpty() ? "" : np.artist + " - ") + np.title, np.title};
        for (String n : names) {
            Lyrics l = readFile(new File(d, safe(n) + ".lrc"));
            if (l == null) l = readFile(new File(d, safe(n) + ".txt"));
            if (l != null) return l;
        }
        // loose match: any file whose cleaned name matches the song
        File[] all = d.listFiles((x, n) -> n.toLowerCase().endsWith(".lrc"));
        if (all != null) {
            for (File f : all) {
                String n = f.getName().substring(0, f.getName().length() - 4);
                String[] at = LyricsMatch.splitArtistTitle(n, "");
                double s = LyricsMatch.score(np.title, np.artist, "", 0, at[1].isEmpty() ? n : at[1], at[0], "", 0);
                if (s >= 0.85) return readFile(f);
            }
        }
        return null;
    }

    static Lyrics readFile(File f) {
        if (!f.isFile() || f.length() > 512 * 1024) return null;
        try {
            Lyrics l = LrcParser.parse(LrcParser.decode(Files.readAllBytes(f.toPath())));
            if (l.empty()) return null;
            l.source = f.getName();
            return l;
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not read lyrics " + f, e);
            return null;
        }
    }

    private static String hash(String k) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(k.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(String.format("%02x", d[i]));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(k.hashCode());
        }
    }

    private static JsonObject readCache(String k) {
        File f = new File(cacheDir(), hash(k) + ".json");
        if (!f.isFile()) return null;
        try {
            return new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCache(String k, JsonObject e) {
        try {
            e.addProperty("key", k);
            ConfigManager.writeAtomic(new File(cacheDir(), hash(k) + ".json"), GSON.toJson(e).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not write the lyrics cache", ex);
        }
    }

    /** Deletes every cached download and "not found" answer (your own .lrc files stay). */
    public static void clearCache() {
        WORKER.submit(() -> {
            File[] all = cacheDir().listFiles((d, n) -> n.endsWith(".json"));
            if (all != null) for (File f : all) f.delete();
        });
        retry();
    }

    /** Saves edited lyrics as LRC (atomic) and uses them right away. */
    public static void save(final Lyrics l, final File target, final Runnable done) {
        WORKER.submit(() -> {
            try {
                target.getParentFile().mkdirs();
                ConfigManager.writeAtomic(target, LrcParser.write(l).getBytes(StandardCharsets.UTF_8));
                l.source = target.getName();
                publish(l, State.FOUND, "File: " + target.getName());
                if (done != null) net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(done);
            } catch (Exception ex) {
                message = "Could not save: " + ex.getMessage();
            }
        });
    }
}
