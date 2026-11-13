package net.m1227.hud;

import net.m1227.client.perf.FrameStats;
import net.m1227.client.perf.PerfSystem;
import net.m1227.combat.CombatLog;
import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.module.PerformanceModule;
import net.m1227.net.LatencyMonitor;
import net.m1227.net.NetObserver;
import net.m1227.ui.Gfx;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * PvP Performance HUD: performance and combat numbers in one panel, plus alerts that only fire on
 * a reliable measurement.
 *
 * Layouts: Minimal (FPS, ping), Compact (one row), Advanced, Detailed (everything, with the
 * source of each value: m = measured here, s = sent by the server, e = estimate). With "Choose
 * items myself" each line has its own switch.
 *
 * The text is rebuilt 4 times per second, not every frame (the numbers behind it update at that
 * rate anyway), so the HUD costs only the drawing.
 */
public final class PvpPerfHud extends HudModule {
    private static final String[] ITEMS = {"FPS", "Frame time", "1% low", "Ping", "Jitter", "Ping range", "CPS left", "CPS right",
            "Target distance", "Attack attempts", "Likely hits", "Unconfirmed attacks", "Combo", "Hit interval", "W-tap timing",
            "Sprint state", "Session time"};
    private static final boolean[][] PRESET = {
            // Minimal
            {true, false, false, true, false, false, false, false, false, false, false, false, false, false, false, false, false},
            // Compact
            {true, false, true, true, false, false, true, false, false, false, false, false, true, false, false, false, false},
            // Advanced
            {true, true, true, true, true, false, true, true, true, false, false, false, true, true, true, false, false},
            // Detailed
            {true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true, true}};
    private static final String[] ALERTS = {"FPS instability", "High ping", "High jitter", "Frame time spike", "Network timeout",
            "Low FPS during combat", "Heavy visual effects", "Excessive particle load"};

    private final Setting.Mode layout;
    private final Setting.Bool custom, tags;
    private final Setting.Bool[] item = new Setting.Bool[ITEMS.length];
    private final Setting.Bool[] alert = new Setting.Bool[ALERTS.length];
    private final long started = System.nanoTime();

    // cached text (rebuilt 4x per second)
    private final String[] lbl = new String[ITEMS.length], val = new String[ITEMS.length];
    private final int[] col = new int[ITEMS.length];
    private int lines;
    private String alertText = "";
    private int alertColor;
    private long nextBuild;
    private float w = 120, h = 20;

    public PvpPerfHud() {
        super("PvP Performance HUD", "FPS, frame time, ping, jitter, CPS, reach, hits and alerts in one panel.", 0.70, 0.30);
        layout = add(new Setting.Mode("Layout", "Advanced", "Minimal", "Compact", "Advanced", "Detailed"));
        custom = add(new Setting.Bool("Choose items myself", false));
        for (int i = 0; i < ITEMS.length; i++) {
            item[i] = add(new Setting.Bool(ITEMS[i], PRESET[2][i])).showIf(() -> custom.on());
        }
        tags = add(new Setting.Bool("Show value sources", false));
        for (int i = 0; i < ALERTS.length; i++) alert[i] = add(new Setting.Bool("Alert: " + ALERTS[i], true));
    }

    private boolean shows(int i) {
        if (custom.on()) return item[i].on();
        int l = layout.is("Minimal") ? 0 : layout.is("Compact") ? 1 : layout.is("Detailed") ? 3 : 2;
        return PRESET[l][i];
    }

    private boolean showTags() {
        return tags.on() || layout.is("Detailed");
    }

    @Override
    public float width() {
        return w;
    }

    @Override
    public float height() {
        return h;
    }

    private void put(String l, String v, int c, char tag) {
        lbl[lines] = l;
        val[lines] = showTags() && tag != ' ' ? v + " " + tag : v;
        col[lines] = c;
        lines++;
    }

