package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.net.LatencyMonitor;
import net.m1227.ui.Gfx;

/**
 * Latency Graph: the last 30 or 60 s of ping as a small line. It shows the measured status ping
 * when the Latency Monitor has samples, otherwise the server's tab list ping (labelled "tab").
 * Spikes are marked. The points are copied 4 times per second; drawing is a few dozen lines.
 */
public final class LatencyGraphHud extends HudModule {
    private final Setting.Mode range;
    private final float[] pts = new float[400];
    private int n;
    private boolean measured;
    private float max = 100, last = Float.NaN;
    private long nextCopy;

    public LatencyGraphHud() {
        super("Latency Graph", "Ping over the last 30 or 60 seconds, with spikes.", 0.70, 0.12);
        range = add(new Setting.Mode("Range", "60 s", "30 s", "60 s"));
    }

    @Override
    public float width() {
        return 110;
    }

    @Override
    public float height() {
        return 40;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        long now = System.nanoTime();
        LatencyMonitor lm = LatencyMonitor.INSTANCE;
        if (now >= nextCopy) {
            nextCopy = now + 250_000_000L;
            long win = range.is("30 s") ? 30_000_000_000L : 60_000_000_000L;
            measured = lm.rtt.size() >= 3;
            n = (measured ? lm.rtt : lm.tab).window(now, win, pts);
            max = 60;
            for (int i = 0; i < n; i++) max = Math.max(max, pts[i] * 1.2f);
            last = n > 0 ? pts[n - 1] : Float.NaN;
        }
        if (editing && n == 0) {
            for (int i = 0; i < 30; i++) pts[i] = 40 + (float) Math.sin(i * 0.7) * 6 + (i == 20 ? 40 : 0);
            n = 30;
            max = 110;
            last = 42;
            measured = true;
        }
        panel(x, y, width(), height());
        String head = (measured ? "Ping" : "Ping (tab)") + "  " + (Float.isNaN(last) ? "N/A" : Math.round(last) + " ms");
        Gfx.textScaled(head, x + 5, y + 4, 0.8f, LABEL);
        double j = lm.rttJitter.value();
        if (measured && !Double.isNaN(j)) {
            String js = String.format("±%.0f", j);
            Gfx.textScaled(js, x + width() - 5 - Gfx.textWidth(js, 0.8f), y + 4, 0.8f, 0xFFFFFFFF);
        }
        float gx = x + 5, gy = y + 14, gw = width() - 10, gh = height() - 18;
        Gfx.rect(gx, gy + gh, gw, 0.5f, 0x30FFFFFF);
        if (n < 2) return;
        float px = 0, py = 0;
        for (int i = 0; i < n; i++) {
            float vx = gx + gw * i / (float) (n - 1), vy = gy + gh - Math.min(1f, pts[i] / max) * gh;
            if (i > 0) Gfx.line(px, py, vx, vy, 0.8f, value());
            if (i > 1 && pts[i] > pts[i - 1] * 1.5f + 30f) Gfx.circle(vx, vy, 1.4f, 0xFFFF6B6B);
            px = vx;
            py = vy;
        }
    }
}
