package net.m1227.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.m1227.Mod1227;
import net.m1227.core.ConfigManager;

/**
 * Optional link to the Windows media controls (Global System Media Transport Controls), the
 * same information Windows shows in its volume flyout: title, artist, album, play state, and
 * the timeline (position and length) for players that publish it (Spotify does; Chromium
 * browsers do for YouTube).
 *
 * Java 8 cannot call the Windows Runtime directly, so a small PowerShell script that ships in
 * the jar (assets/m1227/scripts/media-bridge.ps1) reads it and prints a JSON line about twice a
 * second. Nothing is installed; the script is copied to config/1227/media-bridge.ps1 and run
 * with "-ExecutionPolicy Bypass" for that one process only. It reads, never controls, never uses
 * the network, and exits with Minecraft. Started only while something needs it (lyrics, the
 * Now Playing widget, the visualizer) and stopped after 20 s without being asked.
 *
 * Positions are honest: when the player does not publish a timeline, {@link Info#timeline} is
 * false and nothing pretends to be in sync.
 */
public final class MediaBridge {
    private MediaBridge() {}

    public static final class Info {
        public String state = "", app = "", title = "", artist = "", album = "";
        public double posMs, startMs, endMs, ageMs;
        public long received;
        public int coverSeq;

        public boolean playing() {
            return "Playing".equals(state);
        }

        /** True when the player publishes a usable timeline. */
        public boolean timeline() {
            return endMs > startMs + 1000;
        }

        /** Position now, in ms from the song's start (extrapolated while playing). */
        public double positionMs() {
            double p = posMs - startMs;
            if (playing()) p += ageMs + (System.nanoTime() - received) / 1e6;
            return Math.max(0, Math.min(endMs - startMs, p));
        }

        public String appName() {
            String a = app.toLowerCase();
            if (a.contains("spotify")) return "Spotify";
            if (a.contains("chrome")) return "Chrome";
            if (a.contains("msedge") || a.contains("edge")) return "Edge";
            if (a.contains("firefox")) return "Firefox";
            if (a.contains("opera")) return "Opera";
            if (a.contains("brave")) return "Brave";
            if (a.contains("zunemusic") || a.contains("media")) return "Media Player";
            if (a.contains("deezer")) return "Deezer";
            if (a.contains("tidal")) return "Tidal";
            int i = app.lastIndexOf('\\');
            String n = i >= 0 ? app.substring(i + 1) : app;
            return n.replace(".exe", "");
        }
    }

    private static volatile Process proc;
    private static volatile Info last;
    private static volatile long lastWanted, startedAt;
    private static volatile String status = "off";
    private static int failures;
    private static long nextTry;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Short state for the settings page: off, starting, running, unsupported, failed... */
    public static String status() {
        return status;
    }

    public static File coverFile() {
        File d = new File(ConfigManager.dir(), "1227/cache");
        d.mkdirs();
        return new File(d, "media-cover.img");
    }

    /**
     * Latest info, or null (no player, not Windows, bridge off or not answering). Calling this
     * keeps the bridge running; call from the game thread only when the feature is on.
     */
    public static Info current() {
        lastWanted = System.currentTimeMillis();
        ensure();
        Info i = last;
        if (i == null || (System.nanoTime() - i.received) > 4_000_000_000L) return null;
        return i;
    }

    /** Called every client tick: stops the script when nothing asked for it lately. */
    public static void tick() {
        if (proc != null && System.currentTimeMillis() - lastWanted > 20000L) stop();
    }

    private static synchronized void ensure() {
        if (!supported()) {
            status = "unsupported (Windows only)";
            return;
        }
        if (proc != null) return;
        long now = System.currentTimeMillis();
        if (now < nextTry) return;
        if (failures >= 3) {
            status = "failed (see the log)";
            return;
        }
        try {
            File dir = new File(ConfigManager.dir(), "1227");
            dir.mkdirs();
            File script = new File(dir, "media-bridge.ps1");
            byte[] data;
            try (InputStream in = MediaBridge.class.getResourceAsStream("/assets/m1227/scripts/media-bridge.ps1")) {
                if (in == null) throw new IllegalStateException("script missing from the jar");
                data = read(in);
            }
            // UTF-8 with BOM so Windows PowerShell 5.1 reads the file as UTF-8
            byte[] withBom = new byte[data.length + 3];
            withBom[0] = (byte) 0xEF;
            withBom[1] = (byte) 0xBB;
            withBom[2] = (byte) 0xBF;
            System.arraycopy(data, 0, withBom, 3, data.length);
            if (!script.isFile() || !java.util.Arrays.equals(Files.readAllBytes(script.toPath()), withBom)) {
                Files.write(script.toPath(), withBom);
            }
            String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.getAbsolutePath(), pid,
                    coverFile().getAbsolutePath());
            pb.redirectErrorStream(true);
            final Process p = pb.start();
            proc = p;
            startedAt = now;
            status = "starting";
            Thread reader = new Thread(() -> readLoop(p), "1227-media-bridge");
            reader.setDaemon(true);
            reader.start();
        } catch (Throwable t) {
            failures++;
            nextTry = now + 30000L;
            status = "failed: " + t.getMessage();
            Mod1227.LOGGER.warn("1227 media bridge could not start", t);
        }
    }

    private static void readLoop(Process p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("{")) continue;
                try {
                    JsonObject o = new JsonParser().parse(line).getAsJsonObject();
                    if (o.has("error")) {
                        String err = o.get("error").getAsString();
                        if (err.startsWith("unsupported")) status = "unsupported on this Windows";
                        else status = "running (last error: " + err + ")";
                        continue;
                    }
                    Info i = new Info();
                    i.received = System.nanoTime();
                    i.state = str(o, "state");
                    if ("none".equals(i.state)) {
                        last = null;
                        status = "running (no player)";
                        continue;
                    }
                    i.app = str(o, "app");
                    i.title = str(o, "title");
                    i.artist = str(o, "artist");
                    i.album = str(o, "album");
                    i.posMs = num(o, "pos");
                    i.startMs = num(o, "start");
                    i.endMs = num(o, "end");
                    i.ageMs = Math.max(0, Math.min(60000, num(o, "age")));
                    i.coverSeq = (int) num(o, "cover");
                    last = i;
                    status = "running";
                    failures = 0;
                } catch (RuntimeException bad) {
                    // one odd line: skip it
                }
            }
        } catch (Exception ignored) {
            // process ended
        }
        synchronized (MediaBridge.class) {
            if (proc == p) {
                proc = null;
                last = null;
                if (System.currentTimeMillis() - startedAt < 10000L) {
                    failures++;
                    nextTry = System.currentTimeMillis() + 30000L;
                    if (!status.startsWith("unsupported")) status = "stopped early (PowerShell unavailable?)";
                } else {
                    status = "off";
                }
            }
        }
    }

    public static synchronized void stop() {
        Process p = proc;
        proc = null;
        last = null;
        if (p != null) p.destroy();
        if (!status.startsWith("unsupported") && !status.startsWith("failed")) status = "off";
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static double num(JsonObject o, String k) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static byte[] read(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        return bo.toByteArray();
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Process p = proc;
            if (p != null) p.destroy();
        }, "1227-media-bridge-stop"));
    }
}
