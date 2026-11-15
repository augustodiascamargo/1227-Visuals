package net.m1227.social;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.m1227.Mod1227;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * 1227 Link without a server plugin: an online relay (a small Cloudflare Worker you can publish
 * for free, see server/1227-cloud-relay). This is how clients such as Lunar do it too: the
 * players' clients talk to the client's own service, not to the Minecraft server.
 *
 * <ul>
 * <li>Rooms: players are grouped by the server they are on. The room id is a SHA-256 hash of the
 * server address, so the relay never learns which server it is.</li>
 * <li>Identity: before connecting, the client asks Mojang's session server to note a random id,
 * exactly as it does when it joins a server (your token goes only to Mojang). The relay then asks
 * Mojang whether this player really did, so nobody can show cosmetics on someone else's name.</li>
 * <li>Sent to the relay: your UUID, your name, the hashed room and the same cosmetic messages as
 * the plugin route (shared cosmetics, emotes). Nothing about the game itself.</li>
 * <li>Connection: one TLS WebSocket, a ping every 25 s, reconnecting after 5 s, 10 s, ... up to
 * 2 minutes when it drops. Off in singleplayer and when no relay address is set.</li>
 * </ul>
 */
public final class CloudLink {
    public static final CloudLink INSTANCE = new CloudLink();
    private static final SecureRandom RANDOM = new SecureRandom();

    final ConcurrentLinkedQueue<byte[]> inbox = new ConcurrentLinkedQueue<byte[]>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "1227-Link-Send");
        t.setDaemon(true);
        return t;
    });
    private volatile WsClient ws;
    private volatile boolean linked, connecting;
    public volatile String status = "off";
    private volatile int generation;
    private String room, url;
    private long nextAttempt;
    private int failures;

    private CloudLink() {}

    public boolean linked() {
        return linked && ws != null && ws.isOpen();
    }

    void markLinked() {
        linked = true;
        failures = 0;
        status = "linked";
    }

    public static String roomOf(String address) {
        return LinkProtocol.roomOf(address);
    }

    /** Main thread, every tick: connects, switches room or disconnects as needed. */
    void update(Minecraft mc, boolean want, String relayUrl) {
        ServerData sd = mc.getCurrentServerData();
        String target = !want || relayUrl == null || relayUrl.trim().isEmpty() || mc.isSingleplayer() || sd == null || mc.thePlayer == null
                ? null : roomOf(sd.serverIP);
        if (target == null) {
            stop(want && (relayUrl == null || relayUrl.trim().isEmpty()) ? "no relay address set" : "off");
            room = null;
            return;
        }
        String u = relayUrl.trim();
        if (!target.equals(room) || !u.equals(url)) {
            stop("switching room");
            room = target;
            url = u;
            nextAttempt = 0L;
            failures = 0;
        }
        long now = System.currentTimeMillis();
        if (ws == null && !connecting && now >= nextAttempt) start(mc);
    }

    private void start(Minecraft mc) {
        connecting = true;
        status = "connecting";
        final int gen = ++generation;
        final GameProfile profile = mc.getSession().getProfile();
        final String token = mc.getSession().getToken();
        final MinecraftSessionService sessions = mc.getSessionService();
        final String roomId = room, base = url;
        Thread t = new Thread(() -> connect(gen, profile, token, sessions, roomId, base), "1227-Link-Connect");
        t.setDaemon(true);
        t.start();
    }

    private void connect(int gen, GameProfile profile, String token, MinecraftSessionService sessions, String roomId, String base) {
        try {
            byte[] r = new byte[16];
            RANDOM.nextBytes(r);
            StringBuilder sid = new StringBuilder();
            for (byte b : r) sid.append(String.format("%02x", b & 0xFF));
            boolean verified;
            try {
                // the same call Minecraft makes when joining a server: proves to Mojang that this
                // session is this player; the relay checks it with Mojang's hasJoined
                sessions.joinServer(profile, token, sid.toString());
                verified = true;
            } catch (Exception e) {
                verified = false; // offline account or Mojang unreachable: the relay decides
            }
            String uuid = profile.getId() == null ? "" : profile.getId().toString().replace("-", "");
            String full = base.replaceAll("/+$", "") + "/v1/room/" + roomId + "?u=" + uuid + "&n="
                    + URLEncoder.encode(profile.getName(), "UTF-8") + "&s=" + sid + "&v=" + (verified ? 1 : 0) + "&p=" + LinkProtocol.VERSION;
            WsClient c = WsClient.connect(full, 8000, Tls.socketFactory(), "1227/" + Mod1227.VERSION, new WsClient.Listener() {
                @Override
                public void onBinary(byte[] data) {
                    if (data.length <= LinkProtocol.MAX_BYTES && inbox.size() < 512) inbox.add(data);
                }

                @Override
                public void onClose(String reason) {
                    if (gen != generation) return;
                    ws = null;
                    linked = false;
                    status = "disconnected (" + reason + ")";
                    retryLater();
                }
            });
            if (gen != generation) {
                c.close();
                return;
            }
            ws = c;
            status = "waiting for the relay";
            // keep the connection alive through proxies and idle timeouts
            while (gen == generation && c.isOpen()) {
                Thread.sleep(25000);
                if (gen == generation && c.isOpen()) c.ping();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (gen == generation) {
                status = "could not connect (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")";
                Mod1227.LOGGER.info("1227 Link relay: " + status);
                retryLater();
            }
        } finally {
            if (gen == generation) connecting = false;
        }
    }

    private void retryLater() {
        failures = Math.min(failures + 1, 6);
        long wait = Math.min(120_000L, 5_000L << (failures - 1));
        nextAttempt = System.currentTimeMillis() + wait;
        connecting = false;
    }

    void send(final byte[] msg) {
        final WsClient c = ws;
        if (c == null || !linked) return;
        sender.execute(() -> {
            try {
                c.sendBinary(msg);
            } catch (Exception ignored) {
                // the reader thread reports the closed connection
            }
        });
    }

    void stop(String why) {
        generation++;
        WsClient c = ws;
        ws = null;
        linked = false;
        connecting = false;
        status = why;
        inbox.clear();
        if (c != null) sender.execute(c::close);
    }
}
