package net.m1227.module;

import java.util.ArrayList;
import java.util.List;
import net.m1227.client.ui.Img;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Health;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.render.Silhouette;
import net.m1227.ui.Gfx;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
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
import org.lwjgl.opengl.GL14;

/**
 * Aim Helper: a visual reference ON the target. By default a glowing point of light sits on the
 * target's body where you should aim (the spot of the hitbox nearest your crosshair, or chest /
 * head), and the part of the body around it lights up. Optionally a thick glowing ring that
 * slides up and down the body, a glow on the whole body, a card next to the target (face, name,
 * distance, health) and marks where your hits landed.
 *
 * Purely visual: it never moves the camera, aims, attacks or changes reach. It only marks the
 * player you are fighting (your current target, or the one under the crosshair) and only while
 * you can actually see them; everything is depth-tested, so nothing shows through walls.
 */
public final class AimHelperModule extends Module {
    private final Setting.Mode marker, point, bodyGlow;
    private final Setting.Col color, glowColor, pointColor;
    private final Setting.Num speed, ringSize, thickness, tilt, glowStrength, pointSize;
    /** Smoothed aim point (world), so the light glides instead of jumping. */
    private double ax, ay, az;
    private EntityLivingBase aimOf;
    private long aimNanos;
    private final Setting.Bool card, hitMarks, rimGlow;
    private final List<double[]> marks = new ArrayList<double[]>();

