package net.m1227.client.ui;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * A small 3D space inside a 2D screen, for previews (character, cosmetics, weapons).
 * World units: +X right, +Y up, +Z towards the viewer; 'scale' screen units per block.
 * The depth buffer is cleared before and after, so 2D drawn later is never hidden by the model.
 */
public final class Stage3D {
    private Stage3D() {}

    public static void begin(float x, float y, float scale, float yawDeg, float pitchDeg) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 300f);
        GlStateManager.scale(scale, -scale, scale);
        GlStateManager.rotate(pitchDeg, 1f, 0f, 0f);
        GlStateManager.rotate(yawDeg, 0f, 1f, 0f);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
    }

    public static void end() {
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.popMatrix();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    /** Soft floor shadow under a preview (2D, call outside begin/end). */
    public static void floor(float cx, float cy, float w, int accent) {
        net.m1227.ui.Gfx.glow(cx, cy, w * 0.9f, net.m1227.ui.Gfx.alpha(accent, 0.18f));
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.scale(1f, 0.22f, 1f);
        net.m1227.ui.Gfx.circle(0, 0, w * 0.45f, 0x50000000);
        net.m1227.ui.Gfx.annulus(0, 0, w * 0.45f, w * 0.47f, 0, Math.PI * 2, net.m1227.ui.Gfx.alpha(accent, 0.5f));
        GlStateManager.popMatrix();
    }
}
