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
        float light = Math.max(0.3f, mc.theWorld == null ? 1f : mc.theWorld.getLightBrightness(new BlockPos(p.posX, p.posY + 1, p.posZ)) * 1.1f);
        render(p.getLocationSkin(), "slim".equals(p.getSkinType()), x, y, z, bodyYawDeg, pose, light);
    }

    /** Same drawing from a skin texture alone (no entity, no world): main menu character and cosmetics preview. */
    public static void render(net.minecraft.util.ResourceLocation skin, boolean slim, double x, double y, double z, float bodyYawDeg, Pose pose, float light) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(skin);
        // 1.27.3: an old 64x32 skin (no separate left limbs, no overlay layers) was read with the
        // 64x64 layout, so the body showed parts of the head and the limbs showed the shirt
        boolean legacy = isLegacy(skin);
        int aw = slim ? 3 : 4;
        float ax = slim ? 5.5f : 6f;
        float ay = slim ? 17.5f : 18f; // slim arms hang half a pixel lower, as in ModelPlayer
        float apy = slim ? 21.5f : 22f;
        java.util.List<Piece> pieces = new java.util.ArrayList<Piece>(12);
        float[] world;
        {
            float yaw = (float) Math.PI - (float) Math.toRadians(bodyYawDeg + pose.spin);
            float c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
            world = new float[]{c, 0, s, 0, 1, 0, -s, 0, c};
        }
        float[] rb = rot(pose.body);
        double ly = y + pose.lift;
        float[] la = pose.lArm.clone();
        la[1] = -la[1];
        la[2] = -la[2]; // mirror: + is always "inwards / outwards" for both arms
        // legs (not carried by the body)
        add(pieces, new BodyFx.Box(2, 6, 0, 4, 12, 4, 0, 16, 0), false, rot(pose.rLeg), 2 * PX, 12 * PX, null, world, x, ly, z);
        if (legacy) add(pieces, new BodyFx.Box(-2, 6, 0, 4, 12, 4, 0, 16, 0), true, rot(pose.lLeg), -2 * PX, 12 * PX, null, world, x, ly, z);
        else add(pieces, new BodyFx.Box(-2, 6, 0, 4, 12, 4, 16, 48, 0), false, rot(pose.lLeg), -2 * PX, 12 * PX, null, world, x, ly, z);
        // body and what it carries (rotating about the hips)
        add(pieces, new BodyFx.Box(0, 18, 0, 8, 12, 4, 16, 16, 0), false, IDENTITY, 0, 12 * PX, rb, world, x, ly, z);
        add(pieces, new BodyFx.Box(0, 28, 0, 8, 8, 8, 0, 0, 0), false, rot(pose.head), 0, 24 * PX, rb, world, x, ly, z);
        add(pieces, new BodyFx.Box(ax, ay, 0, aw, 12, 4, 40, 16, 0), false, rot(pose.rArm), ax * PX, apy * PX, rb, world, x, ly, z);
        if (legacy) add(pieces, new BodyFx.Box(-ax, ay, 0, aw, 12, 4, 40, 16, 0), true, rot(la), -ax * PX, apy * PX, rb, world, x, ly, z);
        else add(pieces, new BodyFx.Box(-ax, ay, 0, aw, 12, 4, 32, 48, 0), false, rot(la), -ax * PX, apy * PX, rb, world, x, ly, z);
        // second layer (hat on every skin; jacket, sleeves and trousers only exist in 64x64 skins)
        add(pieces, new BodyFx.Box(0, 28, 0, 8, 8, 8, 32, 0, 0.5f), false, rot(pose.head), 0, 24 * PX, rb, world, x, ly, z).overlay = true;
        if (!legacy) {
            add(pieces, new BodyFx.Box(0, 18, 0, 8, 12, 4, 16, 32, 0.25f), false, IDENTITY, 0, 12 * PX, rb, world, x, ly, z).overlay = true;
            add(pieces, new BodyFx.Box(ax, ay, 0, aw, 12, 4, 40, 32, 0.25f), false, rot(pose.rArm), ax * PX, apy * PX, rb, world, x, ly, z).overlay = true;
            add(pieces, new BodyFx.Box(-ax, ay, 0, aw, 12, 4, 48, 48, 0.25f), false, rot(la), -ax * PX, apy * PX, rb, world, x, ly, z).overlay = true;
            add(pieces, new BodyFx.Box(2, 6, 0, 4, 12, 4, 0, 32, 0.25f), false, rot(pose.rLeg), 2 * PX, 12 * PX, null, world, x, ly, z).overlay = true;
            add(pieces, new BodyFx.Box(-2, 6, 0, 4, 12, 4, 0, 48, 0.25f), false, rot(pose.lLeg), -2 * PX, 12 * PX, null, world, x, ly, z).overlay = true;
        }

        // Where the viewer is, in these coordinates: faces turned away are skipped and the pieces
        // are drawn far to near. The picture is then right even when the depth buffer is not
        // (a menu that left depth writes off, another mod's state): the 1.27.2 menu character
        // could show the inside of its head and body.
        View view = view();
        for (Piece pc : pieces) pc.key = view.closeness(pc.cx, pc.cy, pc.cz) + (pc.overlay ? 1e-3f : 0f);
        java.util.Collections.sort(pieces, new java.util.Comparator<Piece>() {
            @Override
            public int compare(Piece a, Piece b) {
                return Float.compare(a.key, b.key);
            }
        });

        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        // clip planes another effect may have left on would cut the model open
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_CLIP_PLANE1);
        BodyFx.texH = legacy ? 32f : 64f;
        try {
            GL11.glBegin(GL11.GL_QUADS);
            for (Piece pc : pieces) {
                BodyFx.mirrorU = pc.mirror;
                faces(pc, view, light);
            }
            GL11.glEnd();
        } finally {
            BodyFx.texH = 64f;
            BodyFx.mirrorU = false;
        }
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableCull();
    }

    private static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /** One box placed in the scene: its 8 corners (bit0 = +x, bit1 = +y, bit2 = +z) and centre. */
    private static final class Piece {
        BodyFx.Box box;
        boolean mirror, overlay;
        final float[][] c = new float[8][3];
        float cx, cy, cz, key;
    }

    private static Piece add(java.util.List<Piece> out, BodyFx.Box box, boolean mirror, float[] r, float px, float py, float[] bodyRot,
                             float[] world, double x, double y, double z) {
        Piece pc = new Piece();
        pc.box = box;
        pc.mirror = mirror;
        for (int k = 0; k < 8; k++) {
            float lx = box.cx + ((k & 1) == 0 ? -box.hx : box.hx) - px;
            float ly = box.cy + ((k & 2) == 0 ? -box.hy : box.hy) - py;
            float lz = box.cz + ((k & 4) == 0 ? -box.hz : box.hz);
            float qx = r[0] * lx + r[1] * ly + r[2] * lz + px;
            float qy = r[3] * lx + r[4] * ly + r[5] * lz + py;
            float qz = r[6] * lx + r[7] * ly + r[8] * lz;
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
            pc.c[k][0] = (float) x + world[0] * qx + world[2] * qz;
            pc.c[k][1] = (float) y + qy;
            pc.c[k][2] = (float) z + world[6] * qx + world[8] * qz;
            pc.cx += pc.c[k][0] / 8f;
            pc.cy += pc.c[k][1] / 8f;
            pc.cz += pc.c[k][2] / 8f;
        }
        out.add(pc);
        return pc;
    }

    // corners of each face (top-left, top-right, bottom-right, bottom-left of its texture rect)
    private static final int[][] FACE = {{3, 2, 0, 1}, {6, 7, 5, 4}, {7, 3, 1, 5}, {2, 6, 4, 0}, {7, 6, 2, 3}, {1, 0, 4, 5}};
    private static final int[] SIDE = {4, 5, 0, 1, 2, 3};

    /** The faces of one piece that face the viewer (same texture layout as BodyFx.boxFaces). */
    private static void faces(Piece pc, View view, float light) {
        BodyFx.Box b = pc.box;
        int w = b.w, h = b.h, d = b.d, u = b.u, v = b.v;
        int[][] rect = {
                {u + d, v + d, w, h},              // front (-z)
                {u + d + w + d, v + d, w, h},      // back (+z)
                {u, v + d, d, h},                  // +x side
                {u + d + w, v + d, d, h},          // -x side
                {u + d, v, w, d},                  // top
                {u + d + w, v, w, d}};             // bottom
        if (pc.mirror) { // a mirrored limb shows each side's texture on the opposite side
            int[] t = rect[2];
            rect[2] = rect[3];
            rect[3] = t;
        }
        for (int f = 0; f < 6; f++) {
            int[] q = FACE[f];
            float fx = 0, fy = 0, fz = 0;
            for (int i = 0; i < 4; i++) {
                fx += pc.c[q[i]][0] / 4f;
                fy += pc.c[q[i]][1] / 4f;
                fz += pc.c[q[i]][2] / 4f;
            }
            if (!view.sees(fx, fy, fz, fx - pc.cx, fy - pc.cy, fz - pc.cz)) continue;
            BodyFx.face(pc.c, q[0], q[1], q[2], q[3], rect[f][0], rect[f][1], rect[f][2], rect[f][3], true, b, SIDE[f], BodyFx.NEUTRAL, light, 1f);
        }
    }

    /** The viewer in model coordinates, from the current GL matrices. */
    private static final class View {
        boolean ortho;
        float dx, dy, dz; // ortho: direction towards the viewer
        float ex, ey, ez; // perspective: eye position

        /** Bigger = closer to the viewer. */
        float closeness(float x, float y, float z) {
            if (ortho) return x * dx + y * dy + z * dz;
            float a = x - ex, b = y - ey, c = z - ez;
            return -(a * a + b * b + c * c);
        }

        /** True when a face at (x, y, z) with outward direction n is turned towards the viewer. */
        boolean sees(float x, float y, float z, float nx, float ny, float nz) {
            if (ortho) return nx * dx + ny * dy + nz * dz > 1e-6f;
            return nx * (ex - x) + ny * (ey - y) + nz * (ez - z) > 1e-6f;
        }
    }

    private static final java.nio.FloatBuffer MV = org.lwjgl.BufferUtils.createFloatBuffer(16), PR = org.lwjgl.BufferUtils.createFloatBuffer(16);

    private static View view() {
        View v = new View();
        MV.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MV);
        PR.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PR);
        float[] m = new float[16];
        MV.get(m);
        float[] p = new float[16];
        PR.get(p);
        float[] inv = invert3(m);
        v.ortho = Math.abs(p[11]) < 1e-4f; // perspective projections have -1 there
        if (inv == null) { // degenerate matrix: assume the usual front view
            v.ortho = true;
            v.dz = 1f;
            return v;
        }
        if (v.ortho) {
            // the eye looks down -z: the viewer is towards +z in eye space
            v.dx = inv[2];
            v.dy = inv[5];
            v.dz = inv[8];
        } else {
            // eye at the origin of eye space: -L^-1 t
            float tx = m[12], ty = m[13], tz = m[14];
            v.ex = -(inv[0] * tx + inv[1] * ty + inv[2] * tz);
            v.ey = -(inv[3] * tx + inv[4] * ty + inv[5] * tz);
            v.ez = -(inv[6] * tx + inv[7] * ty + inv[8] * tz);
        }
        return v;
    }

    /** Inverse of the 3x3 linear part of a column-major 4x4 matrix, row-major, or null. */
    static float[] invert3(float[] m) {
        float a = m[0], b = m[4], c = m[8];
        float d = m[1], e = m[5], f = m[9];
        float g = m[2], h = m[6], i = m[10];
        float A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        float det = a * A + b * B + c * C;
        if (Math.abs(det) < 1e-12f) return null;
        float k = 1f / det;
        return new float[]{
                A * k, -(b * i - c * h) * k, (b * f - c * e) * k,
                B * k, (a * i - c * g) * k, -(a * f - c * d) * k,
                C * k, -(a * h - b * g) * k, (a * e - b * d) * k};
    }

    private static final java.util.Map<net.minecraft.util.ResourceLocation, Boolean> LEGACY = new java.util.HashMap<net.minecraft.util.ResourceLocation, Boolean>();

    /** True for an old-format 64x32 skin (call with the skin bound). */
    private static boolean isLegacy(net.minecraft.util.ResourceLocation skin) {
        Boolean known = LEGACY.get(skin);
        if (known != null) return known;
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w < 64 || h <= 0) return false; // not uploaded yet (or a placeholder): ask again next frame
        boolean legacy = h * 2 == w;
        if (LEGACY.size() > 256) LEGACY.clear();
        LEGACY.put(skin, legacy);
        return legacy;
    }
}
