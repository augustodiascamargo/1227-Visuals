package net.m1227.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the public LRCLIB API (https://lrclib.net/docs), which needs no key.
 * <ul>
 * <li>Every request sends a User-Agent naming 1227 and its version, as the docs ask.</li>
 * <li>Requests are made one at a time, at least {@link #MIN_GAP_MS} apart (the caller runs this
 * on its own background thread; nothing here touches the game thread).</li>
 * <li>404 = not found (normal answer, cached by the caller); 429 = slow down: the Retry-After
 * header is respected (60 s when missing) and no request goes out before then.</li>
 * <li>Short timeouts, a size cap on answers, and no retries in a loop.</li>
 * </ul>
 * Data sent: the song title, artist, album and duration. Nothing else about you.
 */
public final class LrcLib {
    public static final String BASE = "https://lrclib.net/api/";
    public static final long MIN_GAP_MS = 1500L;
    private static String userAgent = "1227 (Minecraft 1.8.9 Forge mod)";
    private static long lastRequest, blockedUntil;

    private LrcLib() {}

    public static void setVersion(String v) {
        userAgent = "1227/" + v + " (Minecraft 1.8.9 Forge client mod)";
    }

    /** One LRCLIB record. */
    public static final class Record {
        public long id;
        public String trackName = "", artistName = "", albumName = "", plainLyrics, syncedLyrics;
        public double duration;
        public boolean instrumental;

        public boolean hasSynced() {
            return syncedLyrics != null && !syncedLyrics.trim().isEmpty();
        }

        public boolean hasAny() {
            return hasSynced() || (plainLyrics != null && !plainLyrics.trim().isEmpty());
        }
    }

    /** Thrown for answers that are neither data nor "not found" (rate limit, server error, offline). */
    public static final class LrcLibException extends IOException {
        public final int status;
        public final long retryAfterMs;

        LrcLibException(String msg, int status, long retryAfterMs) {
            super(msg);
            this.status = status;
            this.retryAfterMs = retryAfterMs;
        }
    }

    /** Milliseconds until another request is allowed (rate limit or minimum gap). */
    public static synchronized long waitMs() {
        long now = System.currentTimeMillis();
        return Math.max(0L, Math.max(blockedUntil, lastRequest + MIN_GAP_MS) - now);
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s == null ? "" : s, "UTF-8");
    }

    /**
     * Exact lookup (/api/get): title, artist, album and duration must match LRCLIB's record (it
     * accepts about 2 s of duration difference). Null when not found.
     */
    public static Record get(String title, String artist, String album, double durationSec) throws IOException {
        StringBuilder q = new StringBuilder("get?track_name=").append(enc(title)).append("&artist_name=").append(enc(artist));
        if (album != null && !album.isEmpty()) q.append("&album_name=").append(enc(album));
        if (durationSec > 0) q.append("&duration=").append(Math.round(durationSec));
        String body = request(q.toString());
        if (body == null) return null;
        return record(new JsonParser().parse(body).getAsJsonObject());
    }

    /** Search (/api/search) by title and artist; up to LRCLIB's own limit of results. */
    public static List<Record> search(String title, String artist) throws IOException {
        StringBuilder q = new StringBuilder("search?track_name=").append(enc(title));
        if (artist != null && !artist.isEmpty()) q.append("&artist_name=").append(enc(artist));
        String body = request(q.toString());
        List<Record> out = new ArrayList<Record>();
        if (body == null) return out;
        JsonElement root = new JsonParser().parse(body);
        if (!root.isJsonArray()) return out;
        JsonArray a = root.getAsJsonArray();
        for (int i = 0; i < a.size() && out.size() < 20; i++) {
            if (a.get(i).isJsonObject()) out.add(record(a.get(i).getAsJsonObject()));
        }
        return out;
    }

    /** Free-text search (/api/search?q=), used by the manual search box. */
    public static List<Record> searchText(String text) throws IOException {
        String body = request("search?q=" + enc(text));
        List<Record> out = new ArrayList<Record>();
        if (body == null) return out;
        JsonElement root = new JsonParser().parse(body);
        if (!root.isJsonArray()) return out;
        for (JsonElement e : root.getAsJsonArray()) {
            if (e.isJsonObject()) out.add(record(e.getAsJsonObject()));
            if (out.size() >= 20) break;
        }
        return out;
    }

    static Record record(JsonObject o) {
        Record r = new Record();
        r.id = o.has("id") && o.get("id").isJsonPrimitive() ? o.get("id").getAsLong() : 0;
        r.trackName = str(o, "trackName");
        r.artistName = str(o, "artistName");
        r.albumName = str(o, "albumName");
        r.plainLyrics = strOrNull(o, "plainLyrics");
        r.syncedLyrics = strOrNull(o, "syncedLyrics");
        r.duration = o.has("duration") && o.get("duration").isJsonPrimitive() ? o.get("duration").getAsDouble() : 0;
        r.instrumental = o.has("instrumental") && o.get("instrumental").isJsonPrimitive() && o.get("instrumental").getAsBoolean();
        return r;
    }

    private static String str(JsonObject o, String k) {
        String s = strOrNull(o, k);
        return s == null ? "" : s;
    }

    private static String strOrNull(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
    }

    /** GET; null on 404. Waits for the gap / rate limit first (call only off the game thread). */
    private static String request(String path) throws IOException {
        long wait = waitMs();
        if (wait > 0) {
            if (wait > 5000L) throw new LrcLibException("Rate limited by LRCLIB, try again in " + (wait / 1000 + 1) + " s", 429, wait);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
        }
        synchronized (LrcLib.class) {
            lastRequest = System.currentTimeMillis();
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE + path).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", userAgent);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code == 429) {
                long ra = 60000L;
                String h = c.getHeaderField("Retry-After");
                if (h != null) {
                    try {
                        ra = Math.max(1000L, Long.parseLong(h.trim()) * 1000L);
                    } catch (NumberFormatException ignored) {
                        // HTTP-date form: keep the default
                    }
                }
                synchronized (LrcLib.class) {
                    blockedUntil = System.currentTimeMillis() + ra;
                }
                throw new LrcLibException("LRCLIB asked to slow down (429)", 429, ra);
            }
            if (code != 200) throw new LrcLibException("LRCLIB answered " + code, code, 0);
            try (InputStream in = c.getInputStream()) {
                return new String(readAll(in, 1 << 20), StandardCharsets.UTF_8);
            }
        } catch (SocketTimeoutException e) {
            throw new LrcLibException("LRCLIB did not answer in time", 0, 0);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
            if (bo.size() > max) throw new IOException("answer too large");
        }
        return bo.toByteArray();
    }

    /** Best candidate (score >= ACCEPT) for the song, or null. */
    public static Record best(List<Record> list, String title, String artist, String album, double duration) {
        Record best = null;
        double bs = 0;
        for (Record r : list) {
            if (!r.hasAny() && !r.instrumental) continue;
            double s = LyricsMatch.score(title, artist, album, duration, r.trackName, r.artistName, r.albumName, r.duration);
            if (r.hasSynced()) s += 0.03; // prefer timed lyrics between equal matches
            if (s > bs) {
                bs = s;
                best = r;
            }
        }
        return bs >= LyricsMatch.ACCEPT ? best : null;
    }
}
