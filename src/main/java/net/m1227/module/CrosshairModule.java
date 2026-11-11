package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Replaces the vanilla crosshair with a configurable one (first person only, like vanilla). */
public final class CrosshairModule extends Module {
    private final Setting.Mode style;
    private final Setting.Col color, targetColor, hitColor;
    private final Setting.Num size, gap, thickness, opacity;
    private final Setting.Bool dot, outline, dynamic, onEntity, hitFlash;
    private long lastHit;
    private float spread;

    public CrosshairModule() {
        super("Crosshair", "Custom crosshair: style, size, gap, colours, dynamic spread.", Category.HUD, false);
        style = add(new Setting.Mode("Style", "Cross", "Cross", "T-Cross", "X", "Circle", "Dot", "Chevron", "Square",
                "Triangle", "Brackets", "Diamond"));
        color = add(new Setting.Col("Color", 0xFFFFFF));
        size = add(new Setting.Num("Size", 4, 1, 12, 0.5, "px"));
        gap = add(new Setting.Num("Gap", 2, 0, 8, 0.5, "px"));
        thickness = add(new Setting.Num("Thickness", 1, 0.5, 3, 0.25, "px"));
        opacity = add(new Setting.Num("Opacity", 1.0, 0.2, 1.0, 0.05, ""));
        dot = add(new Setting.Bool("Center dot", false));
        outline = add(new Setting.Bool("Outline", true));
        dynamic = add(new Setting.Bool("Dynamic spread (moving / jumping)", true));
        onEntity = add(new Setting.Bool("Color when aiming at entity", true));
        targetColor = add(new Setting.Col("Entity color", 0xFF4D5E));
        hitFlash = add(new Setting.Bool("Flash on hit", true));
        hitColor = add(new Setting.Col("Hit flash color", 0xFFC857));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        lastHit = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.thirdPersonView != 0 || mc.thePlayer == null) return;
        e.setCanceled(true);
        draw(mc, e.resolution.getScaledWidth() / 2f, e.resolution.getScaledHeight() / 2f);
    }

    private void draw(Minecraft mc, float cx, float cy) {
        EntityPlayerSP p = mc.thePlayer;
        float target = 0f;
        if (dynamic.on()) {
            double v = Math.sqrt((p.posX - p.prevPosX) * (p.posX - p.prevPosX) + (p.posZ - p.prevPosZ) * (p.posZ - p.prevPosZ));
            target = (float) Math.min(4.0, v * 12.0) + (p.onGround ? 0f : 2.5f);
        }
        spread += (target - spread) * 0.25f;
        int c = 0xFF000000 | color.rgb();
        if (onEntity.on() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            c = 0xFF000000 | targetColor.rgb();
        }
        float hit = 1f - Math.min(1f, (System.currentTimeMillis() - lastHit) / 250f);
        if (hitFlash.on() && hit > 0f) c = Gfx.mix(c, 0xFF000000 | hitColor.rgb(), hit);
        c = Gfx.alpha(c, opacity.f());
        float s = size.f(), g = gap.f() + spread, t = thickness.f();
        String st = style.get();
        if (outline.on()) shape(st, cx, cy, s, g, t, Gfx.alpha(0xFF000000, opacity.f() * 0.6f), 1f);
        shape(st, cx, cy, s, g, t, c, 0f);
        if (dot.on() || st.equals("Dot")) {
            if (outline.on()) Gfx.circle(cx, cy, t + 1f, Gfx.alpha(0xFF000000, opacity.f() * 0.6f));
            Gfx.circle(cx, cy, Math.max(0.8f, t), c);
        }
    }

    /** grow > 0 draws a slightly larger copy (outline). */
    private static void shape(String st, float cx, float cy, float s, float g, float t, int c, float grow) {
        float tt = t + grow * 2f;
        if (st.equals("Cross") || st.equals("T-Cross")) {
            if (!st.equals("T-Cross")) Gfx.rect(cx - tt / 2f, cy - g - s - grow, tt, s + grow * 2f, c);
            Gfx.rect(cx - tt / 2f, cy + g - grow, tt, s + grow * 2f, c);
            Gfx.rect(cx - g - s - grow, cy - tt / 2f, s + grow * 2f, tt, c);
            Gfx.rect(cx + g - grow, cy - tt / 2f, s + grow * 2f, tt, c);
        } else if (st.equals("Circle")) {
            int n = 32;
            float r = g + s * 0.5f;
            for (int i = 0; i < n; i++) {
                double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
                Gfx.line(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r,
                        cy + (float) Math.sin(a1) * r, tt, c);
            }
        } else if (st.equals("Chevron")) {
            Gfx.line(cx - s - g, cy + s * 0.6f + g, cx, cy + g * 0.3f, tt, c);
            Gfx.line(cx, cy + g * 0.3f, cx + s + g, cy + s * 0.6f + g, tt, c);
        } else if (st.equals("X")) {
            float d = 0.7071f, a0 = g, a1 = g + s;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    Gfx.line(cx + sx * a0 * d, cy + sy * a0 * d, cx + sx * (a1 + grow) * d, cy + sy * (a1 + grow) * d, tt, c);
                }
            }
        } else if (st.equals("Triangle")) {
            float r = g + s * 0.6f;
            float[] px = new float[3], py = new float[3];
            for (int i = 0; i < 3; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 3;
                px[i] = cx + (float) Math.cos(a) * r;
                py[i] = cy + (float) Math.sin(a) * r;
            }
            for (int i = 0; i < 3; i++) Gfx.line(px[i], py[i], px[(i + 1) % 3], py[(i + 1) % 3], tt, c);
        } else if (st.equals("Brackets")) {
            float h = g + s * 0.5f, arm = Math.max(1.5f, s * 0.45f);
            for (int sx = -1; sx <= 1; sx += 2) {
                Gfx.line(cx + sx * h, cy - h, cx + sx * h, cy + h, tt, c);
                Gfx.line(cx + sx * h, cy - h, cx + sx * (h - arm), cy - h, tt, c);
                Gfx.line(cx + sx * h, cy + h, cx + sx * (h - arm), cy + h, tt, c);
            }
        } else if (st.equals("Diamond")) {
            float h = g + s * 0.6f;
            Gfx.line(cx, cy - h, cx + h, cy, tt, c);
            Gfx.line(cx + h, cy, cx, cy + h, tt, c);
            Gfx.line(cx, cy + h, cx - h, cy, tt, c);
            Gfx.line(cx - h, cy, cx, cy - h, tt, c);
        } else if (st.equals("Square")) {
            float h = g + s * 0.5f;
            Gfx.line(cx - h, cy - h, cx + h, cy - h, tt, c);
            Gfx.line(cx + h, cy - h, cx + h, cy + h, tt, c);
            Gfx.line(cx + h, cy + h, cx - h, cy + h, tt, c);
            Gfx.line(cx - h, cy + h, cx - h, cy - h, tt, c);
        }
    }
}
