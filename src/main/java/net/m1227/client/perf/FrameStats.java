package net.m1227.client.perf;

import net.m1227.core.CombatTracker;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Always-on frame monitor, used by the PvP Performance HUD, the dashboard, the analyzer and
 * Dynamic Quality.
 *
 * What it measures (all with System.nanoTime(), nothing estimated):
 * <ul>
 * <li>frame time: the time between the ends of two consecutive frames (render + swap + any FPS
 * cap wait), for the last 4096 frames;</li>
 * <li>every 250 ms: FPS of the last second, average / P50 / P95 / P99 / max frame time and the
 * 1% low of the last 10 s;</li>
 * <li>spikes: frames slower than 2.5x the median and at least 12 ms slower, counted in and out of
 * combat;</li>
 * <li>a 5 minute history at one point per second (average FPS and worst frame);</li>
 * <li>time spent inside three events by every mod listening to them (1227 is usually most of
 * it): world effects (RenderWorldLastEvent), HUD (overlay) and the client tick.</li>
 * </ul>
 * Cost: two array writes per frame, and a sort of up to 4096 floats four times per second.
 */
public final class FrameStats {
    public static final FrameStats INSTANCE = new FrameStats();
    private static final int N = 4096;

    private final float[] ms = new float[N];
    private final long[] at = new long[N];
    private int head, size;
    private long lastEnd;
    private final float[] scratch = new float[N];

    // results, refreshed every 250 ms (read by HUDs every frame without any work)
    public volatile float fps, avgMs, p50, p95, p99, maxMs, low1;
    public volatile int spikes, combatSpikes, frames10s;
    private long nextStats;
    private float lastMedian = 16f;

    /** One point per second for 5 minutes. */
    public final Stats.Series fpsHistory = new Stats.Series(300), worstHistory = new Stats.Series(300);
    /** Recent spikes (time, ms) for the graph. */
    public final Stats.Series spikeLog = new Stats.Series(64);
    private long secStart;
    private int secFrames;
    private float secWorst;

    // per-event cost (ms per frame / per tick, smoothed)
    public volatile float worldMs, hudMs, tickMs;
    private long worldT0, hudT0, tickT0;

    private long frameNo;

    private FrameStats() {}

    /** Counts frames (increases at the end of each frame). */
    public long frameNumber() {
        return frameNo;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        frameNo++;
        long now = System.nanoTime();
        if (lastEnd != 0L) {
            float f = (now - lastEnd) / 1e6f;
            if (f > 0f && f < 5000f) add(now, f);
        }
        lastEnd = now;
        if (now >= nextStats) {
            nextStats = now + 250_000_000L;
            compute(now);
            DynamicQuality.update(avgMs);
        }
    }

    private void add(long now, float f) {
        ms[head] = f;
        at[head] = now;
        head = (head + 1) % N;
        if (size < N) size++;
        if (f > lastMedian * 2.5f && f > lastMedian + 12f) {
            spikes++;
            if (CombatTracker.target(3000L) != null) combatSpikes++;
            spikeLog.add(now, f);
        }
        if (secStart == 0L) secStart = now;
        secFrames++;
        secWorst = Math.max(secWorst, f);
        if (now - secStart >= 1_000_000_000L) {
            fpsHistory.add(now, secFrames * 1e9f / (now - secStart));
            worstHistory.add(now, secWorst);
            secStart = now;
            secFrames = 0;
            secWorst = 0f;
        }
    }

    private void compute(long now) {
        // last 1 s: FPS and average; last 10 s: percentiles and 1% low
        int n1 = 0, n10 = 0;
        double sum1 = 0;
        for (int i = 0; i < size; i++) {
            int k = (head - 1 - i + N) % N;
            long age = now - at[k];
            if (age > 10_000_000_000L) break;
            if (age <= 1_000_000_000L) {
                n1++;
                sum1 += ms[k];
            }
            scratch[n10++] = ms[k];
        }
        frames10s = n10;
        if (n1 > 0) {
            fps = (float) (n1 * 1000.0 / Math.max(1.0, sum1));
            avgMs = (float) (sum1 / n1);
        } else {
            fps = 0f;
            avgMs = 0f;
        }
        if (n10 > 0) {
            java.util.Arrays.sort(scratch, 0, n10);
            p50 = (float) Stats.sortedPercentile(scratch, n10, 50);
            p95 = (float) Stats.sortedPercentile(scratch, n10, 95);
            p99 = (float) Stats.sortedPercentile(scratch, n10, 99);
            maxMs = scratch[n10 - 1];
            low1 = n10 >= 100 ? (float) Stats.lowFps(scratch, n10, 1) : Float.NaN;
            lastMedian = Math.max(1f, p50);
        }
    }

    /** Copies the last n frame times (oldest first) into out; returns how many. */
    public int recent(float[] out) {
        int n = Math.min(out.length, size);
        for (int i = 0; i < n; i++) out[i] = ms[(head - n + i + N) % N];
        return n;
    }

    public void resetSpikes() {
        spikes = 0;
        combatSpikes = 0;
        spikeLog.clear();
    }

    // ------------------------------------------------------------- event cost

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldStart(RenderWorldLastEvent e) {
        worldT0 = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void worldEnd(RenderWorldLastEvent e) {
        if (worldT0 != 0L) worldMs += ((System.nanoTime() - worldT0) / 1e6f - worldMs) * 0.05f;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void hudStart(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL) hudT0 = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void hudEnd(RenderGameOverlayEvent.Post e) {
        if (e.type == RenderGameOverlayEvent.ElementType.ALL && hudT0 != 0L) hudMs += ((System.nanoTime() - hudT0) / 1e6f - hudMs) * 0.05f;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tickStart(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.START) tickT0 = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void tickEnd(TickEvent.ClientTickEvent e) {
        // START..END of the client tick: Minecraft's own tick work plus every mod's handlers
        if (e.phase == TickEvent.Phase.END && tickT0 != 0L) tickMs += ((System.nanoTime() - tickT0) / 1e6f - tickMs) * 0.1f;
    }
}
