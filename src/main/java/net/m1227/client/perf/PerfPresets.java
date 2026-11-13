package net.m1227.client.perf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.m1227.Mod1227;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;
import net.m1227.module.PerformanceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * Performance presets: Low End, Balanced, High FPS and Visual Quality, and (1.21) six PvP
 * presets: Competitive, Low Latency, Maximum FPS, Low-End PvP, Balanced PvP and Visual PvP.
 * No preset ever lowers the player render distance, and none goes below 6 chunks (96 blocks,
 * beyond the range servers send players in). Each one sets real
 * Minecraft video options (render distance, graphics, smooth lighting, clouds, particles,
 * entity shadows, VBOs, FPS limit, VSync) and 1227's own limits (effect quality, particle
 * budget, effect distance, entity and particle distance, Motion Blur). Every preset is editable:
 * "Save current as preset" stores the current values under that name, in
 * config/1227/performance-presets.json.
 *
 * 1.21: a preset is a {@link ChangeSet}: the preview lists every value that will change, and
 * "Restore previous" puts back the values from before the last preset (Minecraft video options
 * included). A field left empty (null) in a preset is not touched.
 */
public final class PerfPresets {
    public static final String[] NAMES = {"Low End", "Balanced", "High FPS", "Visual Quality",
            "Competitive", "Low Latency", "Maximum FPS", "Low-End PvP", "Balanced PvP", "Visual PvP"};
    public static final String[] NAMES_WITH_CUSTOM = {"Custom", "Low End", "Balanced", "High FPS", "Visual Quality",
            "Competitive", "Low Latency", "Maximum FPS", "Low-End PvP", "Balanced PvP", "Visual PvP"};
    public static final String UNDO_KEY = "preset";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PerfPresets() {}

    /** Values of one preset. */
    public static final class Values {
        // boxed: null = this preset does not touch the value
        public Integer renderDistance, smoothLighting, clouds, particles, maxFps, maxParticles, effectDistance, entityDistance, particleCap, particleDistance;
        public Boolean fancy, shadows, vsync, vbo, motionBlur;
        public String quality;
        // 1.21 PvP extras
        public Boolean pvpVisual, competitive, lowLatency, dynamicQuality;
        public String lowLatencyFps;
        public Integer cosmeticDistance, maxAuras, maxWeaponFx;

        JsonObject json() {
            return (JsonObject) GSON.toJsonTree(this);
        }

        static Values of(JsonElement e) {
            return GSON.fromJson(e, Values.class);
        }
    }

    private static Values pvp(int rd, boolean fancy, int smooth, int clouds, int particles, boolean shadows, String quality,
                              int maxParticles, int effectDistance, int entityDistance, int particleCap, int particleDistance) {
        Values v = new Values();
        v.vbo = true;
        v.renderDistance = rd;
        v.fancy = fancy;
        v.smoothLighting = smooth;
        v.clouds = clouds;
        v.particles = particles;
        v.shadows = shadows;
        v.quality = quality;
        v.maxParticles = maxParticles;
        v.effectDistance = effectDistance;
        v.entityDistance = entityDistance;
        v.particleCap = particleCap;
        v.particleDistance = particleDistance;
        v.motionBlur = false;
        v.pvpVisual = true;
        v.dynamicQuality = true;
        v.competitive = false;
        v.lowLatency = false;
        return v;
    }

