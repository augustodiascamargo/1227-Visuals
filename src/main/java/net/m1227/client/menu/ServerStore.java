package net.m1227.client.menu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.m1227.Mod1227;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server favourites and join history, kept in config/1227/servers.json (Minecraft's own server
 * list stays in servers.dat, untouched). History is recorded when you actually join a server.
 */
public final class ServerStore {
    public static final ServerStore INSTANCE = new ServerStore();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_HISTORY = 25;

    public static final class Visit {
        public final String name, ip;
        public final long time;

        Visit(String name, String ip, long time) {
            this.name = name;
            this.ip = ip;
            this.time = time;
        }
    }

    private final Set<String> favorites = new LinkedHashSet<String>();
    private final List<Visit> history = new ArrayList<Visit>();
    private boolean loaded;
    private Object lastWorld;

    private ServerStore() {}

    private static File file() {
        File d = new File(Mod1227.configDir, "1227");
        d.mkdirs();
        return new File(d, "servers.json");
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        try {
            File f = file();
            if (!f.isFile()) return;
            JsonElement root = new JsonParser().parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            if (!root.isJsonObject()) return;
            JsonObject o = root.getAsJsonObject();
            if (o.has("favorites")) for (JsonElement e : o.getAsJsonArray("favorites")) favorites.add(e.getAsString().toLowerCase());
            if (o.has("history")) {
                for (JsonElement e : o.getAsJsonArray("history")) {
                    JsonObject v = e.getAsJsonObject();
                    history.add(new Visit(v.get("name").getAsString(), v.get("ip").getAsString(), v.get("time").getAsLong()));
                }
            }
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not read 1227 servers.json", ex);
        }
    }

    private void save() {
        try {
            JsonObject o = new JsonObject();
            JsonArray f = new JsonArray();
            for (String s : favorites) f.add(new com.google.gson.JsonPrimitive(s));
            o.add("favorites", f);
            JsonArray h = new JsonArray();
            for (Visit v : history) {
                JsonObject e = new JsonObject();
                e.addProperty("name", v.name);
                e.addProperty("ip", v.ip);
                e.addProperty("time", v.time);
                h.add(e);
            }
            o.add("history", h);
            File out = file(), tmp = new File(out.getPath() + ".tmp");
            Files.write(tmp.toPath(), GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            Mod1227.LOGGER.warn("Could not save 1227 servers.json", ex);
        }
    }

    public boolean favorite(String ip) {
        load();
        return ip != null && favorites.contains(ip.toLowerCase());
    }

    public void toggleFavorite(String ip) {
        load();
        String k = ip.toLowerCase();
        if (!favorites.remove(k)) favorites.add(k);
        save();
    }

    public List<String> favorites() {
        load();
        return new ArrayList<String>(favorites);
    }

    public List<Visit> history() {
        load();
        return new ArrayList<Visit>(history);
    }

    public void clearHistory() {
        load();
        history.clear();
        save();
    }

    /** The last server you joined, as a ServerData (for Reconnect). */
    public static ServerData lastServer() {
        INSTANCE.load();
        if (INSTANCE.history.isEmpty()) return null;
        Visit v = INSTANCE.history.get(0);
        return new ServerData(v.name, v.ip, false);
    }

    private void record(ServerData sd) {
        load();
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).ip.equalsIgnoreCase(sd.serverIP)) history.remove(i);
        history.add(0, new Visit(sd.serverName == null ? sd.serverIP : sd.serverName, sd.serverIP, System.currentTimeMillis()));
        while (history.size() > MAX_HISTORY) history.remove(history.size() - 1);
        save();
    }

    /** Records each multiplayer world you enter once. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            lastWorld = null;
            return;
        }
        if (mc.theWorld == lastWorld) return;
        lastWorld = mc.theWorld;
        ServerData sd = mc.getCurrentServerData();
        if (sd != null && !mc.isIntegratedServerRunning() && sd.serverIP != null) record(sd);
    }
}
