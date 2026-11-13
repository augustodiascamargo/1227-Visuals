package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * Latency Monitor: the passive part (tab list ping, keep-alive timing, server tick estimate,
 * stalls, position corrections) always runs and costs almost nothing; this module adds the own
 * round-trip measurement with status pings and the alerts. Results: Performance Dashboard,
 * Network tab; PvP Performance HUD; Latency Graph HUD.
 */
public final class LatencyMonitorModule extends Module {
    public static LatencyMonitorModule INSTANCE;
    private final Setting.Bool probe;
    private final Setting.Num interval;
    public final Setting.Num highPing, highJitter;

    public LatencyMonitorModule() {
        super("Latency Monitor", "Measured round trip, jitter, server tick rate and connection stalls.", Category.PVP, false);
        INSTANCE = this;
        probe = add(new Setting.Bool("Measure with status pings", true));
        interval = add(new Setting.Num("Status ping every", 10, 3, 60, 1, " s")).showIf(() -> probe.on());
        highPing = add(new Setting.Num("High ping alert above", 150, 40, 500, 10, " ms"));
        highJitter = add(new Setting.Num("High jitter alert above", 20, 3, 100, 1, " ms"));
        add(new Setting.Action("Open the Network tab", () -> net.m1227.client.menu.PerfDashboardScreen.open(1)));
    }

    public boolean probeOn() {
        return probe.on();
    }

    public int probeIntervalMs() {
        return interval.i() * 1000;
    }
}
