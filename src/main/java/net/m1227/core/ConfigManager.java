package net.m1227.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.m1227.Mod1227;

/**
 * Persists module state and settings to config/1227.json (older lumenvisuals.json is imported once).
 * Writes are atomic (temp file + move) and throttled; a corrupt file is
 * moved aside instead of crashing the game.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;
    private static volatile boolean dirty;
    private static long lastSave;

    private ConfigManager() {}

    public static void init(File configDir) {
        file = new File(configDir, "1227.json");
        load();
    }

    /** Writes the current settings to another file (profiles). */
    public static synchronized boolean exportTo(File target) {
        File current = file;
        try {
            file = target;
            dirty = true;
            save();
            return target.isFile();
        } finally {
            file = current;
        }
    }

    /** Loads settings from another file (profiles), then saves them as the active config. */
    public static synchronized boolean importFrom(File source) {
        if (!source.isFile()) return false;
        File current = file;
        try {
            file = source;
            load();
        } finally {
            file = current;
        }
        dirty = true;
        save();
        return true;
    }

    public static void markDirty() {
        dirty = true;
    }

    /** Called every client tick; saves at most once every 2 seconds. */
    public static void tick() {
        if (dirty && file != null && System.currentTimeMillis() - lastSave > 2000L) save();
    }

    public static synchronized void save() {
        if (file == null || !dirty) return;
        dirty = false;
        lastSave = System.currentTimeMillis();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", 1);
            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.all()) {
                JsonObject o = new JsonObject();
                if (m.toggleable()) {
                    o.addProperty("enabled", m.isEnabled());
                    o.addProperty("key", m.key);
                }
                JsonObject s = new JsonObject();
                for (Setting<?> st : m.settings()) {
                    if (st.persistent()) s.add(st.name, st.toJson());
                }
                o.add("settings", s);
                mods.add(m.name, o);
            }
            root.add("modules", mods);

            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            Path tmp = new File(file.getPath() + ".tmp").toPath();
            Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            Mod1227.LOGGER.error("Could not save config", ex);
        }
    }

    private static void load() {
        if (!file.isFile()) {
            File legacy = new File(file.getParentFile(), "lumenvisuals.json");
            if (legacy.isFile()) {
                File current = file;
                file = legacy;
                load();
                file = current;
                dirty = true;
                save();
                return;
            }
            dirty = true;
            save();
            return;
        }
        try {
            Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8);
            JsonObject root;
            try {
                root = new JsonParser().parse(r).getAsJsonObject();
            } finally {
                r.close();
            }
            JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : null;
            if (mods != null) {
                for (Module m : ModuleManager.all()) {
                    JsonElement me = mods.get(m.name);
                    if (me == null || !me.isJsonObject()) continue;
                    JsonObject o = me.getAsJsonObject();
                    if (m.toggleable()) {
                        if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
                        if (o.has("key")) m.key = o.get("key").getAsInt();
                    }
                    if (o.has("settings") && o.get("settings").isJsonObject()) {
                        JsonObject s = o.getAsJsonObject("settings");
                        for (Setting<?> st : m.settings()) {
                            JsonElement se = s.get(st.name);
                            if (se == null) continue;
                            try {
                                st.fromJson(se);
                            } catch (RuntimeException ignored) {
                                // keep the default for this one setting
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Config unreadable, starting from defaults", ex);
            File bak = new File(file.getPath() + ".bak");
            if (bak.exists()) bak.delete();
            file.renameTo(bak);
        }
        dirty = false;
    }
}
