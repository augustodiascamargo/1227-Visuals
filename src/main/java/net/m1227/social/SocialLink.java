package net.m1227.social;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.m1227.Mod1227;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client side of 1227 Link.
 *
 * A client mod cannot make a server pass custom data between players, so this works only on
 * servers that run the 1227 Relay plugin (server/1227-relay). On every other server the only
 * thing sent is the standard plugin-channel registration ("REGISTER 1227|fx", which servers
 * without the plugin ignore), and nothing else, ever: the client stays silent until the server
 * answers with WELCOME. The rest of 1227 works the same with or without the relay.
 *
 * Sent: what you share (which of Halo / Aura Hat / Wings you use and their colours) and your
 * emotes. At most 4 messages per second, the state at most every 250 ms and again every 30 s
 * (so a reconnecting relay or a late joiner gets it). Received messages are decoded and checked
 * by {@link LinkProtocol}; players are matched by UUID to players actually in your world.
 */
public final class SocialLink {
    public static final SocialLink INSTANCE = new SocialLink();

    /** What another 1227 player shares. */
    public static final class Remote {
        public LinkProtocol.State state = new LinkProtocol.State();
        public int emote = -1;
        public long emoteStart; // nanoTime
        public double emoteX, emoteZ;
        public boolean emoteAnchored;
        public long lastSeen;
        public int lastSeq = Integer.MIN_VALUE;
    }

    private FMLEventChannel channel;
    private final ConcurrentLinkedQueue<byte[]> inbox = new ConcurrentLinkedQueue<byte[]>();
    public final Map<UUID, Remote> remotes = new HashMap<UUID, Remote>();
    public volatile boolean linked;
    public volatile String relayName = "";
    private boolean registeredThisWorld;
    private World lastWorld;
    private long probeUntil;
    private int seq;
    private boolean stateDirty = true;
    private long lastStateSent, nextRefresh;
    private float tokens = 4f;
    private long tokensAt;
    private LinkProtocol.State lastSent;
    private int myEmote = -1;
    private long myEmoteStart;
    public volatile int sent, received, dropped;

    private SocialLink() {}

    /** Called once from the mod's init. */
    public void init() {
        try {
            channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(LinkProtocol.CHANNEL);
            channel.register(this);
        } catch (Throwable t) {
            channel = null;
            Mod1227.LOGGER.warn("1227 Link: could not open the network channel (multiplayer cosmetics off)", t);
        }
    }

    private static net.m1227.module.SocialModule mod() {
        return net.m1227.module.SocialModule.INSTANCE;
    }

    // ------------------------------------------------------------------ receive (network thread)

