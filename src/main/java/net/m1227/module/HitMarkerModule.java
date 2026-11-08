package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** A small X around the crosshair when a hit is confirmed; bigger and red on a kill. */
public final class HitMarkerModule extends Module {
    private final Setting.Col color, killColor;
    private final Setting.Num size, duration;
    private long hitTime, killTime;

    public HitMarkerModule() {
        super("Hit Marker", "Crosshair marker on confirmed hits and kills.", Category.HUD, true);
        color = add(new Setting.Col("Hit color", 0xFFFFFF));
        killColor = add(new Setting.Col("Kill color", 0xFF4D5E));
        size = add(new Setting.Num("Size", 6, 3, 14, 0.5, "px"));
        duration = add(new Setting.Num("Duration", 0.3, 0.1, 1.0, 0.05, "s"));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        hitTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onKill(CombatTracker.KillEvent e) {
        killTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        long now = System.currentTimeMillis();
        float dur = duration.f() * 1000f;
        boolean kill = now - killTime < dur * 1.6f;
        float t = (now - (kill ? killTime : hitTime)) / (kill ? dur * 1.6f : dur);
        if (t >= 1f || t < 0f) return;
        float cx = e.resolution.getScaledWidth() / 2f, cy = e.resolution.getScaledHeight() / 2f;
        float s = size.f() * (kill ? 1.5f : 1f);
        float gap = 2.5f + 3f * (1f - (1f - t) * (1f - t)) + (kill ? 1.5f : 0f);
        float a = 1f - t * t;
        int c = Gfx.alpha(0xFF000000 | (kill ? killColor.rgb() : color.rgb()), a);
        float d = 0.7071f;
        for (int k = 0; k < 4; k++) {
            float sx = (k & 1) == 0 ? 1f : -1f, sy = (k & 2) == 0 ? 1f : -1f;
            Gfx.line(cx + sx * gap * d, cy + sy * gap * d, cx + sx * (gap + s) * d, cy + sy * (gap + s) * d,
                    kill ? 2.2f : 1.6f, c);
        }
    }
}
