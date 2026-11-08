package net.m1227.vfx;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Expanding translucent energy domes plus ground rings. Each wave draws a
 * few nested shells (a cached unit-sphere display list, scaled per shell)
 * and two ring sprites on the ground. A screen-space distortion pass would
 * need a framebuffer shader and is not part of this version.
 */
public final class ShockwaveSystem {
    public static final ShockwaveSystem INSTANCE = new ShockwaveSystem();
    private static final int MAX = 10;
    private static final int SHELLS = 3;

    private final double[] x = new double[MAX], y = new double[MAX], z = new double[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], radius = new float[MAX], alpha = new float[MAX];
    private final int[] c0 = new int[MAX], c1 = new int[MAX];
    private int count;
    private int list = -1;

    private ShockwaveSystem() {}

    public boolean active() {
        return count > 0;
    }

    public void clear() {
        count = 0;
    }

    public void spawn(double px, double py, double pz, float r, float lifeSec, int rgb0, int rgb1, float a) {
        if (count >= MAX) return;
        int i = count++;
        x[i] = px;
        y[i] = py;
        z[i] = pz;
        age[i] = 0f;
        life[i] = Math.max(0.2f, lifeSec);
        radius[i] = r;
        alpha[i] = a;
        c0[i] = rgb0;
        c1[i] = rgb1;
    }

    public void update(float dt) {
        for (int i = 0; i < count; ) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                int l = count - 1;
                x[i] = x[l];
                y[i] = y[l];
                z[i] = z[l];
                age[i] = age[l];
                life[i] = life[l];
                radius[i] = radius[l];
                alpha[i] = alpha[l];
                c0[i] = c0[l];
                c1[i] = c1[l];
                count--;
                continue;
            }
            i++;
        }
    }

    private static float ease(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    /** Queues the ground rings as per-frame sprites. */
    public void emitSprites(ParticleSystem ps) {
        for (int i = 0; i < count; i++) {
            float t = age[i] / life[i];
            float e = ease(t);
            for (int k = 0; k < 2; k++) {
                float rk = radius[i] * e * (k == 0 ? 1f : 0.62f);
                if (rk < 0.05f) continue;
                float a = alpha[i] * (float) Math.pow(1f - t, 1.4) * (k == 0 ? 0.9f : 0.6f);
                Particle p = ps.frame(x[i], y[i] + 0.04, z[i]);
                if (p == null) continue;
                p.sprite = k == 0 ? Sprite.RING : Sprite.HALO;
                p.flat = true;
                p.size0 = p.size1 = rk * 1.23f;
                p.rot = k * 1.1f + t * 0.8f;
                p.setColors(c1[i], c0[i]);
                p.alpha = a;
            }
        }
    }

    public void render(double camX, double camY, double camZ) {
        if (count == 0) return;
        ensureList();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        for (int i = 0; i < count; i++) {
            float t = age[i] / life[i];
            for (int s = 0; s < SHELLS; s++) {
                float ts = t - s * 0.07f;
                if (ts <= 0f || ts >= 1f) continue;
                float r = radius[i] * ease(ts) * (1f - s * 0.1f);
                float a = alpha[i] * (float) Math.pow(1f - ts, 1.6) * (0.38f - s * 0.07f);
                if (a <= 0.003f || r < 0.05f) continue;
                int c = ParticleSystem.mix(c0[i], c1[i], s / (float) (SHELLS - 1));
                GlStateManager.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
                GlStateManager.pushMatrix();
                GlStateManager.translate((float) (x[i] - camX), (float) (y[i] - camY), (float) (z[i] - camZ));
                GlStateManager.scale(r, r, r);
                GL11.glCallList(list);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void ensureList() {
        if (list != -1) return;
        final int lat = 14, lon = 28;
        list = GL11.glGenLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        for (int i = 0; i < lat; i++) {
            double a0 = Math.PI * (-0.5 + (double) i / lat), a1 = Math.PI * (-0.5 + (double) (i + 1) / lat);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= lon; j++) {
                double b = 2.0 * Math.PI * j / lon;
                GL11.glVertex3d(Math.cos(a0) * Math.cos(b), Math.sin(a0), Math.cos(a0) * Math.sin(b));
                GL11.glVertex3d(Math.cos(a1) * Math.cos(b), Math.sin(a1), Math.cos(a1) * Math.sin(b));
            }
            GL11.glEnd();
        }
        GL11.glEndList();
    }
}
