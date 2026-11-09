package net.m1227.vfx;

import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * A tiny 3D "video" of an effect, drawn inside the menu. It owns private
 * particle / line / arc / shockwave systems and an off-screen framebuffer,
 * swaps them in only while it simulates and draws, and replays the effect
 * on a loop on a mannequin standing on a lit floor disc. Uses the real
 * effect code, so what you see is exactly what plays in game.
 */
public final class PreviewStage {
    private static final float CAM_Y = 1.45f, CAM_Z = -3.9f, PITCH = 11f, YAW = 0f;
    private static final FloatBuffer PROJ = BufferUtils.createFloatBuffer(16), VIEW = BufferUtils.createFloatBuffer(16);
    /** Spot effects are played at while a stage triggers them: feet at the origin, facing the camera. */
    static double[] spot;

    private final ParticleSystem ps = ParticleSystem.isolated(1500);
    private final Lines lines = Lines.isolated();
    private final Arcs arcs = Arcs.isolated();
    private final ShockwaveSystem waves = ShockwaveSystem.isolated();
    private Framebuffer fb;
    private boolean scissorWas;
    private float timer;
    private long last;
    private final float period;
    private final boolean mannequin;
    /** Continuous effects (auras) are re-drawn every frame instead of replayed on a loop. */
    private final boolean continuous;

    public PreviewStage(float loopSeconds, boolean mannequin, boolean continuous) {
        this.period = loopSeconds;
        this.mannequin = mannequin;
        this.continuous = continuous;
        this.timer = 0.15f;
    }

    public void reset() {
        ps.clearAll();
        lines.clear();
        arcs.clear();
        waves.clear();
        timer = 0.15f;
    }

