package net.m1227.hud;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Notifier;

/**
 * Saved HUD layouts: position, scale and on/off of every HUD element. Three slots are kept in
 * config/1227-hud-layouts.json, and a layout can be copied to / pasted from the clipboard to
 * share it.
 */
public final class HudLayouts {
    private HudLayouts() {}

    private static File file() {
        return new File(ConfigManager.dir(), "1227-hud-layouts.json");
    }

    public static JsonObject capture() {
        JsonObject o = new JsonObject();
        for (Module m : ModuleManager.inCategory(Category.HUD)) {
            if (!(m instanceof HudModule)) continue;
            HudModule h = (HudModule) m;
            JsonObject e = new JsonObject();
            e.addProperty("x", h.posX.get());
            e.addProperty("y", h.posY.get());
            e.addProperty("scale", h.scale.get());
            e.addProperty("on", h.isEnabled());
            o.add(h.name, e);
        }
        return o;
    }

    public static boolean apply(JsonObject o) {
        boolean any = false;
        for (Module m : ModuleManager.inCategory(Category.HUD)) {
            // layouts saved before 1.20 call the Now Playing widget "Music"
            String key = o.has(m.name) ? m.name : m.name.equals("Now Playing") && o.has("Music") ? "Music" : m.name;
            if (!(m instanceof HudModule) || !o.has(key) || !o.get(key).isJsonObject()) continue;
            HudModule h = (HudModule) m;
            JsonObject e = o.getAsJsonObject(key);
            try {
                if (e.has("x")) h.posX.set(clamp(e.get("x").getAsDouble(), 0, 1));
                if (e.has("y")) h.posY.set(clamp(e.get("y").getAsDouble(), 0, 1));
                if (e.has("scale")) h.scale.set(clamp(e.get("scale").getAsDouble(), 0.5, 2.5));
                if (e.has("on") && h.toggleable()) h.setEnabled(e.get("on").getAsBoolean());
                any = true;
            } catch (RuntimeException ignored) {
                // a bad entry only skips that element
            }
        }
        if (any) ConfigManager.markDirty();
        return any;
    }

    private static double clamp(double v, double a, double b) {
        return Double.isNaN(v) ? a : Math.max(a, Math.min(b, v));
    }

    private static JsonObject readAll() {
        try {
            File f = file();
            if (!f.isFile()) return new JsonObject();
            JsonElement e = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            return new JsonObject();
        }
    }

    private static JsonObject cache;
    private static long cacheAt;

    /** Same as reading the file, but cached for a second (the editor asks every frame). */
    public static boolean hasSlotCached(int slot) {
        long now = System.currentTimeMillis();
        if (cache == null || now - cacheAt > 1000L) {
            cache = readAll();
            cacheAt = now;
        }
        return cache.has(String.valueOf(slot));
    }

    public static void save(int slot) {
        JsonObject all = readAll();
        all.add(String.valueOf(slot), capture());
        try {
            File f = file(), tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(all).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            cache = null;
            Notifier.post(Notifier.Kind.SYSTEM, "HUD layout", "Saved to slot " + slot, Notifier.GOOD, 1600);
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not save HUD layout", ex);
            Notifier.post(Notifier.Kind.SYSTEM, "HUD layout", "Could not save", Notifier.BAD, 1600);
        }
    }

    public static void load(int slot) {
        JsonObject all = readAll();
        String k = String.valueOf(slot);
        boolean ok = all.has(k) && all.get(k).isJsonObject() && apply(all.getAsJsonObject(k));
        Notifier.post(Notifier.Kind.SYSTEM, "HUD layout", ok ? "Loaded slot " + slot : "Slot " + slot + " is empty",
                ok ? Notifier.GOOD : Notifier.BAD, 1600);
    }

    public static void copy() {
        JsonObject o = new JsonObject();
        o.addProperty("type", "1227-hud-layout");
        o.add("layout", capture());
        net.minecraft.client.gui.GuiScreen.setClipboardString(o.toString());
        Notifier.post(Notifier.Kind.SYSTEM, "HUD layout", "Copied to clipboard", Notifier.GOOD, 1600);
    }

    public static void paste() {
        boolean ok = false;
        try {
            String t = net.minecraft.client.gui.GuiScreen.getClipboardString();
            JsonObject o = new JsonParser().parse(t).getAsJsonObject();
            if ("1227-hud-layout".equals(o.get("type").getAsString())) ok = apply(o.getAsJsonObject("layout"));
        } catch (Exception ignored) {
            ok = false;
        }
        Notifier.post(Notifier.Kind.SYSTEM, "HUD layout", ok ? "Pasted from clipboard" : "Clipboard has no HUD layout",
                ok ? Notifier.GOOD : Notifier.BAD, 1800);
    }
}
