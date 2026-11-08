package net.m1227.module;

import java.nio.ByteBuffer;
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
 * drawn (so text and the hotbar stay sharp). Each frame the previous result
 * is blended over the new frame and then copied back into a texture: a
 * single full-screen quad and one GPU-side copy per frame, no shaders.
 *
 * In "Dynamic" mode the strength follows how fast the camera turns and how
 * fast you move, so a still view is perfectly sharp. The per-frame blend is
 * corrected for frame time, so the trail length is the same at 60 or 400 FPS.
 */
public final class MotionBlurModule extends Module {
    private final Setting.Mode mode, quality;
    private final Setting.Num intensity, smoothness, cameraSens, moveSens, limit;

    private int tex = -1, texW, texH;
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
        if (tex != -1) {
            GL11.glDeleteTextures(tex);
            tex = -1;
        }
        primed = false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.max(0.0005f, Math.min(0.1f, (now - lastNanos) / 1.0E9f));
        lastNanos = now;

        // how much motion is there right now (0..1)
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
        int w = mc.displayWidth, h = mc.displayHeight;
        ensureTexture(w, h);
        float strength = Math.min(limit.f(), amount) * intensity.f();
        // retention per 1/60 s, converted to this frame's duration
        float alpha = strength <= 0.01f ? 0f : (float) Math.pow(strength, dt * 60f);
        if (primed && alpha > 0.01f) draw(e.resolution.getScaledWidth(), e.resolution.getScaledHeight(), alpha);
        frame++;
        if (!primed || !quality.is("Performance") || (frame & 1) == 0 || alpha <= 0.01f) {
            GlStateManager.bindTexture(tex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            primed = true;
        }
    }

    private void ensureTexture(int w, int h) {
        if (tex != -1 && w == texW && h == texH) return;
        if (tex != -1) GL11.glDeleteTextures(tex);
        tex = GL11.glGenTextures();
        texW = w;
        texH = h;
        GlStateManager.bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, w, h, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        primed = false;
    }

    private void draw(float sw, float sh, float alpha) {
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 0, 1);
        GlStateManager.bindTexture(tex);
        GlStateManager.color(1f, 1f, 1f, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(0f, 0f);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(0f, sh);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(sw, sh);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(sw, 0f);
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableDepth();
    }
}
