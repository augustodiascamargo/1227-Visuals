package net.m1227.client.perf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.hud.HudModule;
import net.m1227.module.PerformanceModule;
import net.m1227.net.LatencyMonitor;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;

/**
 * 1227 Performance Analyzer: looks at what the monitors measured and lists possible problems,
 * each with the numbers it is based on. Some come with a fix, shown as a change list before it is
 * applied and undoable afterwards; none is applied by itself. Rules need enough data (at least
 * 300 frames in the last 10 s, a few network samples) and say "collecting" until then.
 */
public final class PerfAnalyzer {
    private PerfAnalyzer() {}

    public static final int INFO = 0, WARN = 1, BAD = 2;

    public static final class Finding {
        public final String id, title, detail;
        public final int severity;
        /** Null when there is nothing safe to change automatically. */
        public final ChangeSet fix;

        Finding(String id, int severity, String title, String detail, ChangeSet fix) {
            this.id = id;
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.fix = fix;
        }
    }

    private static final Set<String> IGNORED = new HashSet<String>();
    /** Ids of the findings that have a fix (for "restore" after the finding went away). */
    public static final String[] FIX_IDS = {"pacing", "frametime", "fx", "vanillafx", "hud", "world", "rd", "combat", "blur", "vsync"};

    public static void ignore(String id) {
        IGNORED.add(id);
    }

    public static void unignoreAll() {
        IGNORED.clear();
    }

    public static String undoKey(String id) {
        return "analyzer-" + id;
    }

    private static boolean on(String module) {
        Module m = ModuleManager.byName(module);
        return m != null && m.isEnabled();
    }

