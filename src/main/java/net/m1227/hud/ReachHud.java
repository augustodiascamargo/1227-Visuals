package net.m1227.hud;

import net.m1227.combat.HitEvidence;
import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;

/**
 * Reach Analysis (1.21). Separates what used to be one number:
 * <ul>
 * <li>Attempt: eye to the target's hitbox (with the 0.1 border Minecraft adds for aiming) when
 * you attacked, measured on your screen's positions;</li>
 * <li>At feedback: the same distance when the damage feedback arrived (the target has moved by
 * then, and the server judged the hit somewhere between the two);</li>
 * <li>the attempt's classification from the Hit Registration Monitor (likely hit, weak,
 * unconfirmed, unknown).</li>
 * </ul>
 * An analysis tool only: it never changes reach or hitboxes.
 */
public final class ReachHud extends HudModule {
    private final Setting.Bool fade, detail;

    public ReachHud() {
        super("Reach Display", "Distance at your attack and at the damage feedback.", 0.02, 0.62);
        fade = add(new Setting.Bool("Fade after 3 s", false));
        detail = add(new Setting.Bool("Show feedback distance and result", true));
    }

    @Override
    public float width() {
        return detail.on() ? 112 : 64;
    }

    @Override
    public float height() {
        return detail.on() ? 26 : 16;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        CombatTracker.Attempt a = CombatTracker.attempts > 0 ? CombatTracker.attempt(0) : null;
        float v = a == null ? -1f : a.reachAttempt;
        long age = a == null ? Long.MAX_VALUE : (System.nanoTime() - a.at) / 1_000_000L;
        if (editing && v < 0) v = 2.87f;
        if (!editing && fade.on() && age > 3000) v = -1f;
        panel(x, y, width(), height());
        String s = v < 0 ? "-- m" : String.format("%.2f m", v);
        Gfx.text(detail.on() ? "Attempt" : "Reach", x + 5, y + 4, LABEL);
        Gfx.text(s, x + width() - 5 - Gfx.textWidth(s), y + 4, 0xFFFFFFFF);
        if (!detail.on()) return;
        String fb;
        int col = LABEL;
        if (editing && a == null) {
            fb = "3.05 m  ·  likely hit";
            col = 0xFF3DDC97;
        } else if (a == null || v < 0) {
            fb = "-";
        } else if (!a.finished) {
            fb = a.reachFeedback >= 0 ? String.format("%.2f m  ·  checking", a.reachFeedback) : "checking...";
        } else {
            String r = a.level.likely() ? "likely hit" : a.level == HitEvidence.Level.WEAK ? "uncertain"
                    : a.level == HitEvidence.Level.NONE ? "unconfirmed" : "unknown";
            col = a.level.likely() ? 0xFF3DDC97 : a.level == HitEvidence.Level.WEAK ? 0xFFFFC24A
                    : a.level == HitEvidence.Level.NONE ? 0xFFFF6B6B : LABEL;
            fb = (a.reachFeedback >= 0 ? String.format("%.2f m  ·  ", a.reachFeedback) : "") + r;
        }
        Gfx.textScaled("Feedback", x + 5, y + 15, 0.82f, LABEL);
        Gfx.textScaled(fb, x + width() - 5 - Gfx.textWidth(fb, 0.82f), y + 15, 0.82f, col);
    }
}
