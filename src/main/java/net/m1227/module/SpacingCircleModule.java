package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Spacing Circle: a ring on the ground around you with the radius you want to keep in a fight
 * (combo distance), plus the distance from your eyes to the opponent's hitbox. The ring changes
 * colour when the opponent is inside, near or outside that distance.
 *
 * Informational only: it never moves you, aims, attacks or changes reach. The opponent is your
 * current target or the nearest player you can actually see (never through walls).
 */
public final class SpacingCircleModule extends Module {
    private final Setting.Num radius, thickness, opacity;
    private final Setting.Mode style;
    private final Setting.Col inColor, nearColor, outColor;
    private final Setting.Bool label, targetMarker;
    private EntityLivingBase opponent;
    private double distance = -1;

    public SpacingCircleModule() {
        super("Spacing Circle", "Ring showing your combo distance and how far the opponent is.", Category.COMBAT, false);
        radius = add(new Setting.Num("Radius", 3.0, 1.0, 6.0, 0.05, "m"));
        style = add(new Setting.Mode("Style", "Glow", "Solid", "Dashed", "Pulse", "Glow"));
        thickness = add(new Setting.Num("Thickness", 0.06, 0.02, 0.25, 0.01, "m"));
        opacity = add(new Setting.Num("Opacity", 0.8, 0.1, 1.0, 0.05, ""));
        inColor = add(new Setting.Col("In range", 0x3DDC97));
        nearColor = add(new Setting.Col("Close to range", 0xFFC24A));
        outColor = add(new Setting.Col("Out of range", 0xFFFFFF));
        label = add(new Setting.Bool("Distance under crosshair", true));
        targetMarker = add(new Setting.Bool("Marker under opponent", true));
    }

    /** Distance from the eyes to the closest point of the entity's hitbox (vanilla's 0.1 border included). */
    static double eyeToBox(EntityPlayer me, EntityLivingBase e, float pt) {
        double ex = me.lastTickPosX + (me.posX - me.lastTickPosX) * pt;
        double ey = me.lastTickPosY + (me.posY - me.lastTickPosY) * pt + me.getEyeHeight();
        double ez = me.lastTickPosZ + (me.posZ - me.lastTickPosZ) * pt;
        double ox = (e.posX - e.lastTickPosX) * (pt - 1), oy = (e.posY - e.lastTickPosY) * (pt - 1), oz = (e.posZ - e.lastTickPosZ) * (pt - 1);
        float b = e.getCollisionBorderSize();
        AxisAlignedBB bb = e.getEntityBoundingBox().expand(b, b, b).offset(ox, oy, oz);
        double cx = Math.max(bb.minX, Math.min(ex, bb.maxX));
        double cy = Math.max(bb.minY, Math.min(ey, bb.maxY));
        double cz = Math.max(bb.minZ, Math.min(ez, bb.maxZ));
        return Math.sqrt((cx - ex) * (cx - ex) + (cy - ey) * (cy - ey) + (cz - ez) * (cz - ez));
    }

