package net.m1227.vfx;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Random;
import net.m1227.module.PerformanceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Pooled billboard particle engine with a procedural sprite atlas.
 *
 * Frame order (all on RenderWorldLastEvent):
 *   HIGHEST  beginFrame : compute dt, time and camera position
 *   NORMAL   modules    : spawn particles / queue per-frame sprites
 *   LOWEST   endFrame   : shockwaves, physics, then one batch per blend mode
 *
 * Two pools: "pool" holds normal particles with a lifetime; "frameParticles"
 * holds sprites that exist for a single frame (auras, magic circles) and are
 * redrawn by their module every frame, so they never flicker or lag.
 * Depth test stays on, so effects are hidden by blocks (never see-through).
 */
public final class ParticleSystem {
    public static final ParticleSystem INSTANCE = new ParticleSystem();
    private static final int HARD_CAP = 6000;
    private static final int FRAME_CAP = 1024;
    private static final int CELL = 128;

    public final Random rng = new Random();
    /** Seconds since the previous frame (0 while a pausing GUI is open). */
    public float dt;
    /** Accumulated effect time in seconds (frozen while paused). */
    public double time;
    /** Camera (eye) position, used for distance checks and view vectors. */
    public double camX, camY, camZ;
    /**
     * Origin that world coordinates must be made relative to when drawing in
     * RenderWorldLastEvent: the view entity's interpolated FEET position. Minecraft's
     * camera matrix already contains the eye-height translation, so subtracting the
     * eye position here would push everything down by ~1.6 blocks.
     */
    public double rX, rY, rZ;

    private final Particle[] pool = new Particle[HARD_CAP];
    private final Particle[] frameParticles = new Particle[FRAME_CAP];
    private int count, frameCount;
    private long lastNanos;
    private ResourceLocation atlas;
    private float bRx, bRz, bUx, bUy, bUz;
    private double maxD2;

    private ParticleSystem() {
        for (int i = 0; i < HARD_CAP; i++) pool[i] = new Particle();
        for (int i = 0; i < FRAME_CAP; i++) frameParticles[i] = new Particle();
    }

    // ------------------------------------------------------------ helpers

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int rainbow(float phase) {
        phase = phase - (float) Math.floor(phase);
        return Color.HSBtoRGB(phase, 0.75f, 1f) & 0xFFFFFF;
    }

    public float rand(float a, float b) {
        return a + rng.nextFloat() * (b - a);
    }

    public int alive() {
        return count;
    }

    /** Scales a particle count by the quality setting (never 0 for n > 0). */
    public int scale(float n) {
        if (n <= 0f) return 0;
        return Math.max(1, Math.round(n * PerformanceModule.qualityScale()));
    }

    public boolean inRange(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        double d = PerformanceModule.INSTANCE.distance.get();
        return dx * dx + dy * dy + dz * dz <= d * d;
    }

    // -------------------------------------------------------------- spawn

    /** Returns a reset particle at the position, or null if capped or too far. */
    public Particle spawn(double x, double y, double z) {
        if (count >= Math.min(HARD_CAP, PerformanceModule.INSTANCE.maxParticles.i())) return null;
        if (!inRange(x, y, z)) return null;
        Particle p = pool[count++];
        p.reset();
        p.x = x;
        p.y = y;
        p.z = z;
        return p;
    }

    public Particle glow(double x, double y, double z, float vx, float vy, float vz,
                         float life, float size0, float size1, int rgb0, int rgb1, float alpha) {
        Particle p = spawn(x, y, z);
        if (p == null) return null;
        p.vx = vx;
        p.vy = vy;
        p.vz = vz;
        p.life = Math.max(0.05f, life);
        p.size0 = size0;
        p.size1 = size1;
        p.setColors(rgb0, rgb1);
        p.alpha = alpha;
        return p;
    }

    /**
     * A sprite that lives for this frame only. The caller re-queues it every
     * frame from its own RenderWorldLastEvent handler.
     */
    public Particle frame(double x, double y, double z) {
        if (frameCount >= FRAME_CAP) return null;
        if (!inRange(x, y, z)) return null;
        Particle p = frameParticles[frameCount++];
        p.reset();
        p.steady = true;
        p.x = x;
        p.y = y;
        p.z = z;
        return p;
    }