    private static Values def(String name) {
        if (name.equals("Competitive")) {
            Values v = pvp(10, false, 1, 0, 1, false, "Medium", 2000, 32, 96, 1500, 32);
            v.competitive = true;
            v.cosmeticDistance = 32;
            v.maxAuras = 3;
            v.maxWeaponFx = 2;
            return v;
        }
        if (name.equals("Low Latency")) {
            Values v = pvp(8, false, 0, 0, 1, false, "Medium", 1500, 32, 64, 1000, 24);
            v.lowLatency = true;
            v.lowLatencyFps = "Stable frame time";
            v.vsync = false;
            v.cosmeticDistance = 24;
            v.maxAuras = 2;
            v.maxWeaponFx = 1;
            return v;
        }
        if (name.equals("Maximum FPS")) {
            Values v = pvp(6, false, 0, 0, 2, false, "Low", 600, 24, 48, 400, 16);
            v.competitive = true;
            v.vsync = false;
            v.maxFps = 260;
            v.cosmeticDistance = 16;
            v.maxAuras = 0;
            v.maxWeaponFx = 0;
            return v;
        }
        if (name.equals("Low-End PvP")) {
            Values v = pvp(6, false, 0, 0, 2, false, "Low", 800, 24, 48, 500, 16);
            v.competitive = true;
            v.maxFps = 120; // a cap keeps an old CPU from running hot and stuttering
            v.cosmeticDistance = 16;
            v.maxAuras = 1;
            v.maxWeaponFx = 0;
            return v;
        }
        if (name.equals("Balanced PvP")) {
            Values v = pvp(10, true, 2, 1, 1, true, "High", 3000, 48, 96, 2000, 32);
            v.cosmeticDistance = 32;
            v.maxAuras = 4;
            v.maxWeaponFx = 3;
            return v;
        }
        if (name.equals("Visual PvP")) {
            Values v = pvp(12, true, 2, 2, 0, true, "High", 4000, 64, 128, 3000, 48);
            v.cosmeticDistance = 48;
            v.maxAuras = 8;
            v.maxWeaponFx = 6;
            return v;
        }
        Values v = new Values();
        v.vbo = true;
        v.vsync = false;
        v.maxFps = 260; // "unlimited" in Minecraft
        if (name.equals("Low End")) {
            v.renderDistance = 6;
            v.fancy = false;
            v.smoothLighting = 0;
            v.clouds = 0;
            v.particles = 2;
            v.shadows = false;
            v.quality = "Low";
            v.maxParticles = 600;
            v.effectDistance = 24;
            v.entityDistance = 48;
            v.particleCap = 500;
            v.particleDistance = 16;
            v.motionBlur = false;
        } else if (name.equals("High FPS")) {
            v.renderDistance = 8;
            v.fancy = false;
            v.smoothLighting = 0;
            v.clouds = 0;
            v.particles = 1;
            v.shadows = false;
            v.quality = "Medium";
            v.maxParticles = 1500;
            v.effectDistance = 32;
            v.entityDistance = 64;
            v.particleCap = 1000;
            v.particleDistance = 24;
            v.motionBlur = false;
        } else if (name.equals("Visual Quality")) {
            v.renderDistance = 16;
            v.fancy = true;
            v.smoothLighting = 2;
            v.clouds = 2;
            v.particles = 0;
            v.shadows = true;
            v.quality = "Ultra";
            v.maxParticles = 6000;
            v.effectDistance = 80;
            v.entityDistance = 256;
            v.particleCap = 4000;
            v.particleDistance = 64;
            v.motionBlur = true;
        } else { // Balanced
            v.renderDistance = 10;
            v.fancy = true;
            v.smoothLighting = 2;
            v.clouds = 1;
            v.particles = 1;
            v.shadows = true;
            v.quality = "High";
            v.maxParticles = 3000;
            v.effectDistance = 48;
            v.entityDistance = 96;
            v.particleCap = 2000;
            v.particleDistance = 32;
            v.motionBlur = true;
        }
        return v;
    }

