package net.m1227.module;

import java.util.ArrayList;
import java.util.List;
import net.m1227.client.ui.Img;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Health;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Aim Helper: a visual reference ON the target. A ring that slides along the target's body, a
 * reference point (chest, head or the spot of the hitbox nearest your crosshair), a small card
 * next to the target (face, name, distance, health) and marks where your hits landed.
 *
 * Purely visual: it never moves the camera, aims, attacks or changes reach. It only marks the
 * player you are fighting (your current target, or the one under the crosshair) and only while
 * you can actually see them; everything is depth-tested, so nothing shows through walls.
 */
public final class AimHelperModule extends Module {
    private final Setting.Mode marker, point;
    private final Setting.Col color;
    private final Setting.Num speed, ringSize;
    private final Setting.Bool card, hitMarks;
    private final List<double[]> marks = new ArrayList<double[]>();

    public AimHelperModule() {
        super("Aim Helper", "A reference ring and point on your target, a target card and hit marks.", Category.COMBAT, false);
        marker = add(new Setting.Mode("Marker", "Ring", "Ring", "Point", "Ring + Point", "Brackets"));
        point = add(new Setting.Mode("Reference point", "Nearest to crosshair", "Nearest to crosshair", "Chest", "Head"))
                .showIf(() -> marker.get().contains("Point"));
        color = add(new Setting.Col("Color", 0xFFFFFF));
        speed = add(new Setting.Num("Ring speed", 1.0, 0.2, 3.0, 0.1, "x")).showIf(() -> marker.get().contains("Ring"));
        ringSize = add(new Setting.Num("Ring size", 1.0, 0.6, 1.6, 0.05, "x")).showIf(() -> marker.get().contains("Ring"));
        card = add(new Setting.Bool("Card next to the target", true));
        hitMarks = add(new Setting.Bool("Hit marks", true));
    }

    /** Your current target, or the living entity under the crosshair; null unless you can see it. */
    private static EntityLivingBase target(Minecraft mc) {
        EntityLivingBase t = null;
        MovingObjectPosition m = mc.objectMouseOver;
        if (m != null && m.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && m.entityHit instanceof EntityLivingBase) {
            t = (EntityLivingBase) m.entityHit;
        }
        if (t == null) t = CombatTracker.target(4000L);
        if (t == null || t.isDead || t.getHealth() <= 0f || t.isInvisible()) return null;
        if (t.getDistanceSqToEntity(mc.thePlayer) > 16 * 16 || !mc.thePlayer.canEntityBeSeen(t)) return null;
        return t;
    }

    private static AxisAlignedBB box(EntityLivingBase e, float pt) {
        double ox = (e.posX - e.lastTickPosX) * (pt - 1), oy = (e.posY - e.lastTickPosY) * (pt - 1), oz = (e.posZ - e.lastTickPosZ) * (pt - 1);
        float b = e.getCollisionBorderSize();
        return e.getEntityBoundingBox().expand(b, b, b).offset(ox, oy, oz);
    }

