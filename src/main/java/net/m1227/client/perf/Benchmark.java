package net.m1227.client.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.Notifier;
import net.m1227.module.PerformanceModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * In-game benchmark: measures every frame for N seconds (after 2 s of warm-up) and saves, in
 * config/1227/benchmarks.json:
 * <ul>
 * <li>average FPS (frames / time) and average frame time;</li>
 * <li>1% low and 0.1% low (the average FPS of the slowest 1% / 0.1% of frames: how stable it
 * is, not how fast; 0.1% only when there are at least 1000 frames);</li>
 * <li>worst frame time;</li>
 * <li>memory in use, entities and particles (averaged), render distance, graphics mode, the
 * preset and the 1227 version.</li>
 * </ul>
 * "Compare" measures the current settings, then a chosen preset, in the same spot, and puts the
 * results side by side; your settings are restored afterwards. Nothing is estimated.
 */
public final class Benchmark {
    public static final Benchmark INSTANCE = new Benchmark();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int WARMUP_NS = 2000000000;

    public static final class Result {
        public String label, date, version, graphics;
        public double avgFps, low1Fps, low01Fps, avgMs, worstMs;
        public int renderDistance, seconds, frames, memoryMb, entities, particles;
    }

    // averages sampled once per second while measuring
    private static long sampleAt;
    private static double sumMem, sumEnt, sumPart;
    private static int samples;
    // compare: phase 0 none, 1 measuring A, 2 measuring B
    private static int comparePhase;
    private static String compareWith;
    private static java.io.File compareBackup;
    private static Result compareA;

    private static boolean running;
    private static long start, last;
    private static int seconds;
    private static float[] times = new float[1 << 16];
    private static int n;

    private Benchmark() {}

    public static boolean running() {
        return running;
    }

    public static void start(int secs) {
        if (Minecraft.getMinecraft().theWorld == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Join a world first", Notifier.WARN, 1800);
            return;
        }
        seconds = secs;
        start = System.nanoTime();
        last = 0L;
        n = 0;
        sumMem = sumEnt = sumPart = 0;
        samples = 0;
        sampleAt = 0L;
        running = true;
        Minecraft.getMinecraft().displayGuiScreen(null);
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Measuring for " + secs + " s, keep still", Notifier.INFO, 2200);
    }

    static void frame() {
        if (!running) return;
        long now = System.nanoTime();
        if (Minecraft.getMinecraft().theWorld == null) {
            running = false;
            if (comparePhase != 0 && compareBackup != null) net.m1227.core.ConfigManager.importFrom(compareBackup);
            comparePhase = 0;
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Stopped (left the world)", Notifier.WARN, 1800);
            return;
        }
        if (now - start > WARMUP_NS) {
            if (last != 0L && n < times.length) times[n++] = (now - last) / 1e6f;
            if (now - sampleAt > 1_000_000_000L) {
                sampleAt = now;
                Minecraft mc = Minecraft.getMinecraft();
                Runtime rt = Runtime.getRuntime();
                sumMem += (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
                sumEnt += mc.theWorld.loadedEntityList.size();
                int fx = 0;
                try {
                    fx = Integer.parseInt(mc.effectRenderer.getStatistics().trim());
                } catch (RuntimeException ignored) {
                    // unknown format
                }
                sumPart += fx + net.m1227.vfx.ParticleSystem.INSTANCE.alive();
                samples++;
            }
            if (now - start > WARMUP_NS + seconds * 1000000000L) finish();
        }
        last = now;
    }

    private static void finish() {
        running = false;
        if (n < 10) return;
        float[] t = Arrays.copyOf(times, n);
        double sum = 0;
        for (float f : t) sum += f;
        Arrays.sort(t);
        int k = Math.max(1, n / 100);
        double worst1 = 0;
        for (int i = n - k; i < n; i++) worst1 += t[i];
        worst1 /= k;
        Result r = new Result();
        r.avgFps = Math.round(n / (sum / 1000.0) * 10) / 10.0;
        r.low1Fps = Math.round(1000.0 / worst1 * 10) / 10.0;
        if (n >= 1000) {
            int k01 = Math.max(1, n / 1000);
            double w01 = 0;
            for (int i = n - k01; i < n; i++) w01 += t[i];
            r.low01Fps = Math.round(1000.0 / (w01 / k01) * 10) / 10.0;
        }
        r.avgMs = Math.round(sum / n * 100) / 100.0;
        r.worstMs = Math.round(t[n - 1] * 10) / 10.0;
        r.frames = n;
        r.seconds = seconds;
        Minecraft mc = Minecraft.getMinecraft();
        r.renderDistance = mc.gameSettings.renderDistanceChunks;
        r.graphics = mc.gameSettings.fancyGraphics ? "Fancy" : "Fast";
        r.version = Mod1227.VERSION;
        if (samples > 0) {
            r.memoryMb = (int) Math.round(sumMem / samples);
            r.entities = (int) Math.round(sumEnt / samples);
            r.particles = (int) Math.round(sumPart / samples);
        }
        r.label = (comparePhase == 1 ? "A: " : comparePhase == 2 ? "B: " : "") + PerformanceModule.INSTANCE.activePreset.get();
        r.date = new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        List<Result> all = results();
        all.add(0, r);
        while (all.size() > 30) all.remove(all.size() - 1);
        save(all);
        if (comparePhase == 1) {
            compareA = r;
            comparePhase = 2;
            PerfPresets.apply(compareWith);
            start(seconds);
            return;
        }
        if (comparePhase == 2) {
            comparePhase = 0;
            if (compareBackup != null) {
                net.m1227.core.ConfigManager.importFrom(compareBackup);
                compareBackup.delete();
                compareBackup = null;
            }
            Result a = compareA;
            double dAvg = r.avgFps - a.avgFps, dLow = r.low1Fps - a.low1Fps;
            Notifier.post(Notifier.Kind.SYSTEM, "Compare: " + compareWith, String.format("avg %+.1f FPS, 1%% low %+.1f FPS (B vs A)", dAvg, dLow),
                    Notifier.INFO, 8000);
            return;
        }
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", r.avgFps + " FPS avg, " + r.low1Fps + " 1% low"
                + (r.low01Fps > 0 ? ", " + r.low01Fps + " 0.1% low" : ""), Notifier.GOOD, 5000);
    }

