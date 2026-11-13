package net.m1227.client.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.module.PerformanceModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * In-game benchmark: measures every frame for N seconds after a warm-up and saves, in
 * config/1227/benchmarks.json:
 * <ul>
 * <li>average FPS (frames / time) and average frame time;</li>
 * <li>1% low and 0.1% low (the average FPS of the slowest 1% / 0.1% of frames: how stable it
 * is, not how fast; 0.1% only when there are at least 1000 frames);</li>
 * <li>frame time P50, P95, P99 and the worst frame;</li>
 * <li>memory in use, entities, Minecraft particles and 1227 particles (averaged once per second),
 * how many 1227 visual modules were on, the preset, the resolution, render distance, VSync, the
 * FPS cap in use, Low Latency Mode and the 1227 version;</li>
 * <li>a frame time graph (the slowest frame of each of 240 slices of the run);</li>
 * <li>whether the scene changed: moving more than 1.5 blocks or turning more than 15 degrees
 * during the run marks the result, because it is then not comparable.</li>
 * </ul>
 * Warm-up: 3 s, or 6 s right after a preset was applied (chunks reload when the render distance
 * changes). "Compare" measures the current settings (A), then a chosen preset (B), the same
 * length, in the same spot, then puts every value back (Minecraft video options included).
 * Nothing is estimated. A result from an empty scene says nothing about a crowded match.
 */
public final class Benchmark {
    public static final Benchmark INSTANCE = new Benchmark();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String COMPARE_KEY = "benchmark-compare";

    public static final class Result {
        public String label, date, version, graphics;
        public double avgFps, low1Fps, low01Fps, avgMs, worstMs;
        public int renderDistance, seconds, frames, memoryMb, entities, particles;
        // 1.21
        public double p50Ms, p95Ms, p99Ms;
        public int vanillaParticles, effectParticles, visualModules, fpsCap;
        public String resolution, vsync, lowLatency, compareId;
        public boolean sceneChanged;
        public float[] graph;
    }

    private static long sampleAt;
    private static double sumMem, sumEnt, sumVan, sumFx;
    private static int samples;
    // compare: phase 0 none, 1 measuring A, 2 measuring B
    private static int comparePhase;
    private static String compareWith, compareId;
    private static Result compareA;

    private static boolean running;
    private static long start, last, warmupNs;
    private static int seconds;
    private static float[] times = new float[1 << 16];
    private static int n;
    private static boolean overflow;
    // scene lock
    private static double sx, sy, sz;
    private static float syaw, spitch;
    private static boolean moved;
    private static double ax = Double.NaN, ay, az;

    private Benchmark() {}

    public static boolean running() {
        return running;
    }

    public static void start(int secs) {
        start(secs, 3_000_000_000L);
    }

