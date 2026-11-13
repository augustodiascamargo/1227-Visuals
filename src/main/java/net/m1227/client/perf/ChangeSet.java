package net.m1227.client.perf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

/**
 * A list of setting changes that can be shown before they happen, applied, and undone.
 *
 * Used by the performance presets, Competitive Mode and the Performance Analyzer. Before applying,
 * the current value of every item that will change is written to config/1227/undo-&lt;key&gt;.json,
 * so "Restore previous" puts back exactly what was there, even after a restart. Only items that
 * really change are listed. Each change can be unticked in the preview.
 */
public final class ChangeSet {
    public final String key, title;
    public final List<Change> changes = new ArrayList<Change>();
    /** Lines shown under the list ("not changed: ..."). */
    public final List<String> notes = new ArrayList<String>();

    public ChangeSet(String key, String title) {
        this.key = key;
        this.title = title;
    }

    public abstract static class Change {
        public final String group, what, from, to;
        public boolean enabled = true;

        Change(String group, String what, String from, String to) {
            this.group = group;
            this.what = what;
            this.from = from;
            this.to = to;
        }

        abstract JsonObject saveOld();

        abstract void apply();
    }

    // ------------------------------------------------------------------ building

    public ChangeSet module(final String name, final boolean on) {
        final Module m = ModuleManager.byName(name);
        if (m == null || !m.toggleable() || m.isEnabled() == on) return this;
        changes.add(new Change("1227 modules", name, m.isEnabled() ? "on" : "off", on ? "on" : "off") {
            @Override
            JsonObject saveOld() {
                JsonObject o = new JsonObject();
                o.addProperty("type", "module");
                o.addProperty("name", name);
                o.addProperty("on", m.isEnabled());
                return o;
            }

            @Override
            void apply() {
                m.setEnabled(on);
            }
        });
        return this;
    }

    /** A 1227 setting. value: Double / Boolean / String / Integer (colour). */
    public ChangeSet setting(final String moduleName, final String settingName, final Object value) {
        final Module m = ModuleManager.byName(moduleName);
        if (m == null) return this;
        final Setting<?> s = m.setting(settingName);
        if (s == null || !s.persistent()) return this;
        String from = shown(s.get()), to = shown(value);
        if (from.equals(to)) return this;
        changes.add(new Change(moduleName, settingName, from, to) {
            @Override
            JsonObject saveOld() {
                JsonObject o = new JsonObject();
                o.addProperty("type", "setting");
                o.addProperty("module", moduleName);
                o.addProperty("setting", settingName);
                o.add("value", s.toJson());
                return o;
            }

            @Override
            void apply() {
                setValue(s, value);
            }
        });
        return this;
    }

    @SuppressWarnings("unchecked")
    static void setValue(Setting<?> s, Object v) {
        if (s instanceof Setting.Num && v instanceof Number) ((Setting.Num) s).setClamped(((Number) v).doubleValue());
        else if (s instanceof Setting.Bool && v instanceof Boolean) ((Setting.Bool) s).set((Boolean) v);
        else if (s instanceof Setting.Mode && v instanceof String) ((Setting.Mode) s).set((String) v);
        else if (s instanceof Setting.Text && v instanceof String) ((Setting.Text) s).set((String) v);
        else if (s instanceof Setting.Col && v instanceof Integer) ((Setting.Col) s).setRgb((Integer) v);
    }

