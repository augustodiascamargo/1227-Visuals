package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.perf.Benchmark;
import net.m1227.client.perf.ChangeSet;
import net.m1227.client.perf.DynamicQuality;
import net.m1227.client.perf.FrameStats;
import net.m1227.client.perf.InputMonitor;
import net.m1227.client.perf.Occlusion;
import net.m1227.client.perf.PerfAnalyzer;
import net.m1227.client.perf.PerfPresets;
import net.m1227.client.perf.PerfSystem;
import net.m1227.client.perf.Stats;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.combat.CombatLog;
import net.m1227.combat.HitEvidence;
import net.m1227.core.CombatTracker;
import net.m1227.core.InputManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.hud.HudModule;
import net.m1227.module.PerformanceModule;
import net.m1227.net.LatencyMonitor;
import net.m1227.net.NetObserver;
import net.m1227.ui.Gfx;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 1227 Performance Dashboard: Overview (frames, cost, particles), Network (Latency Monitor and
 * graph), Combat (Hit Registration Monitor, Combat Timeline, Hit Timing, W-Tap), Input (Input
 * Monitor), Benchmark (results, graphs, A/B) and Analyzer (Performance Analyzer).
 *
 * Every value carries a tag: MEASURED (measured here), ESTIMATE (worked out from measurements),
 * SERVER (a number the server sent) or N/A (nothing reliable to show).
 */
public final class PerfDashboardScreen extends UiScreen {
    private static final String[] TABS = {"Overview", "Network", "Combat", "Input", "Benchmark", "Analyzer"};
    private static final int MEASURED = 0, ESTIMATE = 1, SERVER = 2, NA = 3;
    private static int lastTab;
    private int tab;
    private int netRange = 1; // 0 = 30 s, 1 = 60 s, 2 = 5 min
    private int selected;
    private final float[] buf = new float[600];
    private final float[] buf2 = new float[600];
    private List<PerfAnalyzer.Finding> findings;
    private long findingsAt;

    private PerfDashboardScreen(GuiScreen parent, int tab) {
        super(parent);
        this.tab = tab;
    }

    public static void open(int tab) {
        lastTab = tab < 0 ? lastTab : tab;
        InputManager.INSTANCE.requestScreen(new PerfDashboardScreen(Minecraft.getMinecraft().currentScreen, lastTab));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // the monitors keep measuring while you look at them
    }

    // ------------------------------------------------------------------ helpers

    private static int tagColor(int kind) {
        return kind == MEASURED ? DS.GOOD : kind == ESTIMATE ? DS.WARN : kind == SERVER ? 0xFF4DA3FF : DS.FAINT;
    }

    private static String tagText(int kind) {
        return kind == MEASURED ? "MEASURED" : kind == ESTIMATE ? "ESTIMATE" : kind == SERVER ? "SERVER" : "N/A";
    }

    /** label ............ value [TAG] */
    private void row(String label, String value, int kind, float x, float y, float w) {
        Ui.text(label, x, y, DS.T_SMALL, DS.MUTED);
        String t = tagText(kind);
        float tw = Ui.width(t, 0.62f) + 6;
        Gfx.roundedRect(x + w - tw, y - 1, tw, 9, 2.5f, Gfx.alpha(tagColor(kind), 0.18f));
        Ui.text(t, x + w - tw + 3, y + 0.8f, 0.62f, tagColor(kind));
        Ui.textRight(kind == NA ? "N/A" : value, x + w - tw - 6, y, DS.T_SMALL, kind == NA ? DS.FAINT : DS.TEXT);
    }

    private static String ms(double v) {
        return Double.isNaN(v) ? "-" : v < 10 ? String.format("%.2f ms", v) : String.format("%.1f ms", v);
    }

    private static String f0(double v) {
        return Double.isNaN(v) ? "-" : String.valueOf(Math.round(v));
    }