    /**
     * Measures the current settings (A), then the preset chosen in Performance > "Compare with"
     * (B), 20 s each, then restores your settings.
     */
    public static void compareStart() {
        if (Minecraft.getMinecraft().theWorld == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Join a world first", Notifier.WARN, 1800);
            return;
        }
        try {
            compareBackup = java.io.File.createTempFile("1227-compare", ".json");
            compareBackup.deleteOnExit();
            net.m1227.core.ConfigManager.exportTo(compareBackup);
        } catch (Exception e) {
            compareBackup = null;
        }
        compareWith = PerformanceModule.INSTANCE.compareWith.get();
        comparePhase = 1;
        start(20);
    }

    private static File file() {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "benchmarks.json");
    }

    private static List<Result> cache;
    private static long cacheStamp = -1;

    /** Saved results (cached; re-read only when the file changes). */
    public static List<Result> results() {
        File cf = file();
        long stamp = cf.isFile() ? cf.lastModified() ^ cf.length() : 0;
        if (cache != null && stamp == cacheStamp) return new ArrayList<Result>(cache);
        cache = read();
        cacheStamp = stamp;
        return new ArrayList<Result>(cache);
    }

    private static List<Result> read() {
        List<Result> out = new ArrayList<Result>();
        try {
            File f = file();
            if (!f.isFile()) return out;
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (JsonElement e : root.getAsJsonArray()) out.add(GSON.fromJson(e, Result.class));
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not read benchmarks.json", e);
        }
        return out;
    }

    private static void save(List<Result> all) {
        try {
            JsonArray a = new JsonArray();
            for (Result r : all) a.add(GSON.toJsonTree(r));
            Files.write(file().toPath(), GSON.toJson(a).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not save benchmarks.json", e);
        }
    }

    public static void clear() {
        save(new ArrayList<Result>());
    }

    /** Progress pill while measuring. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (!running || e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        long el = System.nanoTime() - start;
        boolean warm = el < WARMUP_NS;
        float f = warm ? 0f : Math.min(1f, (el - WARMUP_NS) / (seconds * 1e9f));
        String s = warm ? "Benchmark: warming up" : "Benchmark: " + (int) (f * 100) + "%";
        int w = e.resolution.getScaledWidth();
        float tw = Gfx.textWidth(s) + 16;
        Gfx.roundedRect(w / 2f - tw / 2f, 6, tw, 16, 8, 0xD0101218);
        Gfx.roundedRect(w / 2f - tw / 2f, 20, tw * f, 2, 1, 0xFF000000 | net.m1227.module.ClientModule.accent());
        Gfx.text(s, w / 2f - tw / 2f + 8, 10, 0xFFFFFFFF);
    }
}
