package net.m1227.net;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.m1227.client.perf.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 1227 Latency Monitor. Every number says where it comes from, and is N/A when there is nothing
 * reliable to show:
 *
 * <ul>
 * <li><b>Tab list ping</b> (server-reported): the value the server puts in the player list. A
 * vanilla server sends it about every 30 s, already averaged, so it is old and smooth. Its age is
 * shown.</li>
 * <li><b>Status ping</b> (our own measurement): a separate, short connection to the same server
 * address does the same exchange the multiplayer list does, and the time between our "ping" and
 * the server's "pong" is a real round trip. Off for singleplayer. Every 10 s by default.</li>
 * <li><b>Jitter</b>: RFC 3550 jitter over the status ping round trips. Also, when the server's
 * keep-alive ids are its own clock in milliseconds (vanilla and Spigot do this; many proxies use
 * random ids), the variation of the keep-alive delay on the game connection itself.</li>
 * <li><b>Server tick rate</b> (estimate): how fast the world clock advances in the server's
 * time packets, against our clock, averaged over 10 packets. Network jitter adds noise.</li>
 * <li><b>Stalls</b>: moments with no packet at all from the server for 2 s or more (a server
 * normally sends many per second, at least a keep-alive every ~2 s).</li>
 * <li><b>Position corrections</b>: how often the server moved you back (S08).</li>
 * <li>Connection time, disconnections, latency spikes.</li>
 * </ul>
 * Packet loss: not available. The game uses TCP, which hides losses from the game (they show up
 * only as delay), and Minecraft has no per-packet acknowledgement we could count.
 */
public final class LatencyMonitor {
    public static final LatencyMonitor INSTANCE = new LatencyMonitor();

    // status ping (own measurement)
    public final Stats.Series rtt = new Stats.Series(400);
    public final Stats.Jitter rttJitter = new Stats.Jitter();
    public volatile float rttLast = Float.NaN, rttMin = Float.NaN, rttMax = Float.NaN, rttAvg = Float.NaN, rttP95 = Float.NaN;
    public volatile int probeOk, probeFail;
    public volatile String probeError = "";
    private final ConcurrentLinkedQueue<long[]> probeResults = new ConcurrentLinkedQueue<long[]>();
    private volatile Thread probeThread;
    private volatile String probeTarget;
    private volatile boolean probeWanted;
    private volatile int probeIntervalMs = 10000;

    // tab list (server-reported)
    public final Stats.Series tab = new Stats.Series(300);
    public volatile int tabPing = -1;
    private long tabChangedAt, tabSampleAt;

    // keep-alive
    public final Stats.Jitter kaJitter = new Stats.Jitter();
    public volatile boolean kaClock;
    private long kaLastArrival;
    private int kaLastId, kaClockVotes;
    public volatile float kaInterval = Float.NaN;

    // server tick estimate
    private final long[] tAt = new long[11];
    private final long[] tWorld = new long[11];
    private int tN, tHead;
    public volatile float tps = Float.NaN;

    // connection
    public volatile boolean connected, local;
    private long connectedAt;
    public volatile int disconnects, stalls, setbacks, spikes;
    public volatile float longestStallS;
    private long stallStart;
    /** Events for the graph: time + kind (0 spike, 1 stall, 2 disconnect, 3 timeout) + value. */
    public final Stats.Series events = new Stats.Series(128);
    public final int[] eventKind = new int[128];
    private int eventHead;

    private LatencyMonitor() {}

    // ------------------------------------------------------------------ connection

    void connected(boolean isLocal) {
        // network thread: only plain fields
        connected = true;
        local = isLocal;
        connectedAt = System.nanoTime();
        resetSession = true;
    }

    private volatile boolean resetSession;

    void disconnected() {
        if (connected) {
            disconnects++;
            event(System.nanoTime(), 2, 0);
        }
        connected = false;
        probeWanted = false;
    }

    public long uptimeNs() {
        return connected ? System.nanoTime() - connectedAt : 0L;
    }

    private void event(long now, int kind, float value) {
        synchronized (events) {
            events.add(now, value);
            eventKind[eventHead] = kind;
            eventHead = (eventHead + 1) % eventKind.length;
        }
    }