    public AimHelperModule() {
        super("Aim Helper", "A glowing ring and body glow on your target, a reference point, a card and hit marks.", Category.COMBAT, false);
        marker = add(new Setting.Mode("Marker", "Glow point", "Glow point", "Ring", "Point", "Ring + Point", "Ring + Glow point", "Brackets"));
        point = add(new Setting.Mode("Reference point", "Nearest to crosshair", "Nearest to crosshair", "Chest", "Head"))
                .showIf(() -> marker.get().contains("oint"));
        color = add(new Setting.Col("Color", 0xFFFFFF));
        speed = add(new Setting.Num("Ring speed", 1.0, 0.2, 3.0, 0.1, "x")).showIf(() -> marker.get().contains("Ring"));
        ringSize = add(new Setting.Num("Ring size", 1.0, 0.6, 1.6, 0.05, "x")).showIf(() -> marker.get().contains("Ring"));
        thickness = add(new Setting.Num("Ring thickness", 1.0, 0.4, 2.5, 0.05, "x")).showIf(() -> marker.get().contains("Ring"));
        tilt = add(new Setting.Num("Ring tilt", 10, 0, 30, 1, " deg")).showIf(() -> marker.get().contains("Ring"));
        pointColor = add(new Setting.Col("Glow point color", 0x8FE6FF)).showIf(() -> marker.get().contains("Glow point"));
        pointSize = add(new Setting.Num("Glow point size", 1.0, 0.4, 2.5, 0.05, "x")).showIf(() -> marker.get().contains("Glow point"));
        bodyGlow = add(new Setting.Mode("Body glow", "Around the point", "Off", "Around the point", "Glow", "Scan band", "Glow + Scan band"));
        glowColor = add(new Setting.Col("Glow color", 0x6FD3FF)).showIf(() -> !bodyGlow.is("Off"));
        glowStrength = add(new Setting.Num("Glow strength", 0.7, 0.1, 1.5, 0.05, "x")).showIf(() -> !bodyGlow.is("Off"));
        rimGlow = add(new Setting.Bool("Glow around the body", true)).showIf(() -> !bodyGlow.is("Off"));
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

    /**
     * Where to aim, on the front of the target's hitbox (facing you), smoothed over time. The
     * reference is the chosen point; it is moved along your line of sight onto the box surface so
     * the light sits on the body, not inside it.
     */
    private Vec3 aimPoint(Minecraft mc, EntityLivingBase tg, AxisAlignedBB bb, float pt) {
        double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2;
        Vec3 ref = point.is("Chest") ? new Vec3(cx, bb.minY + (bb.maxY - bb.minY) * 0.68, cz)
                : point.is("Head") ? new Vec3(cx, bb.maxY - 0.22, cz) : nearestToCrosshair(mc, bb, pt);
        Vec3 eye = mc.thePlayer.getPositionEyes(pt);
        Vec3 dir = ref.subtract(eye);
        double len = dir.lengthVector();
        if (len > 1e-3) {
            Vec3 far = new Vec3(eye.xCoord + dir.xCoord / len * 40, eye.yCoord + dir.yCoord / len * 40, eye.zCoord + dir.zCoord / len * 40);
            MovingObjectPosition hit = bb.contract(0.08, 0.0, 0.08).calculateIntercept(eye, far);
            if (hit != null && hit.hitVec != null) ref = hit.hitVec;
        }
        long now = System.nanoTime();
        float dt = aimNanos == 0 ? 1f : Math.min(1f, (now - aimNanos) / 1e9f);
        aimNanos = now;
        if (aimOf != tg) {
            aimOf = tg;
            ax = ref.xCoord;
            ay = ref.yCoord;
            az = ref.zCoord;
        } else {
            double k = 1 - Math.exp(-dt * 18);
            ax += (ref.xCoord - ax) * k;
            ay += (ref.yCoord - ay) * k;
            az += (ref.zCoord - az) * k;
        }
        return new Vec3(ax, ay, az);
    }

    private final float[] uv = new float[4];

    /**
     * The light on the body: a wide soft halo, a tighter glow and a white-hot core, facing the
     * camera, pulsing gently. Drawn over the body (the target is visible: checked above).
     */
    private void glowPoint(RenderManager rm, Vec3 p, double t) {
        int c = pointColor.rgb();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float s = pointSize.f(), pulse = 0.85f + 0.15f * (float) Math.sin(t * 5.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(p.xCoord - rm.viewerPosX, p.yCoord - rm.viewerPosY, p.zCoord - rm.viewerPosZ);
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        ParticleSystem.INSTANCE.bindAtlas();
        ParticleSystem.uv(Sprite.GLOW, uv);
        quad(0.55f * s * pulse, r, g, b, 0.35f);
        quad(0.26f * s, r, g, b, 0.75f);
        quad(0.12f * s * pulse, Math.min(1f, r + 0.5f), Math.min(1f, g + 0.5f), Math.min(1f, b + 0.5f), 1f);
        quad(0.06f * s, 1f, 1f, 1f, 1f);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.popMatrix();
    }

    private void quad(float h, float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(uv[0], uv[1]);
        GL11.glVertex3f(-h, -h, 0);
        GL11.glTexCoord2f(uv[0], uv[3]);
        GL11.glVertex3f(-h, h, 0);
        GL11.glTexCoord2f(uv[2], uv[3]);
        GL11.glVertex3f(h, h, 0);
        GL11.glTexCoord2f(uv[2], uv[1]);
        GL11.glVertex3f(h, -h, 0);
        GL11.glEnd();
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
        if (tg != null) {
            AxisAlignedBB bb = box(tg, pt);
            double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2;
            String m = marker.get();
            // the ring slides from the feet to the head and back, easing at both ends
            double k = 0.5 - 0.5 * Math.cos(t * 2.2 * speed.get());
            double ringY = bb.minY + 0.12 + (bb.maxY - bb.minY - 0.24) * k;
            Vec3 aim = aimPoint(mc, tg, bb, pt);
            if (!bodyGlow.is("Off")) bodyGlow(mc, tg, bb, pt, bodyGlow.is("Around the point") ? aim.yCoord : ringY, t);
            if (m.contains("Glow point")) glowPoint(rm, aim, t);
            state(true);
            if (m.contains("Ring")) {
                float r = (float) ((bb.maxX - bb.minX) * 0.5 + 0.2) * ringSize.f();
                thickRing(cx - rm.viewerPosX, ringY - rm.viewerPosY, cz - rm.viewerPosZ, r, thickness.f(), c, t);
            }
            if (m.equals("Point") || m.equals("Ring + Point")) {
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
        } else {
            state(true);
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

    private final java.nio.DoubleBuffer plane = org.lwjgl.BufferUtils.createDoubleBuffer(4);

    /**
     * Light on the target's body. The body is drawn again with additive "constant colour"
     * blending, so its own texture gets lit in the glow colour; depth-tested at the same depth,
     * so only the parts you can see light up. The scan band is the same thing limited by two
     * clip planes to a slice around the ring, in three nested slices for a soft falloff.
     */
    private void bodyGlow(Minecraft mc, EntityLivingBase tg, AxisAlignedBB bb, float pt, double ringY, double t) {
        int gc = glowColor.rgb();
        float gr = ((gc >> 16) & 255) / 255f, gg = ((gc >> 8) & 255) / 255f, gb = (gc & 255) / 255f;
        float str = glowStrength.f();
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 3.2);
        boolean soft = bodyGlow.get().startsWith("Glow"), band = bodyGlow.get().contains("Scan") || bodyGlow.is("Around the point");
        RenderManager rm = mc.getRenderManager();
        Silhouette.entityState(true);
        GlStateManager.enableBlend();
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        try {
            if (soft) {
                float a = 0.55f * str * pulse;
                GL14.glBlendColor(Math.min(1f, gr * a), Math.min(1f, gg * a), Math.min(1f, gb * a), 1f);
                GlStateManager.blendFunc(0x8001 /* GL_CONSTANT_COLOR */, GL11.GL_ONE);
                Silhouette.render(tg, pt, 0, 0, 0);
            }
            if (band) {
                double y = ringY - rm.viewerPosY;
                float[] half = {0.34f, 0.2f, 0.09f};
                float[] amt = {0.45f, 0.6f, 0.8f};
                GL11.glEnable(GL11.GL_CLIP_PLANE0);
                GL11.glEnable(GL11.GL_CLIP_PLANE1);
                for (int i = 0; i < 3; i++) {
                    clip(GL11.GL_CLIP_PLANE0, 0, 1, 0, -(y - half[i]));
                    clip(GL11.GL_CLIP_PLANE1, 0, -1, 0, y + half[i]);
                    float a = amt[i] * str;
                    GL14.glBlendColor(Math.min(1f, (gr + 0.25f) * a), Math.min(1f, (gg + 0.25f) * a), Math.min(1f, (gb + 0.25f) * a), 1f);
                    GlStateManager.blendFunc(0x8001 /* GL_CONSTANT_COLOR */, GL11.GL_ONE);
                    Silhouette.render(tg, pt, 0, 0, 0);
                }
            }
        } finally {
            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL11.glDisable(GL11.GL_CLIP_PLANE1);
            GL14.glBlendColor(0f, 0f, 0f, 0f);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(true);
            Silhouette.entityState(false);
        }
        if (rimGlow.on()) {
            // soft light around the silhouette: large glow sprites inside the body, the body hides their centre
            ParticleSystem ps = ParticleSystem.INSTANCE;
            double cx = (bb.minX + bb.maxX) / 2, cz = (bb.minZ + bb.maxZ) / 2, h = bb.maxY - bb.minY;
            float a = 0.35f * str * pulse;
            AuraShapes.sprite(ps, Sprite.GLOW, cx, bb.minY + h * 0.82, cz, 0.6f, 0f, false, gc, a);
            AuraShapes.sprite(ps, Sprite.GLOW, cx, bb.minY + h * 0.5, cz, 0.85f, 0f, false, gc, a);
            AuraShapes.sprite(ps, Sprite.GLOW, cx, bb.minY + h * 0.18, cz, 0.65f, 0f, false, gc, a * 0.8f);
            AuraShapes.sprite(ps, Sprite.GLOW, cx, ringY, cz, 1.0f, 0f, false, gc, a * 0.9f);
        }
    }

    private void clip(int which, double a, double b, double c, double d) {
        plane.clear();
        plane.put(a).put(b).put(c).put(d).flip();
        GL11.glClipPlane(which, plane);
    }

    /**
     * The ring: a short glowing tube (a wall you see from the side plus a flat band you see from
     * above) with a soft halo on both sides, gently tilting as it moves. Camera-relative.
     */
    private void thickRing(double x, double y, double z, float r, float thick, int c, double t) {
        float cr = ((c >> 16) & 255) / 255f, cg = ((c >> 8) & 255) / 255f, cb = (c & 255) / 255f;
        float hh = 0.035f * thick, w = 0.05f * thick, halo = 0.14f * thick;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        float tl = tilt.f();
        if (tl > 0f) {
            double dir = t * 0.9;
            GlStateManager.rotate(tl * (float) Math.sin(t * 1.7), (float) Math.cos(dir), 0f, (float) Math.sin(dir));
        }
        int n = 72;
        // halo on the flat ring, inside and outside
        for (int side = -1; side <= 1; side += 2) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= n; i++) {
                double an = Math.PI * 2 * i / n;
                float co = (float) Math.cos(an), si = (float) Math.sin(an);
                GL11.glColor4f(cr, cg, cb, 0.3f);
                GL11.glVertex3f(co * r, 0f, si * r);
                GL11.glColor4f(cr, cg, cb, 0f);
                GL11.glVertex3f(co * (r + side * halo), 0f, si * (r + side * halo));
            }
            GL11.glEnd();
        }
        // halo above and below the tube wall
        for (int side = -1; side <= 1; side += 2) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= n; i++) {
                double an = Math.PI * 2 * i / n;
                float co = (float) Math.cos(an), si = (float) Math.sin(an);
                GL11.glColor4f(cr, cg, cb, 0.28f);
                GL11.glVertex3f(co * r, side * hh, si * r);
                GL11.glColor4f(cr, cg, cb, 0f);
                GL11.glVertex3f(co * r, side * (hh + halo * 0.8f), si * r);
            }
            GL11.glEnd();
        }
        // the solid tube: outer wall, inner wall, top and bottom bands
        float[][] walls = {{r + w / 2, -hh, r + w / 2, hh}, {r - w / 2, -hh, r - w / 2, hh}, {r - w / 2, hh, r + w / 2, hh}, {r - w / 2, -hh, r + w / 2, -hh}};
        float br = Math.min(1f, cr + 0.15f), bg = Math.min(1f, cg + 0.15f), bb2 = Math.min(1f, cb + 0.15f);
        for (float[] wl : walls) {
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int i = 0; i <= n; i++) {
                double an = Math.PI * 2 * i / n;
                float co = (float) Math.cos(an), si = (float) Math.sin(an);
                GL11.glColor4f(br, bg, bb2, 0.95f);
                GL11.glVertex3f(co * wl[0], wl[1], si * wl[0]);
                GL11.glVertex3f(co * wl[2], wl[3], si * wl[2]);
            }
            GL11.glEnd();
        }
        GlStateManager.popMatrix();
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
