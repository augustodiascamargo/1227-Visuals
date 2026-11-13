package net.m1227.hud;

import net.m1227.combat.CombatLog;
import net.m1227.core.Setting;
import net.m1227.net.NetObserver;
import net.m1227.ui.Gfx;

/**
 * W-Tap Trainer (1.21; was W-Tap Indicator). In 1.8 the server ends your sprint after a sprint
 * hit; the next hit only gets the sprint knockback if a new "start sprinting" reaches the server
 * first. This HUD reads the sprint packets your game actually sent, in the exact order they were
 * sent with your attacks:
 * <ul>
 * <li>observed: when "stop sprinting" and "start sprinting" were sent, and when the attack was
 * sent;</li>
 * <li>inferred: whether the server counted the attack as a sprint hit (it assumes the server ended
 * the sprint after your previous sprint hit).</li>
 * </ul>
 * Grades: Perfect (reset of 2 ticks or less), Good (up to ~5 ticks), Slow, No reset, No sprint.
 * Also shows the last resets and how consistent they were.
 *
 * Read-only practice tool: it only watches; it never presses, releases or sends anything.
 */
public final class WTapHud extends HudModule {
    private final Setting.Bool timeline, stats;

    public WTapHud() {
        super("W-Tap Indicator", "Grades your sprint resets from the sprint packets you sent.", 0.44, 0.70);
        timeline = add(new Setting.Bool("Last resets", true));
        stats = add(new Setting.Bool("Session stats", true));
    }

    @Override
    public float width() {
        return 132;
    }

    @Override
    public float height() {
        return 30 + (timeline.on() ? 10 : 0) + (stats.on() ? 10 : 0);
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        panel(x, y, width(), height());
        CombatLog log = CombatLog.INSTANCE;
        CombatLog.Reset r = log.resetCount() > 0 ? log.reset(0) : null;
        String g = r == null ? "" : r.grade;
        int gc = r == null ? 0xFFFFFFFF : r.color;
        if (editing && r == null) {
            g = "Perfect";
            gc = 0xFF3DDC97;
        }
        long age = r == null ? Long.MAX_VALUE : (System.nanoTime() - r.at) / 1_000_000L;
        float pop = editing ? 1f : Math.max(0f, 1f - age / 2500f);
        Gfx.text("W-Tap", x + 5, y + 4, LABEL);
        if (!g.isEmpty() && pop > 0f) {
            int alpha = (int) (80 + 175 * pop);
            Gfx.textScaled(g, x + width() - 5 - Gfx.textWidth(g, 1.25f), y + 3, 1.25f, (alpha << 24) | (gc & 0xFFFFFF));
        }
        String detail;
        if (!NetObserver.INSTANCE.attached() && !editing) detail = "waiting for a server connection";
        else if (editing && r == null) detail = "reset 95 ms  ·  start→hit 140 ms";
        else if (r == null) detail = "-";
        else detail = (r.resetMs >= 0 ? "reset " + Math.round(r.resetMs) + " ms" : "no reset")
                + (r.startToAttackMs >= 0 ? "  ·  start→hit " + Math.round(r.startToAttackMs) + " ms" : "");
        Gfx.textScaled(detail, x + 5, y + 17, 0.86f, 0xFFFFFFFF);
        float cy = y + 29;
        if (timeline.on()) {
            int n = Math.min(12, log.resetCount());
            float bw = (width() - 10) / 12f;
            for (int i = 0; i < 12; i++) {
                int col = editing ? (i % 4 == 3 ? 0xFFFFC24A : 0xFF3DDC97) : i < n ? log.reset(n - 1 - i).color : 0x30FFFFFF;
                Gfx.roundedRect(x + 5 + i * bw, cy, bw - 1.5f, 5, 1.5f, col);
            }
            cy += 10;
        }
        if (stats.on()) {
            int h = editing ? 24 : log.attacks, s = editing ? 19 : log.sprintAttacks, p = editing ? 11 : log.perfect;
            double c = editing ? 18 : log.resetConsistency();
            String st = h == 0 ? "no attacks yet" : Math.round(100f * s / h) + "% sprint  ·  " + p + " perfect"
                    + (Double.isNaN(c) ? "" : "  ·  ±" + Math.round(c) + " ms");
            Gfx.textScaled(st, x + 5, cy, 0.82f, LABEL);
        }
    }
}
