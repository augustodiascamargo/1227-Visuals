package net.m1227.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Spotify and YouTube, the permitted way: the playlist opens in the Spotify app or in your
 * browser (where it plays with your own account), and 1227 only
 *  - reads what is playing from that window's title (Spotify shows "Artist - Song", YouTube
 *    tabs end in " - YouTube"),
 *  - sends the standard media keys (play/pause, next, previous) that both apps react to,
 *  - shows the playlist's name and cover from the public oEmbed endpoints (no key, no login).
 * Nothing is downloaded or streamed by 1227. Windows only (uses user32 through JNA, which
 * Minecraft 1.8.9 already ships).
 */
public final class External {
    private External() {}

    public interface User32 extends StdCallLibrary {
        interface EnumProc extends StdCallCallback {
            boolean callback(Pointer hWnd, Pointer data);
        }

        boolean EnumWindows(EnumProc proc, Pointer data);

        int GetWindowTextW(Pointer hWnd, char[] buf, int max);

        boolean IsWindowVisible(Pointer hWnd);

        int GetWindowThreadProcessId(Pointer hWnd, IntByReference pid);

        void keybd_event(byte vk, byte scan, int flags, Pointer extra);
    }

    public interface Kernel32 extends StdCallLibrary {
        Pointer OpenProcess(int access, boolean inherit, int pid);

        boolean QueryFullProcessImageNameW(Pointer process, int flags, char[] name, IntByReference size);

        boolean CloseHandle(Pointer h);
    }

    private static User32 user32;
    private static Kernel32 kernel32;
    private static boolean tried;

    private static boolean load() {
        if (tried) return user32 != null;
        tried = true;
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return false;
        try {
            user32 = (User32) Native.loadLibrary("user32", User32.class);
            kernel32 = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
        } catch (Throwable t) {
            user32 = null;
            kernel32 = null;
        }
        return user32 != null;
    }

    public static boolean available() {
        return load();
    }

    /** What is playing: {source, text} — source "Spotify" or "YouTube" — or null. */
    public static String[] nowPlaying() {
        if (!load()) return null;
        final String[][] found = {null, null};
        try {
            user32.EnumWindows(new User32.EnumProc() {
                @Override
                public boolean callback(Pointer hWnd, Pointer data) {
                if (!user32.IsWindowVisible(hWnd)) return true;
                char[] buf = new char[512];
                int n = user32.GetWindowTextW(hWnd, buf, buf.length);
                if (n <= 0) return true;
                String title = new String(buf, 0, n);
                if (found[0] == null && isSpotify(hWnd)) {
                    // paused Spotify shows "Spotify" / "Spotify Premium" / "Spotify Free"
                    if (title.contains(" - ") && !title.startsWith("Spotify")) found[0] = new String[]{"Spotify", title};
                } else if (found[1] == null) {
                    int i = title.lastIndexOf(" - YouTube");
                    if (i > 0) {
                        String t = title.substring(0, i);
                        if (t.startsWith("(") && t.indexOf(") ") > 0) t = t.substring(t.indexOf(") ") + 2); // "(3) " unread badge
                        found[1] = new String[]{"YouTube", t};
                    }
                }
                return true;
                }
            }, null);
        } catch (Throwable t) {
            return null;
        }
        return found[0] != null ? found[0] : found[1];
    }

    private static boolean isSpotify(Pointer hWnd) {
        try {
            IntByReference pid = new IntByReference();
            user32.GetWindowThreadProcessId(hWnd, pid);
            Pointer h = kernel32.OpenProcess(0x1000 /* QUERY_LIMITED_INFORMATION */, false, pid.getValue());
            if (h == null) return false;
            try {
                char[] name = new char[1024];
                IntByReference size = new IntByReference(name.length);
                if (!kernel32.QueryFullProcessImageNameW(h, 0, name, size)) return false;
                return new String(name, 0, size.getValue()).toLowerCase().endsWith("\\spotify.exe");
            } finally {
                kernel32.CloseHandle(h);
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static final int PLAY_PAUSE = 0xB3, NEXT = 0xB0, PREVIOUS = 0xB1;

    /** Sends a media key to whichever app owns media playback (Spotify, the browser's YouTube tab...). */
    public static void mediaKey(int vk) {
        if (!load()) return;
        try {
            user32.keybd_event((byte) vk, (byte) 0, 0, null);
            user32.keybd_event((byte) vk, (byte) 0, 2 /* KEYUP */, null);
        } catch (Throwable ignored) {
            // no input injection available
        }
    }

    // ------------------------------------------------------------- links

    /** Accepts open.spotify.com links and spotify: URIs for playlists/albums/tracks (see Links). */
    public static String spotifyUri(String link) {
        return Links.spotifyUri(link);
    }

    public static String spotifyWeb(String uri) {
        return Links.spotifyWeb(uri);
    }

    /** Accepts youtube.com / music.youtube.com playlist or video links (see Links). */
    public static String youtubeUrl(String link) {
        return Links.youtubeUrl(link);
    }

    /** Opens the Spotify link in the Spotify app (falls back to the web player), or the YouTube link in the browser. */
    public static boolean open(String link) {
        String sp = spotifyUri(link), yt = youtubeUrl(link);
        if (sp == null && yt == null) return false;
        final String first = sp != null ? sp : yt, fallback = sp != null ? spotifyWeb(sp) : null;
        Thread t = new Thread(() -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(first));
            } catch (Throwable e) {
                try {
                    if (fallback != null) java.awt.Desktop.getDesktop().browse(new java.net.URI(fallback));
                } catch (Throwable ignored) {
                    // no browser
                }
            }
        }, "1227-open-link");
        t.setDaemon(true);
        t.start();
        return true;
    }

    /** {title, thumbnail url} of a Spotify/YouTube link from the public oEmbed endpoint, or null. */
    public static String[] oembed(String link) {
        String sp = spotifyUri(link), yt = youtubeUrl(link);
        String endpoint;
        try {
            if (sp != null) endpoint = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(spotifyWeb(sp), "UTF-8");
            else if (yt != null) endpoint = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(yt, "UTF-8");
            else return null;
            HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("User-Agent", "1227Visuals");
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream()) {
                byte[] all = readAll(in, 1 << 16);
                JsonObject o = new JsonParser().parse(new String(all, StandardCharsets.UTF_8)).getAsJsonObject();
                String title = o.has("title") ? o.get("title").getAsString() : "";
                String thumb = o.has("thumbnail_url") ? o.get("thumbnail_url").getAsString() : null;
                return new String[]{title, thumb};
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] readAll(InputStream in, int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) {
            bo.write(b, 0, n);
            if (bo.size() > max) throw new java.io.IOException("too large");
        }
        return bo.toByteArray();
    }
}
