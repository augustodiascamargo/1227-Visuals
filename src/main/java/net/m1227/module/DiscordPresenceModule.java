package net.m1227.module;

import com.google.gson.JsonObject;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Discord Rich Presence through Discord's local IPC pipe (the official way games do it; no
 * account access, no token). Your profile shows "Playing <your Discord application's name>":
 * create an application called "1227Visuals" in the Discord Developer Portal and paste its
 * Application ID here. Windows only (Discord's pipe is \\.\pipe\discord-ipc-N).
 */
public final class DiscordPresenceModule extends Module {
    private final Setting.Text appId;
    private final Setting.Bool showServer, showTime;
    private final Setting.Text details;

    private volatile boolean running;
    private Thread worker;
    private final long started = System.currentTimeMillis() / 1000L;

    public DiscordPresenceModule() {
        super("Discord Presence", "Shows \"Playing 1227Visuals\" on your Discord profile.", Category.MUSIC, false);
        appId = add(new Setting.Text("Discord Application ID", "", 24, "paste the ID of your 1227Visuals app"));
        details = add(new Setting.Text("First line", "Minecraft 1.8.9", 64, "text under the game name"));
        showServer = add(new Setting.Bool("Show server / singleplayer", true));
        showTime = add(new Setting.Bool("Show elapsed time", true));
    }

    @Override
    protected void onEnable() {
        running = true;
        worker = new Thread(this::loop, "1227-discord-presence");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    protected void onDisable() {
        running = false;
        if (worker != null) worker.interrupt();
    }

    private RandomAccessFile pipe;

    private void loop() {
        String lastId = "";
        String lastSent = null;
        boolean warned = false;
        while (running) {
            try {
                String id = appId.get().trim();
                if (!id.matches("\\d{15,22}")) {
                    if (!warned) {
                        warned = true;
                        Notifier.post(Notifier.Kind.SYSTEM, "Discord Presence", "Paste your Application ID in the module settings", Notifier.WARN, 3000);
                    }
                    close();
                    Thread.sleep(3000);
                    continue;
                }
                warned = false;
                if (pipe == null || !id.equals(lastId)) {
                    close();
                    open(id);
                    lastId = id;
                    lastSent = null;
                }
                JsonObject act = activity();
                String key = act.toString();
                if (!key.equals(lastSent)) {
                    send(1, command(act));
                    read(); // Discord answers every command; read it so the pipe doesn't fill up
                    lastSent = key;
                }
                Thread.sleep(15000); // Discord rate-limits presence updates (5 per 20 s)
            } catch (InterruptedException ie) {
                break;
            } catch (Exception ex) {
                close();
                try {
                    Thread.sleep(15000); // Discord closed or not running: try again later
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        close();
    }

    private void open(String id) throws Exception {
        Exception last = null;
        for (int i = 0; i < 10; i++) {
            try {
                pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                break;
            } catch (Exception ex) {
                last = ex;
            }
        }
        if (pipe == null) throw last == null ? new Exception("no pipe") : last;
        JsonObject hs = new JsonObject();
        hs.addProperty("v", 1);
        hs.addProperty("client_id", id);
        send(0, hs.toString());
        String reply = read();
        if (!reply.contains("READY")) throw new Exception("handshake refused: " + reply);
        Mod1227.LOGGER.info("Discord Rich Presence connected");
    }

    private void close() {
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Exception ignored) {
                // already closed
            }
        }
        pipe = null;
    }

    private void send(int op, String json) throws Exception {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer bb = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(op).putInt(b.length).put(b);
        pipe.write(bb.array());
    }

    private String read() throws Exception {
        byte[] head = new byte[8];
        pipe.readFully(head);
        ByteBuffer bb = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        bb.getInt();
        int len = bb.getInt();
        if (len < 0 || len > 1 << 20) throw new Exception("bad frame");
        byte[] body = new byte[len];
        pipe.readFully(body);
        return new String(body, StandardCharsets.UTF_8);
    }

    private JsonObject activity() {
        Minecraft mc = Minecraft.getMinecraft();
        JsonObject act = new JsonObject();
        String d = details.get().trim();
        if (!d.isEmpty()) act.addProperty("details", d);
        if (showServer.on()) {
            ServerData sd = mc.getCurrentServerData();
            String state = mc.theWorld == null ? "In the menus" : mc.isSingleplayer() || sd == null ? "Singleplayer" : "Playing on " + sd.serverIP;
            act.addProperty("state", state);
        }
        if (showTime.on()) {
            JsonObject ts = new JsonObject();
            ts.addProperty("start", started);
            act.add("timestamps", ts);
        }
        return act;
    }

    private static String command(JsonObject act) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid());
        args.add("activity", act);
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", Long.toString(System.nanoTime()));
        return cmd.toString();
    }

    private static int pid() {
        try {
            String n = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Integer.parseInt(n.substring(0, n.indexOf('@')));
        } catch (Exception e) {
            return 0;
        }
    }
}
