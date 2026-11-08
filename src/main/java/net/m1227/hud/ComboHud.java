package net.m1227.hud;

import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;

/** Current combo (hits in a row without being hit), with a pop animation on every hit. */
public final class ComboHud extends HudModule {
    private final Setting.Bool hideZero;
    private int lastCombo;
    private long popAt;

    public ComboHud() {
        super("Combo Counter", "Hits in a row without taking a hit.", 0.46, 0.62);
        hideZero = add(new Setting.Bool("Hide when 0", true));
    }

    @Override
    public float width() {
        return 64;
    }

    @Override
    public float height() {
        return 18;
    }

    @Override
    protected boolean hasContent() {
        return !hideZero.on() || CombatTracker.combo() > 0;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        int c = CombatTracker.combo();
        if (editing && c == 0) c = 7;
        if (c != lastCombo) {
            if (c > lastCombo) popAt = System.currentTimeMillis();
            lastCombo = c;
        }
        float pop = 1f - Math.min(1f, (System.currentTimeMillis() - popAt) / 220f);
        panel(x, y, width(), height());
        Gfx.text("COMBO", x + 6, y + 5, LABEL);
        String v = String.valueOf(c);
        float s = 1f + pop * 0.35f;
        float vw = Gfx.textWidth(v) * s;
        Gfx.textScaled(v, x + width() - 6 - vw, y + 9 - 4 * s, s, c >= 10 ? 0xFFFFC857 : value());
    }
}
