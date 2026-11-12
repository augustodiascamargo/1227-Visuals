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
        backupOnStart();
        load();
    }

    /** Folder that holds 1227's files (config/). */
    public static File dir() {
        return file == null ? new File("config") : file.getParentFile();
    }

    /**
     * Keeps a copy of the config from the start of each of the last 5 game sessions in
     * config/1227-backups/, so a bad change or a broken file can be rolled back by hand
     * (copy a backup over config/1227.json while the game is closed).
     */
    private static void backupOnStart() {
        try {
            if (!file.isFile() || file.length() == 0) return;
            File dir = new File(file.getParentFile(), "1227-backups");
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            Files.copy(file.toPath(), new File(dir, "1227-" + stamp + ".json").toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] all = dir.listFiles((d, n) -> n.startsWith("1227-") && n.endsWith(".json"));
            if (all != null && all.length > 5) {
                java.util.Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
                for (int i = 0; i < all.length - 5; i++) all[i].delete();
            }
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not back up the 1227 config", ex);
        }
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

    /** Copies the whole config as JSON text to the clipboard. */
    public static synchronized boolean exportToClipboard() {
        try {
            File tmp = File.createTempFile("1227-export", ".json");
            tmp.deleteOnExit();
            if (!exportTo(tmp)) return false;
            String json = new String(Files.readAllBytes(tmp.toPath()), StandardCharsets.UTF_8);
            tmp.delete();
            net.minecraft.client.gui.GuiScreen.setClipboardString(json);
            Notifier.post(Notifier.Kind.SYSTEM, "Config", "Copied to clipboard", Notifier.GOOD, 1800);
            return true;
        } catch (Exception ex) {
            Mod1227.LOGGER.error("Could not export config", ex);
            Notifier.post(Notifier.Kind.SYSTEM, "Config", "Export failed", Notifier.BAD, 1800);
            return false;
        }
    }

    /** Reads a config exported with {@link #exportToClipboard} from the clipboard. */
    public static synchronized boolean importFromClipboard() {
        String text = net.minecraft.client.gui.GuiScreen.getClipboardString();
        boolean ok = false;
        try {
            if (text != null && text.trim().startsWith("{")) {
                JsonObject root = new JsonParser().parse(text).getAsJsonObject();
                if (root.has("modules")) {
                    File tmp = File.createTempFile("1227-import", ".json");
                    tmp.deleteOnExit();
                    Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
                    ok = importFrom(tmp);
                    tmp.delete();
                }
            }
        } catch (Exception ex) {
            ok = false;
        }
        Notifier.post(Notifier.Kind.SYSTEM, "Config", ok ? "Imported from clipboard" : "Clipboard has no 1227 config",
                ok ? Notifier.GOOD : Notifier.BAD, 2000);
        return ok;
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
                if (m.favorite) o.addProperty("favorite", true);
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
                    m.favorite = o.has("favorite") && o.get("favorite").getAsBoolean();
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