    /** Point of the hitbox nearest to the look ray (where the crosshair is closest to the target). */
    private static Vec3 nearestToCrosshair(Minecraft mc, AxisAlignedBB bb, float pt) {
        Vec3 eye = mc.thePlayer.getPositionEyes(pt), look = mc.thePlayer.getLook(pt);
        double cx = (bb.minX + bb.maxX) / 2, cy = (bb.minY + bb.maxY) / 2, cz = (bb.minZ + bb.maxZ) / 2;
        double d = (cx - eye.xCoord) * look.xCoord + (cy - eye.yCoord) * look.yCoord + (cz - eye.zCoord) * look.zCoord;
        double px = eye.xCoord + look.xCoord * d, py = eye.yCoord + look.yCoord * d, pz = eye.zCoord + look.zCoord * d;
        return new Vec3(clamp(px, bb.minX, bb.maxX), clamp(py, bb.minY, bb.maxY), clamp(pz, bb.minZ, bb.maxZ));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!hitMarks.on() || e.entityPlayer != mc.thePlayer || !(e.target instanceof EntityLivingBase)) return;
        Vec3 p = nearestToCrosshair(mc, box((EntityLivingBase) e.target, 1f), 1f);
        marks.add(new double[]{p.xCoord, p.yCoord, p.zCoord, System.currentTimeMillis()});
        if (marks.size() > 12) marks.remove(0);
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        float pt = e.partialTicks;
        RenderManager rm = mc.getRenderManager();
        int c = color.rgb();
        double t = System.nanoTime() / 1e9;
        EntityLivingBase tg = target(mc);
        state(true);
        if (tg != null) {
            AxisAlignedBB bb = box(tg, pt);
            double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2;
            String m = marker.get();
            if (m.contains("Ring")) {
                // slides from the feet to the head and back
                double k = 0.5 - 0.5 * Math.cos(t * 2.2 * speed.get());
                double y = bb.minY + 0.1 + (bb.maxY - bb.minY - 0.2) * k;
                float r = (float) ((bb.maxX - bb.minX) * 0.5 + 0.18) * ringSize.f();
                ring(cx - rm.viewerPosX, y - rm.viewerPosY, cz - rm.viewerPosZ, r, 0.045f, c, 0.95f);
                ring(cx - rm.viewerPosX, y - rm.viewerPosY, cz - rm.viewerPosZ, r, 0.16f, c, 0.18f);
            }
            if (m.contains("Point")) {
                Vec3 p = point.is("Chest") ? new Vec3(cx, bb.minY + (bb.maxY - bb.minY) * 0.68, cz)
                        : point.is("Head") ? new Vec3(cx, bb.maxY - 0.22, cz) : nearestToCrosshair(mc, bb, pt);
                billboard(rm, p.xCoord, p.yCoord, p.zCoord);
                Gfx.circle(0, 0, 3.4f, 0xA0000000);
                Gfx.circle(0, 0, 2.4f, 0xFF000000 | c);
                Gfx.annulus(0, 0, 5.2f, 6.0f, 0, Math.PI * 2, Gfx.alpha(0xFF000000 | c, 0.6f));
                GlStateManager.popMatrix();
            }
            if (m.equals("Brackets")) brackets(rm, bb, c);
            if (card.on()) card(mc, rm, tg, bb, pt);
        }
        // hit marks: a small X where each hit landed, fading in 0.6 s
        long now = System.currentTimeMillis();
        for (int i = marks.size() - 1; i >= 0; i--) {
            double[] h = marks.get(i);
            float age = (now - (long) h[3]) / 600f;
            if (age >= 1f) {
                marks.remove(i);
                continue;
            }
            billboard(rm, h[0], h[1], h[2]);
            float s = 3f + age * 2f;
            int col = Gfx.alpha(0xFFFF5C8A, 1f - age);
            Gfx.line(-s, -s, s, s, 1.3f, col);
            Gfx.line(s, -s, -s, s, 1.3f, col);
            GlStateManager.popMatrix();
        }
        state(false);
    }

    private static void state(boolean on) {
        if (on) {
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        } else {
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    /** Horizontal ring band around (x, y, z), camera-relative. */
    private static void ring(double x, double y, double z, float r, float w, int c, float a) {
        float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= 64; i++) {
            double an = Math.PI * 2 * i / 64;
            double co = Math.cos(an), si = Math.sin(an);
            GL11.glColor4f(cr, cg, cb, a);
            GL11.glVertex3d(x + co * (r - w / 2), y, z + si * (r - w / 2));
            GL11.glVertex3d(x + co * (r + w / 2), y, z + si * (r + w / 2));
        }
        GL11.glEnd();
    }

    /** Pushes a matrix facing the camera at a world point; 1 unit = 1/40 block. Caller pops. */
    private static void billboard(RenderManager rm, double x, double y, double z) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - rm.viewerPosX, y - rm.viewerPosY, z - rm.viewerPosZ);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.scale(-0.025f, -0.025f, 0.025f);
    }

    /** Four corner brackets around the target, facing the camera. */
    private static void brackets(RenderManager rm, AxisAlignedBB bb, int c) {
        double cx = (bb.minX + bb.maxX) / 2, cy = (bb.minY + bb.maxY) / 2, cz = (bb.minZ + bb.maxZ) / 2;
        billboard(rm, cx, cy, cz);
        float hw = (float) (bb.maxX - bb.minX) * 20f + 4f, hh = (float) (bb.maxY - bb.minY) * 20f + 4f, l = 8f;
        int col = 0xFF000000 | c;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                Gfx.line(sx * hw, sy * hh, sx * (hw - l), sy * hh, 1.6f, col);
                Gfx.line(sx * hw, sy * hh, sx * hw, sy * (hh - l), 1.6f, col);
            }
        }
        GlStateManager.popMatrix();
    }

    /** The card beside the target: face, name, distance and a health ring. */
    private static void card(Minecraft mc, RenderManager rm, EntityLivingBase e, AxisAlignedBB bb, float pt) {
        double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2;
        // to the right of the target as you see it
        double yaw = Math.toRadians(rm.playerViewY);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double side = (bb.maxX - bb.minX) * 0.5 + 0.55;
        billboard(rm, cx + rx * side, bb.minY + (bb.maxY - bb.minY) * 0.62, cz + rz * side);
        float w = 74, h = 26;
        Gfx.roundedRect(0, -h / 2f, w, h, 5, 0xC0101218);
        Gfx.roundedOutline(0, -h / 2f, w, h, 5, 0.6f, 0x30FFFFFF);
        if (e instanceof AbstractClientPlayer) {
            net.minecraft.util.ResourceLocation skin = ((AbstractClientPlayer) e).getLocationSkin();
            Img.draw(skin, 4, -9, 18, 18, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 1f);
            Img.draw(skin, 4, -9, 18, 18, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 1f);
            GlStateManager.disableTexture2D();
        } else {
            Gfx.roundedRect(4, -9, 18, 18, 3, 0xFF2A2E3B);
        }
        float hp = Health.of(e), max = Math.max(1f, e.getMaxHealth());
        float k = Math.max(0f, Math.min(1f, hp / max));
        String name = Gfx.trim(e.getName(), 34);
        double dist = Math.sqrt(e.getDistanceSqToEntity(mc.thePlayer));
        Gfx.textScaled(name, 26, -9, 0.75f, 0xFFECEEF4);
        Gfx.textScaled(String.format("Distance %.1f", dist), 26, 0, 0.62f, 0xFF9AA0AE);
        int hc = k > 0.6f ? 0xFF3DDC97 : k > 0.3f ? 0xFFFFC24A : 0xFFFF6B6B;
        Gfx.annulus(w - 11, 0, 5.5f, 7f, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * k, hc);
        Gfx.annulus(w - 11, 0, 5.5f, 7f, -Math.PI / 2 + Math.PI * 2 * k, Math.PI * 1.5, 0x30FFFFFF);
        String hpS = String.valueOf(Math.round(hp));
        Gfx.textScaled(hpS, w - 11 - Gfx.textWidth(hpS, 0.5f) / 2f, -2, 0.5f, 0xFFFFFFFF);
        GlStateManager.disableTexture2D();
        GlStateManager.popMatrix();
    }
}
