package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;

/**
 * Global limits for every visual effect, plus one-click presets. Always active, not toggleable.
 * The presets only change 1227's own settings (quality, particle cap, effect distance and the
 * heaviest optional modules); they never touch Minecraft's video settings.
 */
public final class PerformanceModule extends Module {
    public static final PerformanceModule INSTANCE = new PerformanceModule();

    public final Setting.Mode quality;
    public final Setting.Num maxParticles;
    public final Setting.Num distance;

    private PerformanceModule() {
        super("Performance", "Effect quality, particle limits and one-click presets.", Category.SETTINGS, true);
        quality = add(new Setting.Mode("Quality", "High", "Low", "Medium", "High", "Ultra"));
        maxParticles = add(new Setting.Num("Max particles", 3000, 200, 8000, 100, ""));
        distance = add(new Setting.Num("Render distance", 48, 8, 128, 4, "m"));
        add(new Setting.Action("Preset: Low-end PC", () -> preset("Low-end PC", "Low", 600, 24, false)));
        add(new Setting.Action("Preset: High FPS", () -> preset("High FPS", "Medium", 1500, 32, false)));
        add(new Setting.Action("Preset: Balanced", () -> preset("Balanced", "High", 3000, 48, true)));
        add(new Setting.Action("Preset: Visual quality", () -> preset("Visual quality", "Ultra", 6000, 80, true)));
    }

    /**
     * Applies a preset. heavyOn = whether Motion Blur may stay on (it renders the frame an extra
     * time, the biggest single cost among 1227's modules); the lighter presets switch it off.
     */
    private void preset(String name, String q, int particles, int dist, boolean heavyOn) {
        quality.set(q);
        maxParticles.set((double) particles);
        distance.set((double) dist);
        if (!heavyOn) {
            Module mb = ModuleManager.byName("Motion Blur");
            if (mb != null && mb.isEnabled()) mb.setEnabled(false);
        }
        ConfigManager.markDirty();
        Notifier.post(Notifier.Kind.SYSTEM, "Performance", name + " preset applied", Notifier.GOOD, 1800);
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    /** Multiplier applied to particle counts. */
    public static float qualityScale() {
        String q = INSTANCE.quality.get();
        return q.equals("Low") ? 0.35f : q.equals("Medium") ? 0.7f : q.equals("Ultra") ? 1.35f : 1f;
    }
}
