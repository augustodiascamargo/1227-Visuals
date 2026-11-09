package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Motion blur by frame accumulation, applied to the world before the HUD is
 * drawn (so text and the hotbar stay sharp). An off-screen framebuffer holds
 * the running blend: every frame the new image is mixed into it with weight
 * (1 - retain), then the result is drawn back over the screen opaque. The
 * accumulated image is a weighted average of recent frames, so brightness
 * is always preserved (the old copy-and-overlay version could darken).
 *
 * In "Dynamic" mode the strength follows how fast the camera turns and how
 * fast you move, so a still view is perfectly sharp. The per-frame blend is
 * corrected for frame time, so the trail length is the same at 60 or 400 FPS.
 */
public final class MotionBlurModule extends Module {
    private final Setting.Mode mode, quality;
    private final Setting.Num intensity, smoothness, cameraSens, moveSens, limit;

    private net.minecraft.client.shader.Framebuffer accum;
    private boolean primed;
    private float amount;
    private long lastNanos;
    private float lastYaw, lastPitch;
    private int frame;

    public MotionBlurModule() {
        super("Motion Blur", "Blur on fast camera turns and movement.", Category.CAMERA, false);
        mode = add(new Setting.Mode("Mode", "Dynamic", "Dynamic", "Constant"));
        intensity = add(new Setting.Num("Intensity", 0.55, 0.05, 0.9, 0.05, ""));
        smoothness = add(new Setting.Num("Smoothness", 0.5, 0.0, 1.0, 0.05, ""));
        cameraSens = add(new Setting.Num("Camera sensitivity", 1.0, 0.0, 3.0, 0.1, "x"));
        moveSens = add(new Setting.Num("Movement sensitivity", 0.6, 0.0, 3.0, 0.1, "x"));
        limit = add(new Setting.Num("Max strength", 0.8, 0.1, 1.0, 0.05, ""));
        quality = add(new Setting.Mode("Quality", "Quality", "Performance", "Quality"));
    }

    @Override
    protected void onDisable() {
        if (accum != null) {
            accum.deleteFramebuffer();
            accum = null;
        }
        primed = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || !net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled()) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.max(0.0005f, Math.min(0.1f, (now - lastNanos) / 1.0E9f));
        lastNanos = now;

        float target;
        if (mode.is("Constant")) {
            target = 1f;
        } else {
            float dYaw = Math.abs(p.rotationYaw - lastYaw), dPitch = Math.abs(p.rotationPitch - lastPitch);
            float turn = (dYaw + dPitch) / dt;                       // degrees per second
            double vx = p.posX - p.prevPosX, vz = p.posZ - p.prevPosZ, vy = p.posY - p.prevPosY;
            float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) * 20f; // blocks per second
            target = turn / 540f * cameraSens.f() + Math.max(0f, speed - 4f) / 10f * moveSens.f();
            target = Math.min(1f, target);
        }
        lastYaw = p.rotationYaw;
        lastPitch = p.rotationPitch;
        float response = 2f + 28f * (1f - smoothness.f());
        amount += (target - amount) * (1f - (float) Math.exp(-response * dt));

        if (mc.currentScreen != null) {
            primed = false;
            return;
        }
        net.minecraft.client.shader.Framebuffer main = mc.getFramebuffer();
        int w = main.framebufferWidth, h = main.framebufferHeight;
        if (accum == null || accum.framebufferWidth != w || accum.framebufferHeight != h) {
            if (accum != null) accum.deleteFramebuffer();
            accum = new net.minecraft.client.shader.Framebuffer(w, h, false);
            accum.setFramebufferFilter(GL11.GL_LINEAR);
            primed = false;
        }
        float strength = Math.min(limit.f(), amount) * intensity.f();
        // how much of the old image survives this frame (per 1/60 s, corrected for frame time)
        float retain = strength <= 0.01f ? 0f : (float) Math.pow(strength, dt * 60f);
        frame++;
        boolean skip = quality.is("Performance") && (frame & 1) == 1 && primed;

        java.nio.FloatBuffer proj = org.lwjgl.BufferUtils.createFloatBuffer(16), model = org.lwjgl.BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, proj);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, model);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, w, h, 0, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableFog(); // a leftover world fog would tint the full-screen quads
        // make sure only texture unit 0 is used: a still-enabled lightmap unit would darken the image
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();

        // 1) mix the new frame into the accumulation buffer
        if (!skip) {
            accum.bindFramebuffer(true);
            if (!primed || retain <= 0.001f) {
                GlStateManager.disableBlend();
                quad(main, w, h, 1f);
                primed = true;
            } else {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                quad(main, w, h, 1f - retain);
            }
        }
        // 2) put the blended image back on screen (opaque, so nothing can darken)
        main.bindFramebuffer(true);
        if (retain > 0.001f) {
            GlStateManager.disableBlend();
            quad(accum, w, h, 1f);
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GL11.glLoadMatrix(proj);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(model);
    }

    /** Full-screen quad sampling a framebuffer's colour texture. */
    private static void quad(net.minecraft.client.shader.Framebuffer src, int w, int h, float alpha) {
        GlStateManager.bindTexture(src.framebufferTexture);
        float u = src.framebufferWidth / (float) src.framebufferTextureWidth;
        float v = src.framebufferHeight / (float) src.framebufferTextureHeight;
        GlStateManager.color(1f, 1f, 1f, alpha);
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, v);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, h);
        GL11.glTexCoord2f(u, 0f);
        GL11.glVertex2f(w, h);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(w, 0f);
        GL11.glEnd();
    }
}
