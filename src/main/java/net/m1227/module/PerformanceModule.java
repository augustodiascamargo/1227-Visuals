package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
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

    /** Entities (not players, unless chosen) farther than this are not drawn; 256 = off. */
    public final Setting.Num entityDistance;
    public final Setting.Bool cullPlayers;
    /** Cap and distance for Minecraft's own particles (4000 / 64 = vanilla behaviour). */
    public final Setting.Num particleCap, particleDistance;
    public final Setting.Bool backgroundLimit;
    public final Setting.Num backgroundFps;
    /** Name of the last preset applied ("Custom" after manual changes made through presets). */
    public final Setting.Mode activePreset;

    private PerformanceModule() {
        super("Performance", "Effect quality, entity and particle limits, background FPS and presets.", Category.SETTINGS, true);
        quality = add(new Setting.Mode("Quality", "High", "Low", "Medium", "High", "Ultra"));
        maxParticles = add(new Setting.Num("Max particles", 3000, 200, 8000, 100, ""));
        distance = add(new Setting.Num("Render distance", 48, 8, 128, 4, "m"));
        entityDistance = add(new Setting.Num("Entity render distance", 256, 16, 256, 8, "m"));
        cullPlayers = add(new Setting.Bool("Entity distance also hides players", false));
        particleCap = add(new Setting.Num("Minecraft particle limit", 4000, 100, 4000, 100, ""));
        particleDistance = add(new Setting.Num("Minecraft particle distance", 64, 8, 64, 4, "m"));
        backgroundLimit = add(new Setting.Bool("Limit FPS when the game is in the background", true));
        backgroundFps = add(new Setting.Num("Background FPS", 15, 5, 60, 5, "")).showIf(() -> backgroundLimit.on());
        activePreset = add(new Setting.Mode("Active preset", "Custom", net.m1227.client.perf.PerfPresets.NAMES_WITH_CUSTOM)).showIf(() -> false);
        for (final String n : net.m1227.client.perf.PerfPresets.NAMES) {
            add(new Setting.Action("Preset: " + n, () -> net.m1227.client.perf.PerfPresets.apply(n)));
        }
        add(new Setting.Action("Run 30 s benchmark", () -> net.m1227.client.perf.Benchmark.start(30)));
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
