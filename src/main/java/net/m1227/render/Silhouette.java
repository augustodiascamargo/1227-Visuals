package net.m1227.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * Draws an entity (or item) as a flat-coloured silhouette that keeps the texture's cut-outs:
 * texture unit 0 is switched to GL_COMBINE with RGB replaced by a constant colour and alpha
 * taken from the texture. Used for outlines, afterimages and hit colour. Depth testing stays on,
 * so nothing is ever drawn through walls.
 */
public final class Silhouette {
    public static final Silhouette INSTANCE = new Silhouette();
    /** True while we re-render an entity: name tags are skipped. */
    private static boolean active;

    private Silhouette() {}

    public static boolean active() {
        return active;
    }

    /** Switches texturing to solid colour (rgb 0xRRGGBB, alpha 0..1). Call end() afterwards. */
    public static void begin(int rgb, float alpha) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        java.nio.FloatBuffer c = org.lwjgl.BufferUtils.createFloatBuffer(16);
        c.put(((rgb >> 16) & 255) / 255f).put(((rgb >> 8) & 255) / 255f).put((rgb & 255) / 255f).put(alpha).flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, c);
    }

    /**
     * Tinted, see-through texturing: RGB = texture x colour, alpha = texture alpha x alpha. Call
     * end() afterwards. Used by the body materials (glass, ghost, hologram...).
     */
    public static void beginTint(int rgb, float alpha) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_RGB, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_RGB, GL11.GL_SRC_COLOR);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_CONSTANT);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        java.nio.FloatBuffer c = org.lwjgl.BufferUtils.createFloatBuffer(16);
        c.put(((rgb >> 16) & 255) / 255f).put(((rgb >> 8) & 255) / 255f).put((rgb & 255) / 255f).put(alpha).flip();
        GL11.glTexEnv(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_COLOR, c);
    }

    public static void end() {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
    }

    /**
     * Re-renders a living entity at its interpolated position (camera relative), optionally
     * shifted by (ox, oy, oz). The caller sets up colour/blend state.
     */
    /** World-pass state entity renderers expect (RenderWorldLastEvent leaves some of it off). */
    public static void entityState(boolean on) {
        Minecraft mc = Minecraft.getMinecraft();
        if (on) {
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
            mc.entityRenderer.enableLightmap();
            net.minecraft.client.renderer.RenderHelper.enableStandardItemLighting();
        } else {
            net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
            mc.entityRenderer.disableLightmap();
            GlStateManager.color(1f, 1f, 1f, 1f);
        }
    }

    public static void render(Entity e, float pt, double ox, double oy, double oz) {
        Minecraft mc = Minecraft.getMinecraft();
        double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt - mc.getRenderManager().viewerPosX + ox;
        double y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt - mc.getRenderManager().viewerPosY + oy;
        double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ + oz;
        float yaw = e.prevRotationYaw + (e.rotationYaw - e.prevRotationYaw) * pt;
        renderAt(e, x, y, z, yaw, pt);
    }

    /** Renders an entity at an explicit camera-relative position and yaw. */
    public static void renderAt(Entity e, double x, double y, double z, float yaw, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        active = true;
        // while hurt, Minecraft swaps the texture setup for its red flash, which would replace
        // our flat colour with the real skin: draw the copy as if not hurt
        net.minecraft.entity.EntityLivingBase l = e instanceof net.minecraft.entity.EntityLivingBase ? (net.minecraft.entity.EntityLivingBase) e : null;
        int hurt = l != null ? l.hurtTime : 0;
        if (l != null) l.hurtTime = 0;
        try {
            mc.getRenderManager().renderEntityWithPosYaw(e, x, y, z, yaw, pt);
        } catch (RuntimeException ignored) {
            // keep the frame alive (another mod's renderer failing must not take the frame down)
        } finally {
            if (l != null && l.hurtTime == 0) l.hurtTime = hurt;
            active = false;
        }
    }

    /**
     * Screen-space outline: silhouettes shifted around the entity in the camera plane, then the
     * entity drawn normally on top. Width grows with distance so it stays a constant thickness.
     */
    public static void outline(Entity e, float pt, int rgb, float alpha, float px) {
        Minecraft mc = Minecraft.getMinecraft();
        double dx = e.posX - mc.getRenderManager().viewerPosX, dy = e.posY - mc.getRenderManager().viewerPosY,
                dz = e.posZ - mc.getRenderManager().viewerPosZ;
        float w = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.0018f * px + 0.012f * px;
        float rx = ActiveRenderInfo.getRotationX(), rz = ActiveRenderInfo.getRotationYZ();
        float ux = ActiveRenderInfo.getRotationXY(), uy = ActiveRenderInfo.getRotationZ(), uz = ActiveRenderInfo.getRotationXZ();
        entityState(true);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        begin(rgb, alpha);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8;
            float c = (float) Math.cos(a) * w, s = (float) Math.sin(a) * w;
            render(e, pt, rx * c + ux * s, uy * s, rz * c + uz * s);
        }
        end();
        GlStateManager.depthMask(true);
        // the real entity again on top, so only the ring around it stays coloured
        render(e, pt, 0, 0, 0);
        entityState(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onName(RenderLivingEvent.Specials.Pre<EntityLivingBase> e) {
        if (active) e.setCanceled(true);
    }
}