    /** Kind of the i-th event (same order as events.value(i)). */
    public int eventKind(int i) {
        synchronized (events) {
            int n = events.size();
            return eventKind[(eventHead - n + i + eventKind.length * 2) % eventKind.length];
        }
    }

    // ------------------------------------------------------------------ per tick

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        drain();
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        if (resetSession) {
            resetSession = false;
            rtt.clear();
            rttJitter.reset();
            kaJitter.reset();
            tab.clear();
            tN = 0;
            tps = Float.NaN;
            kaClock = false;
            kaClockVotes = 0;
            kaLastArrival = 0L;
            rttMin = rttMax = rttAvg = rttP95 = rttLast = Float.NaN;
            probeOk = probeFail = 0;
            stalls = setbacks = spikes = 0;
            longestStallS = 0f;
            tabPing = -1;
            ServerData sd = mc.getCurrentServerData();
            probeTarget = local || sd == null ? null : sd.serverIP;
        }
        // tab list ping, once per second
        if (connected && mc.thePlayer != null && mc.getNetHandler() != null && now - tabSampleAt > 1_000_000_000L) {
            tabSampleAt = now;
            NetworkPlayerInfo i = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            int p = i == null || local ? -1 : i.getResponseTime();
            if (p != tabPing) tabChangedAt = now;
            tabPing = p;
            if (p >= 0) tab.add(now, p);
        }
        // stalls: nothing from the server for 2 s
        if (connected && NetObserver.INSTANCE.attached()) {
            long quiet = now - NetObserver.INSTANCE.lastInbound();
            if (quiet > 2_000_000_000L && stallStart == 0L) stallStart = NetObserver.INSTANCE.lastInbound();
        }
        // status ping thread
        net.m1227.module.LatencyMonitorModule m = net.m1227.module.LatencyMonitorModule.INSTANCE;
        probeWanted = connected && !local && probeTarget != null && m != null && m.isEnabled() && m.probeOn();
        if (m != null) probeIntervalMs = m.probeIntervalMs();
        if (probeWanted && probeThread == null) startProbe();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFrame(TickEvent.RenderTickEvent e) {
        // combat evidence is matched as soon as the packet is seen
        if (e.phase == TickEvent.Phase.START) drain();
    }

    /** Reads everything the network thread saw, in order, and routes it. Main thread. */
    private void drain() {
        NetObserver o = NetObserver.INSTANCE;
        NetObserver.Seen s;
        while ((s = o.poll()) != null) {
            onPacket(s);
            net.m1227.core.CombatTracker.onPacket(s);
        }
        long[] r;
        while ((r = probeResults.poll()) != null) addProbe(r[0], (float) Double.longBitsToDouble(r[1]));
    }

    private void onPacket(NetObserver.Seen s) {
        long now = s.nanos;
        if (stallStart != 0L) {
            float d = (now - stallStart) / 1e9f;
            if (d >= 2f) {
                stalls++;
                longestStallS = Math.max(longestStallS, d);
                event(now, 1, d);
            }
            stallStart = 0L;
        }
        if (s.outbound) return;
        if (s.packet instanceof S00PacketKeepAlive) {
            int id = ((S00PacketKeepAlive) s.packet).func_149134_c();
            if (kaLastArrival != 0L) {
                double arrivalMs = (now - kaLastArrival) / 1e6;
                kaInterval = (float) arrivalMs;
                // ids that advance like a millisecond clock: (id - previous id) close to the arrival gap
                double idGap = (double) (id - kaLastId);
                boolean clockLike = Math.abs(idGap - arrivalMs) < 400 && idGap > 0;
                kaClockVotes = clockLike ? Math.min(5, kaClockVotes + 1) : Math.max(-5, kaClockVotes - 2);
                boolean was = kaClock;
                kaClock = kaClockVotes >= 3;
                if (kaClock && !was) kaJitter.reset();
            }
            if (kaClock) kaJitter.add(now / 1e6 - id); // arrival minus server send time (+ constant offset)
            kaLastArrival = now;
            kaLastId = id;
        } else if (s.packet instanceof S03PacketTimeUpdate) {
            long w = ((S03PacketTimeUpdate) s.packet).getTotalWorldTime();
            tAt[tHead] = now;
            tWorld[tHead] = w;
            tHead = (tHead + 1) % tAt.length;
            if (tN < tAt.length) tN++;
            if (tN >= 4) {
                int oldest = (tHead - tN + tAt.length) % tAt.length, newest = (tHead - 1 + tAt.length) % tAt.length;
                double secs = (tAt[newest] - tAt[oldest]) / 1e9;
                long ticks = tWorld[newest] - tWorld[oldest];
                if (secs > 1.0 && ticks >= 0) tps = (float) Math.min(20.0, ticks / secs);
            }
        } else if (s.packet instanceof S08PacketPlayerPosLook) {
            setbacks++;
        }
    }

