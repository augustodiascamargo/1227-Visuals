package net.m1227.net;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.m1227.Mod1227;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Read-only view of the connection to the server, for the Latency Monitor and the Hit
 * Registration Monitor.
 *
 * A small handler is added to the connection's pipeline, right before Minecraft's own packet
 * handler. For every packet it does one thing: it notes the arrival (or send) time and, for a few
 * packet types, puts (time, packet) in a queue that the main thread reads. Then it passes the very
 * same packet on, immediately, in the same call. It never delays, drops, copies, changes, reorders
 * or sends a packet, and it never touches the world (the network thread must not), so a failure in
 * our code can never stop a packet: the pass-on is in a finally block.
 *
 * Observed packets:
 * <ul>
 * <li>from the server: keep-alive (S00), world time (S03), your health (S06), position correction
 * (S08), entity animation (S0B: swing, crit), entity velocity (S12), entities removed (S13) and
 * entity status (S19: hurt, death);</li>
 * <li>to the server: attack (C02) and sprint start / stop (C0B).</li>
 * </ul>
 * Packet loss cannot be observed here: the game runs over TCP, which re-sends lost data by itself,
 * so a loss reaches the game only as extra delay.
 */
public final class NetObserver {
    public static final NetObserver INSTANCE = new NetObserver();
    static final String NAME = "m1227_observer";

    /** A packet seen on the network thread, with its monotonic time. */
    public static final class Seen {
        public final long nanos;
        public final Packet<?> packet;
        public final boolean outbound;

        Seen(long nanos, Packet<?> packet, boolean outbound) {
            this.nanos = nanos;
            this.packet = packet;
            this.outbound = outbound;
        }
    }

    private final ConcurrentLinkedQueue<Seen> queue = new ConcurrentLinkedQueue<Seen>();
    private volatile boolean attached;
    private volatile long connectedAt;
    /** Last packet from the server (nanoTime), and packets counted since connecting. */
    volatile long lastInbound;
    private volatile long inboundCount, outboundCount;
    /** Set when the queue grew past its limit (main thread not reading it): observation paused. */
    private volatile boolean overflow;
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    private NetObserver() {}

    public boolean attached() {
        return attached;
    }

    public long connectedAt() {
        return connectedAt;
    }

    public long lastInbound() {
        return lastInbound;
    }

    public long inboundCount() {
        return inboundCount;
    }

    public long outboundCount() {
        return outboundCount;
    }

    /** Next observed packet, or null. Main thread only. */
    public Seen poll() {
        Seen s = queue.poll();
        if (s != null) queued.decrementAndGet();
        return s;
    }

    private static boolean interesting(Object m, boolean out) {
        if (out) return m instanceof C02PacketUseEntity || m instanceof C0BPacketEntityAction;
        return m instanceof S19PacketEntityStatus || m instanceof S0BPacketAnimation || m instanceof S12PacketEntityVelocity
                || m instanceof S00PacketKeepAlive || m instanceof S03PacketTimeUpdate || m instanceof S08PacketPlayerPosLook
                || m instanceof S06PacketUpdateHealth || m instanceof S13PacketDestroyEntities;
    }

    private void note(Object m, boolean out) {
        long now = System.nanoTime();
        if (out) outboundCount++;
        else {
            inboundCount++;
            lastInbound = now;
        }
        if (!(m instanceof Packet) || !interesting(m, out)) return;
        // the main thread reads this every tick; if it stops (e.g. loading), stop adding
        if (queued.get() > 20000) {
            if (!overflow) {
                overflow = true;
                queue.clear();
                queued.set(0);
            }
            return;
        }
        overflow = false;
        queue.add(new Seen(now, (Packet<?>) m, out));
        queued.incrementAndGet();
    }

    /** The pipeline handler: look, then pass on, always. */
    final class Handler extends ChannelDuplexHandler {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                note(msg, false);
            } catch (Throwable ignored) {
                // observation is best effort; the packet must go on regardless
            } finally {
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            try {
                note(msg, true);
            } catch (Throwable ignored) {
                // same as above
            } finally {
                ctx.write(msg, promise);
            }
        }
    }

    @SubscribeEvent
    public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent e) {
        attach(e.manager);
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        attached = false;
        LatencyMonitor.INSTANCE.disconnected();
    }

    private void attach(NetworkManager nm) {
        try {
            queue.clear();
            queued.set(0);
            inboundCount = 0;
            outboundCount = 0;
            connectedAt = System.nanoTime();
            lastInbound = connectedAt;
            if (nm == null || nm.channel() == null) return;
            // the session counts as connected even if the observer cannot attach (tab ping still works)
            LatencyMonitor.INSTANCE.connected(nm.isLocalChannel());
            ChannelPipeline p = nm.channel().pipeline();
            if (p.get(NAME) != null) {
                attached = true;
                return;
            }
            if (p.get("packet_handler") == null) {
                Mod1227.LOGGER.warn("1227 network monitor: Minecraft's packet handler not found, monitor off");
                return;
            }
            p.addBefore("packet_handler", NAME, new Handler());
            attached = true;
        } catch (Throwable t) {
            attached = false;
            Mod1227.LOGGER.warn("1227 network monitor could not attach (monitor off; the game is not affected)", t);
        }
    }
}