    private EntityLivingBase findOpponent(Minecraft mc) {
        EntityLivingBase t = CombatTracker.target(4000L);
        if (t != null && t.getDistanceSqToEntity(mc.thePlayer) < 12 * 12 && mc.thePlayer.canEntityBeSeen(t)) return t;
        EntityPlayer best = null;
        double bd = 10 * 10;
        List<EntityPlayer> list = mc.theWorld.playerEntities;
        for (int i = 0; i < list.size(); i++) {
            EntityPlayer p = list.get(i);
            if (p == mc.thePlayer || p.isInvisible() || p.isDead || p.isSpectator()) continue;
            double d = p.getDistanceSqToEntity(mc.thePlayer);
            if (d < bd && mc.thePlayer.canEntityBeSeen(p)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private int stateColor() {
        if (distance < 0) return outColor.rgb();
        double r = radius.get();
        if (distance <= r) return inColor.rgb();
        if (distance <= r + 0.6) return nearColor.rgb();
        return outColor.rgb();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        float pt = e.partialTicks;
        opponent = findOpponent(mc);
        distance = opponent == null ? -1 : eyeToBox(mc.thePlayer, opponent, pt);
        EntityPlayer me = mc.thePlayer;
        double x = me.lastTickPosX + (me.posX - me.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
        double y = me.lastTickPosY + (me.posY - me.lastTickPosY) * pt - mc.getRenderManager().viewerPosY + 0.02;
        double z = me.lastTickPosZ + (me.posZ - me.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
        int c = stateColor();
        float a = opacity.f() * (opponent == null ? 0.45f : 1f);
        double t = System.nanoTime() / 1e9;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float r = radius.f(), w = thickness.f();
        String st = style.get();
        if (st.equals("Glow")) {
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            band(x, y, z, r, w * 4f, c, a * 0.35f, false, t, true);
            band(x, y, z, r, w, c, a, false, t, false);
        } else if (st.equals("Pulse")) {
            band(x, y, z, r, w, c, a, false, t, false);
            float k = (float) ((t * 0.8) % 1.0);
            band(x, y, z, r * (0.15f + 0.85f * k), w * 0.7f, c, a * (1f - k) * 0.7f, false, t, false);
        } else {
            band(x, y, z, r, w, c, a, st.equals("Dashed"), t, false);
        }
        if (targetMarker.on() && opponent != null) {
            double ox = opponent.lastTickPosX + (opponent.posX - opponent.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
            double oy = opponent.lastTickPosY + (opponent.posY - opponent.lastTickPosY) * pt - mc.getRenderManager().viewerPosY + 0.02;
            double oz = opponent.lastTickPosZ + (opponent.posZ - opponent.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
            float hr = opponent.width * 0.5f + opponent.getCollisionBorderSize();
            band(ox, oy, oz, hr + 0.08f, 0.05f, c, a, false, t, false);
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** Flat ring band of radius r and width w; soft = fades to both edges. */
    private static void band(double x, double y, double z, float r, float w, int c, float a, boolean dashed, double t, boolean soft) {
        float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
        int seg = 96;
        double spin = dashed ? t * 0.4 : 0;
        if (soft) {
            // inner fade, bright middle, outer fade
            for (int half = 0; half < 2; half++) {
                GL11.glBegin(GL11.GL_QUAD_STRIP);
                for (int i = 0; i <= seg; i++) {
                    double ang = Math.PI * 2 * i / seg;
                    float co = (float) Math.cos(ang), si = (float) Math.sin(ang);
                    float r0 = half == 0 ? r - w * 0.5f : r, r1 = half == 0 ? r : r + w * 0.5f;
                    GL11.glColor4f(cr, cg, cb, half == 0 ? 0f : a);
                    GL11.glVertex3d(x + co * r0, y, z + si * r0);
                    GL11.glColor4f(cr, cg, cb, half == 0 ? a : 0f);
                    GL11.glVertex3d(x + co * r1, y, z + si * r1);
                }
                GL11.glEnd();
            }
            return;
        }
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < seg; i++) {
            if (dashed && (i & 3) >= 2) continue;
            double a0 = Math.PI * 2 * i / seg + spin, a1 = Math.PI * 2 * (i + 1) / seg + spin;
            float r0 = r - w * 0.5f, r1 = r + w * 0.5f;
            GL11.glColor4f(cr, cg, cb, a);
            GL11.glVertex3d(x + Math.cos(a0) * r0, y, z + Math.sin(a0) * r0);
            GL11.glVertex3d(x + Math.cos(a0) * r1, y, z + Math.sin(a0) * r1);
            GL11.glVertex3d(x + Math.cos(a1) * r1, y, z + Math.sin(a1) * r1);
            GL11.glVertex3d(x + Math.cos(a1) * r0, y, z + Math.sin(a1) * r0);
        }
        GL11.glEnd();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || !label.on() || distance < 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.gameSettings.hideGUI) return;
        ScaledResolution sr = e.resolution;
        String s = String.format("%.2f m", distance);
        float w = Gfx.textWidth(s);
        Gfx.textShadow(s, sr.getScaledWidth() / 2f - w / 2f, sr.getScaledHeight() / 2f + 10, 0xFF000000 | stateColor());
    }

    @Override
    protected void onDisable() {
        opponent = null;
        distance = -1;
    }
}