    /**
     * Simulates and draws into the framebuffer, then blits it at (x, y, w, h) in GUI space.
     * trigger plays the effect once (it is run with the stage's systems installed).
     */
    public void draw(float x, float y, float w, float h, int pxW, int pxH, Runnable trigger, int accent) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!OpenGlHelper.isFramebufferEnabled() || pxW < 8 || pxH < 8) return;
        long now = System.nanoTime();
        float dt = last == 0L ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (fb == null || fb.framebufferWidth != pxW || fb.framebufferHeight != pxH) {
            if (fb != null) fb.deleteFramebuffer();
            fb = new Framebuffer(pxW, pxH, true);
            fb.setFramebufferFilter(GL11.GL_LINEAR);
        }

        // install the private systems
        ParticleSystem gPs = ParticleSystem.INSTANCE;
        Lines gLines = Lines.INSTANCE;
        Arcs gArcs = Arcs.INSTANCE;
        ShockwaveSystem gWaves = ShockwaveSystem.INSTANCE;
        ParticleSystem.INSTANCE = ps;
        Lines.INSTANCE = lines;
        Arcs.INSTANCE = arcs;
        ShockwaveSystem.INSTANCE = waves;
        ps.camX = ps.viewX = 0;
        ps.camY = ps.viewY = CAM_Y;
        ps.camZ = ps.viewZ = CAM_Z;
        ps.rX = ps.rY = ps.rZ = 0;
        PROJ.clear();
        VIEW.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJ);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, VIEW);
        try {
            timer -= dt;
            if (continuous || timer <= 0f) {
                timer = period;
                spot = new double[]{0, 0, 0, 0, 1}; // forward = the camera's view direction (+z), like a player facing the target
                try {
                    trigger.run();
                } catch (Throwable ignored) {
                    // a broken preview never breaks the menu
                } finally {
                    spot = null;
                }
            }
            scissorWas = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); // the menu's scissor box is in screen pixels, not ours
            fb.bindFramebuffer(true);
            GlStateManager.clearColor(0f, 0f, 0f, 0f);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            Project.gluPerspective(48f, pxW / (float) pxH, 0.05f, 100f);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.rotate(PITCH, 1f, 0f, 0f);
            GlStateManager.rotate(YAW + 180f, 0f, 1f, 0f);
            GlStateManager.translate(0f, -CAM_Y, -CAM_Z);
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableFog();
            GlStateManager.disableCull();
            floor(accent);
            if (mannequin) mannequin();
            ps.renderStage(dt, 0, CAM_Y, CAM_Z, YAW, PITCH);
        } finally {
            ParticleSystem.INSTANCE = gPs;
            Lines.INSTANCE = gLines;
            Arcs.INSTANCE = gArcs;
            ShockwaveSystem.INSTANCE = gWaves;
            mc.getFramebuffer().bindFramebuffer(true);
            if (scissorWas) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            PROJ.rewind();
            GL11.glLoadMatrix(PROJ);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            VIEW.rewind();
            GL11.glLoadMatrix(VIEW);
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
        }
        // blit
        GlStateManager.enableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.bindTexture(fb.framebufferTexture);
        float u = fb.framebufferWidth / (float) fb.framebufferTextureWidth, v = fb.framebufferHeight / (float) fb.framebufferTextureHeight;
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, v);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u, 0);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GlStateManager.enableDepth();
    }

    public void dispose() {
        if (fb != null) fb.deleteFramebuffer();
        fb = null;
    }

    // ------------------------------------------------------------- props

    /** Soft radial floor disc with a faint grid ring, tinted by the accent. */
    private static void floor(int accent) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.depthMask(false);
        float r = ((accent >> 16) & 255) / 255f, g = ((accent >> 8) & 255) / 255f, b = (accent & 255) / 255f;
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(0.16f + r * 0.1f, 0.17f + g * 0.1f, 0.22f + b * 0.1f, 0.85f);
        GL11.glVertex3f(0f, 0f, 0f);
        GL11.glColor4f(0.05f, 0.05f, 0.08f, 0f);
        for (int i = 0; i <= 48; i++) {
            double a = i * Math.PI * 2 / 48;
            GL11.glVertex3f((float) Math.cos(a) * 3.2f, 0f, (float) Math.sin(a) * 3.2f);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.2f);
        for (int k = 1; k <= 3; k++) {
            GL11.glBegin(GL11.GL_LINE_LOOP);
            GL11.glColor4f(r, g, b, 0.22f / k);
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI * 2 / 64;
                GL11.glVertex3f((float) Math.cos(a) * 0.9f * k, 0.002f, (float) Math.sin(a) * 0.9f * k);
            }
            GL11.glEnd();
        }
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
    }

    /** Blocky stand-in figure (same proportions as a Minecraft player), softly shaded. */
    private static void mannequin() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableBlend();
        box(-0.25f, 1.5f, -0.25f, 0.25f, 2.0f, 0.25f, 0.62f);   // head
        box(-0.25f, 0.75f, -0.125f, 0.25f, 1.5f, 0.125f, 0.5f); // body
        box(-0.5f, 0.75f, -0.125f, -0.25f, 1.5f, 0.125f, 0.45f); // arms
        box(0.25f, 0.75f, -0.125f, 0.5f, 1.5f, 0.125f, 0.45f);
        box(-0.25f, 0f, -0.125f, 0f, 0.75f, 0.125f, 0.4f);       // legs
        box(0f, 0f, -0.125f, 0.25f, 0.75f, 0.125f, 0.4f);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void box(float x0, float y0, float z0, float x1, float y1, float z1, float shade) {
        float s = shade;
        GL11.glBegin(GL11.GL_QUADS);
        // front (towards camera, -z) brightest, sides darker, top lightest
        GL11.glColor4f(0.24f * s + 0.08f, 0.25f * s + 0.08f, 0.32f * s + 0.1f, 1f);
        GL11.glVertex3f(x0, y0, z0);
        GL11.glVertex3f(x1, y0, z0);
        GL11.glVertex3f(x1, y1, z0);
        GL11.glVertex3f(x0, y1, z0);
        GL11.glColor4f(0.16f * s + 0.05f, 0.17f * s + 0.05f, 0.22f * s + 0.07f, 1f);
        GL11.glVertex3f(x0, y0, z1);
        GL11.glVertex3f(x0, y1, z1);
        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x0, y0, z0);
        GL11.glVertex3f(x0, y1, z0);
        GL11.glVertex3f(x0, y1, z1);
        GL11.glVertex3f(x0, y0, z1);
        GL11.glVertex3f(x1, y0, z0);
        GL11.glVertex3f(x1, y0, z1);
        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x1, y1, z0);
        GL11.glColor4f(0.32f * s + 0.1f, 0.33f * s + 0.1f, 0.42f * s + 0.12f, 1f);
        GL11.glVertex3f(x0, y1, z0);
        GL11.glVertex3f(x1, y1, z0);
        GL11.glVertex3f(x1, y1, z1);
        GL11.glVertex3f(x0, y1, z1);
        GL11.glEnd();
    }
}
