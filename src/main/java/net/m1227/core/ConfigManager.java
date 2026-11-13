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
 * <ul>
 * <li>Writes are atomic (temp file + atomic move, plain replace where the file system has no
 * atomic move) and throttled to one every 2 s.</li>
 * <li>A failed write keeps the changes pending and is retried, so nothing is lost when the disk
 * is busy or the file is locked by another program.</li>
 * <li>A corrupt file is kept as 1227.json.corrupt-&lt;date&gt; and the newest readable backup from
 * config/1227-backups is loaded instead of the defaults.</li>
 * <li>Files carry a format version; older formats are migrated on load (see {@link #migrate}).</li>
 * </ul>
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File file;
    private static volatile boolean dirty;
    private static long lastSave;
    /** Current file format. 1 = up to 1.19, 2 = 1.20 (adds "client" and migrations). */
    public static final int FORMAT = 2;
    private static int failures;

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
        try {
            apply(read(source));
        } catch (Exception ex) {
            // a broken profile changes nothing
            Mod1227.LOGGER.warn("Could not import " + source.getName(), ex);
            return false;
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
        // cleared before reading the settings: a change made while writing marks it dirty again
        dirty = false;
        lastSave = System.currentTimeMillis();
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", FORMAT);
            root.addProperty("client", Mod1227.VERSION);
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
            writeAtomic(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            failures = 0;
        } catch (Exception ex) {
            // keep the changes pending: tick() tries again in 2 s
            dirty = true;
            if (failures++ % 30 == 0) Mod1227.LOGGER.error("Could not save config (will retry)", ex);
        }
    }

    /** Writes through a temp file in the same folder, then moves it over the target in one step. */
    public static void writeAtomic(File target, byte[] data) throws java.io.IOException {
        Path tmp = new File(target.getPath() + ".tmp").toPath();
        Files.write(tmp, data);
        try {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Brings an older file up to the current format. Each step only renames or converts entries;
     * values it does not know are left alone, so a newer file opened by an older 1227 still loads
     * everything both versions share.
     */
    static void migrate(JsonObject root) {
        int v = root.has("version") && root.get("version").isJsonPrimitive() ? root.get("version").getAsInt() : 1;
        JsonObject mods = root.has("modules") && root.get("modules").isJsonObject() ? root.getAsJsonObject("modules") : null;
        if (v < 2 && mods != null) {
            // 1.20: the "Music" HUD became "Now Playing"
            rename(mods, "Music", "Now Playing");
        }
        root.addProperty("version", FORMAT);
    }

    private static void rename(JsonObject mods, String from, String to) {
        if (mods.has(from) && !mods.has(to)) {
            mods.add(to, mods.get(from));
            mods.remove(from);
        }
    }

    /** For future migrations of a single setting inside a module. */
    static void renameSetting(JsonObject mods, String module, String from, String to) {
        if (!mods.has(module) || !mods.get(module).isJsonObject()) return;
        JsonObject m = mods.getAsJsonObject(module);
        if (!m.has("settings") || !m.get("settings").isJsonObject()) return;
        JsonObject s = m.getAsJsonObject("settings");
        if (s.has(from) && !s.has(to)) {
            s.add(to, s.get(from));
            s.remove(from);
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
            apply(read(file));
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Config unreadable, trying the newest backup", ex);
            String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
            File keep = new File(file.getPath() + ".corrupt-" + stamp);
            if (!file.renameTo(keep)) Mod1227.LOGGER.warn("Could not move the unreadable config aside");
            if (restoreNewestBackup()) {
                Notifier.post(Notifier.Kind.SYSTEM, "Config", "Settings file was damaged: restored a backup", Notifier.WARN, 4000);
                dirty = true;
                save();
            }
        }
        dirty = false;
    }

    private static JsonObject read(File f) throws Exception {
        Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8);
        try {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            migrate(root);
            return root;
        } finally {
            r.close();
        }
    }

    /** Loads the newest backup that parses. True if one was found. */
    private static boolean restoreNewestBackup() {
        File dir = new File(file.getParentFile(), "1227-backups");
        File[] all = dir.listFiles((d, n) -> n.startsWith("1227-") && n.endsWith(".json"));
        if (all == null) return false;
        java.util.Arrays.sort(all, (a, b) -> b.getName().compareTo(a.getName()));
        for (File b : all) {
            try {
                apply(read(b));
                Mod1227.LOGGER.info("Config restored from " + b.getName());
                return true;
            } catch (Exception ignored) {
                // that one is broken too: try the previous
            }
        }
        return false;
    }

    private static void apply(JsonObject root) {
        {
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
        }
    }
}
