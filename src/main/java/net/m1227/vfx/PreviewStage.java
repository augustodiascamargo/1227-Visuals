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
    private static final float CAM_Y = 1.3f, CAM_Z = -3.25f, PITCH = 8f, YAW = 0f;
    private static final FloatBuffer PROJ = BufferUtils.createFloatBuffer(16), VIEW = BufferUtils.createFloatBuffer(16);
    /** Spot effects are played at while a stage triggers them: feet at the origin, facing the camera. */
    static double[] spot;
    /** True while a menu preview draws its stand-in (world-only features skip it). */
    public static boolean rendering;

    private final ParticleSystem ps = ParticleSystem.isolated(1500);
    private final Lines lines = Lines.isolated();
    private final Arcs arcs = Arcs.isolated();
    private final ShockwaveSystem waves = ShockwaveSystem.isolated();
    private final BodyFx bodies = BodyFx.isolated();
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
        bodies.clear();
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
        BodyFx gBodies = BodyFx.INSTANCE;
        BodyFx.INSTANCE = bodies;
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
                if (!continuous) bodies.standInHidden = false;
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
            Project.gluPerspective(46f, pxW / (float) pxH, 0.05f, 100f);
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
            if (mannequin && !bodies.standInHidden && !player(mc)) mannequin();
            ps.renderStage(dt, 0, CAM_Y, CAM_Z, YAW, PITCH);
        } finally {
            ParticleSystem.INSTANCE = gPs;
            Lines.INSTANCE = gLines;
            Arcs.INSTANCE = gArcs;
            ShockwaveSystem.INSTANCE = gWaves;
            BodyFx.INSTANCE = gBodies;
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

    private static net.minecraft.client.entity.EntityOtherPlayerMP model;

    /**
     * Draws your own character (with your skin) as the stand-in, like the inventory screen does.
     * Returns false when it cannot (no world yet), so the plain mannequin is used instead.
     */
    private static boolean player(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) return false;
        try {
            if (model == null || model.worldObj != mc.theWorld) {
                model = new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
            }
            net.minecraft.client.entity.EntityOtherPlayerMP e = model;
            e.setPosition(0, 0, 0);
            e.prevPosX = e.lastTickPosX = e.posX;
            e.prevPosY = e.lastTickPosY = e.posY;
            e.prevPosZ = e.lastTickPosZ = e.posZ;
            e.rotationYaw = e.prevRotationYaw = 180f;
            e.renderYawOffset = e.prevRenderYawOffset = 180f;
            e.rotationYawHead = e.prevRotationYawHead = 180f;
            e.rotationPitch = e.prevRotationPitch = 0f;
            net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
            net.minecraft.entity.Entity viewer = rm.livingPlayer;
            rm.livingPlayer = e; // no name tag above the stand-in
            rm.setRenderShadow(false);
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableRescaleNormal();
            net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.color(1f, 1f, 1f, 1f);
            rendering = true;
            try {
                rm.renderEntityWithPosYaw(e, 0, 0, 0, 180f, 1f);
            } finally {
                rendering = false;
            }
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            rm.setRenderShadow(true);
            rm.livingPlayer = viewer;
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableBlend();
            return true;
        } catch (Throwable t) {
            return false;
        }
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
