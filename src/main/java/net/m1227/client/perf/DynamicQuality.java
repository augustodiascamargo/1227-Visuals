package net.m1227.client.perf;

import net.m1227.module.PvpVisualModule;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

/**
 * Dynamic Quality: lowers the amount of COSMETIC effects (auras, trails, ambience, skies, weapon
 * idle particles...) step by step while frames take longer than the target, and raises it back
 * slowly when there is room. Combat effects, players, name tags and the HUD are never scaled.
 *
 * Checked every 250 ms against the average frame time of the last second. The target is the one
 * chosen in PvP Visual Optimization, but never faster than your FPS cap or VSync allow (otherwise
 * a capped game would always look "too slow"). Steps: -0.05 when more than 10% over the target,
 * +0.025 when more than 15% under it, between the chosen minimum and 1.
 */
public final class DynamicQuality {
    private DynamicQuality() {}

    private static volatile float factor = 1f;

    /** Multiplier for cosmetic effect amounts (1 = full). */
    public static float factor() {
        PvpVisualModule m = PvpVisualModule.INSTANCE;
        return m != null && m.isEnabled() && m.dynamicOn() ? factor : 1f;
    }

    static void update(float avgMs) {
        PvpVisualModule m = PvpVisualModule.INSTANCE;
        if (m == null || !m.isEnabled() || !m.dynamicOn() || avgMs <= 0f) {
            factor = 1f;
            return;
        }
        float targetFps = m.targetFps();
        Minecraft mc = Minecraft.getMinecraft();
        int cap = mc.gameSettings.limitFramerate;
        if (cap > 0 && cap < 260) targetFps = Math.min(targetFps, cap);
        if (mc.gameSettings.enableVsync) targetFps = Math.min(targetFps, refreshRate());
        float target = 1000f / Math.max(10f, targetFps);
        float f = factor;
        float speed = m.reaction();
        if (avgMs > target * 1.1f) f -= 0.05f * speed;
        else if (avgMs < target * 0.85f) f += 0.025f * speed;
        factor = Math.max(m.minQuality(), Math.min(1f, f));
    }

    public static int refreshRate() {
        try {
            int r = Display.getDesktopDisplayMode().getFrequency();
            return r > 0 ? r : 60;
        } catch (Throwable t) {
            return 60;
        }
    }
}
