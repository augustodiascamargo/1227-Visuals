package net.m1227.render;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Shifts whatever is drawn next by an exact number of screen pixels (and optionally pushes it a
 * little deeper), by pre-multiplying the projection matrix. Used for outlines that are the same
 * thickness from every angle and at every distance: the same model drawn several times, each
 * time moved a few pixels in a different direction, behind the real one.
 *
 * begin() saves the projection, shift() can be called many times, end() restores it exactly.
 */
public final class ScreenOffset {
    private ScreenOffset() {}

    private static final FloatBuffer SAVED = BufferUtils.createFloatBuffer(16);

    public static void begin() {
        SAVED.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, SAVED);
    }

    /** dx/dy in screen pixels (y up), z in normalised depth units (positive = farther). */
    public static void shift(float dxPx, float dyPx, float zNdc) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.translate(2f * dxPx / Math.max(1, mc.displayWidth), 2f * dyPx / Math.max(1, mc.displayHeight), zNdc);
        SAVED.rewind();
        GL11.glMultMatrix(SAVED);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    public static void end() {
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        SAVED.rewind();
        GL11.glLoadMatrix(SAVED);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
    }

    /**
     * Depth push that keeps a copy behind a body about 0.6 blocks deep at distance d (blocks),
     * for Minecraft's near plane of 0.05.
     */
    public static float behind(double d) {
        double dd = Math.max(1.0, d);
        return (float) Math.min(0.02, 2 * 0.05 / (dd * dd) * 0.6);
    }
}