    private static File file() {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "performance-presets.json");
    }

    /** Presets as saved by the user (missing ones use the defaults above). */
    public static Map<String, Values> load() {
        Map<String, Values> out = new LinkedHashMap<String, Values>();
        for (String n : NAMES) out.put(n, def(n));
        try {
            File f = file();
            if (f.isFile()) {
                JsonObject o = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                for (String n : NAMES) if (o.has(n)) {
                    Values v = Values.of(o.get(n));
                    if (v != null && v.quality != null) out.put(n, v);
                }
            }
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not read performance presets; using defaults", e);
        }
        return out;
    }

    private static void store(Map<String, Values> all) {
        try {
            JsonObject o = new JsonObject();
            for (Map.Entry<String, Values> e : all.entrySet()) o.add(e.getKey(), e.getValue().json());
            File out = file(), tmp = new File(out.getPath() + ".tmp");
            Files.write(tmp.toPath(), GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not save performance presets", e);
        }
    }

    /** The values currently in effect (Minecraft + 1227). */
    public static Values current() {
        GameSettings gs = Minecraft.getMinecraft().gameSettings;
        PerformanceModule p = PerformanceModule.INSTANCE;
        Values v = new Values();
        v.renderDistance = gs.renderDistanceChunks;
        v.fancy = gs.fancyGraphics;
        v.smoothLighting = gs.ambientOcclusion;
        v.clouds = gs.clouds;
        v.particles = gs.particleSetting;
        v.shadows = gs.entityShadows;
        v.maxFps = PerfSystem.userFrameLimit();
        v.vsync = gs.enableVsync;
        v.vbo = gs.useVbo;
        v.quality = p.quality.get();
        v.maxParticles = p.maxParticles.i();
        v.effectDistance = p.distance.i();
        v.entityDistance = p.entityDistance.i();
        v.particleCap = p.particleCap.i();
        v.particleDistance = p.particleDistance.i();
        Module mb = ModuleManager.byName("Motion Blur");
        v.motionBlur = mb != null && mb.isEnabled();
        return v;
    }

    public static void saveCurrentAs(String name) {
        Map<String, Values> all = load();
        all.put(name, current());
        store(all);
        PerformanceModule.INSTANCE.activePreset.set(name);
        Notifier.post(Notifier.Kind.SYSTEM, "Performance", "Saved as " + name, Notifier.GOOD, 1600);
    }

    public static void resetAll() {
        File f = file();
        if (f.isFile()) f.delete();
        Notifier.post(Notifier.Kind.SYSTEM, "Performance", "Presets back to defaults", Notifier.WARN, 1600);
    }

    /** Every value the preset would change right now. */
    public static ChangeSet changeSet(String name, String undoKey) {
        Values v = load().get(name);
        ChangeSet c = new ChangeSet(undoKey, "Preset: " + name);
        if (v == null) return c;
        if (v.renderDistance != null) c.mc("renderDistance", v.renderDistance);
        if (v.fancy != null) c.mc("fancy", v.fancy ? 1 : 0);
        if (v.smoothLighting != null) c.mc("smoothLighting", v.smoothLighting);
        if (v.clouds != null) c.mc("clouds", v.clouds);
        if (v.particles != null) c.mc("particles", v.particles);
        if (v.shadows != null) c.mc("shadows", v.shadows ? 1 : 0);
        if (v.vbo != null) c.mc("vbo", v.vbo ? 1 : 0);
        if (v.vsync != null) c.mc("vsync", v.vsync ? 1 : 0);
        if (v.maxFps != null) c.mc("maxFps", v.maxFps);
        if (v.quality != null) c.setting("Performance", "Quality", v.quality);
        if (v.maxParticles != null) c.setting("Performance", "Max particles", (double) v.maxParticles);
        if (v.effectDistance != null) c.setting("Performance", "Render distance", (double) v.effectDistance);
        if (v.entityDistance != null) c.setting("Performance", "Entity render distance", (double) v.entityDistance);
        if (v.particleCap != null) c.setting("Performance", "Minecraft particle limit", (double) v.particleCap);
        if (v.particleDistance != null) c.setting("Performance", "Minecraft particle distance", (double) v.particleDistance);
        if (v.motionBlur != null && !v.motionBlur) c.module("Motion Blur", false);
        if (v.pvpVisual != null) c.module("PvP Visual Optimization", v.pvpVisual);
        if (v.dynamicQuality != null) c.setting("PvP Visual Optimization", "Dynamic quality", v.dynamicQuality);
        if (v.cosmeticDistance != null) c.setting("PvP Visual Optimization", "Cosmetic effect distance", (double) v.cosmeticDistance);
        if (v.maxAuras != null) c.setting("PvP Visual Optimization", "Max other players with auras / cosmetics", (double) v.maxAuras);
        if (v.maxWeaponFx != null) c.setting("PvP Visual Optimization", "Max other players with weapon effects", (double) v.maxWeaponFx);
        if (v.lowLatencyFps != null) c.setting("Low Latency Mode", "FPS cap", v.lowLatencyFps);
        if (v.lowLatency != null) c.module("Low Latency Mode", v.lowLatency);
        if (v.competitive != null) c.module("Competitive Mode", v.competitive);
        c.notes.add("Never changed by a preset: the player render distance, name tags, the crosshair and hit feedback.");
        return c;
    }

    /** Applies at once (benchmark compare); the preview screen is the normal way. */
    public static void apply(String name) {
        applySet(name, changeSet(name, UNDO_KEY));
    }

    public static void applySet(String name, ChangeSet c) {
        c.apply();
        PerformanceModule.INSTANCE.activePreset.set(name);
        ConfigManager.markDirty();
        Notifier.post(Notifier.Kind.SYSTEM, "Performance", name + " applied (" + c.changes.size() + " changes)", Notifier.GOOD, 1800);
    }

    /** Opens the preview of a preset. */
    public static void preview(final String name) {
        net.m1227.client.menu.ChangePreviewScreen.open(changeSet(name, UNDO_KEY), "Apply " + name, new Runnable() {
            @Override
            public void run() {
                PerformanceModule.INSTANCE.activePreset.set(name);
                Notifier.post(Notifier.Kind.SYSTEM, "Performance", name + " applied", Notifier.GOOD, 1800);
            }
        });
    }

    public static void restorePrevious() {
        if (ChangeSet.restore(UNDO_KEY)) {
            PerformanceModule.INSTANCE.activePreset.set("Custom");
            Notifier.post(Notifier.Kind.SYSTEM, "Performance", "Settings from before the last preset are back", Notifier.GOOD, 2000);
        } else {
            Notifier.post(Notifier.Kind.SYSTEM, "Performance", "Nothing to restore", Notifier.WARN, 1600);
        }
    }
}
