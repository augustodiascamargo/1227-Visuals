package net.m1227.module;

import net.m1227.client.perf.DynamicQuality;
import net.m1227.client.perf.FrameStats;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/**
 * Low Latency Mode: frame cap and VSync choices aimed at a steady frame time and less waiting
 * between your input and the picture, without forcing one answer on every PC.
 *
 * <ul>
 * <li>FPS cap: keep yours, unlimited, the monitor's refresh rate, twice it, a custom value, or
 * "stable frame time" (measured: the FPS your PC holds 95% of the time in the current scene,
 * minus 5%). Caps up to 259 use Minecraft's own limiter; higher caps use the same LWJGL limiter
 * at the start of the frame (Minecraft's slider stops at 260 = unlimited).</li>
 * <li>VSync: keep yours, on or off.</li>
 * <li>"Limit frames queued by the GPU driver": waits for the GPU to finish each frame before
 * handing it over (glFinish), so the driver cannot queue frames ahead. This can lower input lag
 * when the GPU is the bottleneck, and it lowers FPS: measure it with the benchmark (A/B) before
 * keeping it. Off by default.</li>
 * </ul>
 * Your own values come back when the mode is off, and the Video Settings screen always shows and
 * saves your own values. Unlimited FPS is not always best: a GPU at 100% queues frames and adds
 * delay, which a cap slightly below what the PC can hold avoids.
 */
public final class LowLatencyModule extends Module {
    public static LowLatencyModule INSTANCE;
    private final Setting.Mode fpsMode, vsync;
    private final Setting.Num custom, stable;
    private final Setting.Bool finish;
    private long measureUntil;

    public LowLatencyModule() {
        super("Low Latency Mode", "FPS cap, VSync and frame pacing choices for steady frame times.", Category.PVP, false);
        INSTANCE = this;
        fpsMode = add(new Setting.Mode("FPS cap", "Stable frame time", "Keep my setting", "Unlimited", "Monitor refresh rate",
                "2x monitor refresh rate", "Custom", "Stable frame time"));
        custom = add(new Setting.Num("Custom FPS", 240, 30, 1000, 5, "")).showIf(() -> fpsMode.is("Custom"));
        stable = add(new Setting.Num("Stable FPS (measured)", 0, 0, 1000, 5, "")).showIf(() -> fpsMode.is("Stable frame time"));
        add(new Setting.Action("Measure stable frame time (10 s)", this::measure));
        vsync = add(new Setting.Mode("VSync", "Keep my setting", "Keep my setting", "On", "Off"));
        finish = add(new Setting.Bool("Limit frames queued by the GPU driver", false));
        add(new Setting.Action("Open Performance Dashboard", () -> net.m1227.client.menu.PerfDashboardScreen.open(0)));
    }

    private void measure() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            Notifier.post(Notifier.Kind.SYSTEM, "Low Latency", "Join a world first: it measures the real scene", Notifier.WARN, 2200);
            return;
        }
        if (!isEnabled()) setEnabled(true);
        measureUntil = System.nanoTime() + 10_500_000_000L;
        mc.displayGuiScreen(null);
        Notifier.post(Notifier.Kind.SYSTEM, "Low Latency", "Measuring for 10 s without a cap. Play normally.", Notifier.INFO, 2600);
    }

    public boolean measuring() {
        return measureUntil != 0L;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || measureUntil == 0L) return;
        if (!Display.isActive() || Minecraft.getMinecraft().theWorld == null) {
            measureUntil = 0L;
            Notifier.post(Notifier.Kind.SYSTEM, "Low Latency", "Measurement stopped (window or world changed)", Notifier.WARN, 2200);
            return;
        }
        if (System.nanoTime() < measureUntil) return;
        measureUntil = 0L;
        FrameStats f = FrameStats.INSTANCE;
        if (f.frames10s < 200 || f.p95 <= 0f) {
            Notifier.post(Notifier.Kind.SYSTEM, "Low Latency", "Not enough frames measured", Notifier.WARN, 2200);
            return;
        }
        int fps = (int) Math.floor(1000.0 / f.p95 * 0.95 / 5.0) * 5;
        fps = Math.max(30, Math.min(1000, fps));
        stable.setClamped(fps);
        fpsMode.set("Stable frame time");
        net.m1227.core.ConfigManager.markDirty();
        Notifier.post(Notifier.Kind.SYSTEM, "Low Latency", "Stable cap: " + fps + " FPS (P95 frame time " + String.format("%.1f", f.p95) + " ms)",
                Notifier.GOOD, 4000);
    }

    /** The cap wanted right now, 0 = keep yours, 260 = unlimited. */
    private int wanted() {
        if (measureUntil != 0L) return 260;
        String m = fpsMode.get();
        if (m.equals("Keep my setting")) return 0;
        if (m.equals("Unlimited")) return 260;
        int r = DynamicQuality.refreshRate();
        if (m.equals("Monitor refresh rate")) return r;
        if (m.equals("2x monitor refresh rate")) return r * 2;
        if (m.equals("Custom")) return custom.i();
        int s = stable.i();
        return s >= 30 ? s : 0; // not measured yet: keep yours
    }

    /** Value for Minecraft's limiter (1..260), or 0 to keep your own. */
    public int vanillaCap() {
        int w = wanted();
        if (w <= 0) return 0;
        return w >= 260 ? 260 : w;
    }

    /** A cap above Minecraft's range, done with LWJGL's limiter at the frame start (0 = none). */
    public int extraCap() {
        if (!isEnabled()) return 0;
        int w = wanted();
        return w > 260 ? w : 0;
    }

    public Boolean vsyncWanted() {
        if (measureUntil != 0L) return Boolean.FALSE;
        return vsync.is("On") ? Boolean.TRUE : vsync.is("Off") ? Boolean.FALSE : null;
    }

    public boolean finishFrames() {
        return isEnabled() && finish.on();
    }

    /** Text for reports: what this mode is doing now. */
    public String describe() {
        if (!isEnabled()) return "off";
        int w = wanted();
        String cap = w == 0 ? "your cap" : w >= 260 && w == 260 ? "unlimited" : w + " FPS";
        return cap + ", VSync " + vsync.get().toLowerCase(java.util.Locale.ROOT) + (finish.on() ? ", driver queue limited" : "");
    }
}
