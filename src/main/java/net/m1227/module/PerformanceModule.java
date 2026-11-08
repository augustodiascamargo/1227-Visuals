package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/** Global limits for every visual effect. Always active, not toggleable. */
public final class PerformanceModule extends Module {
    public static final PerformanceModule INSTANCE = new PerformanceModule();

    public final Setting.Mode quality;
    public final Setting.Num maxParticles;
    public final Setting.Num distance;

    private PerformanceModule() {
        super("Performance", "Particle limits and quality for all effects.", Category.SETTINGS, true);
        quality = add(new Setting.Mode("Quality", "High", "Low", "Medium", "High"));
        maxParticles = add(new Setting.Num("Max particles", 3000, 200, 6000, 100, ""));
        distance = add(new Setting.Num("Render distance", 48, 8, 128, 4, "m"));
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    /** Multiplier applied to particle counts. */
    public static float qualityScale() {
        String q = INSTANCE.quality.get();
        return q.equals("Low") ? 0.35f : (q.equals("Medium") ? 0.7f : 1f);
    }
}
