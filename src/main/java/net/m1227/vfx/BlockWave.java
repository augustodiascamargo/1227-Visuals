package net.m1227.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Block Wave (a jump / landing effect): the top blocks of the ground pop up as translucent
 * copies, tinted by each block's map colour, travelling outwards like a ripple. Only drawn;
 * the world is never changed.
 */
public final class BlockWave {
    public static final BlockWave INSTANCE = new BlockWave();

    private BlockWave() {}


    private static final class Wave {
        final double x, z;
        final int y0;
        final float radius, life;
        final int color;
        final float alpha;
        final long start = System.nanoTime();
        final java.util.Map<Long, int[]> tops = new java.util.HashMap<Long, int[]>(); // key -> {topY, rgb}

        Wave(double x, double y, double z, float radius, float life, int color, float alpha) {
            this.x = x;
            this.z = z;
            this.y0 = (int) Math.floor(y);
            this.radius = radius;
            this.life = life;
            this.color = color;
            this.alpha = alpha;
        }
    }

    private final java.util.List<Wave> waves = new java.util.ArrayList<Wave>();

    /** Starts a wave at (x, feet y, z) reaching radius r over life seconds. */
    public void spawn(double x, double y, double z, float r, float life, int color, float alpha) {
        if (net.m1227.vfx.PreviewStage.rendering) return; // no real ground in the menu preview
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return;
        Wave w = new Wave(x, y, z, Math.min(r, 10f), Math.max(0.4f, life), color, alpha);
        int R = (int) Math.ceil(w.radius) + 1;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int bx = (int) Math.floor(x) + dx, bz = (int) Math.floor(z) + dz;
                double d = Math.hypot(bx + 0.5 - x, bz + 0.5 - z);
                if (d > w.radius + 0.6) continue;
                for (int by = w.y0 + 1; by >= w.y0 - 4; by--) {
                    net.minecraft.util.BlockPos pos = new net.minecraft.util.BlockPos(bx, by, bz);
                    net.minecraft.block.state.IBlockState stt = mc.theWorld.getBlockState(pos);
                    net.minecraft.block.Block b = stt.getBlock();
                    if (b.getMaterial() == net.minecraft.block.material.Material.air || !b.isFullCube()) continue;
                    if (!mc.theWorld.isAirBlock(pos.up())) break;
                    int rgb = b.getMapColor(stt).colorValue;
                    w.tops.put(((long) bx << 32) ^ (bz & 0xFFFFFFFFL), new int[]{by, rgb, bx, bz});
                    break;
                }
            }
        }
        if (!w.tops.isEmpty()) waves.add(w);
        while (waves.size() > 6) waves.remove(0);
    }

    @SubscribeEvent
    public void onWorldRender(net.minecraftforge.client.event.RenderWorldLastEvent e) {
        if (waves.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        long now = System.nanoTime();
        java.util.Iterator<Wave> it = waves.iterator();
        GL11.glBegin(GL11.GL_QUADS);
        while (it.hasNext()) {
            Wave w = it.next();
            float t = (now - w.start) / 1e9f / w.life;
            if (t >= 1f) {
                it.remove();
                continue;
            }
            float front = w.radius * (1f - (1f - t) * (1f - t)); // eases out
            float fade = 1f - t * t;
            for (int[] c : w.tops.values()) {
                double d = Math.hypot(c[2] + 0.5 - w.x, c[3] + 0.5 - w.z);
                float band = (float) Math.abs(d - front);
                if (band > 1.4f) continue;
                float k = 1f - band / 1.4f;
                float lift = 0.55f * k * k * (3f - 2f * k) * fade;
                float a = w.alpha * 0.55f * k * fade;
                if (a < 0.02f) continue;
                int col = net.m1227.vfx.ParticleSystem.mix(c[1], w.color, 0.55f);
                double x0 = c[2] - vx, z0 = c[3] - vz, y1 = c[0] + 1 + lift + 0.01 - vy;
                cube(x0 - 0.02, y1 - 1.0, z0 - 0.02, x0 + 1.02, y1, z0 + 1.02, col, a);
            }
        }
        GL11.glEnd();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void cube(double x0, double y0, double z0, double x1, double y1, double z1, int c, float a) {
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        // top (brightest)
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x0, y1, z1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y1, z0);
        GL11.glColor4f(r * 0.8f, g * 0.8f, b * 0.8f, a);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x0, y1, z1);
        GL11.glVertex3d(x0, y0, z1);
        GL11.glColor4f(r * 0.65f, g * 0.65f, b * 0.65f, a);
        GL11.glVertex3d(x0, y0, z1);
        GL11.glVertex3d(x0, y1, z1);
        GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x1, y0, z1);
    }
}
