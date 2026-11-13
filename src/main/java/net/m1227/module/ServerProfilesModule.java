package net.m1227.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.m1227.client.settings.Profiles;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server profiles: link a saved 1227 profile (Settings > Profiles: "Hypixel", "Practice",
 * "BedWars"...) to a server address, or to singleplayer. When you join, the linked profile is
 * applied automatically, or just suggested, as you choose. Links are kept in
 * config/1227/server-profiles.json on your computer; nothing is sent anywhere.
 */
public final class ServerProfilesModule extends Module {
    private final Setting.Mode onJoin;
    private final Setting.Text profileName;
    private String lastContext = "";

    public ServerProfilesModule() {
        super("Server Profiles", "Apply a saved profile automatically on the servers you link it to.", Category.SETTINGS, false);
        onJoin = add(new Setting.Mode("When joining", "Apply", "Apply", "Only suggest"));
        profileName = add(new Setting.Text("Profile to link", "", 40, "name of a saved profile"));
        add(new Setting.Action("Link this server to that profile", this::link));
        add(new Setting.Action("Unlink this server", this::unlink));
        add(new Setting.Action("Apply the linked profile now", () -> {
            String p = linked(context());
            if (p != null) apply(p, true);
            else Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", "No profile linked here", Notifier.INFO, 1800);
        }));
    }

    /** "singleplayer" or the server address you are on, or "" in menus. */
    public static String context() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return "";
        if (mc.isSingleplayer()) return "singleplayer";
        if (mc.getCurrentServerData() != null) return mc.getCurrentServerData().serverIP.toLowerCase().trim();
        return "";
    }

    private static File file() {
        File d = new File(ConfigManager.dir(), "1227");
        d.mkdirs();
        return new File(d, "server-profiles.json");
    }

    private static JsonObject read() {
        try {
            File f = file();
            if (f.isFile()) return new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ignored) {
            // start empty
        }
        return new JsonObject();
    }

    private static void write(JsonObject o) {
        try {
            ConfigManager.writeAtomic(file(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // shown by the caller's notification
        }
    }

    public static String linked(String ctx) {
        if (ctx.isEmpty()) return null;
        JsonObject o = read();
        // "mc.hypixel.net" also matches a link made for "hypixel.net"
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
            if (ctx.equals(e.getKey()) || ctx.endsWith("." + e.getKey())) return e.getValue().getAsString();
        }
        return null;
    }

    private File profileFile(String name) {
        for (File f : Profiles.list()) {
            if (Profiles.name(f).equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    private void link() {
        String ctx = context(), name = profileName.get().trim();
        if (ctx.isEmpty()) {
            Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", "Join the server first", Notifier.WARN, 1800);
            return;
        }
        if (profileFile(name) == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", "No saved profile called \"" + name + "\"", Notifier.BAD, 2200);
            return;
        }
        JsonObject o = read();
        o.addProperty(ctx, name);
        write(o);
        Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", ctx + " → " + name, Notifier.GOOD, 2000);
    }

    private void unlink() {
        String ctx = context();
        JsonObject o = read();
        if (o.has(ctx)) {
            o.remove(ctx);
            write(o);
            Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", "Unlinked " + ctx, Notifier.GOOD, 1800);
        }
    }

    private void apply(String name, boolean now) {
        File f = profileFile(name);
        if (f == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Server profiles", "Linked profile \"" + name + "\" was deleted", Notifier.WARN, 2500);
            return;
        }
        if (onJoin.is("Apply") || now) {
            Profiles.load(f);
            Notifier.post(Notifier.Kind.SYSTEM, "Profile applied", name, Notifier.GOOD, 2200);
        } else {
            Notifier.post(Notifier.Kind.SYSTEM, "Profile for this server", name + " (Settings → Profiles to load it)", Notifier.INFO, 3500);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        String ctx = context();
        if (ctx.equals(lastContext)) return;
        lastContext = ctx;
        String p = linked(ctx);
        if (p != null) apply(p, false);
    }
}
