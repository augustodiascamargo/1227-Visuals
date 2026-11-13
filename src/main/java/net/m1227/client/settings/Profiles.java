package net.m1227.client.settings;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.ConfigManager;
import net.m1227.core.Notifier;

/**
 * Profile System: named copies of the whole 1227 config in config/1227/profiles/&lt;name&gt;.json
 * (save, load, overwrite, delete), plus the automatic start-up backups in config/1227-backups.
 */
public final class Profiles {
    private Profiles() {}

    public static File dir() {
        File d = new File(new File(Mod1227.configDir, "1227"), "profiles");
        d.mkdirs();
        return d;
    }

    public static File backupsDir() {
        return new File(ConfigManager.dir(), "1227-backups");
    }

    /** Safe file name: letters, digits, space, dash and underscore only. */
    public static String clean(String name) {
        String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return s.length() > 32 ? s.substring(0, 32).trim() : s;
    }

    private static List<File> listCache, backupCache;
    private static long listAt, backupAt;

    /** Profiles (the folder is read at most once a second; the settings screen asks every frame). */
    public static List<File> list() {
        long now = System.currentTimeMillis();
        if (listCache == null || now - listAt > 1000) {
            listCache = readList();
            listAt = now;
        }
        return listCache;
    }

    public static List<File> backups() {
        long now = System.currentTimeMillis();
        if (backupCache == null || now - backupAt > 1000) {
            backupCache = readBackups();
            backupAt = now;
        }
        return backupCache;
    }

    /** Forgets the cached lists (after saving or deleting). */
    static void changed() {
        listCache = null;
        backupCache = null;
    }

    private static List<File> readList() {
        File[] f = dir().listFiles((d, n) -> n.toLowerCase().endsWith(".json"));
        List<File> out = new ArrayList<File>();
        if (f == null) return out;
        Arrays.sort(f, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        out.addAll(Arrays.asList(f));
        return out;
    }

    private static List<File> readBackups() {
        File[] f = backupsDir().listFiles((d, n) -> n.startsWith("1227-") && n.endsWith(".json"));
        List<File> out = new ArrayList<File>();
        if (f == null) return out;
        Arrays.sort(f, (a, b) -> b.getName().compareTo(a.getName()));
        out.addAll(Arrays.asList(f));
        return out;
    }

    public static String name(File f) {
        String n = f.getName();
        return n.endsWith(".json") ? n.substring(0, n.length() - 5) : n;
    }

    public static boolean save(String name) {
        String n = clean(name);
        if (n.isEmpty()) {
            Notifier.post(Notifier.Kind.SYSTEM, "Profiles", "Type a name first", Notifier.WARN, 1600);
            return false;
        }
        boolean ok = ConfigManager.exportTo(new File(dir(), n + ".json"));
        changed();
        Notifier.post(Notifier.Kind.SYSTEM, "Profile " + n, ok ? "Saved" : "Save failed", ok ? Notifier.GOOD : Notifier.BAD, 1600);
        return ok;
    }

    public static void load(File f) {
        boolean ok = ConfigManager.importFrom(f);
        Notifier.post(Notifier.Kind.SYSTEM, "Profile " + name(f), ok ? "Loaded" : "Could not load", ok ? Notifier.GOOD : Notifier.BAD, 1600);
        if (ok) net.m1227.core.Keybinds.INSTANCE.sync();
    }

    public static void delete(File f) {
        boolean ok = f.delete();
        changed();
        Notifier.post(Notifier.Kind.SYSTEM, "Profile " + name(f), ok ? "Deleted" : "Could not delete", ok ? Notifier.WARN : Notifier.BAD, 1600);
    }
}
