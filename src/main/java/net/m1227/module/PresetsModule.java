package net.m1227.module;

import java.io.File;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;

/**
 * Global presets (which effects are on and how heavy they are), colour
 * palettes and three saved profiles in config/1227/profiles.
 */
public final class PresetsModule extends Module {
    public PresetsModule() {
        super("Presets", "Quality presets, colour palettes and saved profiles.", Category.SETTINGS, true);
        global("Performance");
        global("Balanced");
        global("High Quality");
        global("Cinematic");
        global("Minimal PvP");
        palette("Mono", 0xFFFFFF, 0xBFEFFF);
        palette("Violet", 0x8B5CFF, 0x2DD4FF);
        palette("Ember", 0xFF8A3D, 0xFFD36B);
        palette("Aqua", 0x3DFFD0, 0x3D9BFF);
        palette("Rose", 0xFF4FA3, 0xFFB4D8);
        for (int i = 1; i <= 3; i++) profile(i);
    }

    @Override
    public boolean toggleable() {
        return false;
    }

    private void global(final String name) {
        add(new Setting.Action("Preset: " + name, new Runnable() {
            @Override
            public void run() {
                apply(name);
                Notifier.post(Notifier.Kind.SYSTEM, "Preset", name, Notifier.ACCENT, 1800);
            }
        }));
    }

    private void palette(final String name, final int c1, final int c2) {
        add(new Setting.Action("Colors: " + name, new Runnable() {
            @Override
            public void run() {
                FxSettings.applyPalette(c1, c2);
                SkyModule.applyPalette(c1, c2);
                Notifier.post(Notifier.Kind.SYSTEM, "Colors", name, c1, 1500);
            }
        }));
    }

    private void profile(final int slot) {
        add(new Setting.Action("Save profile " + slot, new Runnable() {
            @Override
            public void run() {
                boolean ok = ConfigManager.exportTo(file(slot));
                Notifier.post(Notifier.Kind.SYSTEM, "Profile " + slot, ok ? "Saved" : "Save failed",
                        ok ? Notifier.GOOD : Notifier.BAD, 1600);
            }
        }));
        add(new Setting.Action("Load profile " + slot, new Runnable() {
            @Override
            public void run() {
                boolean ok = ConfigManager.importFrom(file(slot));
                Notifier.post(Notifier.Kind.SYSTEM, "Profile " + slot, ok ? "Loaded" : "Empty slot",
                        ok ? Notifier.GOOD : Notifier.WARN, 1600);
            }
        }));
    }

    private static File file(int slot) {
        File dir = new File(new File(Mod1227.configDir, "1227"), "profiles");
        dir.mkdirs();
        return new File(dir, "profile" + slot + ".json");
    }

    // ---------------------------------------------------------- presets

    private static final String[] DECOR = {"Dynamic Skies", "Ambience", "Ambient Particles", "Player Aura", "Aura Hat", "Halo",
            "Trails", "Jump Effects", "Weapon Effects", "Motion Blur"};

    static void apply(String name) {
        PerformanceModule perf = PerformanceModule.INSTANCE;
        if (name.equals("Performance")) {
            perf(perf, "Low", 800, 24);
            for (String d : DECOR) on(d, false);
            on("Target Aura", true);
            on("Hit Effect", true);
            on("Kill Effect", false);
            on("Shockwave", false);
        } else if (name.equals("Balanced")) {
            perf(perf, "Medium", 2500, 48);
            for (String d : DECOR) on(d, false);
            on("Dynamic Skies", true);
            on("Trails", true);
            on("Target Aura", true);
            on("Hit Effect", true);
            on("Kill Effect", true);
            on("Shockwave", true);
        } else if (name.equals("High Quality")) {
            perf(perf, "High", 4500, 64);
            on("Dynamic Skies", true);
            on("Ambient Particles", false);
            on("Trails", true);
            on("Jump Effects", true);
            on("Weapon Effects", true);
            on("Target Aura", true);
            on("Hit Effect", true);
            on("Kill Effect", true);
            on("Shockwave", true);
            on("Motion Blur", false);
        } else if (name.equals("Cinematic")) {
            perf(perf, "High", 6000, 96);
            for (String d : DECOR) on(d, true);
            on("Aura Hat", false);
            on("Ambient Particles", false);
            on("Target Aura", true);
            on("Hit Effect", true);
            on("Kill Effect", true);
            on("Shockwave", true);
        } else { // Minimal PvP
            perf(perf, "Low", 1000, 32);
            for (String d : DECOR) on(d, false);
            on("Target Aura", false);
            on("Kill Effect", false);
            on("Shockwave", false);
            on("Hit Effect", true);
            on("Hit Marker", true);
            on("Dynamic Island", true);
            on("View Bobbing", true);
            set("View Bobbing", "Mode", "Off (No View Bobbing)");
        }
    }

    private static void perf(PerformanceModule p, String q, int max, int dist) {
        p.quality.set(q);
        p.maxParticles.setClamped(max);
        p.distance.setClamped(dist);
    }

    private static void on(String module, boolean state) {
        Module m = ModuleManager.byName(module);
        if (m != null) m.setEnabled(state);
    }

    private static void set(String module, String setting, String value) {
        Module m = ModuleManager.byName(module);
        if (m == null) return;
        Setting<?> s = m.setting(setting);
        if (s instanceof Setting.Mode) {
            for (String o : ((Setting.Mode) s).options) {
                if (o.equals(value)) ((Setting.Mode) s).set(value);
            }
        }
    }
}