    private static String shown(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.2f", d);
        }
        if (v instanceof Number && !(v instanceof Integer)) return shown(((Number) v).doubleValue());
        if (v instanceof Boolean) return (Boolean) v ? "on" : "off";
        return String.valueOf(v);
    }

    /** A Minecraft video option (see {@link #mcGet} for names). */
    public ChangeSet mc(final String option, final int value) {
        final int now = mcGet(option);
        if (now == value || now == Integer.MIN_VALUE) return this;
        changes.add(new Change("Minecraft video", mcLabel(option), mcShown(option, now), mcShown(option, value)) {
            @Override
            JsonObject saveOld() {
                JsonObject o = new JsonObject();
                o.addProperty("type", "mc");
                o.addProperty("option", option);
                o.addProperty("value", now);
                return o;
            }

            @Override
            void apply() {
                mcSet(option, value);
            }
        });
        return this;
    }

    // ------------------------------------------------------------------ Minecraft options

    public static int mcGet(String o) {
        GameSettings gs = Minecraft.getMinecraft().gameSettings;
        if (o.equals("renderDistance")) return gs.renderDistanceChunks;
        if (o.equals("fancy")) return gs.fancyGraphics ? 1 : 0;
        if (o.equals("smoothLighting")) return gs.ambientOcclusion;
        if (o.equals("clouds")) return gs.clouds;
        if (o.equals("particles")) return gs.particleSetting;
        if (o.equals("shadows")) return gs.entityShadows ? 1 : 0;
        if (o.equals("maxFps")) return PerfSystem.userFrameLimit();
        if (o.equals("vsync")) return gs.enableVsync ? 1 : 0;
        if (o.equals("vbo")) return gs.useVbo ? 1 : 0;
        return Integer.MIN_VALUE;
    }

    /** Through Minecraft's own setters (they reload what each option needs). Saves options.txt. */
    public static void mcSet(String o, int v) {
        Minecraft mc = Minecraft.getMinecraft();
        GameSettings gs = mc.gameSettings;
        PerfSystem.restoreUserVideo();
        if (o.equals("renderDistance")) gs.setOptionFloatValue(GameSettings.Options.RENDER_DISTANCE, v);
        else if (o.equals("fancy") && (gs.fancyGraphics ? 1 : 0) != v) gs.setOptionValue(GameSettings.Options.GRAPHICS, 1);
        else if (o.equals("smoothLighting")) for (int i = 0; i < 3 && gs.ambientOcclusion != v; i++) gs.setOptionValue(GameSettings.Options.AMBIENT_OCCLUSION, 1);
        else if (o.equals("clouds")) for (int i = 0; i < 3 && gs.clouds != v; i++) gs.setOptionValue(GameSettings.Options.RENDER_CLOUDS, 1);
        else if (o.equals("particles")) for (int i = 0; i < 3 && gs.particleSetting != v; i++) gs.setOptionValue(GameSettings.Options.PARTICLES, 1);
        else if (o.equals("shadows") && (gs.entityShadows ? 1 : 0) != v) gs.setOptionValue(GameSettings.Options.ENTITY_SHADOWS, 1);
        else if (o.equals("maxFps")) gs.setOptionFloatValue(GameSettings.Options.FRAMERATE_LIMIT, v);
        else if (o.equals("vsync") && (gs.enableVsync ? 1 : 0) != v) gs.setOptionValue(GameSettings.Options.ENABLE_VSYNC, 1);
        else if (o.equals("vbo") && (gs.useVbo ? 1 : 0) != v) gs.setOptionValue(GameSettings.Options.USE_VBO, 1);
        gs.saveOptions();
    }

    private static String mcLabel(String o) {
        if (o.equals("renderDistance")) return "Render distance";
        if (o.equals("fancy")) return "Graphics";
        if (o.equals("smoothLighting")) return "Smooth lighting";
        if (o.equals("clouds")) return "Clouds";
        if (o.equals("particles")) return "Particles";
        if (o.equals("shadows")) return "Entity shadows";
        if (o.equals("maxFps")) return "Max framerate";
        if (o.equals("vsync")) return "VSync";
        if (o.equals("vbo")) return "Use VBOs";
        return o;
    }

    private static String mcShown(String o, int v) {
        if (o.equals("renderDistance")) return v + " chunks";
        if (o.equals("fancy")) return v == 1 ? "Fancy" : "Fast";
        if (o.equals("smoothLighting")) return v == 0 ? "Off" : v == 1 ? "Minimum" : "Maximum";
        if (o.equals("clouds")) return v == 0 ? "Off" : v == 1 ? "Fast" : "Fancy";
        if (o.equals("particles")) return v == 0 ? "All" : v == 1 ? "Decreased" : "Minimal";
        if (o.equals("maxFps")) return v >= 260 ? "Unlimited" : v + " FPS";
        return v == 1 ? "On" : "Off";
    }

    // ------------------------------------------------------------------ apply / undo

    private static File undoFile(String key) {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "undo-" + key.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json");
    }

    public boolean any() {
        for (Change c : changes) if (c.enabled) return true;
        return false;
    }

    /** Saves the old values of the ticked changes, then applies them. */
    public void apply() {
        JsonArray old = new JsonArray();
        for (Change c : changes) if (c.enabled) old.add(c.saveOld());
        try {
            Files.write(undoFile(key).toPath(), old.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not save the undo file for " + key, e);
        }
        for (Change c : changes) {
            if (!c.enabled) continue;
            try {
                c.apply();
            } catch (RuntimeException e) {
                Mod1227.LOGGER.warn("Could not apply " + c.what, e);
            }
        }
        ConfigManager.markDirty();
    }

    public static boolean canRestore(String key) {
        return undoFile(key).isFile();
    }

    /** Puts back what was there before the last apply with this key. True if something was restored. */
    public static boolean restore(String key) {
        File f = undoFile(key);
        if (!f.isFile()) return false;
        try {
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (JsonElement el : root.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                String type = o.get("type").getAsString();
                if (type.equals("module")) {
                    Module m = ModuleManager.byName(o.get("name").getAsString());
                    if (m != null) m.setEnabled(o.get("on").getAsBoolean());
                } else if (type.equals("setting")) {
                    Module m = ModuleManager.byName(o.get("module").getAsString());
                    Setting<?> s = m == null ? null : m.setting(o.get("setting").getAsString());
                    if (s != null) s.fromJson(o.get("value"));
                } else if (type.equals("mc")) {
                    mcSet(o.get("option").getAsString(), o.get("value").getAsInt());
                }
            }
            f.delete();
            ConfigManager.markDirty();
            return true;
        } catch (Exception e) {
            Mod1227.LOGGER.warn("Could not restore " + key, e);
            return false;
        }
    }
}
