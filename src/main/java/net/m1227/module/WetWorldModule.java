package net.m1227.module;

import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * Wet World: when it rains, puddles slowly form on open ground (and dry out after the rain), they
 * show a fake reflection of the sky that gets stronger at grazing angles, and raindrops make
 * expanding ripples on the ground and in the puddles. Only on your screen; blocks are untouched.
 * Puddles only appear where rain really falls (open sky, a biome with rain, a full solid block).
 */
public final class WetWorldModule extends Module {
    private final Setting.Bool ripples, puddles;
    private final Setting.Num amount, radius, rippleRate;

    private float wet;
    /** Cached puddle spots: x, y, z, size seed. Refreshed twice a second. */
    private final List<double[]> spots = new ArrayList<double[]>();
    private final List<double[]> drops = new ArrayList<double[]>();
    private int refresh;
    private long last;

    public WetWorldModule() {
        super("Wet World", "Rain makes puddles with sky reflections and ripples on the ground.", Category.AMBIENCE, false);
        puddles = add(new Setting.Bool("Puddles", true));
        amount = add(new Setting.Num("Puddle amount", 0.18, 0.04, 0.45, 0.01, "")).showIf(() -> puddles.on());
        ripples = add(new Setting.Bool("Rain ripples", true));
        rippleRate = add(new Setting.Num("Ripples per second", 60, 10, 200, 5, "")).showIf(() -> ripples.on());
        radius = add(new Setting.Num("Radius", 12, 6, 24, 1, "m"));
    }

    private static float hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        float rain = mc.theWorld.getRainStrength(1f);
        // puddles fill in about 20 s of rain and dry in about 40 s
        wet += rain > 0.2f ? (rain - wet) * 0.0025f + 0.0008f : -0.0012f;
        wet = Math.max(0f, Math.min(1f, wet));
        if (--refresh <= 0) {
            refresh = 10;
            scan(mc.theWorld, mc.thePlayer);
        }
    }

    private void scan(World w, Entity p) {
        spots.clear();
        if (!puddles.on() || wet <= 0.01f) return;
        int r = radius.i(), px = (int) Math.floor(p.posX), pz = (int) Math.floor(p.posZ);
        float density = amount.f();
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                if (hash(x, z, 1) > density) continue;
                BlockPos top = w.getPrecipitationHeight(new BlockPos(x, 0, z));
                if (Math.abs(top.getY() - p.posY) > 8) continue;
                Block below = w.getBlockState(top.down()).getBlock();
                if (!below.isFullCube() || !below.getMaterial().isSolid() || !w.getBiomeGenForCoords(top).canRain()) continue;
                if (!w.isAirBlock(top)) continue;
                spots.add(new double[]{x + 0.5, top.getY() + 0.015, z + 0.5, hash(x, z, 2)});
            }
        }
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float rain = mc.theWorld.getRainStrength(e.partialTicks);
        if (wet <= 0.01f && rain <= 0.05f && drops.isEmpty()) return;
        RenderManager rm = mc.getRenderManager();
        Entity view = mc.getRenderViewEntity();
        Vec3 sky = mc.theWorld.getSkyColor(view, e.partialTicks);
        Vec3 look = view.getLook(e.partialTicks);
        float graze = (float) (1.0 - Math.abs(look.yCoord)); // reflections show more at shallow angles

        GlStateManager.pushMatrix();
        GlStateManager.translate(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1f, -10f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        float light = Math.max(0.35f, mc.theWorld.getSunBrightness(e.partialTicks));

        if (puddles.on() && wet > 0.01f) {
            for (double[] s : spots) {
                float size = (0.22f + 0.28f * (float) s[3]) * Math.min(1f, wet * 1.6f);
                // dark wet base
                blob(s, size, 0.06f * light, 0.08f * light, 0.11f * light, 0.45f * wet, 0f);
                // sky reflection, a slightly smaller layer
                blob(s, size * 0.86f, (float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord, (0.12f + 0.3f * graze) * wet, 0f);
                // a soft highlight that slides as you look around
                blob(s, size * 0.35f, 1f, 1f, 1f, 0.12f * graze * wet * light, (float) Math.atan2(look.zCoord, look.xCoord));
            }
        }
        if (ripples.on() && rain > 0.05f) {
            spawnDrops(mc.theWorld, view, rain, dt);
        }
        for (int i = drops.size() - 1; i >= 0; i--) {
            double[] d = drops.get(i);
            d[3] += dt / 0.55;
            if (d[3] >= 1) {
                drops.remove(i);
                continue;
            }
            float k = (float) d[3];
            float r = 0.04f + 0.32f * k;
            ring(d[0], d[1], d[2], r, 0.025f, 0.82f * light, 0.88f * light, 0.95f * light, (1f - k) * 0.55f * rain);
        }
        GlStateManager.doPolygonOffset(0f, 0f);
        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private void spawnDrops(World w, Entity view, float rain, float dt) {
        float want = rippleRate.f() * rain * dt;
        int n = (int) want + (Math.random() < want - (int) want ? 1 : 0);
        int r = Math.min(10, radius.i());
        for (int i = 0; i < n && drops.size() < 400; i++) {
            double x, z;
            // half of the drops land in puddles so they visibly ripple
            if (!spots.isEmpty() && Math.random() < 0.5) {
                double[] s = spots.get((int) (Math.random() * spots.size()));
                x = s[0] + (Math.random() - 0.5) * 0.5;
                z = s[2] + (Math.random() - 0.5) * 0.5;
            } else {
                x = view.posX + (Math.random() * 2 - 1) * r;
                z = view.posZ + (Math.random() * 2 - 1) * r;
            }
            BlockPos top = w.getPrecipitationHeight(new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - view.posY) > 8 || !w.isRainingAt(top)) continue;
            Block below = w.getBlockState(top.down()).getBlock();
            if (!below.getMaterial().isSolid() && !below.getMaterial().isLiquid()) continue;
            double y = below.getMaterial().isLiquid() ? top.getY() - 0.1 : top.getY() + 0.02;
            drops.add(new double[]{x, y, z, 0});
        }
    }

    /** Irregular round blob (a puddle layer). spin turns the shape (for the moving highlight). */
    private static void blob(double[] s, float size, float r, float g, float b, float a, float spin) {
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(s[0], s[1], s[2]);
        int n = 14;
        for (int i = 0; i <= n; i++) {
            double an = Math.PI * 2 * i / n + spin;
            float wob = 0.75f + 0.25f * (float) Math.sin(an * 3 + s[3] * 20) + 0.12f * (float) Math.sin(an * 5 + s[3] * 7);
            GL11.glColor4f(r, g, b, a * 0.55f);
            GL11.glVertex3d(s[0] + Math.cos(an) * size * wob, s[1], s[2] + Math.sin(an) * size * wob);
        }
        GL11.glEnd();
    }

    private static void ring(double x, double y, double z, float rad, float w, float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= 20; i++) {
            double an = Math.PI * 2 * i / 20;
            double co = Math.cos(an), si = Math.sin(an);
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex3d(x + co * (rad - w), y, z + si * (rad - w));
            GL11.glVertex3d(x + co * (rad + w), y, z + si * (rad + w));
        }
        GL11.glEnd();
    }
}
