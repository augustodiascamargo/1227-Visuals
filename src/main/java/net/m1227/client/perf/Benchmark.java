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
 * In-game benchmark: measures every frame for N seconds (after 2 s of warm-up) and saves the
 * average FPS, the 1% low and the worst frame with the preset and render distance in use, in
 * config/1227/benchmarks.json. Run it before and after changing a preset, in the same spot, to
 * compare. Nothing is estimated: the numbers are measured frame times.
 */
public final class Benchmark {
    public static final Benchmark INSTANCE = new Benchmark();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int WARMUP_NS = 2000000000;

    public static final class Result {
        public String label, date;
        public double avgFps, low1Fps, worstMs;
        public int renderDistance, seconds, frames;
    }

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
        running = true;
        Minecraft.getMinecraft().displayGuiScreen(null);
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Measuring for " + secs + " s, keep still", Notifier.INFO, 2200);
    }

    static void frame() {
        if (!running) return;
        long now = System.nanoTime();
        if (Minecraft.getMinecraft().theWorld == null) {
            running = false;
            Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", "Stopped (left the world)", Notifier.WARN, 1800);
            return;
        }
        if (now - start > WARMUP_NS) {
            if (last != 0L && n < times.length) times[n++] = (now - last) / 1e6f;
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
        r.worstMs = Math.round(t[n - 1] * 10) / 10.0;
        r.frames = n;
        r.seconds = seconds;
        r.renderDistance = Minecraft.getMinecraft().gameSettings.renderDistanceChunks;
        r.label = PerformanceModule.INSTANCE.activePreset.get();
        r.date = new java.text.SimpleDateFormat("dd/MM HH:mm").format(new java.util.Date());
        List<Result> all = results();
        all.add(0, r);
        while (all.size() > 30) all.remove(all.size() - 1);
        save(all);
        Notifier.post(Notifier.Kind.SYSTEM, "Benchmark", r.avgFps + " FPS avg, " + r.low1Fps + " 1% low", Notifier.GOOD, 5000);
    }

    private static File file() {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "benchmarks.json");
    }

    public static List<Result> results() {
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
