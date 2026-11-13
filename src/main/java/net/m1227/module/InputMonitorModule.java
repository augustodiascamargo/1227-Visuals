package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;

/**
 * Input Monitor: turns on the input timing measurements (see client.perf.InputMonitor). The
 * results are in the Performance Dashboard, Input tab, and in the PvP Performance HUD.
 */
public final class InputMonitorModule extends Module {
    public static InputMonitorModule INSTANCE;

    public InputMonitorModule() {
        super("Input Monitor", "Estimates how long clicks wait for the game tick and the next frame.", Category.PVP, false);
        INSTANCE = this;
        add(new Setting.Action("Open the Input tab", () -> net.m1227.client.menu.PerfDashboardScreen.open(3)));
        add(new Setting.Action("Clear measurements", () -> net.m1227.client.perf.InputMonitor.INSTANCE.clear()));
    }
}