    public static List<Finding> run() {
        List<Finding> out = new ArrayList<Finding>();
        Minecraft mc = Minecraft.getMinecraft();
        FrameStats f = FrameStats.INSTANCE;
        PerformanceModule p = PerformanceModule.INSTANCE;
        boolean frames = f.frames10s >= 300;

        if (frames && f.fps > 60 && !Float.isNaN(f.low1) && f.low1 < f.fps * 0.5f) {
            ChangeSet c = new ChangeSet(undoKey("pacing"), "Steadier frame pacing");
            c.module("Low Latency Mode", true).setting("Low Latency Mode", "FPS cap", "Stable frame time");
            c.notes.add("Then use Low Latency Mode > \"Measure stable frame time\" in a normal scene.");
            out.add(new Finding("pacing", WARN, "High FPS, low 1% low",
                    String.format("Average %.0f FPS but 1%% low %.0f FPS: frames come unevenly (stutter or frame pacing). "
                            + "A cap a little below what your PC holds steadies them.", f.fps, f.low1), c));
        }
        if (frames && f.p95 > 25f) {
            int rd = mc.gameSettings.renderDistanceChunks;
            ChangeSet c = null;
            if (rd > 6) {
                c = new ChangeSet(undoKey("frametime"), "Shorter render distance");
                c.mc("renderDistance", Math.max(6, rd - 2));
            }
            out.add(new Finding("frametime", BAD, "Slow frames",
                    String.format("95%% of frames take up to %.1f ms (%.0f FPS or less).%s", f.p95, 1000f / f.p95,
                            rd > 6 ? " Render distance " + rd + " -> " + Math.max(6, rd - 2) + " chunks would lighten every frame." : ""), c));
        }
        int fx = ParticleSystem.INSTANCE.alive(), fxCap = p.maxParticles.i();
        if (fx > fxCap * 0.8f || ParticleSystem.refusedCosmetic > 2000) {
            ChangeSet c = new ChangeSet(undoKey("fx"), "Fewer cosmetic particles");
            c.module("PvP Visual Optimization", true).setting("PvP Visual Optimization", "Dynamic quality", true);
            out.add(new Finding("fx", WARN, "Many 1227 particles",
                    fx + " of " + fxCap + " effect particles in use. Consider lowering the cosmetic particle limit, "
                            + "or let Dynamic Quality lower cosmetics when frames get slow.", c));
        }
        int van = PerfSystem.vanillaParticles;
        if (van > 2000 && p.particleCap.get() > 1500) {
            ChangeSet c = new ChangeSet(undoKey("vanillafx"), "Minecraft particle limit");
            c.setting("Performance", "Minecraft particle limit", 1500.0);
            out.add(new Finding("vanillafx", WARN, "Many Minecraft particles", van + " Minecraft particles alive right now.", c));
        }
        // HUD cost
        HudModule heavy = null;
        float total = 0f;
        for (Module m : ModuleManager.all()) {
            if (!(m instanceof HudModule) || !m.isEnabled()) continue;
            HudModule h = (HudModule) m;
            total += h.costMs;
            if (heavy == null || h.costMs > heavy.costMs) heavy = h;
        }
        if (heavy != null && (heavy.costMs > 0.6f || total > 2.5f)) {
            ChangeSet c = new ChangeSet(undoKey("hud"), "Heaviest HUD element off");
            c.module(heavy.name, false);
            out.add(new Finding("hud", WARN, "Heavy HUD",
                    String.format("HUD elements take %.2f ms per frame; the heaviest is %s (%.2f ms). "
                            + "Liquid Glass backgrounds cost the most; \"Solid\" is the cheapest.", total, heavy.name, heavy.costMs), c));
        }
        if (frames && f.worldMs > 3f) {
            ChangeSet c = new ChangeSet(undoKey("world"), "Competitive Mode");
            c.module("Competitive Mode", true);
            out.add(new Finding("world", WARN, "Heavy world effects",
                    String.format("World effects (all mods, mostly 1227) take %.1f ms per frame. Competitive Mode turns the "
                            + "cosmetic ones off for a match and restores them after.", f.worldMs), c));
        }
        if (frames && mc.gameSettings.renderDistanceChunks >= 12 && f.fps < 90) {
            ChangeSet c = new ChangeSet(undoKey("rd"), "Render distance 10");
            c.mc("renderDistance", 10);
            out.add(new Finding("rd", INFO, "High render distance for this PC",
                    mc.gameSettings.renderDistanceChunks + " chunks at " + Math.round(f.fps) + " FPS. In PvP, 8 to 10 chunks is usually plenty.", c));
        }
        Runtime rt = Runtime.getRuntime();
        double used = (rt.totalMemory() - rt.freeMemory()) / (double) rt.maxMemory();
        if (used > 0.85) {
            out.add(new Finding("mem", WARN, "Memory almost full",
                    String.format("%.0f%% of the %d MB Java may use. Full memory makes the garbage collector pause the game. "
                            + "Give Minecraft more memory in your launcher, or lower the render distance. "
                            + "1227 does not change Java settings.", used * 100, rt.maxMemory() / 1048576), null));
        }
        if (f.combatSpikes >= 5) {
            ChangeSet c = new ChangeSet(undoKey("combat"), "Competitive Mode");
            c.module("Competitive Mode", true);
            out.add(new Finding("combat", WARN, "Stutter during combat",
                    f.combatSpikes + " frame spikes while fighting this session.", c));
        }
        if (on("Motion Blur") && frames && f.fps < 100) {
            ChangeSet c = new ChangeSet(undoKey("blur"), "Motion Blur off");
            c.module("Motion Blur", false);
            out.add(new Finding("blur", INFO, "Motion Blur on a busy PC", "Motion Blur adds a full-screen pass every frame.", c));
        }
        if (mc.gameSettings.enableVsync && frames) {
            ChangeSet c = new ChangeSet(undoKey("vsync"), "VSync off (Low Latency Mode)");
            c.module("Low Latency Mode", true).setting("Low Latency Mode", "VSync", "Off");
            out.add(new Finding("vsync", INFO, "VSync is on",
                    "VSync stops tearing but can hold each frame up to one refresh. Some players prefer it off with an FPS cap.", c));
        }
        // network
        LatencyMonitor lm = LatencyMonitor.INSTANCE;
        net.m1227.module.LatencyMonitorModule lmm = net.m1227.module.LatencyMonitorModule.INSTANCE;
        float hp = lmm == null ? 150 : lmm.highPing.f(), hj = lmm == null ? 20 : lmm.highJitter.f();
        float ping = lm.rtt.size() >= 5 ? lm.rttP95 : lm.tabPing;
        if (lm.connected && ping > hp) {
            out.add(new Finding("ping", WARN, "High ping",
                    String.format("%s %.0f ms. A client mod cannot lower it; a wired connection or a closer server can.",
                            lm.rtt.size() >= 5 ? "Measured round trip (P95)" : "Tab list ping (server-reported)", ping), null));
        }
        double j = lm.rttJitter.value();
        if (lm.connected && !Double.isNaN(j) && j > hj) {
            out.add(new Finding("jitter", WARN, "Unstable ping",
                    String.format("Average round trip %.0f ms, but it varies by about %.0f ms (jitter) with %d spikes. "
                            + "Check Wi-Fi, other downloads or the route to the server.", lm.rttAvg, j, lm.spikes), null));
        }
        if (lm.connected && lm.stalls > 0) {
            out.add(new Finding("stall", lm.longestStallS > 5 ? BAD : WARN, "Connection stalls",
                    String.format("%d moments with nothing from the server for 2 s or more (longest %.1f s).", lm.stalls, lm.longestStallS), null));
        }
        if (lm.connected && !Float.isNaN(lm.tps) && lm.tps < 18f) {
            out.add(new Finding("tps", INFO, "Server running slow (estimate)",
                    String.format("The server's world clock advances at about %.1f ticks per second (20 is normal). "
                            + "Lag from this is on the server, not your PC.", lm.tps), null));
        }
        if (lm.setbacks >= 10) {
            out.add(new Finding("setback", INFO, "Position corrections",
                    "The server moved you back " + lm.setbacks + " times this session (lag, or the server's anticheat).", null));
        }
        List<Finding> shown = new ArrayList<Finding>();
        for (Finding x : out) if (!IGNORED.contains(x.id)) shown.add(x);
        return shown;
    }

    /** True while there is not enough data for the frame rules. */
    public static boolean collecting() {
        return FrameStats.INSTANCE.frames10s < 300;
    }
}
