package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Replaces the vanilla crosshair with a configurable one (first person only, like vanilla).
 *
 * 1.22: the drawing is one function, {@link #render}, used both in game and by the Crosshair
 * Editor's live preview, so the preview is exactly what you get. New: four styles (Circle Dot,
 * Arcs, Hybrid, Sniper), animations (Pulse, Rotate, Breathing gap), an outline width and
 * presets.
 */
public final class CrosshairModule extends Module {
    public static CrosshairModule INSTANCE;
    public static final String[] STYLES = {"Cross", "T-Cross", "X", "Circle", "Dot", "Chevron", "Square", "Triangle", "Brackets",
            "Diamond", "Circle Dot", "Arcs", "Hybrid", "Sniper"};
    public final Setting.Mode style, animation;
    public final Setting.Col color, targetColor, hitColor;
    public final Setting.Num size, gap, thickness, opacity, outlineWidth, animSpeed;
    public final Setting.Bool dot, outline, dynamic, onEntity, hitFlash;
    private long lastHit;
    private float spread;

    public CrosshairModule() {
        super("Crosshair", "Custom crosshair with a live editor: styles, colours, animation, presets.", Category.HUD, false);
        INSTANCE = this;
        style = add(new Setting.Mode("Style", "Cross", STYLES));
        color = add(new Setting.Col("Color", 0xFFFFFF));
        size = add(new Setting.Num("Size", 4, 1, 12, 0.5, "px"));
        gap = add(new Setting.Num("Gap", 2, 0, 8, 0.5, "px"));
        thickness = add(new Setting.Num("Thickness", 1, 0.5, 3, 0.25, "px"));
        opacity = add(new Setting.Num("Opacity", 1.0, 0.2, 1.0, 0.05, ""));
        dot = add(new Setting.Bool("Center dot", false));
        outline = add(new Setting.Bool("Outline", true));
        outlineWidth = add(new Setting.Num("Outline width", 1, 0.5, 2, 0.25, "px")).showIf(() -> outline.on());
        dynamic = add(new Setting.Bool("Dynamic spread (moving / jumping)", true));
        onEntity = add(new Setting.Bool("Color when aiming at entity", true));
        targetColor = add(new Setting.Col("Entity color", 0xFF4D5E));
        hitFlash = add(new Setting.Bool("Flash on hit", true));
        hitColor = add(new Setting.Col("Hit flash color", 0xFFC857));
        animation = add(new Setting.Mode("Animation", "None", "None", "Pulse", "Rotate", "Breathing gap"));
        animSpeed = add(new Setting.Num("Animation speed", 1.0, 0.2, 3.0, 0.1, "x")).showIf(() -> !animation.is("None"));
        add(new Setting.Action("Open crosshair editor", () -> net.m1227.client.menu.CrosshairEditorScreen.open()));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        lastHit = System.currentTimeMillis();
    }

    /** For the editor's "test hit flash". */
    public void flash() {
        lastHit = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onPre(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.thirdPersonView != 0 || mc.thePlayer == null) return;
        e.setCanceled(true);
        EntityPlayerSP p = mc.thePlayer;
        float target = 0f;
        if (dynamic.on()) {
            double v = Math.sqrt((p.posX - p.prevPosX) * (p.posX - p.prevPosX) + (p.posZ - p.prevPosZ) * (p.posZ - p.prevPosZ));
            target = (float) Math.min(4.0, v * 12.0) + (p.onGround ? 0f : 2.5f);
        }
        spread += (target - spread) * 0.25f;
        boolean aim = mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
        render(e.resolution.getScaledWidth() / 2f, e.resolution.getScaledHeight() / 2f, aim, spread);
    }

    /** Hit flash amount right now (0..1). */
    public float hitAmount() {
        return 1f - Math.min(1f, (System.currentTimeMillis() - lastHit) / 250f);
    }