    private void addProbe(long now, float ms) {
        if (ms < 0) {
            probeFail++;
            event(now, 3, 0);
            return;
        }
        probeOk++;
        float median = rtt.size() >= 5 ? (float) percentile(50) : Float.NaN;
        rtt.add(now, ms);
        rttJitter.add(ms);
        rttLast = ms;
        rttMin = Float.isNaN(rttMin) ? ms : Math.min(rttMin, ms);
        rttMax = Float.isNaN(rttMax) ? ms : Math.max(rttMax, ms);
        rttAvg = (float) mean();
        rttP95 = (float) percentile(95);
        if (!Float.isNaN(median) && ms > median * 1.5f + 30f) {
            spikes++;
            event(now, 0, ms);
        }
    }

    private final float[] scratch = new float[400];
    private final float[] scratch2 = new float[400];

    private double percentile(double p) {
        int n = rtt.size();
        for (int i = 0; i < n; i++) scratch[i] = rtt.value(i);
        return Stats.percentile(scratch, n, p, scratch2);
    }

    private double mean() {
        int n = rtt.size();
        double s = 0;
        for (int i = 0; i < n; i++) s += rtt.value(i);
        return n == 0 ? Double.NaN : s / n;
    }

    /** Seconds since the tab list ping last changed (-1 = never). */
    public float tabAgeS() {
        return tabChangedAt == 0L ? -1f : (System.nanoTime() - tabChangedAt) / 1e9f;
    }

    /** Best available round trip for timing windows: status ping median, else tab ping, else NaN. */
    public float bestRttMs() {
        if (rtt.size() >= 3) return (float) percentile(50);
        if (tabPing > 0) return tabPing;
        return Float.NaN;
    }

    /** Lowest status ping round trip seen (NaN if fewer than 5): a physical lower bound for replies. */
    public float floorRttMs() {
        return rtt.size() >= 5 ? rttMin : Float.NaN;
    }

    /** Connection quality summary for the HUD: 0 unknown, 1 good, 2 fair, 3 poor. */
    public int quality() {
        float j = (float) rttJitter.value();
        float p = !Float.isNaN(rttP95) ? rttP95 : tabPing;
        if (!connected || (Float.isNaN(j) && p < 0)) return 0;
        if (stalls > 0 && longestStallS > 5) return 3;
        if ((!Float.isNaN(j) && j > 25) || p > 200) return 3;
        if ((!Float.isNaN(j) && j > 10) || p > 120) return 2;
        return 1;
    }

    // ------------------------------------------------------------------ status ping thread

    private void startProbe() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                probeLoop();
            }
        }, "1227-LatencyProbe");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        probeThread = t;
        t.start();
    }

    private void probeLoop() {
        String resolvedFor = null;
        String host = null;
        int port = 25565;
        try {
            while (probeWanted) {
                String target = probeTarget;
                if (target == null) break;
                if (!target.equals(resolvedFor)) {
                    // also does the SRV lookup, like the multiplayer list (network, so off the main thread)
                    net.minecraft.client.multiplayer.ServerAddress a = net.minecraft.client.multiplayer.ServerAddress.fromString(target);
                    host = a.getIP();
                    port = a.getPort();
                    resolvedFor = target;
                }
                float ms;
                try {
                    ms = StatusPing.ping(host, port, 3000);
                    probeError = "";
                } catch (Exception ex) {
                    ms = -1f;
                    probeError = ex.getClass().getSimpleName();
                }
                probeResults.add(new long[]{System.nanoTime(), Double.doubleToLongBits(ms)});
                long until = System.nanoTime() + probeIntervalMs * 1_000_000L;
                while (probeWanted && System.nanoTime() < until) Thread.sleep(200); // background thread only
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            probeError = t.getClass().getSimpleName();
        } finally {
            probeThread = null;
        }
    }
}