    @SubscribeEvent
    public void onPacket(FMLNetworkEvent.ClientCustomPacketEvent e) {
        try {
            ByteBuf b = e.packet.payload();
            int n = b.readableBytes();
            if (n <= 0 || n > LinkProtocol.MAX_BYTES || inbox.size() > 512) {
                dropped++;
                return;
            }
            byte[] data = new byte[n];
            b.getBytes(b.readerIndex(), data);
            inbox.add(data);
        } catch (Throwable t) {
            dropped++;
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        linked = false;
        registeredThisWorld = false;
        inbox.clear();
    }

    // ------------------------------------------------------------------ send

    private boolean take() {
        long now = System.nanoTime();
        if (tokensAt != 0L) tokens = Math.min(4f, tokens + (now - tokensAt) / 1e9f * 4f);
        tokensAt = now;
        if (tokens < 1f) return false;
        tokens -= 1f;
        return true;
    }

    private void send(byte[] msg) {
        if (channel == null || !take()) return;
        try {
            channel.sendToServer(new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(msg)), LinkProtocol.CHANNEL));
            sent++;
        } catch (Throwable t) {
            dropped++;
        }
    }

    /** The plugin-channel registration every modded client sends; the server learns we listen on 1227|fx. */
    private void register(Minecraft mc) {
        try {
            byte[] b = LinkProtocol.CHANNEL.getBytes(StandardCharsets.UTF_8);
            mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("REGISTER", new PacketBuffer(Unpooled.wrappedBuffer(b))));
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227 Link: could not register the channel", t);
        }
    }

    public void localEmote(int id) {
        myEmote = id;
        myEmoteStart = System.nanoTime();
        if (linked && mod() != null && mod().shareEmotes()) send(LinkProtocol.emote(++seq, id, 0));
        stateDirty = true;
    }

    public void localStop() {
        if (myEmote < 0) return;
        myEmote = -1;
        if (linked && mod() != null && mod().shareEmotes()) send(LinkProtocol.stop(++seq));
        stateDirty = true;
    }

    public void cosmeticsChanged() {
        stateDirty = true;
    }

    private LinkProtocol.State myState() {
        LinkProtocol.State s = new LinkProtocol.State();
        net.m1227.module.SocialModule m = mod();
        if (m != null && m.shareCosmetics()) {
            if (on("Halo")) {
                s.flags |= LinkProtocol.F_HALO;
                put(s, 0, net.m1227.module.HaloModule.INSTANCE == null ? null : net.m1227.module.HaloModule.INSTANCE.shareColors());
            }
            if (on("Aura Hat")) {
                s.flags |= LinkProtocol.F_HAT;
                put(s, 2, net.m1227.module.AuraHatModule.INSTANCE == null ? null : net.m1227.module.AuraHatModule.INSTANCE.shareColors());
            }
            if (on("Wings")) {
                s.flags |= LinkProtocol.F_WINGS;
                put(s, 4, net.m1227.module.WingsModule.INSTANCE == null ? null : net.m1227.module.WingsModule.INSTANCE.shareColors());
            }
        }
        if (m != null && m.shareEmotes() && myEmote >= 0) {
            s.emote = myEmote;
            s.emoteMs = (int) Math.min(600000, (System.nanoTime() - myEmoteStart) / 1_000_000L);
        }
        return s;
    }

    private static void put(LinkProtocol.State s, int at, int[] c) {
        if (c == null) return;
        s.colors[at] = c[0];
        s.colors[at + 1] = c[1];
    }

    private static boolean on(String module) {
        net.m1227.core.Module m = net.m1227.core.ModuleManager.byName(module);
        return m != null && m.isEnabled();
    }

    private static boolean same(LinkProtocol.State a, LinkProtocol.State b) {
        if (a == null || b == null || a.flags != b.flags || a.emote != b.emote) return false;
        for (int i = 0; i < 6; i++) if (a.colors[i] != b.colors[i]) return false;
        return true;
    }

    // ------------------------------------------------------------------ main thread

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        if (mc.theWorld != lastWorld) {
            // new world (respawn, dimension or a proxy moving you to another server): forget the
            // players of the old one, register again, and if we were linked ask the relay again
            lastWorld = mc.theWorld;
            remotes.clear();
            registeredThisWorld = false;
            if (linked) {
                linked = false;
                probeUntil = now + 5_000_000_000L;
            }
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.getNetHandler() == null || mc.isSingleplayer()) return;
        net.m1227.module.SocialModule m = mod();
        if (m == null || !m.isEnabled()) return;
        if (!registeredThisWorld) {
            registeredThisWorld = true;
            register(mc);
            if (probeUntil > now) send(LinkProtocol.hello(Mod1227.VERSION, 1)); // only after a WELCOME on this connection
        }
        byte[] data;
        while ((data = inbox.poll()) != null) handle(mc, data, now);
        if (!linked) return;
        LinkProtocol.State s = myState();
        boolean changed = !same(s, lastSent);
        if (now > nextRefresh || ((changed || stateDirty) && now - lastStateSent > 250_000_000L)) {
            s.seq = ++seq;
            send(LinkProtocol.state(s));
            lastSent = s;
            lastStateSent = now;
            nextRefresh = now + 30_000_000_000L;
            stateDirty = false;
        }
        // forget players not heard of for 2 minutes, or no longer in the world
        for (Iterator<Map.Entry<UUID, Remote>> it = remotes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Remote> en = it.next();
            if (now - en.getValue().lastSeen > 120_000_000_000L && mc.theWorld.getPlayerEntityByUUID(en.getKey()) == null) it.remove();
        }
    }

    private void handle(Minecraft mc, byte[] data, long now) {
        LinkProtocol.Message msg = LinkProtocol.decode(data);
        if (msg == null) {
            dropped++;
            return;
        }
        received++;
        if (msg.type == LinkProtocol.WELCOME) {
            boolean first = !linked;
            linked = true;
            probeUntil = 0L;
            relayName = msg.text;
            nextRefresh = 0L; // send our state now
            lastSent = null;
            if (first) send(LinkProtocol.hello(Mod1227.VERSION, 1));
            return;
        }
        if (!linked) return;
        UUID id = new UUID(msg.msb, msg.lsb);
        if (mc.thePlayer != null && id.equals(mc.thePlayer.getUniqueID())) return;
        if (msg.type == LinkProtocol.LEAVE) {
            remotes.remove(id);
            return;
        }
        Remote r = remotes.get(id);
        if (r == null) {
            if (remotes.size() >= 256) return;
            r = new Remote();
            remotes.put(id, r);
        }
        r.lastSeen = now;
        if (msg.innerType == LinkProtocol.STATE) {
            r.state = msg.state;
            if (msg.state.emote >= 0 && r.emote != msg.state.emote) startEmote(mc, id, r, msg.state.emote, msg.state.emoteMs, now);
            else if (msg.state.emote < 0) r.emote = -1;
        } else if (msg.innerType == LinkProtocol.EMOTE) {
            startEmote(mc, id, r, msg.emote, msg.emoteMs, now);
        } else if (msg.innerType == LinkProtocol.STOP) {
            r.emote = -1;
        }
    }

    private static void startEmote(Minecraft mc, UUID id, Remote r, int emote, int elapsedMs, long now) {
        r.emote = emote;
        r.emoteStart = now - elapsedMs * 1_000_000L;
        net.minecraft.entity.player.EntityPlayer p = mc.theWorld.getPlayerEntityByUUID(id);
        r.emoteAnchored = p != null;
        if (p != null) {
            r.emoteX = p.posX;
            r.emoteZ = p.posZ;
        }
    }
}
