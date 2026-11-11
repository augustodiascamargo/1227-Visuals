package net.m1227.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

/**
 * A player drawn from six skinned boxes with free joints, used for emotes.
 * Pose angles are in degrees: pitch (+ = forward / up for limbs, + = look up
 * for the head), yaw (+ = towards the body's centre line for the right arm),
 * roll (+ = outwards for the right arm). The body rotates about the hips and
 * carries the head and arms with it.
 */
public final class Puppet {
    private static final float PX = 0.9375f / 16f;

    /** Joint angles of one pose. */
    public static final class Pose {
        public final float[] head = new float[3], body = new float[3], rArm = new float[3], lArm = new float[3], rLeg = new float[3], lLeg = new float[3];
        /** Extra body yaw (spins) in degrees and vertical offset in blocks. */
        public float spin, lift;

        public Pose reset() {
            for (float[] a : new float[][]{head, body, rArm, lArm, rLeg, lLeg}) a[0] = a[1] = a[2] = 0f;
            spin = 0f;
            lift = 0f;
            return this;
        }
    }

    private Puppet() {}

    private static float[] rot(float[] e) {
        double p = Math.toRadians(e[0]), y = Math.toRadians(e[1]), r = Math.toRadians(e[2]);
        float cp = (float) Math.cos(p), sp = (float) Math.sin(p), cy = (float) Math.cos(y), sy = (float) Math.sin(y),
                cr = (float) Math.cos(r), sr = (float) Math.sin(r);
        float[] rx = {1, 0, 0, 0, cp, -sp, 0, sp, cp};
        float[] ry = {cy, 0, sy, 0, 1, 0, -sy, 0, cy};
        float[] rz = {cr, -sr, 0, sr, cr, 0, 0, 0, 1};
        return mul(ry, mul(rx, rz));
    }

    private static float[] mul(float[] a, float[] b) {
        float[] o = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) o[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        }
        return o;
    }

    /** Draws the player's skin in the given pose with feet at (x, y, z) (render-relative). */
    public static void render(AbstractClientPlayer p, double x, double y, double z, float bodyYawDeg, Pose pose, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean slim = "slim".equals(p.getSkinType());
        int aw = slim ? 3 : 4;
        float ax = slim ? 5.5f : 6f;
        BodyFx.Box head = new BodyFx.Box(0, 28, 0, 8, 8, 8, 0, 0, 0);
        BodyFx.Box hat = new BodyFx.Box(0, 28, 0, 8, 8, 8, 32, 0, 0.5f);
        BodyFx.Box body = new BodyFx.Box(0, 18, 0, 8, 12, 4, 16, 16, 0);
        BodyFx.Box jacket = new BodyFx.Box(0, 18, 0, 8, 12, 4, 16, 32, 0.25f);
        BodyFx.Box rArm = new BodyFx.Box(ax, 18, 0, aw, 12, 4, 40, 16, 0);
        BodyFx.Box rSleeve = new BodyFx.Box(ax, 18, 0, aw, 12, 4, 40, 32, 0.25f);
        BodyFx.Box lArm = new BodyFx.Box(-ax, 18, 0, aw, 12, 4, 32, 48, 0);
        BodyFx.Box lSleeve = new BodyFx.Box(-ax, 18, 0, aw, 12, 4, 48, 48, 0.25f);
        BodyFx.Box rLeg = new BodyFx.Box(2, 6, 0, 4, 12, 4, 0, 16, 0);
        BodyFx.Box rPants = new BodyFx.Box(2, 6, 0, 4, 12, 4, 0, 32, 0.25f);
        BodyFx.Box lLeg = new BodyFx.Box(-2, 6, 0, 4, 12, 4, 16, 48, 0);
        BodyFx.Box lPants = new BodyFx.Box(-2, 6, 0, 4, 12, 4, 0, 48, 0.25f);

        float yaw = (float) Math.PI - (float) Math.toRadians(bodyYawDeg + pose.spin);
        float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float[] world = {c, 0, s, 0, 1, 0, -s, 0, c};
        float[] rb = rot(pose.body);
        float light = Math.max(0.3f, mc.theWorld == null ? 1f : mc.theWorld.getLightBrightness(new BlockPos(p.posX, p.posY + 1, p.posZ)) * 1.1f);

        mc.getTextureManager().bindTexture(p.getLocationSkin());
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glBegin(GL11.GL_QUADS);
        // legs (not carried by the body)
        part(rLeg, rPants, rot(pose.rLeg), 2 * PX, 12 * PX, 0, null, world, x, y + pose.lift, z, light);
        part(lLeg, lPants, rot(pose.lLeg), -2 * PX, 12 * PX, 0, null, world, x, y + pose.lift, z, light);
        // body and what it carries (rotating about the hips)
        part(body, jacket, IDENTITY, 0, 12 * PX, 0, rb, world, x, y + pose.lift, z, light);
        part(head, hat, rot(pose.head), 0, 24 * PX, 0, rb, world, x, y + pose.lift, z, light);
        part(rArm, rSleeve, rot(pose.rArm), ax * PX, 22 * PX, 0, rb, world, x, y + pose.lift, z, light);
        float[] la = pose.lArm.clone();
        la[1] = -la[1];
        la[2] = -la[2]; // mirror: + is always "inwards / outwards" for both arms
        part(lArm, lSleeve, rot(la), -ax * PX, 22 * PX, 0, rb, world, x, y + pose.lift, z, light);
        GL11.glEnd();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    private static void part(BodyFx.Box b, BodyFx.Box overlay, float[] r, float px, float py, float pz, float[] bodyRot,
                             float[] world, double x, double y, double z, float light) {
        for (BodyFx.Box box : new BodyFx.Box[]{b, overlay}) {
            float[][] c = new float[8][3];
            for (int k = 0; k < 8; k++) {
                float lx = box.cx + ((k & 1) == 0 ? -box.hx : box.hx) - px;
                float ly = box.cy + ((k & 2) == 0 ? -box.hy : box.hy) - py;
                float lz = box.cz + ((k & 4) == 0 ? -box.hz : box.hz) - pz;
                float qx = r[0] * lx + r[1] * ly + r[2] * lz + px;
                float qy = r[3] * lx + r[4] * ly + r[5] * lz + py;
                float qz = r[6] * lx + r[7] * ly + r[8] * lz + pz;
                if (bodyRot != null) {
                    float hy = 12 * PX; // body pivot: hips
                    float ty = qy - hy;
                    float nx = bodyRot[0] * qx + bodyRot[1] * ty + bodyRot[2] * qz;
                    float ny = bodyRot[3] * qx + bodyRot[4] * ty + bodyRot[5] * qz + hy;
                    float nz = bodyRot[6] * qx + bodyRot[7] * ty + bodyRot[8] * qz;
                    qx = nx;
                    qy = ny;
                    qz = nz;
                }
                c[k][0] = (float) x + world[0] * qx + world[2] * qz;
                c[k][1] = (float) y + qy;
                c[k][2] = (float) z + world[6] * qx + world[8] * qz;
            }
            BodyFx.boxFaces(c, box, true, light, 1f);
        }
    }
}