    /**
     * Draws the crosshair centred on (cx, cy) in the current GUI coordinates. Used by the game and
     * by the editor preview (which scales the matrix around the centre to zoom).
     */
    public void render(float cx, float cy, boolean aimingAtEntity, float spreadPx) {
        int c = 0xFF000000 | color.rgb();
        if (onEntity.on() && aimingAtEntity) c = 0xFF000000 | targetColor.rgb();
        float hit = hitAmount();
        if (hitFlash.on() && hit > 0f) c = Gfx.mix(c, 0xFF000000 | hitColor.rgb(), hit);
        float time = (System.currentTimeMillis() % 100000L) / 1000f * animSpeed.f();
        float op = opacity.f();
        float s = size.f(), g = gap.f() + spreadPx, t = thickness.f();
        String anim = animation.get();
        if (anim.equals("Pulse")) op *= 0.65f + 0.35f * (0.5f + 0.5f * (float) Math.sin(time * 4f));
        else if (anim.equals("Breathing gap")) g += 1.2f * (0.5f + 0.5f * (float) Math.sin(time * 2.5f));
        c = Gfx.alpha(c, op);
        int oc = Gfx.alpha(0xFF000000, op * 0.6f);
        float ow = outlineWidth.f();
        String st = style.get();
        boolean rotate = anim.equals("Rotate");
        if (rotate) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(cx, cy, 0);
            GlStateManager.rotate((time * 90f) % 360f, 0, 0, 1);
            GlStateManager.translate(-cx, -cy, 0);
        }
        try {
            if (outline.on()) shape(st, cx, cy, s, g, t, oc, ow);
            shape(st, cx, cy, s, g, t, c, 0f);
        } finally {
            if (rotate) GlStateManager.popMatrix();
        }
        if (dot.on() || st.equals("Dot") || st.equals("Circle Dot")) {
            if (outline.on()) Gfx.circle(cx, cy, Math.max(0.8f, t) + ow, oc);
            Gfx.circle(cx, cy, Math.max(0.8f, t), c);
        }
    }

    private static void ring(float cx, float cy, float r, float tt, int c, double from, double to, int n) {
        for (int i = 0; i < n; i++) {
            double a0 = from + (to - from) * i / n, a1 = from + (to - from) * (i + 1) / n;
            Gfx.line(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, tt, c);
        }
    }

    private static void cross(float cx, float cy, float s, float g, float tt, int c, float grow, boolean top) {
        if (top) Gfx.rect(cx - tt / 2f, cy - g - s - grow, tt, s + grow * 2f, c);
        Gfx.rect(cx - tt / 2f, cy + g - grow, tt, s + grow * 2f, c);
        Gfx.rect(cx - g - s - grow, cy - tt / 2f, s + grow * 2f, tt, c);
        Gfx.rect(cx + g - grow, cy - tt / 2f, s + grow * 2f, tt, c);
    }

    /** grow > 0 draws a slightly larger copy (outline). */
    public static void shape(String st, float cx, float cy, float s, float g, float t, int c, float grow) {
        float tt = t + grow * 2f;
        if (st.equals("Cross") || st.equals("T-Cross")) {
            cross(cx, cy, s, g, tt, c, grow, !st.equals("T-Cross"));
        } else if (st.equals("Circle") || st.equals("Circle Dot")) {
            ring(cx, cy, g + s * 0.5f, tt, c, 0, Math.PI * 2, 32);
        } else if (st.equals("Arcs")) {
            float r = g + s * 0.6f;
            for (int q = 0; q < 4; q++) {
                double a = q * Math.PI / 2 + Math.PI / 4;
                ring(cx, cy, r, tt, c, a - 0.5, a + 0.5, 6);
            }
        } else if (st.equals("Hybrid")) {
            cross(cx, cy, s * 0.6f, g, tt, c, grow, true);
            ring(cx, cy, g + s * 0.6f + 2f, Math.max(0.5f, tt * 0.7f), c, 0, Math.PI * 2, 32);
        } else if (st.equals("Sniper")) {
            float len = s * 2.2f;
            cross(cx, cy, len, g + 1f, Math.max(0.5f, tt * 0.6f), c, grow, true);
            ring(cx, cy, g + len * 0.55f, Math.max(0.5f, tt * 0.6f), c, 0, Math.PI * 2, 40);
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
            float px0 = 0, py0 = 0;
            for (int i = 0; i <= 3; i++) {
                double a = -Math.PI / 2 + i * Math.PI * 2 / 3;
                float px = cx + (float) Math.cos(a) * r, py = cy + (float) Math.sin(a) * r;
                if (i > 0) Gfx.line(px0, py0, px, py, tt, c);
                px0 = px;
                py0 = py;
            }
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

    // ------------------------------------------------------------------ presets

    public static final String[] PRESETS = {"Classic", "Competitive", "Minimal dot", "Circle", "Neon", "Sniper", "Soft brackets", "Hybrid"};

    public void applyPreset(String n) {
        Setting<?>[] all = {style, color, size, gap, thickness, opacity, dot, outline, outlineWidth, dynamic, animation};
        for (Setting<?> s : all) s.reset();
        if (n.equals("Competitive")) {
            style.set("Cross");
            size.setClamped(3.5);
            gap.setClamped(1.5);
            thickness.setClamped(1.0);
            color.setRgb(0x00FF7F);
            dynamic.set(false);
        } else if (n.equals("Minimal dot")) {
            style.set("Dot");
            thickness.setClamped(1.25);
            dynamic.set(false);
        } else if (n.equals("Circle")) {
            style.set("Circle Dot");
            size.setClamped(5);
            gap.setClamped(2.5);
            thickness.setClamped(0.75);
        } else if (n.equals("Neon")) {
            style.set("Arcs");
            color.setRgb(0x8FE6FF);
            size.setClamped(5);
            gap.setClamped(3);
            outline.set(false);
            animation.set("Pulse");
        } else if (n.equals("Sniper")) {
            style.set("Sniper");
            size.setClamped(5);
            gap.setClamped(1);
            thickness.setClamped(0.75);
            dot.set(true);
            dynamic.set(false);
        } else if (n.equals("Soft brackets")) {
            style.set("Brackets");
            size.setClamped(6);
            gap.setClamped(3);
            opacity.setClamped(0.85);
            color.setRgb(0xECEEF4);
        } else if (n.equals("Hybrid")) {
            style.set("Hybrid");
            size.setClamped(4);
            gap.setClamped(1.5);
            color.setRgb(0xFFC857);
        }
        net.m1227.core.ConfigManager.markDirty();
    }

    /** Every crosshair setting back to its default. */
    public void restoreDefaults() {
        for (Setting<?> s : settings()) if (s.persistent()) s.reset();
    }
}
