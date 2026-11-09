package net.m1227.vfx;

import java.util.Random;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Electric arcs: jagged lines between two points that re-roll their shape
 * ~30 times a second (so they crackle) and fade out. Each arc is drawn as a
 * wide dim glow pass plus a thin bright core, with optional side branches.
 */
public final class Arcs {
    public static Arcs INSTANCE = new Arcs();
    private static final int MAX = 48, SEG = 12;

    private final double[] x0 = new double[MAX], y0 = new double[MAX], z0 = new double[MAX];
    private final double[] x1 = new double[MAX], y1 = new double[MAX], z1 = new double[MAX];
    private final float[] age = new float[MAX], life = new float[MAX], jitter = new float[MAX], alpha = new float[MAX];
    private final int[] color = new int[MAX], seed = new int[MAX];
    private int count;
    private final Random rng = new Random();
    private final float[] px = new float[SEG + 1], py = new float[SEG + 1], pz = new float[SEG + 1];

    Arcs() {}

    /** A private instance for menu previews. */
    public static Arcs isolated() {
        return new Arcs();
    }

    public boolean active() {
        return count > 0;
    }

    public void clear() {
        count = 0;
    }

    public void spawn(double ax, double ay, double az, double bx, double by, double bz,
                      float lifeSec, float jit, int rgb, float a) {
        if (count >= MAX) return;
        int i = count++;
        x0[i] = ax;
        y0[i] = ay;
        z0[i] = az;
        x1[i] = bx;
        y1[i] = by;
        z1[i] = bz;
        age[i] = 0f;
        life[i] = Math.max(0.05f, lifeSec);
        jitter[i] = jit;
        color[i] = rgb;
        alpha[i] = a;
        seed[i] = rng.nextInt();
    }

    void update(float dt) {
        for (int i = 0; i < count; ) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                int l = --count;
                x0[i] = x0[l]; y0[i] = y0[l]; z0[i] = z0[l];
                x1[i] = x1[l]; y1[i] = y1[l]; z1[i] = z1[l];
                age[i] = age[l]; life[i] = life[l]; jitter[i] = jitter[l];
                color[i] = color[l]; alpha[i] = alpha[l]; seed[i] = seed[l];
                continue;
            }
            i++;
        }
    }

    void render(ParticleSystem ps) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        for (int i = 0; i < count; i++) {
            float t = age[i] / life[i];
            float a = alpha[i] * (1f - t * t);
            if (a <= 0.01f) continue;
            rng.setSeed(seed[i] * 31L + (long) (age[i] * 30f));
            build(i, ps);
            int c = color[i];
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            strip(5f, r, g, b, a * 0.22f);
            strip(1.6f, Math.min(1f, r + 0.4f), Math.min(1f, g + 0.4f), Math.min(1f, b + 0.4f), a);
            // one short branch from a random midpoint
            int k = 3 + rng.nextInt(SEG - 6);
            float bx = px[k], by = py[k], bz = pz[k];
            float len = jitter[i] * 2.5f;
            float ex = bx + (rng.nextFloat() - 0.5f) * len, ey = by + (rng.nextFloat() - 0.3f) * len,
                    ez = bz + (rng.nextFloat() - 0.5f) * len;
            GL11.glLineWidth(1.2f);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glColor4f(r, g, b, a * 0.7f);
            GL11.glVertex3f(bx, by, bz);
            GL11.glVertex3f((bx + ex) / 2f + (rng.nextFloat() - 0.5f) * len * 0.3f, (by + ey) / 2f, (bz + ez) / 2f);
            GL11.glColor4f(r, g, b, 0f);
            GL11.glVertex3f(ex, ey, ez);
            GL11.glEnd();
        }
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    private void build(int i, ParticleSystem ps) {
        float ax = (float) (x0[i] - ps.rX), ay = (float) (y0[i] - ps.rY), az = (float) (z0[i] - ps.rZ);
        float bx = (float) (x1[i] - ps.rX), by = (float) (y1[i] - ps.rY), bz = (float) (z1[i] - ps.rZ);
        for (int s = 0; s <= SEG; s++) {
            float u = s / (float) SEG;
            float j = (s == 0 || s == SEG) ? 0f : jitter[i] * (float) Math.sin(Math.PI * u);
            px[s] = ax + (bx - ax) * u + (rng.nextFloat() - 0.5f) * 2f * j;
            py[s] = ay + (by - ay) * u + (rng.nextFloat() - 0.5f) * 2f * j;
            pz[s] = az + (bz - az) * u + (rng.nextFloat() - 0.5f) * 2f * j;
        }
    }

    private void strip(float width, float r, float g, float b, float a) {
        GL11.glLineWidth(width);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int s = 0; s <= SEG; s++) GL11.glVertex3f(px[s], py[s], pz[s]);
        GL11.glEnd();
    }
}
