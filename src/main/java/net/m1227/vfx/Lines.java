package net.m1227.vfx;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Luminous world-space line segments that live for one frame. Modules add
 * shapes every frame from their RenderWorldLastEvent handler (so the lines
 * follow entities exactly); everything is drawn in one batch: a wide faint
 * glow pass and a thin bright core, additive, depth-tested.
 * Fixed preallocated arrays: no allocation per frame.
 */
public final class Lines {
    public static final Lines INSTANCE = new Lines();
    private static final int MAX = 6000;

    private final float[] a = new float[MAX * 6];
    private final float[] col = new float[MAX * 4];
    private int count;

    private Lines() {}

    /** Adds a segment; positions are world coordinates. */
    public void add(double x1, double y1, double z1, double x2, double y2, double z2, int rgb, float alpha) {
        if (count >= MAX || alpha <= 0.004f) return;
        ParticleSystem ps = ParticleSystem.INSTANCE;
        int i = count * 6;
        a[i] = (float) (x1 - ps.rX);
        a[i + 1] = (float) (y1 - ps.rY);
        a[i + 2] = (float) (z1 - ps.rZ);
        a[i + 3] = (float) (x2 - ps.rX);
        a[i + 4] = (float) (y2 - ps.rY);
        a[i + 5] = (float) (z2 - ps.rZ);
        int c = count * 4;
        col[c] = ((rgb >> 16) & 255) / 255f;
        col[c + 1] = ((rgb >> 8) & 255) / 255f;
        col[c + 2] = (rgb & 255) / 255f;
        col[c + 3] = Math.min(1f, alpha);
        count++;
    }

    /**
     * Regular polygon (n sides) or circle (large n) around a centre. tilt
     * leans the shape around the X axis (0 = flat on the ground).
     */
    public void polygon(double cx, double cy, double cz, double r, int n, double rot, double tilt, int rgb, float alpha) {
        double ct = Math.cos(tilt), st = Math.sin(tilt);
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= n; i++) {
            double ang = rot + i * Math.PI * 2 / n;
            double lx = Math.cos(ang) * r, lz = Math.sin(ang) * r;
            double x = cx + lx, y = cy + lz * st, z = cz + lz * ct;
            if (i > 0) add(px, py, pz, x, y, z, rgb, alpha);
            px = x;
            py = y;
            pz = z;
        }
    }

    /** Star polygon: connects every k-th point of an n-gon (n=6,k=2 is a hexagram). */
    public void star(double cx, double cy, double cz, double r, int n, int k, double rot, int rgb, float alpha) {
        for (int i = 0; i < n; i++) {
            double a0 = rot + i * Math.PI * 2 / n, a1 = rot + ((i + k) % n) * Math.PI * 2 / n;
            add(cx + Math.cos(a0) * r, cy, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, cy, cz + Math.sin(a1) * r, rgb, alpha);
        }
    }

    void render() {
        if (count == 0) return;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        pass(4.5f, 0.22f);
        pass(1.6f, 1f);
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
        count = 0;
    }

    void clear() {
        count = 0;
    }

    private void pass(float width, float alphaMul) {
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        for (int s = 0; s < count; s++) {
            int i = s * 6, c = s * 4;
            GL11.glColor4f(col[c], col[c + 1], col[c + 2], col[c + 3] * alphaMul);
            GL11.glVertex3f(a[i], a[i + 1], a[i + 2]);
            GL11.glVertex3f(a[i + 3], a[i + 4], a[i + 5]);
        }
        GL11.glEnd();
    }
}