    // ------------------------------------------------------------- events

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beginFrame(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float d = lastNanos == 0L ? 0f : (now - lastNanos) / 1.0E9f;
        lastNanos = now;
        if (d > 0.1f) d = 0.1f;
        GuiScreen s = mc.currentScreen;
        if (s != null && mc.isSingleplayer() && s.doesGuiPauseGame()) d = 0f;
        dt = d;
        time += d;

        Entity v = mc.getRenderViewEntity();
        if (v != null) {
            camX = lerp(v.lastTickPosX, v.posX, e.partialTicks);
            camY = lerp(v.lastTickPosY, v.posY, e.partialTicks) + v.getEyeHeight();
            camZ = lerp(v.lastTickPosZ, v.posZ, e.partialTicks);
            rX = lerp(v.lastTickPosX, v.posX, e.partialTicks);
            rY = lerp(v.lastTickPosY, v.posY, e.partialTicks);
            rZ = lerp(v.lastTickPosZ, v.posZ, e.partialTicks);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void endFrame(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.getRenderViewEntity() == null) {
            frameCount = 0;
            return;
        }
        ShockwaveSystem sw = ShockwaveSystem.INSTANCE;
        if (sw.active()) {
            sw.update(dt);
            sw.emitSprites(this);
            sw.render(rX, rY, rZ);
        }
        update(dt);
        if (count > 0 || frameCount > 0) render(mc);
        Arcs arcs = Arcs.INSTANCE;
        if (arcs.active()) {
            arcs.update(dt);
            arcs.render(this);
        }
        Lines.INSTANCE.render();
        frameCount = 0;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        if (e.world.isRemote) {
            count = 0;
            frameCount = 0;
            ShockwaveSystem.INSTANCE.clear();
            Arcs.INSTANCE.clear();
            Lines.INSTANCE.clear();
        }
    }

    // ------------------------------------------------------------ physics

    private void update(float dt) {
        if (dt <= 0f) return;
        for (int i = 0; i < count; ) {
            Particle p = pool[i];
            p.age += dt;
            if (p.age >= p.life) {
                pool[i] = pool[count - 1];
                pool[count - 1] = p;
                count--;
                continue;
            }
            float damp = Math.max(0f, 1f - p.drag * dt);
            p.vx *= damp;
            p.vy = p.vy * damp - p.gravity * dt;
            p.vz *= damp;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.rot += p.rotVel * dt;
            if (p.sway != 0f) {
                p.x += Math.cos(p.age * p.swayFreq + p.swayPhase) * p.sway * dt;
                p.z += Math.sin(p.age * p.swayFreq * 0.8f + p.swayPhase) * p.sway * dt;
            }
            i++;
        }
    }

    // -------------------------------------------------------------- atlas

    private void ensureTexture(Minecraft mc) {
        if (atlas != null) return;
        BufferedImage img = new BufferedImage(CELL * 4, CELL * 4, BufferedImage.TYPE_INT_ARGB);
        cellGlow(img, 0, 0);
        cellRing(img, CELL, 0);
        cellStar(img, CELL * 2, 0);
        cellPetal(img, CELL * 3, 0);
        cellCrystal(img, 0, CELL);
        cellSmoke(img, CELL, CELL);
        cellCircle(img, CELL * 2, CELL);
        cellHalo(img, CELL * 3, CELL);
        cellLink(img, 0, CELL * 2);
        cellBill(img, CELL, CELL * 2);
        cellCoin(img, CELL * 2, CELL * 2);
        cellShard(img, CELL * 3, CELL * 2);
        cellRune(img, 0, CELL * 3);
        cellFlame(img, CELL, CELL * 3);
        cellHex(img, CELL * 2, CELL * 3);
        cellBeam(img, CELL * 3, CELL * 3);
        DynamicTexture tex = new DynamicTexture(img);
        atlas = mc.getTextureManager().getDynamicTextureLocation("m1227_atlas", tex);
        GlStateManager.bindTexture(tex.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
    }

    private static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static void px(BufferedImage img, int x, int y, float a) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        img.setRGB(x, y, (al << 24) | 0xFFFFFF);
    }

    private static void cellGlow(BufferedImage img, int ox, int oy) {
        float h = CELL / 2f;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float dx = (x + 0.5f - h) / h, dy = (y + 0.5f - h) / h;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a = d >= 1f ? 0f : (float) Math.pow(1f - d, 2.2);
                if (d < 0.15f) a = Math.min(1f, a + (0.15f - d) * 3f);
                px(img, ox + x, oy + y, a);
            }
        }
    }

    private static void ringAt(Graphics2D g, float cx, float cy, float r, float w, int alpha) {
        g.setColor(new Color(255, 255, 255, alpha));
        g.setStroke(new BasicStroke(w));
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2f, r * 2f));
    }

    private static void cellRing(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = ox + CELL / 2f, d = oy + CELL / 2f;
        ringAt(g, c, d, 52f, 10f, 40);
        ringAt(g, c, d, 52f, 5f, 120);
        ringAt(g, c, d, 52f, 2.2f, 255);
        g.dispose();
    }

    private static void cellStar(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = CELL / 2f;
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0 - Math.PI / 2.0;
            float rad = (i % 2 == 0) ? 58f : 7f;
            float x = ox + c + (float) Math.cos(ang) * rad, y = oy + c + (float) Math.sin(ang) * rad;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.closePath();
        g.setColor(new Color(255, 255, 255, 110));
        g.fill(new Ellipse2D.Float(ox + c - 14, oy + c - 14, 28, 28));
        g.setColor(new Color(255, 255, 255, 245));
        g.fill(p);
        g.dispose();
    }

    private static void cellPetal(BufferedImage img, int ox, int oy) {
        float h = CELL / 2f;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float nx = (x + 0.5f - h) / 20f, ny = (y + 0.5f - h) / 54f;
                float d = (float) Math.sqrt(nx * nx + ny * ny);
                float a = d >= 1f ? 0f : (float) Math.pow(1f - d, 0.55) * 0.95f;
                if (d > 0.82f && d < 1f) a = Math.min(1f, a + 0.25f);
                px(img, ox + x, oy + y, a);
            }
        }
    }

    private static void cellCrystal(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float[][] left = {{64, 4}, {38, 40}, {46, 102}, {64, 124}};
        float[][] right = {{64, 4}, {90, 40}, {82, 102}, {64, 124}};
        float[][] top = {{64, 4}, {38, 40}, {64, 58}, {90, 40}};
        fillPoly(g, ox, oy, left, 120);
        fillPoly(g, ox, oy, right, 215);
        fillPoly(g, ox, oy, top, 255);
        g.setColor(new Color(255, 255, 255, 255));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        outline(g, ox, oy, new float[][]{{64, 4}, {90, 40}, {82, 102}, {64, 124}, {46, 102}, {38, 40}});
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Line2D.Float(ox + 38, oy + 40, ox + 64, oy + 58));
        g.draw(new Line2D.Float(ox + 90, oy + 40, ox + 64, oy + 58));
        g.draw(new Line2D.Float(ox + 64, oy + 58, ox + 64, oy + 124));
        g.dispose();
    }

    private static void fillPoly(Graphics2D g, int ox, int oy, float[][] pts, int alpha) {
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < pts.length; i++) {
            if (i == 0) p.moveTo(ox + pts[i][0], oy + pts[i][1]);
            else p.lineTo(ox + pts[i][0], oy + pts[i][1]);
        }
        p.closePath();
        g.setColor(new Color(255, 255, 255, alpha));
        g.fill(p);
    }

    private static void outline(Graphics2D g, int ox, int oy, float[][] pts) {
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < pts.length; i++) {
            if (i == 0) p.moveTo(ox + pts[i][0], oy + pts[i][1]);
            else p.lineTo(ox + pts[i][0], oy + pts[i][1]);
        }
        p.closePath();
        g.draw(p);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private static float vnoise(float x, float y) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float xf = x - xi, yf = y - yi;
        float u = xf * xf * (3f - 2f * xf), v = yf * yf * (3f - 2f * yf);
        float a = hash(xi, yi), b = hash(xi + 1, yi), c = hash(xi, yi + 1), d = hash(xi + 1, yi + 1);
        float top = a + (b - a) * u, bot = c + (d - c) * u;
        return top + (bot - top) * v;
    }

    private static void cellSmoke(BufferedImage img, int ox, int oy) {
        float h = CELL / 2f;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float dx = (x + 0.5f - h) / h, dy = (y + 0.5f - h) / h;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= 1f) {
                    px(img, ox + x, oy + y, 0f);
                    continue;
                }
                float n = 0.6f * vnoise(x * 0.09f, y * 0.09f) + 0.4f * vnoise(x * 0.2f + 17f, y * 0.2f + 5f);
                float edge = 1f - d;
                edge = edge * edge * (3f - 2f * edge);
                px(img, ox + x, oy + y, edge * (0.3f + 0.95f * n));
            }
        }
    }

    private static void cellCircle(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = ox + CELL / 2f, d = oy + CELL / 2f;
        ringAt(g, c, d, 60f, 2.4f, 255);
        ringAt(g, c, d, 55f, 1.2f, 200);
        ringAt(g, c, d, 34f, 2f, 255);
        g.setColor(new Color(255, 255, 255, 230));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int t = 0; t < 2; t++) {
            Path2D.Float p = new Path2D.Float();
            for (int i = 0; i < 3; i++) {
                double ang = Math.toRadians(-90 + t * 60 + i * 120);
                float x = c + (float) Math.cos(ang) * 34f, y = d + (float) Math.sin(ang) * 34f;
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.closePath();
            g.draw(p);
        }
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(-90 + i * 60);
            ringAt(g, c + (float) Math.cos(ang) * 34f, d + (float) Math.sin(ang) * 34f, 4f, 1.4f, 255);
        }
        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(255, 255, 255, 220));
        for (int k = 0; k < 24; k++) {
            double ang = Math.toRadians(k * 15);
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            g.draw(new Line2D.Float(c + ca * 55f, d + sa * 55f, c + ca * 60f, d + sa * 60f));
        }
        for (int k = 0; k < 12; k++) {
            double ang = Math.toRadians(k * 30 + 15);
            g.fill(new Ellipse2D.Float(c + (float) Math.cos(ang) * 46f - 1.5f, d + (float) Math.sin(ang) * 46f - 1.5f, 3f, 3f));
        }
        g.dispose();
    }

    private static void cellHalo(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = ox + CELL / 2f, d = oy + CELL / 2f;
        ringAt(g, c, d, 50f, 9f, 40);
        ringAt(g, c, d, 50f, 3f, 255);
        ringAt(g, c, d, 42f, 1.2f, 160);
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(255, 255, 255, 210));
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45);
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            g.draw(new Line2D.Float(c + ca * 50f, d + sa * 50f, c + ca * 58f, d + sa * 58f));
        }
        g.dispose();
    }

    private static void cellLink(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        g.setColor(new Color(255, 255, 255, 120));
        g.setStroke(new BasicStroke(15f));
        g.draw(new RoundRectangle2D.Float(ox + 36, oy + 14, 56, 100, 40, 40));
        g.setColor(new Color(255, 255, 255, 235));
        g.setStroke(new BasicStroke(8f));
        g.draw(new RoundRectangle2D.Float(ox + 36, oy + 14, 56, 100, 40, 40));
        g.setColor(new Color(255, 255, 255, 255));
        g.setStroke(new BasicStroke(2.5f));
        g.draw(new RoundRectangle2D.Float(ox + 36, oy + 14, 56, 100, 40, 40));
        g.dispose();
    }

    private static void cellBill(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(new RoundRectangle2D.Float(ox + 8, oy + 32, 112, 64, 8, 8));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.6f));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new RoundRectangle2D.Float(ox + 15, oy + 39, 98, 50, 5, 5));
        g.draw(new Ellipse2D.Float(ox + 48, oy + 50, 32, 28));
        g.draw(new Line2D.Float(ox + 20, oy + 64, ox + 40, oy + 64));
        g.draw(new Line2D.Float(ox + 88, oy + 64, ox + 108, oy + 64));
        g.dispose();
    }

    private static void cellCoin(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        g.setColor(new Color(255, 255, 255, 240));
        g.fill(new Ellipse2D.Float(ox + 22, oy + 22, 84, 84));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.6f));
        g.setStroke(new BasicStroke(3f));
        g.draw(new Ellipse2D.Float(ox + 33, oy + 33, 62, 62));
        g.draw(new Line2D.Float(ox + 64, oy + 46, ox + 64, oy + 82));
        g.dispose();
    }

    private static void cellShard(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        fillPoly(g, ox, oy, new float[][]{{64, 6}, {112, 100}, {22, 118}}, 150);
        fillPoly(g, ox, oy, new float[][]{{64, 6}, {112, 100}, {62, 72}}, 235);
        g.setColor(new Color(255, 255, 255, 255));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        outline(g, ox, oy, new float[][]{{64, 6}, {112, 100}, {22, 118}});
        g.dispose();
    }

    private static void cellRune(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = ox + CELL / 2f, d = oy + CELL / 2f;
        ringAt(g, c, d, 58f, 2f, 255);
        ringAt(g, c, d, 44f, 1.4f, 220);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 240));
        Random r = new Random(1227L);
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45 + 22.5);
            float gx = c + (float) Math.cos(ang) * 51f, gy = d + (float) Math.sin(ang) * 51f;
            Path2D.Float p = new Path2D.Float();
            p.moveTo(gx + (r.nextFloat() - 0.5f) * 8f, gy - 4f);
            for (int s = 0; s < 3; s++) p.lineTo(gx + (r.nextFloat() - 0.5f) * 9f, gy + (r.nextFloat() - 0.5f) * 9f);
            g.draw(p);
        }
        Path2D.Float tri = new Path2D.Float();
        for (int i = 0; i < 3; i++) {
            double ang = Math.toRadians(-90 + i * 120);
            float x = c + (float) Math.cos(ang) * 38f, y = d + (float) Math.sin(ang) * 38f;
            if (i == 0) tri.moveTo(x, y);
            else tri.lineTo(x, y);
        }
        tri.closePath();
        g.draw(tri);
        ringAt(g, c, d, 12f, 1.6f, 255);
        g.dispose();
    }

    private static void cellFlame(BufferedImage img, int ox, int oy) {
        float h = CELL / 2f;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float v = (y + 0.5f) / CELL;               // 0 top .. 1 bottom
                float halfW = 0.42f * (float) Math.pow(Math.sin(Math.PI * Math.min(1f, v * 1.05f)), 0.8) * (0.35f + 0.65f * v);
                float dx = Math.abs((x + 0.5f - h) / h);
                float a = halfW <= 0.001f ? 0f : 1f - dx / (halfW * 2f);
                a = Math.max(0f, a);
                a = (float) Math.pow(a, 1.6) * (0.4f + 0.6f * v);
                px(img, ox + x, oy + y, a);
            }
        }
    }

    private static void cellHex(BufferedImage img, int ox, int oy) {
        Graphics2D g = g2(img);
        float c = ox + CELL / 2f, d = oy + CELL / 2f;
        for (int pass = 0; pass < 3; pass++) {
            float w = pass == 0 ? 9f : (pass == 1 ? 4.5f : 2f);
            int al = pass == 0 ? 45 : (pass == 1 ? 120 : 255);
            Path2D.Float p = new Path2D.Float();
            for (int i = 0; i < 6; i++) {
                double ang = Math.toRadians(i * 60 + 30);
                float x = c + (float) Math.cos(ang) * 54f, y = d + (float) Math.sin(ang) * 54f;
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.closePath();
            g.setColor(new Color(255, 255, 255, al));
            g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(p);
        }
        g.dispose();
    }

    private static void cellBeam(BufferedImage img, int ox, int oy) {
        float h = CELL / 2f;
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                float dx = Math.abs((x + 0.5f - h) / h);
                float v = (y + 0.5f) / CELL;
                float a = (float) Math.pow(Math.max(0f, 1f - dx), 3.0) * (float) Math.sin(Math.PI * v);
                px(img, ox + x, oy + y, a);
            }
        }
    }

    // ---------------------------------------------------------- rendering

    /** Binds the procedural sprite atlas (creating it if needed). Used by the sky renderer. */
    public void bindAtlas() {
        Minecraft mc = Minecraft.getMinecraft();
        ensureTexture(mc);
        mc.getTextureManager().bindTexture(atlas);
    }

    /** Atlas texture coordinates {u0, v0, u1, v1} of a sprite cell. */
    public static void uv(int sprite, float[] out) {
        int col = sprite & 3, row = sprite >> 2;
        out[0] = col * 0.25f + 0.002f;
        out[1] = row * 0.25f + 0.002f;
        out[2] = out[0] + 0.246f;
        out[3] = out[1] + 0.246f;
    }

    private static int c255(float v) {
        int i = (int) (v * 255f);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    private void render(Minecraft mc) {
        ensureTexture(mc);
        RenderManager rm = mc.getRenderManager();
        double ry = Math.toRadians(rm.playerViewY), rp = Math.toRadians(rm.playerViewX);
        float sy = (float) Math.sin(ry), cy = (float) Math.cos(ry);
        float sp = (float) Math.sin(rp), cp = (float) Math.cos(rp);
        // Camera right / up vectors in world space (billboard basis).
        bRx = cy;
        bRz = sy;
        bUx = -sp * sy;
        bUy = cp;
        bUz = sp * cy;
        double maxD = PerformanceModule.INSTANCE.distance.get();
        maxD2 = maxD * maxD;

        mc.getTextureManager().bindTexture(atlas);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        GlStateManager.color(1f, 1f, 1f, 1f);

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Pull sprites slightly towards the camera so ones near the ground never z-fight or sink into it.
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.5f, -3f);
        drawBatch(true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawBatch(false);
        GlStateManager.disablePolygonOffset();

        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawBatch(boolean alphaPass) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int i = 0; i < count; i++) emit(wr, pool[i], alphaPass);
        for (int i = 0; i < frameCount; i++) emit(wr, frameParticles[i], alphaPass);
        tess.draw();
    }

    private void emit(WorldRenderer wr, Particle p, boolean alphaPass) {
        if (p.alphaBlend != alphaPass) return;
        double ddx = p.x - rX, ddy = p.y - rY, ddz = p.z - rZ;
        if (ddx * ddx + ddy * ddy + ddz * ddz > maxD2) return;

        float t = p.age / p.life;
        float alpha;
        if (p.steady) {
            alpha = p.alpha;
        } else {
            float f = 1f - t;
            float fo = p.fadePow == 1f ? f : (float) Math.pow(f, p.fadePow);
            alpha = p.alpha * Math.min(1f, t * 6f) * fo;
        }
        if (p.pulse > 0f) {
            alpha *= 1f - p.pulse + p.pulse * (0.5f + 0.5f * (float) Math.sin(p.age * p.pulseFreq + p.pulsePhase));
        }
        if (alpha <= 0.004f) return;

        float size = p.size0 + (p.size1 - p.size0) * t;
        int r = c255(p.r0 + (p.r1 - p.r0) * t);
        int g = c255(p.g0 + (p.g1 - p.g0) * t);
        int b = c255(p.b0 + (p.b1 - p.b0) * t);
        int a = c255(alpha);
        float px = (float) ddx, py = (float) ddy, pz = (float) ddz;

        int col = p.sprite & 3, row = p.sprite >> 2;
        float u0 = col * 0.25f + 0.002f, u1 = u0 + 0.246f;
        float v0 = row * 0.25f + 0.002f, v1 = v0 + 0.246f;

        float ax, ay, az, bx, by, bz;
        boolean done = false;
        ax = ay = az = bx = by = bz = 0f;

        if (p.flat) {
            float c = (float) Math.cos(p.rot), s = (float) Math.sin(p.rot);
            ax = c * size;
            az = s * size;
            bx = -s * size;
            bz = c * size;
            done = true;
        } else if (p.stretch > 0f) {
            float sx = p.vx * bRx + p.vz * bRz;
            float sY = p.vx * bUx + p.vy * bUy + p.vz * bUz;
            float len = (float) Math.sqrt(sx * sx + sY * sY);
            if (len > 0.05f) {
                float dxn = sx / len, dyn = sY / len;
                float half = size + len * p.stretch, wid = size * 0.5f;
                ax = (bRx * dxn + bUx * dyn) * half;
                ay = (bUy * dyn) * half;
                az = (bRz * dxn + bUz * dyn) * half;
                bx = (-bRx * dyn + bUx * dxn) * wid;
                by = (bUy * dxn) * wid;
                bz = (-bRz * dyn + bUz * dxn) * wid;
                done = true;
            }
        }
        if (!done) {
            if (p.rot != 0f) {
                float c = (float) Math.cos(p.rot), s = (float) Math.sin(p.rot);
                ax = (bRx * c + bUx * s) * size;
                ay = (bUy * s) * size;
                az = (bRz * c + bUz * s) * size;
                bx = (-bRx * s + bUx * c) * size;
                by = (bUy * c) * size;
                bz = (-bRz * s + bUz * c) * size;
            } else {
                ax = bRx * size;
                az = bRz * size;
                bx = bUx * size;
                by = bUy * size;
                bz = bUz * size;
            }
        }
        wr.pos(px - ax - bx, py - ay - by, pz - az - bz).tex(u0, v1).color(r, g, b, a).endVertex();
        wr.pos(px + ax - bx, py + ay - by, pz + az - bz).tex(u1, v1).color(r, g, b, a).endVertex();
        wr.pos(px + ax + bx, py + ay + by, pz + az + bz).tex(u1, v0).color(r, g, b, a).endVertex();
        wr.pos(px - ax + bx, py - ay + by, pz - az + bz).tex(u0, v0).color(r, g, b, a).endVertex();
    }
}
