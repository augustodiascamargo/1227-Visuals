package net.m1227.relay;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * 1227 Relay: the small server part of 1227 Link. A client mod cannot make a server pass data
 * between players, so this plugin does it, for cosmetic data only (shared cosmetics and emotes).
 *
 * What it does:
 * <ul>
 * <li>when a player's client registers the "1227|fx" channel, answers WELCOME and sends the last
 * known state of the other linked players in the same world;</li>
 * <li>for every message from a linked player: checks the header (magic 0x27, version 1, a known
 * client type: HELLO, STATE, EMOTE, STOP), the size (256 bytes at most) and the rate (default 8
 * per second, extra ones dropped), then forwards it to the other linked players in the same world
 * as RELAY with the sender's UUID. The body is passed on unchanged; clients validate it;</li>
 * <li>keeps the last STATE of each player for players who join later;</li>
 * <li>LEAVE when a player quits or changes world.</li>
 * </ul>
 * It never touches gameplay, positions, combat or any other packet, and players without 1227 are
 * not affected at all.
 */
public final class RelayPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    static final String CHANNEL = "1227|fx";
    static final byte MAGIC = 0x27, VERSION = 1;
    static final byte HELLO = 1, STATE = 2, EMOTE = 3, STOP = 4;
    static final byte WELCOME = 0x41, RELAY = 0x42, LEAVE = 0x43;
    static final int MAX_BYTES = 256;

    private final Map<UUID, Linked> linked = new HashMap<UUID, Linked>();
    private int rate;
    private String name;
    private boolean sameWorld;

    private static final class Linked {
        byte[] lastState;
        float tokens;
        long tokensAt;
        long dropped;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rate = Math.max(1, Math.min(50, getConfig().getInt("max-messages-per-second", 8)));
        name = getConfig().getString("name", "1227 Relay");
        sameWorld = getConfig().getBoolean("same-world-only", true);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("1227 Relay on channel " + CHANNEL + " (" + rate + " messages/s per player)");
    }

    @Override
    public void onDisable() {
        linked.clear();
    }

    // ------------------------------------------------------------------ messages

    static byte[] header(byte type, int extra) {
        byte[] b = new byte[3 + extra];
        b[0] = MAGIC;
        b[1] = VERSION;
        b[2] = type;
        return b;
    }

    private byte[] welcome() {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bo);
            d.writeByte(MAGIC);
            d.writeByte(VERSION);
            d.writeByte(WELCOME);
            d.writeByte(rate);
            byte[] n = name.getBytes(StandardCharsets.UTF_8);
            int len = Math.min(32, n.length);
            d.writeByte(len);
            d.write(n, 0, len);
            return bo.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] relay(UUID from, byte[] msg) {
        byte[] out = new byte[3 + 16 + 1 + (msg.length - 3)];
        out[0] = MAGIC;
        out[1] = VERSION;
        out[2] = RELAY;
        long m = from.getMostSignificantBits(), l = from.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            out[3 + i] = (byte) (m >>> (56 - i * 8));
            out[11 + i] = (byte) (l >>> (56 - i * 8));
        }
        out[19] = msg[2];
        System.arraycopy(msg, 3, out, 20, msg.length - 3);
        return out;
    }

    static byte[] leave(UUID who) {
        byte[] out = header(LEAVE, 16);
        long m = who.getMostSignificantBits(), l = who.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            out[3 + i] = (byte) (m >>> (56 - i * 8));
            out[11 + i] = (byte) (l >>> (56 - i * 8));
        }
        return out;
    }

    static boolean valid(byte[] m) {
        return m != null && m.length >= 3 && m.length <= MAX_BYTES && m[0] == MAGIC && m[1] == VERSION && m[2] >= HELLO && m[2] <= STOP;
    }

    private void send(Player p, byte[] data) {
        if (p.isOnline() && p.getListeningPluginChannels().contains(CHANNEL)) p.sendPluginMessage(this, CHANNEL, data);
    }

    private boolean audience(Player from, Player to) {
        return to != from && linked.containsKey(to.getUniqueId()) && (!sameWorld || to.getWorld().equals(from.getWorld()));
    }

    private void link(Player p) {
        Linked l = linked.get(p.getUniqueId());
        if (l == null) {
            l = new Linked();
            l.tokens = rate;
            linked.put(p.getUniqueId(), l);
        }
        send(p, welcome());
        sendStatesTo(p);
    }

    /** The last known state of every other linked player this player can see. */
    private void sendStatesTo(Player p) {
        for (Player o : getServer().getOnlinePlayers()) {
            if (!audience(p, o)) continue;
            Linked l = linked.get(o.getUniqueId());
            if (l != null && l.lastState != null) send(p, relay(o.getUniqueId(), l.lastState));
        }
    }

    @EventHandler
    public void onRegister(PlayerRegisterChannelEvent e) {
        if (CHANNEL.equals(e.getChannel())) link(e.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] msg) {
        if (!CHANNEL.equals(channel) || !valid(msg)) return;
        Linked l = linked.get(p.getUniqueId());
        if (l == null) {
            // a client that registered before the plugin was enabled
            link(p);
            l = linked.get(p.getUniqueId());
        }
        long now = System.nanoTime();
        if (l.tokensAt != 0L) l.tokens = Math.min(rate, l.tokens + (now - l.tokensAt) / 1e9f * rate);
        l.tokensAt = now;
        if (l.tokens < 1f) {
            l.dropped++;
            return;
        }
        l.tokens -= 1f;
        if (msg[2] == HELLO) {
            send(p, welcome());
            sendStatesTo(p);
            return;
        }
        if (msg[2] == STATE) l.lastState = msg.clone();
        byte[] out = relay(p.getUniqueId(), msg);
        for (Player o : getServer().getOnlinePlayers()) if (audience(p, o)) send(o, out);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (linked.remove(p.getUniqueId()) == null) return;
        byte[] out = leave(p.getUniqueId());
        for (Player o : getServer().getOnlinePlayers()) if (o != p && linked.containsKey(o.getUniqueId())) send(o, out);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (!linked.containsKey(p.getUniqueId()) || !sameWorld) return;
        World old = e.getFrom();
        byte[] out = leave(p.getUniqueId());
        for (Player o : old.getPlayers()) if (o != p && linked.containsKey(o.getUniqueId())) send(o, out);
        // the players of the new world, both ways
        sendStatesTo(p);
        Linked l = linked.get(p.getUniqueId());
        if (l != null && l.lastState != null) {
            byte[] st = relay(p.getUniqueId(), l.lastState);
            for (Player o : p.getWorld().getPlayers()) if (audience(p, o)) send(o, st);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        long dropped = 0;
        for (Linked l : linked.values()) dropped += l.dropped;
        s.sendMessage(ChatColor.LIGHT_PURPLE + "1227 Relay: " + ChatColor.WHITE + linked.size() + " linked players, " + dropped + " messages dropped (rate limit)");
        return true;
    }
}
