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
 * Performance presets: Low End, Balanced, High FPS and Visual Quality. Each one sets real
 * Minecraft video options (render distance, graphics, smooth lighting, clouds, particles,
 * entity shadows, VBOs, FPS limit, VSync) and 1227's own limits (effect quality, particle
 * budget, effect distance, entity and particle distance, Motion Blur). Every preset is editable:
 * "Save current as preset" stores the current values under that name, in
 * config/1227/performance-presets.json.
 */
public final class PerfPresets {
    public static final String[] NAMES = {"Low End", "Balanced", "High FPS", "Visual Quality"};
    public static final String[] NAMES_WITH_CUSTOM = {"Custom", "Low End", "Balanced", "High FPS", "Visual Quality"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PerfPresets() {}

    /** Values of one preset. */
    public static final class Values {
        public int renderDistance, smoothLighting, clouds, particles, maxFps, maxParticles, effectDistance, entityDistance, particleCap, particleDistance;
        public boolean fancy, shadows, vsync, vbo, motionBlur;
        public String quality;

        JsonObject json() {
            return (JsonObject) GSON.toJsonTree(this);
        }

        static Values of(JsonElement e) {
            return GSON.fromJson(e, Values.class);
        }
    }

    private static Values def(String name) {
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
        v.maxFps = gs.limitFramerate;
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

    public static void apply(String name) {
        Values v = load().get(name);
        if (v == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings gs = mc.gameSettings;
        // Minecraft options, through Minecraft's own setters (they reload what each option needs)
        gs.setOptionFloatValue(GameSettings.Options.RENDER_DISTANCE, v.renderDistance);
        if (gs.fancyGraphics != v.fancy) gs.setOptionValue(GameSettings.Options.GRAPHICS, 1);
        for (int i = 0; i < 3 && gs.ambientOcclusion != v.smoothLighting; i++) gs.setOptionValue(GameSettings.Options.AMBIENT_OCCLUSION, 1);
        for (int i = 0; i < 3 && gs.clouds != v.clouds; i++) gs.setOptionValue(GameSettings.Options.RENDER_CLOUDS, 1);
        for (int i = 0; i < 3 && gs.particleSetting != v.particles; i++) gs.setOptionValue(GameSettings.Options.PARTICLES, 1);
        if (gs.entityShadows != v.shadows) gs.setOptionValue(GameSettings.Options.ENTITY_SHADOWS, 1);
        if (gs.useVbo != v.vbo) gs.setOptionValue(GameSettings.Options.USE_VBO, 1);
        if (gs.enableVsync != v.vsync) gs.setOptionValue(GameSettings.Options.ENABLE_VSYNC, 1);
        gs.setOptionFloatValue(GameSettings.Options.FRAMERATE_LIMIT, v.maxFps);
        gs.saveOptions();
        // 1227
        PerformanceModule p = PerformanceModule.INSTANCE;
        p.quality.set(v.quality);
        p.maxParticles.setClamped(v.maxParticles);
        p.distance.setClamped(v.effectDistance);
        p.entityDistance.setClamped(v.entityDistance);
        p.particleCap.setClamped(v.particleCap);
        p.particleDistance.setClamped(v.particleDistance);
        Module mb = ModuleManager.byName("Motion Blur");
        if (mb != null && !v.motionBlur && mb.isEnabled()) mb.setEnabled(false);
        p.activePreset.set(name);
        ConfigManager.markDirty();
        Notifier.post(Notifier.Kind.SYSTEM, "Performance", name + " applied", Notifier.GOOD, 1800);
    }
}