    private void build(boolean editing) {
        lines = 0;
        FrameStats f = FrameStats.INSTANCE;
        LatencyMonitor lm = LatencyMonitor.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        int white = 0xFFFFFFFF, warn = 0xFFFFC24A, bad = 0xFFFF6B6B, good = 0xFF3DDC97;
        boolean probe = lm.rtt.size() >= 3;
        for (int i = 0; i < ITEMS.length; i++) {
            if (!shows(i)) continue;
            switch (i) {
                case 0: put("FPS", editing ? "240" : String.valueOf(Math.round(f.fps)), f.fps < 60 && !editing ? warn : value(), 'm'); break;
                case 1: put("Frame", editing ? "4.2 ms" : String.format("%.1f ms", f.avgMs), white, 'm'); break;
                case 2: put("1% low", editing ? "160" : Float.isNaN(f.low1) ? "N/A" : String.valueOf(Math.round(f.low1)),
                        !editing && !Float.isNaN(f.low1) && f.low1 < f.fps * 0.5f ? warn : white, 'm'); break;
                case 3: {
                    String v;
                    char t;
                    if (editing) {
                        v = "38 ms";
                        t = 'm';
                    } else if (probe) {
                        v = Math.round(lm.rttLast) + " ms";
                        t = 'm';
                    } else if (lm.tabPing >= 0) {
                        v = lm.tabPing + " ms";
                        t = 's';
                    } else {
                        v = "N/A";
                        t = ' ';
                    }
                    put("Ping", v, white, t);
                    break;
                }
                case 4: {
                    double j = lm.rttJitter.value();
                    put("Jitter", editing ? "3.1 ms" : Double.isNaN(j) ? "N/A" : String.format("%.1f ms", j), white, Double.isNaN(j) ? ' ' : 'm');
                    break;
                }
                case 5: put("Ping range", editing ? "31-52 ms" : probe ? Math.round(lm.rttMin) + "-" + Math.round(lm.rttP95) + " ms" : "N/A", white, probe ? 'm' : ' '); break;
                case 6: put("CPS L", editing ? "11" : String.valueOf(ClickCounter.cps(0)), white, 'm'); break;
                case 7: put("CPS R", editing ? "6" : String.valueOf(ClickCounter.cps(1)), white, 'm'); break;
                case 8: {
                    EntityLivingBase t = CombatTracker.target(5000L);
                    String v = editing ? "2.9 m" : t == null || mc.thePlayer == null ? "-" : String.format("%.1f m", mc.thePlayer.getDistanceToEntity(t));
                    put("Target", v, white, 'm');
                    break;
                }
                case 9: put("Attempts", editing ? "84" : String.valueOf(CombatTracker.attempts), white, 'm'); break;
                case 10: put("Likely hits", editing ? "61" : String.valueOf(CombatTracker.strong + CombatTracker.moderate), good, 'e'); break;
                case 11: put("Unconfirmed", editing ? "9" : String.valueOf(CombatTracker.none), white, 'e'); break;
                case 12: put("Combo", editing ? "4" : String.valueOf(CombatTracker.combo()), value(), 'e'); break;
                case 13: {
                    float g = CombatLog.INSTANCE.attemptGaps.last();
                    put("Hit interval", editing ? "310 ms" : Float.isNaN(g) ? "-" : Math.round(g) + " ms", white, 'm');
                    break;
                }
                case 14: {
                    CombatLog log = CombatLog.INSTANCE;
                    CombatLog.Reset r = log.resetCount() > 0 ? log.reset(0) : null;
                    put("W-tap", editing ? "Perfect" : r == null ? "-" : r.grade + (r.resetMs >= 0 ? " " + Math.round(r.resetMs) + "ms" : ""),
                            r == null ? white : r.color, 'm');
                    break;
                }
                case 15: put("Sprint", editing ? "on" : mc.thePlayer != null && mc.thePlayer.isSprinting() ? "on" : "off", white, 'm'); break;
                default: {
                    long s = (System.nanoTime() - started) / 1_000_000_000L;
                    put("Session", s / 3600 > 0 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60), white, ' ');
                }
            }
        }
        // alerts: the first active one is shown
        alertText = "";
        if (editing) return;
        long now = System.nanoTime();
        net.m1227.module.LatencyMonitorModule lmm = net.m1227.module.LatencyMonitorModule.INSTANCE;
        float hp = lmm == null ? 150 : lmm.highPing.f(), hj = lmm == null ? 20 : lmm.highJitter.f();
        boolean frames = f.frames10s >= 300;
        double jit = lm.rttJitter.value();
        boolean[] act = new boolean[ALERTS.length];
        act[0] = frames && !Float.isNaN(f.low1) && f.low1 < f.fps * 0.5f;
        act[1] = lm.connected && (probe ? lm.rttLast > hp : lm.tabPing > hp);
        act[2] = lm.connected && !Double.isNaN(jit) && jit > hj;
        act[3] = f.spikeLog.size() > 0 && now - f.spikeLog.time(f.spikeLog.size() - 1) < 2_000_000_000L;
        act[4] = lm.connected && NetObserver.INSTANCE.attached() && now - NetObserver.INSTANCE.lastInbound() > 2_000_000_000L;
        act[5] = frames && CombatTracker.target(3000L) != null && f.fps < 60;
        act[6] = frames && f.worldMs > 4f;
        act[7] = ParticleSystem.INSTANCE.alive() > PerformanceModule.INSTANCE.maxParticles.i() * 0.9f || PerfSystem.vanillaParticles > 3000;
        for (int i = 0; i < ALERTS.length; i++) {
            if (act[i] && alert[i].on()) {
                alertText = ALERTS[i];
                alertColor = i == 4 || i == 5 ? bad : warn;
                break;
            }
        }
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        long now = System.nanoTime();
        if (editing || now >= nextBuild) {
            nextBuild = now + 250_000_000L;
            build(editing);
        }
        boolean row = layout.is("Compact") || layout.is("Minimal");
        float pad = 5;
        if (row) {
            // one line: "FPS 240  Ping 38 ms ..."
            float tw = pad;
            for (int i = 0; i < lines; i++) tw += Gfx.textWidth(lbl[i], 0.8f) + 3 + Gfx.textWidth(val[i]) + 8;
            w = Math.max(40, tw - 8 + pad);
            h = 15 + (alertText.isEmpty() ? 0 : 10);
            panel(x, y, w, h);
            float cx = x + pad;
            for (int i = 0; i < lines; i++) {
                Gfx.textScaled(lbl[i], cx, y + 4.6f, 0.8f, LABEL);
                cx += Gfx.textWidth(lbl[i], 0.8f) + 3;
                Gfx.text(val[i], cx, y + 4, col[i]);
                cx += Gfx.textWidth(val[i]) + 8;
            }
            if (!alertText.isEmpty()) Gfx.textScaled("▲ " + alertText, x + pad, y + 15, 0.8f, alertColor);
            return;
        }
        float lw = 0, vw = 0;
        for (int i = 0; i < lines; i++) {
            lw = Math.max(lw, Gfx.textWidth(lbl[i], 0.85f));
            vw = Math.max(vw, Gfx.textWidth(val[i]));
        }
        w = Math.max(90, pad * 2 + lw + 10 + vw);
        if (!alertText.isEmpty()) w = Math.max(w, Gfx.textWidth("▲ " + alertText, 0.85f) + pad * 2);
        h = 6 + lines * 10 + (alertText.isEmpty() ? 0 : 11);
        panel(x, y, w, h);
        float ly = y + 4;
        for (int i = 0; i < lines; i++) {
            Gfx.textScaled(lbl[i], x + pad, ly + 0.6f, 0.85f, LABEL);
            Gfx.text(val[i], x + w - pad - Gfx.textWidth(val[i]), ly, col[i]);
            ly += 10;
        }
        if (!alertText.isEmpty()) Gfx.textScaled("▲ " + alertText, x + pad, ly + 1, 0.85f, alertColor);
    }
}
