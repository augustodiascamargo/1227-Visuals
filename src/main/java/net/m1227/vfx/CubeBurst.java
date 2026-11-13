package net.m1227.vfx;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Small solid cubes thrown out from a point, falling, bouncing on the ground and fading (the
 * "Pixel Burst" kill effect). Simulated per frame on your screen only.
 */
public final class CubeBurst {
    public static final CubeBurst INSTANCE = new CubeBurst();
    private final List<float[]> cubes = new ArrayList<float[]>();
    private double[] origin;
    private long last;

    private CubeBurst() {}

    /** Spawns n cubes at (x, y, z) mixing two colours. */
    public void spawn(double x, double y, double z, int n, float size, float speed, int c0, int c1) {
        if (origin == null || cubes.isEmpty()) origin = new double[]{x, y, z};
        for (int i = 0; i < n; i++) {
            double a = Math.random() * Math.PI * 2;
            float h = (float) Math.random();
            float sp = speed * (1.5f + (float) Math.random() * 2.5f);
            int c = ParticleSystem.mix(c0, c1, (float) Math.random());
            // x, y, z (relative to origin), vx, vy, vz, size, colour, age, life, spin
            cubes.add(new float[]{(float) (x - origin[0]), (float) (y - origin[1] + h * 1.2f), (float) (z - origin[2]),
                    (float) Math.cos(a) * sp * (0.4f + h), 2f + (float) Math.random() * 3.5f * speed, (float) Math.sin(a) * sp * (0.4f + h),
                    size * (0.05f + (float) Math.random() * 0.07f), Float.intBitsToFloat(c), 0f, 1.6f + (float) Math.random() * 0.8f,
                    (float) Math.random() * 360f});
        }
        if (cubes.size() > 900) cubes.subList(0, cubes.size() - 900).clear();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (cubes.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            cubes.clear();
            return;
        }
        RenderManager rm = mc.getRenderManager();
        GlStateManager.pushMatrix();
        GlStateManager.translate(origin[0] - rm.viewerPosX, origin[1] - rm.viewerPosY, origin[2] - rm.viewerPosZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = cubes.size() - 1; i >= 0; i--) {
            float[] c = cubes.get(i);
            c[8] += dt;
            if (c[8] >= c[9]) {
                cubes.remove(i);
                continue;
            }
            c[4] -= 14f * dt;
            float nx = c[0] + c[3] * dt, ny = c[1] + c[4] * dt, nz = c[2] + c[5] * dt;
            // bounce on solid ground
            BlockPos below = new BlockPos(origin[0] + nx, origin[1] + ny - c[6], origin[2] + nz);
            if (c[4] < 0 && mc.theWorld.getBlockState(below).getBlock().getMaterial().isSolid()) {
                c[4] = -c[4] * 0.35f;
                c[3] *= 0.6f;
                c[5] *= 0.6f;
                ny = c[1];
            }
            c[0] = nx;
            c[1] = ny;
            c[2] = nz;
            c[10] += dt * 200f * (Math.abs(c[3]) + Math.abs(c[5]));
            float a = Math.min(1f, (c[9] - c[8]) / 0.4f);
            cube(c[0], c[1], c[2], c[6], c[10], Float.floatToIntBits(c[7]), a);
        }
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void cube(float x, float y, float z, float s, float spin, int c, float a) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        double an = Math.toRadians(spin);
        float co = (float) Math.cos(an) * s, si = (float) Math.sin(an) * s;
        float[][] v = new float[8][];
        for (int k = 0; k < 8; k++) {
            float lx = (k & 1) == 0 ? -1 : 1, ly = (k & 2) == 0 ? -s : s, lz = (k & 4) == 0 ? -1 : 1;
            v[k] = new float[]{x + lx * co - lz * si, y + ly, z + lx * si + lz * co};
        }
        int[][] faces = {{2, 3, 7, 6}, {0, 1, 5, 4}, {0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 3, 2}, {4, 5, 7, 6}};
        float[] shade = {1f, 0.5f, 0.75f, 0.75f, 0.85f, 0.85f};
        for (int f = 0; f < 6; f++) {
            GL11.glColor4f(r * shade[f], g * shade[f], b * shade[f], a);
            for (int k : faces[f]) GL11.glVertex3f(v[k][0], v[k][1], v[k][2]);
        }
    }
}