    private static void start(int secs, long warmup) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Join a world first", Notifier.WARN, 1800);
            return;
        }
        seconds = secs;
        warmupNs = warmup;
        start = System.nanoTime();
        last = 0L;
        n = 0;
        overflow = false;
        sumMem = sumEnt = sumVan = sumFx = 0;
        samples = 0;
        sampleAt = 0L;
        moved = false;
        sx = mc.thePlayer.posX;
        sy = mc.thePlayer.posY;
        sz = mc.thePlayer.posZ;
        syaw = mc.thePlayer.rotationYaw;
        spitch = mc.thePlayer.rotationPitch;
        running = true;
        mc.displayGuiScreen(null);
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Measuring for " + secs + " s. Do not move or turn", Notifier.INFO, 2400);
    }

    private static int vanillaParticleCount(Minecraft mc) {
        try {
            return Integer.parseInt(mc.effectRenderer.getStatistics().trim());
        } catch (RuntimeException ignored) {
            return -1; // unknown format (another mod changed it)
        }
    }

    static void frame() {
        if (!running) return;
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            running = false;
            if (comparePhase != 0) ChangeSet.restore(COMPARE_KEY);
            comparePhase = 0;
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Stopped (left the world)", Notifier.WARN, 1800);
            return;
        }
        if (now - start > warmupNs) {
            if (last != 0L) {
                if (n == times.length) {
                    if (times.length < (1 << 20)) times = Arrays.copyOf(times, times.length * 2);
                    else overflow = true;
                }
                if (n < times.length) times[n++] = (now - last) / 1e6f;
            }
            if (now - sampleAt > 1_000_000_000L) {
                sampleAt = now;
                Runtime rt = Runtime.getRuntime();
                sumMem += (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
                sumEnt += mc.theWorld.loadedEntityList.size();
                sumVan += Math.max(0, vanillaParticleCount(mc));
                sumFx += net.m1227.vfx.ParticleSystem.INSTANCE.alive();
                samples++;
                double d = Math.sqrt(sq(mc.thePlayer.posX - sx) + sq(mc.thePlayer.posY - sy) + sq(mc.thePlayer.posZ - sz));
                float yaw = Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - syaw));
                if (d > 1.5 || yaw > 15f || Math.abs(mc.thePlayer.rotationPitch - spitch) > 15f) moved = true;
            }
            if (now - start > warmupNs + seconds * 1000000000L) finish();
        }
        last = now;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static int visualModules() {
        int c = 0;
        for (Module m : ModuleManager.all()) {
            if (!m.toggleable() || !m.isEnabled()) continue;
            String g = m.category.group;
            if ("VISUALS".equals(g) || m.category == Category.CAMERA || m.category == Category.MUSIC) c++;
        }
        return c;
    }

    private static void finish() {
        running = false;
        if (n < 10) return;
        float[] t = Arrays.copyOf(times, n);
        double sum = 0;
        for (float f : t) sum += f;
        Result r = new Result();
        // graph: worst frame per slice, in time order
        int slices = Math.min(240, n);
        r.graph = new float[slices];
        for (int i = 0; i < n; i++) {
            int k = (int) ((long) i * slices / n);
            r.graph[k] = Math.max(r.graph[k], Math.round(t[i] * 10f) / 10f);
        }
        Arrays.sort(t);
        r.avgFps = round1(n / (sum / 1000.0));
        r.low1Fps = round1(Stats.lowFps(t, n, 1));
        if (n >= 1000) r.low01Fps = round1(Stats.lowFps(t, n, 0.1));
        r.avgMs = Math.round(sum / n * 100) / 100.0;
        r.p50Ms = round2(Stats.sortedPercentile(t, n, 50));
        r.p95Ms = round2(Stats.sortedPercentile(t, n, 95));
        r.p99Ms = round2(Stats.sortedPercentile(t, n, 99));
        r.worstMs = round1(t[n - 1]);
        r.frames = n;
        r.seconds = seconds;
        Minecraft mc = Minecraft.getMinecraft();
        r.renderDistance = mc.gameSettings.renderDistanceChunks;
        r.graphics = mc.gameSettings.fancyGraphics ? "Fancy" : "Fast";
        r.resolution = mc.displayWidth + "x" + mc.displayHeight;
        r.vsync = mc.gameSettings.enableVsync ? "On" : "Off";
        r.fpsCap = mc.gameSettings.limitFramerate;
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        r.lowLatency = ll == null ? "off" : ll.describe();
        r.version = Mod1227.VERSION;
        r.visualModules = visualModules();
        r.sceneChanged = moved;
        if (samples > 0) {
            r.memoryMb = (int) Math.round(sumMem / samples);
            r.entities = (int) Math.round(sumEnt / samples);
            r.vanillaParticles = (int) Math.round(sumVan / samples);
            r.effectParticles = (int) Math.round(sumFx / samples);
            r.particles = r.vanillaParticles + r.effectParticles;
        }
        r.label = (comparePhase == 1 ? "A: " : comparePhase == 2 ? "B: " : "") + PerformanceModule.INSTANCE.activePreset.get();
        r.compareId = comparePhase != 0 ? compareId : null;
        r.date = new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        List<Result> all = results();
        all.add(0, r);
        while (all.size() > 30) all.remove(all.size() - 1);
        save(all);
        if (overflow) Mod1227.LOGGER.warn("1227 benchmark: more than 1M frames, the rest were not recorded");
        if (comparePhase == 1) {
            compareA = r;
            comparePhase = 2;
            ax = mc.thePlayer.posX;
            ay = mc.thePlayer.posY;
            az = mc.thePlayer.posZ;
            ChangeSet c = PerfPresets.changeSet(compareWith, COMPARE_KEY);
            c.apply();
            start(seconds, 6_000_000_000L); // chunks may reload after a render distance change
            return;
        }
        if (comparePhase == 2) {
            comparePhase = 0;
            ChangeSet.restore(COMPARE_KEY);
            Result a = compareA;
            boolean sameSpot = !Double.isNaN(ax) && Math.sqrt(sq(mc.thePlayer.posX - ax) + sq(mc.thePlayer.posY - ay) + sq(mc.thePlayer.posZ - az)) < 1.5;
            double dAvg = r.avgFps - a.avgFps, dLow = r.low1Fps - a.low1Fps;
            String warn = (a.sceneChanged || r.sceneChanged || !sameSpot) ? "  (scene changed: not comparable)" : "";
            Notifier.post(Notifier.Kind.SYSTEM, "Compare: " + compareWith, String.format("avg %+.1f FPS, 1%% low %+.1f FPS (B vs A)", dAvg, dLow) + warn,
                    Notifier.INFO, 8000);
            return;
        }
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", r.avgFps + " FPS avg, " + r.low1Fps + " 1% low"
                + (r.low01Fps > 0 ? ", " + r.low01Fps + " 0.1% low" : "") + (moved ? " (you moved)" : ""), Notifier.GOOD, 5000);
    }

    private static double round1(double v) {
        return Double.isNaN(v) ? 0 : Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Double.isNaN(v) ? 0 : Math.round(v * 100) / 100.0;
    }

    /**
     * Measures the current settings (A), then the preset chosen in Performance > "Compare with"
     * (B), 20 s each, then restores every value it changed.
     */
    public static void compareStart() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Join a world first", Notifier.WARN, 1800);
            return;
        }
        // a compare interrupted by a crash or a quit left its undo file: put those values back first
        ChangeSet.restore(COMPARE_KEY);
        compareWith = PerformanceModule.INSTANCE.compareWith.get();
        compareId = Long.toHexString(System.currentTimeMillis());
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
        boolean warm = el < warmupNs;
        float f = warm ? 0f : Math.min(1f, (el - warmupNs) / (seconds * 1e9f));
        String s = (comparePhase == 1 ? "A " : comparePhase == 2 ? "B " : "") + (warm ? "Benchmark: warming up" : "Benchmark: " + (int) (f * 100) + "%");
        int w = e.resolution.getScaledWidth();
        float tw = Gfx.textWidth(s) + 16;
        Gfx.roundedRect(w / 2f - tw / 2f, 6, tw, 16, 8, 0xD0101218);
        Gfx.roundedRect(w / 2f - tw / 2f, 20, tw * f, 2, 1, 0xFF000000 | net.m1227.module.ClientModule.accent());
        Gfx.text(s, w / 2f - tw / 2f + 8, 10, 0xFFFFFFFF);
    }
}