    /** Line graph of n values (oldest first) in a box, with optional guide lines. */
    private static void graph(float[] v, int n, float x, float y, float w, float h, float max, int color, float... guides) {
        Gfx.roundedRect(x, y, w, h, DS.R_SM, DS.SURFACE_2);
        for (float g : guides) {
            if (g >= max) continue;
            float gy = y + h - g / max * h;
            Gfx.rect(x + 2, gy, w - 4, 0.5f, DS.LINE_STRONG);
            Ui.text(Math.round(g) + "", x + 3, gy - 7, 0.6f, DS.FAINT);
        }
        if (n < 2) {
            Ui.textCenter("no data yet", x + w / 2f, y + h / 2f - 3, DS.T_SMALL, DS.FAINT);
            return;
        }
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            float gx = x + 2 + (w - 4) * i / (float) (n - 1);
            float gy = y + h - 2 - Math.min(1f, v[i] / max) * (h - 4);
            if (i > 0) Gfx.line(px, py, gx, gy, 0.8f, color);
            px = gx;
            py = gy;
        }
    }

    private void section(String title, float x, float y) {
        Ui.caption(title, x, y);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float pw = Math.min(680, w - 16), px = (w - pw) / 2f, top = 10;
        Ui.textBold("Performance Dashboard", px, top, DS.T_TITLE, DS.TEXT);
        Ui.textRight("green = measured  ·  amber = estimate  ·  blue = sent by the server  ·  grey = not available",
                px + pw, top + 5, 0.7f, DS.FAINT);
        float tx = px, ty = top + 22;
        for (int i = 0; i < TABS.length; i++) {
            final int k = i;
            float bw = Ui.width(TABS[i], DS.T_BODY) + 20;
            ui.button("tab" + i, TABS[i], tx, ty, bw, 18, tab == i ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> {
                tab = k;
                lastTab = k;
            });
            tx += bw + 4;
        }
        ui.iconButton("back", px + pw - 20, ty - 1, 20, Glyph.BACK, "Back", true, this::back);
        float cy = ty + 24, ch = h - cy - 8;
        Ui.card(px, cy, pw, ch);
        float off = ui.beginScroll("dash" + tab, px + 4, cy + 4, pw - 8, ch - 8);
        float used;
        switch (tab) {
            case 1: used = network(px + 12, cy + 10 - off, pw - 24); break;
            case 2: used = combat(px + 12, cy + 10 - off, pw - 24); break;
            case 3: used = input(px + 12, cy + 10 - off, pw - 24); break;
            case 4: used = bench(px + 12, cy + 10 - off, pw - 24); break;
            case 5: used = analyzer(px + 12, cy + 10 - off, pw - 24); break;
            default: used = overview(px + 12, cy + 10 - off, pw - 24);
        }
        ui.endScroll(used + 20);
    }

    // ------------------------------------------------------------------ overview

    private float overview(float x, float y0, float w) {
        FrameStats f = FrameStats.INSTANCE;
        float y = y0, col = (w - 16) / 2f;
        section("FRAMES (LAST 10 S)", x, y);
        y += 13;
        float yy = y;
        row("FPS (last second)", f0(f.fps), MEASURED, x, yy, col);
        row("Frame time, average", ms(f.avgMs), MEASURED, x, yy += 11, col);
        row("Frame time P50 / P95", ms(f.p50) + " / " + ms(f.p95), MEASURED, x, yy += 11, col);
        row("Frame time P99 / worst", ms(f.p99) + " / " + ms(f.maxMs), MEASURED, x, yy += 11, col);
        row("1% low", Float.isNaN(f.low1) ? "-" : f0(f.low1) + " FPS", Float.isNaN(f.low1) ? NA : MEASURED, x, yy += 11, col);
        row("Spikes (all / in combat)", f.spikes + " / " + f.combatSpikes, MEASURED, x, yy += 11, col);
        float x2 = x + col + 16;
        yy = y;
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576, maxMb = rt.maxMemory() / 1048576;
        row("Memory", usedMb + " / " + maxMb + " MB", MEASURED, x2, yy, col);
        row("World effects (all mods)", ms(f.worldMs), MEASURED, x2, yy += 11, col);
        row("HUD (all mods)", ms(f.hudMs), MEASURED, x2, yy += 11, col);
        row("Client tick (Minecraft + mods)", ms(f.tickMs), MEASURED, x2, yy += 11, col);
        row("Occlusion tests last frame", String.valueOf(Occlusion.lastFrameTests), MEASURED, x2, yy += 11, col);
        row("Dynamic quality (cosmetics)", Math.round(DynamicQuality.factor() * 100) + "%", MEASURED, x2, yy += 11, col);
        y = yy + 16;
        section("FRAME TIME, LAST " + Math.min(600, f.frames10s) + " FRAMES (ms)", x, y);
        y += 12;
        int n = f.recent(buf);
        float mx = 0;
        for (int i = 0; i < n; i++) mx = Math.max(mx, buf[i]);
        graph(buf, n, x, y, w, 60, Math.max(20f, mx * 1.1f), DS.accent(), 6.94f, 16.7f, 33.3f);
        y += 68;
        section("FPS, LAST 5 MINUTES (ONE POINT PER SECOND)", x, y);
        y += 12;
        Stats.Series fh = f.fpsHistory;
        int m = Math.min(fh.size(), buf2.length);
        float fmx = 0;
        for (int i = 0; i < m; i++) {
            buf2[i] = fh.value(fh.size() - m + i);
            fmx = Math.max(fmx, buf2[i]);
        }
        graph(buf2, m, x, y, w, 46, Math.max(60f, fmx * 1.1f), DS.GOOD, 60f, 144f, 240f);
        y += 54;
        section("PARTICLES AND MODES", x, y);
        y += 13;
        yy = y;
        ParticleSystem ps = ParticleSystem.INSTANCE;
        row("1227 particles / limit", ps.alive() + " / " + PerformanceModule.INSTANCE.maxParticles.i(), MEASURED, x, yy, col);
        row("Refused (cosmetic / combat)", ParticleSystem.refusedCosmetic + " / " + ParticleSystem.refusedCombat, MEASURED, x, yy += 11, col);
        int van = PerfSystem.vanillaParticles;
        row("Minecraft particles", van < 0 ? "" : String.valueOf(van), van < 0 ? NA : MEASURED, x, yy += 11, col);
        yy = y;
        row("Competitive Mode", on("Competitive Mode") ? "on" : "off", MEASURED, x2, yy, col);
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        row("Low Latency Mode", ll == null ? "-" : ll.describe(), MEASURED, x2, yy += 11, col);
        row("PvP Visual Optimization", on("PvP Visual Optimization") ? "on" : "off", MEASURED, x2, yy += 11, col);
        y = yy + 16;
        section("HEAVIEST HUD ELEMENTS (ms PER FRAME)", x, y);
        y += 13;
        HudModule[] top = new HudModule[5];
        for (Module mm : ModuleManager.all()) {
            if (!(mm instanceof HudModule) || !mm.isEnabled()) continue;
            HudModule hm = (HudModule) mm;
            for (int i = 0; i < top.length; i++) {
                if (top[i] == null || hm.costMs > top[i].costMs) {
                    System.arraycopy(top, i, top, i + 1, top.length - i - 1);
                    top[i] = hm;
                    break;
                }
            }
        }
        for (HudModule hm : top) {
            if (hm == null) break;
            row(hm.name, ms(hm.costMs), MEASURED, x, y, col);
            y += 11;
        }
        return y - y0;
    }

    private static boolean on(String n) {
        Module m = ModuleManager.byName(n);
        return m != null && m.isEnabled();
    }

    // ------------------------------------------------------------------ network

    private float network(float x, float y0, float w) {
        LatencyMonitor l = LatencyMonitor.INSTANCE;
        float y = y0, col = (w - 16) / 2f, x2 = x + col + 16;
        boolean probe = l.rtt.size() > 0;
        boolean modOn = on("Latency Monitor");
        if (!l.connected) {
            Ui.text("Not connected to a server. Singleplayer has no network latency to measure.", x, y, DS.T_BODY, DS.MUTED);
            return 20;
        }
        if (!modOn && !l.local) {
            ui.button("lmon", "Turn on Latency Monitor (status pings)", x, y, 230, 18, Ui.PRIMARY, true, Glyph.BOLT, () -> {
                Module m = ModuleManager.byName("Latency Monitor");
                if (m != null) m.setEnabled(true);
            });
            y += 24;
        }
        section("OWN MEASUREMENT: STATUS PING (SEPARATE CONNECTION, SAME SERVER)", x, y);
        y += 13;
        float yy = y;
        int k = probe ? MEASURED : NA;
        row("Last round trip", ms(l.rttLast), k, x, yy, col);
        row("Best / worst", ms(l.rttMin) + " / " + ms(l.rttMax), k, x, yy += 11, col);
        row("Average / P95", ms(l.rttAvg) + " / " + ms(l.rttP95), k, x, yy += 11, col);
        double j = l.rttJitter.value();
        row("Jitter (RFC 3550)", ms(j), Double.isNaN(j) ? NA : MEASURED, x, yy += 11, col);
        row("Probes ok / failed", l.probeOk + " / " + l.probeFail + (l.probeError.isEmpty() ? "" : "  " + l.probeError), modOn ? MEASURED : NA, x, yy += 11, col);
        yy = y;
        row("Tab list ping", l.tabPing < 0 ? "" : l.tabPing + " ms", l.tabPing < 0 ? NA : SERVER, x2, yy, col);
        float age = l.tabAgeS();
        row("Tab list value age", age < 0 ? "" : Math.round(age) + " s", age < 0 ? NA : MEASURED, x2, yy += 11, col);
        double kj = l.kaJitter.value();
        row("Keep-alive delay jitter", l.kaClock ? ms(kj) : "", l.kaClock && !Double.isNaN(kj) ? MEASURED : NA, x2, yy += 11, col);
        row("Keep-alive interval", ms(l.kaInterval), Float.isNaN(l.kaInterval) ? NA : MEASURED, x2, yy += 11, col);
        row("Server tick rate", Float.isNaN(l.tps) ? "" : String.format("%.1f TPS", l.tps), Float.isNaN(l.tps) ? NA : ESTIMATE, x2, yy += 11, col);
        y = yy + 16;
        // graph
        section("LATENCY GRAPH", x, y);
        String[] rn = {"30 s", "60 s", "5 min"};
        for (int i = 0; i < 3; i++) {
            final int r = i;
            ui.button("rng" + i, rn[i], x + w - 150 + i * 50, y - 4, 46, 13, netRange == i ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> netRange = r);
        }
        y += 13;
        long now = System.nanoTime();
        long win = netRange == 0 ? 30_000_000_000L : netRange == 1 ? 60_000_000_000L : 300_000_000_000L;
        int n = l.rtt.window(now, win, buf);
        int n2 = l.tab.window(now, win, buf2);
        float mx = 50;
        for (int i = 0; i < n; i++) mx = Math.max(mx, buf[i]);
        for (int i = 0; i < n2; i++) mx = Math.max(mx, buf2[i]);
        mx *= 1.15f;
        float gh = 70;
        graph(buf2, n2, x, y, w, gh, mx, 0x904DA3FF, 50f, 100f, 200f);
        if (n >= 2) {
            float px = 0, py = 0;
            for (int i = 0; i < n; i++) {
                float gx = x + 2 + (w - 4) * i / (float) (n - 1);
                float gy = y + gh - 2 - Math.min(1f, buf[i] / mx) * (gh - 4);
                if (i > 0) Gfx.line(px, py, gx, gy, 1f, DS.GOOD);
                px = gx;
                py = gy;
            }
        }
        // events on the time axis
        for (int i = 0; i < l.events.size(); i++) {
            long t = l.events.time(i);
            if (now - t > win) continue;
            float ex = x + w - (now - t) / (float) win * w;
            int kind = l.eventKind(i);
            int c = kind == 0 ? DS.BAD : kind == 1 ? DS.WARN : kind == 2 ? DS.FAINT : 0xFFB06CFF;
            Gfx.rect(ex, y + 2, 1f, gh - 4, Gfx.alpha(c, 0.7f));
        }
        y += gh + 4;
        Ui.text("green: status ping round trip  ·  blue: tab list ping  ·  red: spike  ·  amber: stall  ·  grey: disconnect  ·  purple: probe timeout",
                x, y, 0.66f, DS.FAINT);
        y += 14;
        section("CONNECTION", x, y);
        y += 13;
        yy = y;
        long up = l.uptimeNs() / 1_000_000_000L;
        row("Connected for", up / 60 + " min " + up % 60 + " s", MEASURED, x, yy, col);
        row("Stalls (2 s+ with no data)", l.stalls + (l.stalls > 0 ? String.format("  (longest %.1f s)", l.longestStallS) : ""), MEASURED, x, yy += 11, col);
        row("Latency spikes", String.valueOf(l.spikes), probe ? MEASURED : NA, x, yy += 11, col);
        row("Disconnects this session", String.valueOf(l.disconnects), MEASURED, x, yy += 11, col);
        yy = y;
        row("Position corrections (S08)", String.valueOf(l.setbacks), MEASURED, x2, yy, col);
        NetObserver o = NetObserver.INSTANCE;
        row("Packets in / out", o.attached() ? o.inboundCount() + " / " + o.outboundCount() : "", o.attached() ? MEASURED : NA, x2, yy += 11, col);
        row("Packet loss", "", NA, x2, yy += 11, col);
        y = yy + 14;
        y = para("Packet loss is not available: Minecraft runs over TCP, which re-sends lost data by itself, so a loss reaches the game only as "
                + "extra delay (a spike or a stall). The status ping is a separate short connection doing what the multiplayer list does; it "
                + "measures the route to the server (or its proxy), not the game's own packets. The tab list ping is the server's own, "
                + "averaged number, usually updated every 30 s.", x, y, w);
        return y - y0;
    }

    private float para(String s, float x, float y, float w) {
        for (String line : Gfx.wrap(s, w / DS.T_SMALL)) {
            Ui.text(line, x, y, DS.T_SMALL, DS.FAINT);
            y += 10;
        }
        return y + 4;
    }

    // ------------------------------------------------------------------ combat

    private float combat(float x, float y0, float w) {
        float y = y0, col = (w - 16) / 2f, x2 = x + col + 16;
        section("HIT REGISTRATION MONITOR (THIS SESSION)", x, y);
        ui.button("rst", "Reset session", x + w - 90, y - 4, 90, 13, Ui.GHOST, true, -1, CombatTracker::resetSession);
        y += 13;
        float yy = y;
        row("Attack attempts", String.valueOf(CombatTracker.attempts), MEASURED, x, yy, col);
        row("Likely registered (strong)", String.valueOf(CombatTracker.strong), ESTIMATE, x, yy += 11, col);
        row("Likely registered (moderate)", String.valueOf(CombatTracker.moderate), ESTIMATE, x, yy += 11, col);
        row("Uncertain (weak evidence)", String.valueOf(CombatTracker.weak), ESTIMATE, x, yy += 11, col);
        row("Unconfirmed (no evidence)", String.valueOf(CombatTracker.none), ESTIMATE, x, yy += 11, col);
        row("Unknown", String.valueOf(CombatTracker.unknown), ESTIMATE, x, yy += 11, col);
        yy = y;
        double rate = CombatTracker.feedbackRate();
        row("Estimated Hit Feedback Rate", Double.isNaN(rate) ? "" : Math.round(rate * 100) + "%", Double.isNaN(rate) ? NA : ESTIMATE, x2, yy, col);
        row("Authoritative Hit Registration", "", NA, x2, yy += 11, col);
        row("Network monitor", NetObserver.INSTANCE.attached() ? "attached" : "not attached", MEASURED, x2, yy += 11, col);
        float win = (float) HitEvidence.windowMs(LatencyMonitor.INSTANCE.bestRttMs());
        row("Evidence window", Math.round(win) + " ms", ESTIMATE, x2, yy += 11, col);
        row("Combo now", String.valueOf(CombatTracker.combo()), ESTIMATE, x2, yy += 11, col);
        y = yy + 16;
        y = para("Strong = the server's hurt signal plus another sign (health drop, knockback or critical) and nothing else that could have "
                + "caused it. Moderate = hurt signal alone. Weak = damage seen, but another player, a projectile, fire/poison, or a signal "
                + "faster than any round trip could explain it. Unconfirmed = no sign of damage in the window (not \"rejected\"). Unknown = "
                + "target immune after the previous hit, gone, or no data. A real hit registration figure needs data from the server itself "
                + "(a server plugin); without one it is not available.", x, y, w);
        section("LAST ATTEMPTS", x, y);
        y += 13;
        int na = Math.min(8, CombatTracker.ringSize());
        if (na == 0) {
            Ui.text("No attacks yet.", x, y, DS.T_SMALL, DS.FAINT);
            y += 11;
        }
        for (int i = 0; i < na; i++) {
            CombatTracker.Attempt a = CombatTracker.attempt(i);
            int c = !a.finished ? DS.MUTED : a.level.likely() ? DS.GOOD : a.level == HitEvidence.Level.WEAK ? DS.WARN
                    : a.level == HitEvidence.Level.NONE ? DS.BAD : DS.FAINT;
            Gfx.circle(x + 3, y + 3, 2.2f, c);
            Ui.text(Gfx.trim(a.targetName, 70), x + 9, y, DS.T_SMALL, DS.TEXT);
            Ui.text(String.format("%.2f m", a.reachAttempt) + (a.reachFeedback >= 0 ? String.format(" → %.2f m", a.reachFeedback) : ""),
                    x + 84, y, DS.T_SMALL, DS.MUTED);
            String d = a.hurtAt != 0L ? "+" + (a.hurtAt - (a.sentAt != 0L ? a.sentAt : a.at)) / 1_000_000L + " ms" : "";
            Ui.text(d, x + 170, y, DS.T_SMALL, DS.MUTED);
            Ui.text(a.finished ? a.level.label + ": " + a.reason : "waiting for evidence", x + 214, y, DS.T_SMALL, c);
            y += 11;
        }
        y += 8;
        section("COMBAT TIMELINE (NEWEST FIRST)", x, y);
        y += 13;
        CombatLog log = CombatLog.INSTANCE;
        long now = System.nanoTime();
        int nt = Math.min(22, log.size());
        if (nt == 0) {
            Ui.text("Nothing observed yet.", x, y, DS.T_SMALL, DS.FAINT);
            y += 11;
        }
        for (int i = 0; i < nt; i++) {
            long ago = (now - log.time(i)) / 1_000_000L;
            Ui.text(ago < 10000 ? ago + " ms" : ago / 1000 + " s", x, y, DS.T_SMALL, DS.FAINT);
            Ui.text(log.kind(i).label, x + 52, y, DS.T_SMALL, log.color(i));
            Ui.text(Gfx.trim(log.text(i) == null ? "" : log.text(i), w - 280), x + 150, y, DS.T_SMALL, DS.MUTED);
            Ui.textRight(log.kind(i).source, x + w, y, 0.66f, DS.FAINT);
            y += 10;
        }
        y += 8;
        section("HIT TIMING ANALYZER", x, y);
        y += 13;
        int[] b = log.gapBuckets;
        int bmax = 1;
        for (int v : b) bmax = Math.max(bmax, v);
        float bw = (col - 4) / b.length;
        String[] lab = {"0", "50", "100", "150", "200", "250", "300", "350", "400", "450", "500+", "1s+"};
        for (int i = 0; i < b.length; i++) {
            float bh = 40f * b[i] / bmax;
            Gfx.roundedRect(x + i * bw, y + 40 - bh, bw - 2, Math.max(1f, bh), 1.5f, Gfx.alpha(DS.accent(), 0.85f));
            Ui.text(lab[i], x + i * bw, y + 43, 0.58f, DS.FAINT);
        }
        yy = y;
        int ng = log.attemptGaps.size();
        for (int i = 0; i < ng; i++) buf[i] = log.attemptGaps.value(i);
        int nh = log.hitGaps.size();
        for (int i = 0; i < nh; i++) buf2[i] = log.hitGaps.value(i);
        row("Between attempts, average", ms(Stats.mean(buf, ng)), ng > 0 ? MEASURED : NA, x2, yy, col);
        row("Between attempts, variation", ms(Stats.stddev(buf, ng)), ng > 1 ? MEASURED : NA, x2, yy += 11, col);
        row("Between likely hits, average", ms(Stats.mean(buf2, nh)), nh > 0 ? ESTIMATE : NA, x2, yy += 11, col);
        row("Between likely hits, variation", ms(Stats.stddev(buf2, nh)), nh > 1 ? ESTIMATE : NA, x2, yy += 11, col);
        y += 56;
        Ui.text("Attempt-to-attempt gaps (ms). The analyzer shows your rhythm; it never suggests a click rate.", x, y, 0.66f, DS.FAINT);
        y += 14;
        section("W-TAP TRAINER (FROM THE SPRINT PACKETS YOU SENT)", x, y);
        y += 13;
        int nr = Math.min(10, log.resetCount());
        if (nr == 0) {
            Ui.text("No attacks sent yet.", x, y, DS.T_SMALL, DS.FAINT);
            y += 11;
        }
        for (int i = 0; i < nr; i++) {
            CombatLog.Reset r = log.reset(i);
            Ui.text(r.grade, x, y, DS.T_SMALL, r.color);
            Ui.text(r.resetMs >= 0 ? "stop→start " + Math.round(r.resetMs) + " ms" : "no reset", x + 70, y, DS.T_SMALL, DS.MUTED);
            Ui.text(r.startToAttackMs >= 0 ? "start→attack " + Math.round(r.startToAttackMs) + " ms" : "", x + 170, y, DS.T_SMALL, DS.MUTED);
            Ui.textRight(r.sprintSent ? "sprint sent: yes" : "sprint sent: no", x + w, y, 0.66f, DS.FAINT);
            y += 10;
        }
        double c = log.resetConsistency();
        row("Reset length variation", ms(c), Double.isNaN(c) ? NA : MEASURED, x, y + 4, col);
        y += 18;
        y = para("Observed: when stop/start sprinting and the attack were sent, in that order. Inferred: whether the server counted a sprint "
                + "hit (it ends your sprint after a sprint hit that lands; only a new start sprinting turns it back on).", x, y, w);
        return y - y0;
    }

    // ------------------------------------------------------------------ input

    private float input(float x, float y0, float w) {
        float y = y0, col = (w - 16) / 2f;
        if (!on("Input Monitor")) {
            ui.button("imon", "Turn on Input Monitor", x, y, 160, 18, Ui.PRIMARY, true, Glyph.BOLT, () -> {
                Module m = ModuleManager.byName("Input Monitor");
                if (m != null) m.setEnabled(true);
            });
            y += 26;
        }
        InputMonitor im = InputMonitor.INSTANCE;
        section("CLICKS (LEFT AND RIGHT)", x, y);
        y += 13;
        int n = im.tickWait.size();
        for (int i = 0; i < n; i++) buf[i] = im.tickWait.value(i);
        int n2 = im.toFrame.size();
        for (int i = 0; i < n2; i++) buf2[i] = im.toFrame.value(i);
        float[] s = new float[128];
        row("Presses measured", String.valueOf(im.presses), MEASURED, x, y, col);
        row("Wait for the game tick, average", ms(Stats.mean(buf, n)), n > 0 ? MEASURED : NA, x, y += 11, col);
        row("Wait for the game tick, P95", ms(Stats.percentile(buf, n, 95, s)), n > 0 ? MEASURED : NA, x, y += 11, col);
        row("Input to frame, average", ms(Stats.mean(buf2, n2)), n2 > 0 ? ESTIMATE : NA, x, y += 11, col);
        row("Input to frame, P95", ms(Stats.percentile(buf2, n2, 95, s)), n2 > 0 ? ESTIMATE : NA, x, y += 11, col);
        int n3 = im.keyWait.size();
        float[] k = new float[64];
        for (int i = 0; i < n3; i++) k[i] = im.keyWait.value(i);
        row("Forward key, wait for tick (avg)", ms(Stats.mean(k, n3)), n3 > 0 ? MEASURED : NA, x, y += 11, col);
        y += 16;
        section("WAIT FOR TICK, LAST " + n + " CLICKS (ms)", x, y);
        y += 12;
        graph(buf, n, x, y, w, 50, 60f, DS.accent(), 25f, 50f);
        y += 58;
        y = para("How 1.8.9 handles a click: the window reads it at the end of a frame, then the game acts on it inside the next game tick. "
                + "Ticks run 20 times per second, so a click waits 0 to 50 ms for its tick whatever your FPS (about 25 ms on average). "
                + "1227 does not change the tick: that would change game timing. \"Input to frame\" goes from the frame that read the click "
                + "to the end of the first frame drawn after the tick handled it; it leaves out the time in the mouse/OS before the window "
                + "read it and the time the monitor takes to show the frame, so it is not input-to-photon latency.", x, y, w);
        y = para("Investigated and not done: reading the mouse again right before each frame. In LWJGL 2 each read replaces the mouse "
                + "movement of the previous one, so movement read but not yet used would be lost. A higher FPS cap, VSync off and the "
                + "options in Low Latency Mode are the safe ways to shorten the frame part.", x, y, w);
        return y - y0;
    }

    // ------------------------------------------------------------------ benchmark

    private float bench(float x, float y0, float w) {
        float y = y0;
        boolean world = mc.theWorld != null;
        ui.button("b30", "Run 30 s", x, y, 80, 18, Ui.PRIMARY, world && !Benchmark.running(), Glyph.PLAY, () -> Benchmark.start(30));
        final PerformanceModule p = PerformanceModule.INSTANCE;
        ui.cycle("cmpw", x + 88, y, 150, 18, "vs " + p.compareWith.get(), () -> p.compareWith.cycle(1), () -> p.compareWith.cycle(-1));
        ui.button("cmp", "Compare A/B (2 x 20 s)", x + 244, y, 150, 18, Ui.SECONDARY, world && !Benchmark.running(), Glyph.CHART, Benchmark::compareStart);
        ui.button("clr", "Clear", x + w - 60, y, 60, 18, Ui.GHOST, true, Glyph.TRASH, Benchmark::clear);
        y += 26;
        List<Benchmark.Result> rs = Benchmark.results();
        if (rs.isEmpty()) {
            Ui.text("No results yet. Stand still in the scene you want to test and run a benchmark.", x, y, DS.T_SMALL, DS.FAINT);
            y += 16;
        }
        if (selected >= rs.size()) selected = 0;
        // A/B pair
        if (rs.size() >= 2 && rs.get(0).compareId != null && rs.get(0).compareId.equals(rs.get(1).compareId)) {
            Benchmark.Result b = rs.get(0), a = rs.get(1);
            section("LAST COMPARISON: " + a.label + "  vs  " + b.label, x, y);
            y += 13;
            float col = (w - 16) / 2f;
            row("Average FPS", f0(a.avgFps) + " → " + f0(b.avgFps) + String.format("  (%+.0f)", b.avgFps - a.avgFps), MEASURED, x, y, col);
            row("1% low", f0(a.low1Fps) + " → " + f0(b.low1Fps) + String.format("  (%+.0f)", b.low1Fps - a.low1Fps), MEASURED, x + col + 16, y, col);
            row("P95 frame time", ms(a.p95Ms) + " → " + ms(b.p95Ms), MEASURED, x, y += 11, col);
            row("P99 frame time", ms(a.p99Ms) + " → " + ms(b.p99Ms), MEASURED, x + col + 16, y, col);
            y += 12;
            if (a.sceneChanged || b.sceneChanged) {
                Ui.text("The scene changed during this comparison (you moved or turned): the numbers are not comparable.", x, y, DS.T_SMALL, DS.WARN);
                y += 11;
            }
            y += 6;
        }
        section("RESULTS (CLICK ONE FOR ITS GRAPH)", x, y);
        y += 13;
        for (int i = 0; i < rs.size(); i++) {
            final int k = i;
            Benchmark.Result r = rs.get(i);
            boolean sel = i == selected;
            Gfx.roundedRect(x - 4, y - 3, w + 8, 14, 3, sel ? Gfx.alpha(DS.accent(), 0.18f) : 0);
            ui.click(x - 4, y - 3, w + 8, 14, () -> selected = k);
            Ui.text(r.date + "  " + r.label, x, y, DS.T_SMALL, DS.TEXT);
            Ui.text(f0(r.avgFps) + " avg  ·  " + f0(r.low1Fps) + " 1%" + (r.low01Fps > 0 ? "  ·  " + f0(r.low01Fps) + " 0.1%" : "")
                    + "  ·  P95 " + ms(r.p95Ms), x + 170, y, DS.T_SMALL, DS.MUTED);
            if (r.sceneChanged) Ui.textRight("scene changed", x + w, y, 0.66f, DS.WARN);
            y += 14;
        }
        if (!rs.isEmpty()) {
            Benchmark.Result r = rs.get(selected);
            y += 6;
            section("SELECTED: " + r.date + "  " + r.label, x, y);
            y += 12;
            if (r.graph != null) {
                float mx = 0;
                for (float v : r.graph) mx = Math.max(mx, v);
                graph(r.graph, r.graph.length, x, y, w, 56, Math.max(20f, mx * 1.1f), DS.accent(), 6.94f, 16.7f, 33.3f);
                y += 62;
                Ui.text("Slowest frame of each slice of the run (ms).", x, y, 0.66f, DS.FAINT);
                y += 12;
            }
            float col = (w - 16) / 2f, x2 = x + col + 16, yy = y;
            row("Average FPS / frame time", f0(r.avgFps) + " / " + ms(r.avgMs), MEASURED, x, yy, col);
            row("1% / 0.1% low", f0(r.low1Fps) + " / " + (r.low01Fps > 0 ? f0(r.low01Fps) : "-"), MEASURED, x, yy += 11, col);
            row("P50 / P95 / P99", ms(r.p50Ms) + " / " + ms(r.p95Ms) + " / " + ms(r.p99Ms), MEASURED, x, yy += 11, col);
            row("Worst frame / frames", ms(r.worstMs) + " / " + r.frames, MEASURED, x, yy += 11, col);
            row("Memory / entities", r.memoryMb + " MB / " + r.entities, MEASURED, x, yy += 11, col);
            yy = y;
            row("Resolution / render distance", (r.resolution == null ? "-" : r.resolution) + " / " + r.renderDistance, MEASURED, x2, yy, col);
            row("VSync / FPS cap", (r.vsync == null ? "-" : r.vsync) + " / " + (r.fpsCap >= 260 ? "unlimited" : r.fpsCap == 0 ? "-" : String.valueOf(r.fpsCap)), MEASURED, x2, yy += 11, col);
            row("Particles: Minecraft / 1227", r.vanillaParticles + " / " + r.effectParticles, MEASURED, x2, yy += 11, col);
            row("1227 visual modules on", String.valueOf(r.visualModules), MEASURED, x2, yy += 11, col);
            row("Low Latency Mode", r.lowLatency == null ? "-" : r.lowLatency, MEASURED, x2, yy += 11, col);
            y = yy + 16;
        }
        y = para("Limits of this test: it measures this scene only, for the chosen time, after a warm-up (3 s, 6 s after a preset). Stand "
                + "still and keep the camera still; moving or turning marks the result. The A/B test runs both halves back to back in the "
                + "same spot and then puts every value back. A result from a quiet or empty scene does not predict a crowded match: test "
                + "in the kind of place you play.", x, y, w);
        return y - y0;
    }

    // ------------------------------------------------------------------ analyzer

    private float analyzer(float x, float y0, float w) {
        float y = y0;
        long now = System.nanoTime();
        if (findings == null || now - findingsAt > 1_000_000_000L) {
            findings = PerfAnalyzer.run();
            findingsAt = now;
        }
        ui.button("unig", "Show ignored again", x + w - 120, y - 2, 120, 14, Ui.GHOST, true, -1, () -> {
            PerfAnalyzer.unignoreAll();
            findingsAt = 0L;
        });
        ui.button("prst", "Restore last preset", x + w - 250, y - 2, 124, 14, Ui.GHOST, ChangeSet.canRestore(PerfPresets.UNDO_KEY), -1,
                PerfPresets::restorePrevious);
        section("PERFORMANCE ANALYZER", x, y);
        y += 16;
        if (PerfAnalyzer.collecting()) {
            Ui.text("Collecting frame data (needs about 10 s of play)...", x, y, DS.T_SMALL, DS.WARN);
            y += 14;
        }
        if (findings.isEmpty()) {
            Ui.text("Nothing to report from the data collected so far.", x, y, DS.T_BODY, DS.GOOD);
            y += 16;
        }
        for (final PerfAnalyzer.Finding f : findings) {
            int c = f.severity == PerfAnalyzer.BAD ? DS.BAD : f.severity == PerfAnalyzer.WARN ? DS.WARN : 0xFF4DA3FF;
            List<String> lines = Gfx.wrap(f.detail, (w - 20) / DS.T_SMALL);
            float hh = 22 + lines.size() * 10 + 18;
            Gfx.roundedRect(x - 2, y - 2, w + 4, hh, DS.R_SM, DS.SURFACE_2);
            Gfx.roundedRect(x - 2, y - 2, 2, hh, 1, c);
            Ui.textBold(f.title, x + 6, y + 2, DS.T_BODY, DS.TEXT);
            float ly = y + 15;
            for (String l : lines) {
                Ui.text(l, x + 6, ly, DS.T_SMALL, DS.MUTED);
                ly += 10;
            }
            float bx = x + 6, by = ly + 2;
            if (f.fix != null && f.fix.any()) {
                ui.button("fix" + f.id, "Review fix", bx, by, 84, 13, Ui.PRIMARY, true, -1, () -> ChangePreviewScreen.open(f.fix, "Apply", null));
                bx += 90;
            }
            if (ChangeSet.canRestore(PerfAnalyzer.undoKey(f.id))) {
                ui.button("rs" + f.id, "Restore", bx, by, 64, 13, Ui.SECONDARY, true, -1, () -> ChangeSet.restore(PerfAnalyzer.undoKey(f.id)));
                bx += 70;
            }
            ui.button("ig" + f.id, "Ignore", bx, by, 56, 13, Ui.GHOST, true, -1, () -> {
                PerfAnalyzer.ignore(f.id);
                findingsAt = 0L;
            });
            y += hh + 6;
        }
        // fixes applied earlier can be undone even after their finding went away
        boolean header = false;
        for (final String id : PerfAnalyzer.FIX_IDS) {
            if (!ChangeSet.canRestore(PerfAnalyzer.undoKey(id))) continue;
            if (!header) {
                y += 6;
                section("APPLIED FIXES", x, y);
                y += 13;
                header = true;
            }
            Ui.text(id, x, y + 2, DS.T_SMALL, DS.MUTED);
            ui.button("ar" + id, "Restore", x + 90, y, 64, 13, Ui.SECONDARY, true, -1, () -> ChangeSet.restore(PerfAnalyzer.undoKey(id)));
            y += 16;
        }
        return y - y0;
    }
}
