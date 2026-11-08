package net.m1227.vfx;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * The standard set of per-effect options (colours, amount, speed, size,
 * duration, opacity, fade speed). Every effect module gets the same
 * controls, which keeps the menu consistent. All instances are tracked so
 * a preset can recolour every effect at once.
 */
public final class FxSettings {
    private static final List<FxSettings> ALL = new ArrayList<FxSettings>();

    public final Setting.Col color1, color2;
    public final Setting.Num amount, speed, size, duration, opacity, fade;

    public FxSettings(Module m, int c1, int c2, double amount, double speed, double size, double duration) {
        color1 = m.add(new Setting.Col("Color", c1));
        color2 = m.add(new Setting.Col("Color 2", c2));
        this.amount = m.add(new Setting.Num("Amount", amount, 1, 60, 1, ""));
        this.speed = m.add(new Setting.Num("Speed", speed, 0.2, 4, 0.1, "x"));
        this.size = m.add(new Setting.Num("Size", size, 0.02, 0.6, 0.01, ""));
        this.duration = m.add(new Setting.Num("Duration", duration, 0.1, 3, 0.05, "s"));
        this.opacity = m.add(new Setting.Num("Opacity", 1.0, 0.1, 1.0, 0.05, ""));
        this.fade = m.add(new Setting.Num("Fade speed", 1.0, 0.3, 3.0, 0.1, "x"));
        ALL.add(this);
    }

    public float alpha() {
        return opacity.f();
    }

    public static void applyPalette(int c1, int c2) {
        for (FxSettings f : ALL) {
            f.color1.setRgb(c1);
            f.color2.setRgb(c2);
        }
    }
}
