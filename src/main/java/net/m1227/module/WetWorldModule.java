package net.m1227.module;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

/**
 * Wet World: what rain looks like on your screen.
 * <ul>
 * <li>Rain: thin falling streaks that lean with the wind (or Minecraft's own rain).</li>
 * <li>Wet ground: open ground gets darker and picks up a soft sky sheen at low angles.</li>
 * <li>Puddles: shallow pools that grow while it rains and dry afterwards. They reflect the sky
 * (more when you look across them), catch the sun or moon, and have soft edges that span
 * several blocks.</li>
 * <li>Ripples and splashes where drops land.</li>
 * </ul>
 * Everything follows the rain you see (so the World module's weather counts) and only where rain
 * really falls: open sky, a biome with rain, a full solid block. Blocks are never changed.
 */
public final class WetWorldModule extends Module {
    private final Setting.Mode rainStyle;
    private final Setting.Num density, wind, rainLight;
    private final Setting.Bool wetGround, puddles, ripples, splashes;
    private final Setting.Num amount, reflect, rippleRate, radius;

    private float wet, preview;
    private float fogR = 0.6f, fogG = 0.7f, fogB = 0.85f;

    // ground cells around you, refreshed twice a second
    private int gx0, gz0, gn;
    private boolean[] valid = new boolean[0];
    private int[] topY = new int[0];
    /** Wet-ground quads: x, y, z per vertex, 4 vertices each. */
    private float[] gq = new float[0];
    private int gqn;
    /** Puddle quads: x, y, z, field, edge per vertex, 4 vertices each (quarter-block cells). */
    private float[] pq = new float[0];
    private int pqn;
    private int refresh;

    /** Drops that landed: x, y, z, age (0..1), in a puddle (1/0). */
    private final List<double[]> drops = new ArrayList<double[]>();
    private static final int SN = 320;
    private final float[] sx = new float[SN], sy = new float[SN], sz = new float[SN];
    private final float[] svx = new float[SN], svy = new float[SN], svz = new float[SN], sage = new float[SN], slife = new float[SN];
    private int sn;
    private long last;

    private final RainRenderer rainRenderer = new RainRenderer();
    private Method vanillaRain;
    private boolean vanillaRainFailed;

    public WetWorldModule() {
        super("Wet World", "Rain streaks, wet ground, puddles with sky reflections and ripples.", Category.AMBIENCE, false);
        rainStyle = add(new Setting.Mode("Rain", "1227 streaks", "1227 streaks", "Minecraft"));
        density = add(new Setting.Num("Rain density", 2.0, 0.5, 4.0, 0.25, "x")).showIf(() -> rainStyle.is("1227 streaks"));
        wind = add(new Setting.Num("Wind", 0.35, 0.0, 1.0, 0.05, "")).showIf(() -> rainStyle.is("1227 streaks"));
        rainLight = add(new Setting.Num("Rain brightness", 1.0, 0.3, 2.0, 0.05, "x")).showIf(() -> rainStyle.is("1227 streaks"));
        wetGround = add(new Setting.Bool("Wet ground", true));
        puddles = add(new Setting.Bool("Puddles", true));
        amount = add(new Setting.Num("Puddle amount", 0.25, 0.05, 0.5, 0.01, "")).showIf(() -> puddles.on());
        reflect = add(new Setting.Num("Reflections", 1.0, 0.0, 1.5, 0.05, "x"));
        ripples = add(new Setting.Bool("Rain ripples", true));
        rippleRate = add(new Setting.Num("Ripples per second", 70, 10, 200, 5, "")).showIf(() -> ripples.on());
        splashes = add(new Setting.Bool("Splashes", true));
        radius = add(new Setting.Num("Radius", 12, 6, 24, 1, "m"));
        add(new Setting.Action("Preview rain (20 s)", () -> {
            preview = 20f;
            wet = Math.max(wet, 0.6f);
            refresh = 0;
        }));
    }

    @Override
    protected void onDisable() {
        WorldClient w = Minecraft.getMinecraft().theWorld;
        if (w != null && w.provider.getWeatherRenderer() == rainRenderer) w.provider.setWeatherRenderer(null);
        drops.clear();
        sn = 0;
    }

    // ------------------------------------------------------------------ noise

    private static float hash(int x, int z, int salt) {
        int h = x * 73856093 ^ z * 19349663 ^ salt * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float vnoise(float x, float z, int salt) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(xi, zi, salt), b = hash(xi + 1, zi, salt), c = hash(xi, zi + 1, salt), d = hash(xi + 1, zi + 1, salt);
        return (a + (b - a) * fx) * (1 - fz) + (c + (d - c) * fx) * fz;
    }

    /** Puddle field: large soft blobs with some smaller detail on the edges. */
    static float field(float x, float z) {
        return 0.7f * vnoise(x * 0.23f, z * 0.23f, 5) + 0.3f * vnoise(x * 0.71f, z * 0.71f, 9);
    }

    private float baseThreshold() {
        return 0.5f + (0.5f - amount.f()) * 0.55f;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------ state

    private float rainNow(World w, float pt) {
        return Math.max(w.getRainStrength(pt), preview > 0f ? Math.min(1f, preview) : 0f);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = mc.theWorld;
        if (w == null || mc.thePlayer == null) return;
        boolean ours = rainStyle.is("1227 streaks");
        IRenderHandler current = w.provider.getWeatherRenderer();
        if (ours && current == null) w.provider.setWeatherRenderer(rainRenderer);
        else if (!ours && current == rainRenderer) w.provider.setWeatherRenderer(null);
        if (--refresh <= 0) {
            refresh = 10;
            scan(w, mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFog(EntityViewRenderEvent.FogColors e) {
        fogR = e.red;
        fogG = e.green;
        fogB = e.blue;
    }

    private boolean same(int x, int z, int y) {
        int i = x - gx0, k = z - gz0;
        if (i < 0 || k < 0 || i >= gn || k >= gn) return false;
        return valid[i * gn + k] && topY[i * gn + k] == y;
    }

    private void scan(World w, Entity p) {
        gqn = 0;
        pqn = 0;
        if (wet <= 0.01f && preview <= 0f) return;
        int r = radius.i(), px = (int) Math.floor(p.posX), pz = (int) Math.floor(p.posZ);
        gn = 2 * r + 1;
        gx0 = px - r;
        gz0 = pz - r;
        if (valid.length < gn * gn) {
            valid = new boolean[gn * gn];
            topY = new int[gn * gn];
        }
        for (int i = 0; i < gn; i++) {
            for (int k = 0; k < gn; k++) {
                int x = gx0 + i, z = gz0 + k, idx = i * gn + k;
                valid[idx] = false;
                if ((i - r) * (i - r) + (k - r) * (k - r) > r * r) continue;
                BlockPos top = w.getPrecipitationHeight(new BlockPos(x, 0, z));
                if (Math.abs(top.getY() - p.posY) > 8) continue;
                Block below = w.getBlockState(top.down()).getBlock();
                if (!below.isFullCube() || !below.getMaterial().isSolid()) continue;
                if (w.getBlockState(top).getBlock().getMaterial().blocksMovement()) continue;
                if (!w.getBiomeGenForCoords(top).canRain()) continue;
                valid[idx] = true;
                topY[idx] = top.getY();
            }
        }
        float tMin = baseThreshold() - 0.01f;
        for (int i = 0; i < gn; i++) {
            for (int k = 0; k < gn; k++) {
                int idx = i * gn + k;
                if (!valid[idx]) continue;
                int x = gx0 + i, z = gz0 + k, yy = topY[idx];
                float y = yy + 0.004f;
                if (wetGround.on()) {
                    gq = grow(gq, gqn + 12);
                    gqn = put3(gq, gqn, x, y, z);
                    gqn = put3(gq, gqn, x + 1, y, z);
                    gqn = put3(gq, gqn, x + 1, y, z + 1);
                    gqn = put3(gq, gqn, x, y, z + 1);
                }
                if (!puddles.on()) continue;
                boolean w0 = same(x - 1, z, yy), e0 = same(x + 1, z, yy), n0 = same(x, z - 1, yy), s0 = same(x, z + 1, yy);
                boolean nw = same(x - 1, z - 1, yy), ne = same(x + 1, z - 1, yy), sw = same(x - 1, z + 1, yy), se = same(x + 1, z + 1, yy);
                for (int a = 0; a < 4; a++) {
                    for (int b = 0; b < 4; b++) {
                        float u0 = a / 4f, u1 = (a + 1) / 4f, v0 = b / 4f, v1 = (b + 1) / 4f;
                        float f00 = field(x + u0, z + v0), f10 = field(x + u1, z + v0), f11 = field(x + u1, z + v1), f01 = field(x + u0, z + v1);
                        if (f00 < tMin && f10 < tMin && f11 < tMin && f01 < tMin) continue;
                        pq = grow(pq, pqn + 20);
                        pqn = put5(pq, pqn, x + u0, y, z + v0, f00, edge(u0, v0, w0, e0, n0, s0, nw, ne, sw, se));
                        pqn = put5(pq, pqn, x + u1, y, z + v0, f10, edge(u1, v0, w0, e0, n0, s0, nw, ne, sw, se));
                        pqn = put5(pq, pqn, x + u1, y, z + v1, f11, edge(u1, v1, w0, e0, n0, s0, nw, ne, sw, se));
                        pqn = put5(pq, pqn, x + u0, y, z + v1, f01, edge(u0, v1, w0, e0, n0, s0, nw, ne, sw, se));
                    }
                }
            }
        }
    }

    /** 0 on a cell border that touches a dry/higher/lower neighbour, so puddles end softly. */
    private static float edge(float u, float v, boolean w, boolean e, boolean n, boolean s, boolean nw, boolean ne, boolean sw, boolean se) {
        if (u == 0f && !w) return 0f;
        if (u == 1f && !e) return 0f;
        if (v == 0f && !n) return 0f;
        if (v == 1f && !s) return 0f;
        if (u == 0f && v == 0f && !nw) return 0f;
        if (u == 1f && v == 0f && !ne) return 0f;
        if (u == 0f && v == 1f && !sw) return 0f;
        if (u == 1f && v == 1f && !se) return 0f;
        return 1f;
    }

    private static float[] grow(float[] a, int need) {
        if (need <= a.length) return a;
        float[] b = new float[Math.max(need, a.length * 2 + 64)];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private static int put3(float[] a, int o, float x, float y, float z) {
        a[o] = x;
        a[o + 1] = y;
        a[o + 2] = z;
        return o + 3;
    }

    private static int put5(float[] a, int o, float x, float y, float z, float f, float e) {
        a[o] = x;
        a[o + 1] = y;
        a[o + 2] = z;
        a[o + 3] = f;
        a[o + 4] = e;
        return o + 5;
    }

    // ------------------------------------------------------------- ground pass

    private double ex, ey, ez;
    private float hr, hg, hb, zr, zg, zb, light, sunX, sunY, sunVis, moonVis, rad, wetGrow, thr;

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = mc.theWorld;
        Entity view = mc.getRenderViewEntity();
        if (w == null || view == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float pt = e.partialTicks;
        float rain = rainNow(w, pt);
        if (preview > 0f) preview -= dt;
        // puddles fill in about 20-30 s of rain and dry in about 40 s; a preview fills them quickly
        if (preview > 0f) wet = Math.min(1f, wet + dt * 0.5f);
        else if (rain > 0.2f) wet += (0.05f * (rain - wet) + 0.016f) * dt;
        else wet -= 0.024f * dt;
        wet = Math.max(0f, Math.min(1f, wet));
        if (wet <= 0.01f && rain <= 0.05f && drops.isEmpty() && sn == 0) return;

        ex = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        ey = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt + view.getEyeHeight();
        ez = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        double cx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double cy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double cz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;

        hr = fogR;
        hg = fogG;
        hb = fogB;
        int zc = SkyModule.zenithColor();
        if (zc >= 0) {
            zr = ((zc >> 16) & 255) / 255f;
            zg = ((zc >> 8) & 255) / 255f;
            zb = (zc & 255) / 255f;
        } else {
            Vec3 sky = w.getSkyColor(view, pt);
            zr = (float) sky.xCoord;
            zg = (float) sky.yCoord;
            zb = (float) sky.zCoord;
        }
        light = Math.max(0.3f, w.getSunBrightness(pt));
        double ang = w.getCelestialAngle(pt) * Math.PI * 2;
        sunX = (float) -Math.sin(ang);
        sunY = (float) Math.cos(ang);
        sunVis = Math.max(0f, Math.min(1f, sunY * 4f + 0.3f)) * (1f - 0.55f * rain);
        moonVis = Math.max(0f, Math.min(1f, -sunY * 4f + 0.3f)) * (1f - 0.55f * rain) * 0.45f;
        rad = radius.f();
        wetGrow = Math.min(1f, wet * 1.4f);
        thr = baseThreshold() + (1f - wetGrow) * 0.15f;
        float refl = reflect.f();

        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-cx, -cy, -cz);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1f, -10f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        if (wet > 0.01f) {
            // 1. darker ground and darker puddle beds (multiply what is already there)
            GlStateManager.blendFunc(GL11.GL_ZERO, GL11.GL_SRC_COLOR);
            GL11.glBegin(GL11.GL_QUADS);
            if (wetGround.on()) {
                for (int o = 0; o < gqn; o += 3) {
                    float k = 1f - 0.24f * wet * fade(gq[o], gq[o + 2]);
                    GL11.glColor4f(k, k, k, 1f);
                    GL11.glVertex3f(gq[o], gq[o + 1], gq[o + 2]);
                }
            }
            if (puddles.on()) {
                for (int o = 0; o < pqn; o += 5) {
                    float k = 1f - 0.42f * mask(o) * fade(pq[o], pq[o + 2]);
                    GL11.glColor4f(k, k, k, 1f);
                    GL11.glVertex3f(pq[o], pq[o + 1], pq[o + 2]);
                }
            }
            GL11.glEnd();

            // 2. sky reflection: weak sheen on wet ground, a real mirror on puddles
            if (refl > 0f) {
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glBegin(GL11.GL_QUADS);
                if (wetGround.on()) {
                    for (int o = 0; o < gqn; o += 3) {
                        float c = reflColor(gq[o], gq[o + 1], gq[o + 2]);
                        GL11.glColor4f(cr, cg, cb, Math.min(1f, wet * fade(gq[o], gq[o + 2]) * (0.02f + 0.22f * c) * refl));
                        GL11.glVertex3f(gq[o], gq[o + 1], gq[o + 2]);
                    }
                }
                if (puddles.on()) {
                    for (int o = 0; o < pqn; o += 5) {
                        float c = reflColor(pq[o], pq[o + 1], pq[o + 2]);
                        GL11.glColor4f(cr, cg, cb, Math.min(1f, mask(o) * fade(pq[o], pq[o + 2]) * (0.22f + 0.7f * c) * refl));
                        GL11.glVertex3f(pq[o], pq[o + 1], pq[o + 2]);
                    }
                }
                GL11.glEnd();
            }

            // 3. sun / moon glint on the puddles
            if (puddles.on() && refl > 0f && (sunVis > 0f || moonVis > 0f)) {
                GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                GL11.glBegin(GL11.GL_QUADS);
                for (int o = 0; o < pqn; o += 5) {
                    float s = glint(pq[o], pq[o + 1], pq[o + 2]);
                    GL11.glColor4f(1f, 0.95f, 0.85f, Math.min(1f, s * mask(o) * fade(pq[o], pq[o + 2]) * refl));
                    GL11.glVertex3f(pq[o], pq[o + 1], pq[o + 2]);
                }
                GL11.glEnd();
            }
        }

        // 4. ripples and splashes
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (rain > 0.05f && (ripples.on() || splashes.on())) spawnDrops(w, view, rain, dt);
        float lr = Math.min(1f, 0.55f + 0.4f * light), lg = Math.min(1f, 0.6f + 0.4f * light), lb = Math.min(1f, 0.68f + 0.4f * light);
        for (int i = drops.size() - 1; i >= 0; i--) {
            double[] d = drops.get(i);
            d[3] += dt / (d[4] > 0 ? 0.7 : 0.45);
            if (d[3] >= 1) {
                drops.remove(i);
                continue;
            }
            if (!ripples.on()) continue;
            float k = (float) d[3];
            float ease = 1f - (1f - k) * (1f - k);
            float a = (1f - k) * (d[4] > 0 ? 0.6f : 0.28f) * Math.max(0.3f, rain);
            float r = (d[4] > 0 ? 0.36f : 0.16f) * ease + 0.02f;
            ring(d[0], d[1], d[2], r, 0.018f, lr, lg, lb, a);
            if (d[4] > 0 && k > 0.15f) ring(d[0], d[1], d[2], r * 0.55f, 0.012f, lr, lg, lb, a * 0.6f);
        }
        if (sn > 0) {
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);
            for (int i = 0; i < sn; ) {
                sage[i] += dt;
                if (sage[i] >= slife[i]) {
                    int l = --sn;
                    sx[i] = sx[l]; sy[i] = sy[l]; sz[i] = sz[l];
                    svx[i] = svx[l]; svy[i] = svy[l]; svz[i] = svz[l];
                    sage[i] = sage[l]; slife[i] = slife[l];
                    continue;
                }
                svy[i] -= 9f * dt;
                sx[i] += svx[i] * dt;
                sy[i] += svy[i] * dt;
                sz[i] += svz[i] * dt;
                float a = 0.55f * (1f - sage[i] / slife[i]);
                GL11.glColor4f(lr, lg, lb, a);
                GL11.glVertex3f(sx[i], sy[i], sz[i]);
                GL11.glColor4f(lr, lg, lb, 0f);
                GL11.glVertex3f(sx[i] - svx[i] * 0.035f, sy[i] - svy[i] * 0.035f, sz[i] - svz[i] * 0.035f);
                i++;
            }
            GL11.glEnd();
            GL11.glLineWidth(1f);
        }

        GlStateManager.doPolygonOffset(0f, 0f);
        GlStateManager.disablePolygonOffset();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        if (fog) GlStateManager.enableFog();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** Puddle coverage at a stored vertex (grows with wetness, soft edges). */
    private float mask(int o) {
        return smooth(thr, thr + 0.045f, pq[o + 3]) * pq[o + 4] * wetGrow;
    }

    /** Fades everything out towards the edge of the radius so there is no hard border. */
    private float fade(float x, float z) {
        double dx = x - ex, dz = z - ez;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        return 1f - smooth(rad * 0.65f, rad, d);
    }

    private float cr, cg, cb;

    /**
     * Sky colour seen in the ground at this point (into cr/cg/cb): horizon colour when you look
     * across, zenith colour when you look down. Returns the Fresnel factor (0 looking straight
     * down, 1 at grazing angles).
     */
    private float reflColor(float x, float y, float z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float c = len < 1e-4 ? 1f : (float) Math.max(0, Math.min(1, -dy / len));
        float t = (float) Math.pow(c, 0.6);
        cr = hr + (zr - hr) * t;
        cg = hg + (zg - hg) * t;
        cb = hb + (zb - hb) * t;
        float f = 1f - c;
        return 0.03f + 0.97f * f * f * f * f * f;
    }

    private float glint(float x, float y, float z) {
        double dx = x - ex, dy = y - ey, dz = z - ez;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4) return 0f;
        float rx = (float) (dx / len), ry = (float) (-dy / len);
        float s = 0f;
        float ds = rx * sunX + ry * sunY; // the sun path is in the x/y plane
        if (ds > 0f && sunVis > 0f) s += (float) Math.pow(ds, 48) * sunVis * 1.4f;
        float dm = -(rx * sunX + ry * sunY);
        if (dm > 0f && moonVis > 0f) s += (float) Math.pow(dm, 64) * moonVis;
        return s;
    }

    private void spawnDrops(World w, Entity view, float rain, float dt) {
        float want = rippleRate.f() * rain * dt;
        int n = (int) want + (Math.random() < want - (int) want ? 1 : 0);
        int r = Math.min(10, radius.i());
        for (int i = 0; i < n && drops.size() < 500; i++) {
            double x = 0, z = 0;
            boolean inPuddle = false;
            // a good share of drops land in puddles so they visibly ripple
            if (pqn > 0 && Math.random() < 0.55) {
                for (int tries = 0; tries < 4 && !inPuddle; tries++) {
                    int q = (int) (Math.random() * (pqn / 20)) * 20;
                    if (mask(q) > 0.4f && mask(q + 10) > 0.4f) {
                        x = pq[q] + Math.random() * 0.25;
                        z = pq[q + 2] + Math.random() * 0.25;
                        inPuddle = true;
                    }
                }
            }
            if (!inPuddle) {
                x = view.posX + (Math.random() * 2 - 1) * r;
                z = view.posZ + (Math.random() * 2 - 1) * r;
            }
            BlockPos top = w.getPrecipitationHeight(new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - view.posY) > 8) continue;
            if (!w.getBiomeGenForCoords(top).canRain()) continue;
            Block below = w.getBlockState(top.down()).getBlock();
            boolean liquid = below.getMaterial().isLiquid();
            if (!below.getMaterial().isSolid() && !liquid) continue;
            double y = liquid ? top.getY() - 0.1 : top.getY() + 0.012;
            drops.add(new double[]{x, y, z, 0, inPuddle || liquid ? 1 : 0});
            if (splashes.on() && sn < SN - 3 && !liquid) {
                int k = inPuddle ? 3 : 2;
                for (int j = 0; j < k && sn < SN; j++) {
                    double an = Math.random() * Math.PI * 2;
                    float sp = 0.35f + (float) Math.random() * 0.45f;
                    sx[sn] = (float) x;
                    sy[sn] = (float) y;
                    sz[sn] = (float) z;
                    svx[sn] = (float) Math.cos(an) * sp;
                    svz[sn] = (float) Math.sin(an) * sp;
                    svy[sn] = 1.1f + (float) Math.random() * 0.9f;
                    sage[sn] = 0f;
                    slife[sn] = 0.22f + (float) Math.random() * 0.14f;
                    sn++;
                }
            }
        }
    }

    private static void ring(double x, double y, double z, float rad, float w, float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= 24; i++) {
            double an = Math.PI * 2 * i / 24;
            double co = Math.cos(an), si = Math.sin(an);
            GL11.glColor4f(r, g, b, 0f);
            GL11.glVertex3d(x + co * (rad - w * 2), y, z + si * (rad - w * 2));
            GL11.glColor4f(r, g, b, a);
            GL11.glVertex3d(x + co * (rad + w), y, z + si * (rad + w));
        }
        GL11.glEnd();
    }

    // ------------------------------------------------------------------ rain

    /** Replaces Minecraft's rain sheets with thin streaks; snow and cold biomes stay vanilla. */
    private final class RainRenderer extends IRenderHandler {
        @Override
        public void render(float pt, WorldClient world, Minecraft mc) {
            Entity view = mc.getRenderViewEntity();
            if (view == null) return;
            double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
            double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
            double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
            BlockPos here = new BlockPos(vx, vy, vz);
            BiomeGenBase b = world.getBiomeGenForCoords(here);
            if (b.getEnableSnow() || b.getFloatTemperature(here) < 0.15f) {
                vanilla(pt, world, mc);
                return;
            }
            float rain = rainNow(world, pt);
            if (rain <= 0.01f) return;
            streaks(pt, world, mc, vx, vy, vz, vy + view.getEyeHeight(), rain);
        }
    }

    /** Draws Minecraft's own rain/snow for this frame (used in snowy places). */
    private void vanilla(float pt, WorldClient world, Minecraft mc) {
        if (vanillaRainFailed) return;
        try {
            if (vanillaRain == null) {
                vanillaRain = ReflectionHelper.findMethod(EntityRenderer.class, mc.entityRenderer,
                        new String[]{"renderRainSnow", "func_78474_d"}, float.class);
            }
            world.provider.setWeatherRenderer(null);
            vanillaRain.invoke(mc.entityRenderer, pt);
        } catch (Throwable t) {
            vanillaRainFailed = true;
            Mod1227.LOGGER.warn("1227: could not draw Minecraft's snow; snow stays hidden while Wet World draws rain", t);
        } finally {
            world.provider.setWeatherRenderer(rainRenderer);
        }
    }

    private void streaks(float pt, WorldClient world, Minecraft mc, double ex, double feetY, double ez, double ey, float rain) {
        int R = mc.gameSettings.fancyGraphics ? 11 : 7;
        float t = (float) ((world.getTotalWorldTime() % 1000000L) + pt) / 20f;
        float windA = t * 0.03f;
        float wnd = wind.f() * 0.55f;
        float wx = (float) Math.cos(windA) * wnd, wz = (float) Math.sin(windA) * wnd;
        float bright = (0.35f + 0.65f * world.getSunBrightness(pt)) * rainLight.f();
        float cr = Math.min(1f, 0.70f * bright), cg = Math.min(1f, 0.77f * bright), cb = Math.min(1f, 0.90f * bright);
        float dens = density.f();
        int ix = (int) Math.floor(ex), iz = (int) Math.floor(ez);
        float lineW = Math.max(1f, Math.min(2.5f, mc.displayHeight / 600f));

        GlStateManager.pushMatrix();
        GlStateManager.translate(-ex, -feetY, -ez); // the world is drawn relative to your feet
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GL11.glLineWidth(lineW);
        GL11.glBegin(GL11.GL_LINES);
        for (int x = ix - R; x <= ix + R; x++) {
            for (int z = iz - R; z <= iz + R; z++) {
                double dcx = x + 0.5 - ex, dcz = z + 0.5 - ez;
                if (dcx * dcx + dcz * dcz > R * R) continue;
                BlockPos col = new BlockPos(x, 0, z);
                BiomeGenBase bio = world.getBiomeGenForCoords(col);
                if (!bio.canRain()) continue;
                double y0 = Math.max(world.getPrecipitationHeight(col).getY(), ey - 12);
                double y1 = ey + 14;
                if (y0 >= y1) continue;
                int count = (int) dens + (hash(x, z, 3) < dens - (int) dens ? 1 : 0);
                for (int s = 0; s < count; s++) {
                    float hx = hash(x, z, 11 + s), hz = hash(x, z, 23 + s), hp = hash(x, z, 37 + s), hv = hash(x, z, 51 + s);
                    double px = x + hx, pz = z + hz;
                    double hdx = px - ex, hdz = pz - ez;
                    float hd = (float) Math.sqrt(hdx * hdx + hdz * hdz);
                    if (hd < 0.45f) continue; // would cross the whole screen
                    float near = Math.min(1f, (hd - 0.45f) / 1.5f);
                    float far = 1f - hd / (R + 0.5f);
                    if (far <= 0f) continue;
                    float speed = 12f + 6f * hv, period = 6f + 2f * hp;
                    float len = (0.5f + 0.4f * hv) * speed / 15f;
                    float off = (t * speed + hp * 37f) % period;
                    for (double top = y1 - off; top > y0; top -= period) {
                        double bot = Math.max(y0, top - len);
                        float vd = (float) Math.abs((top + bot) * 0.5 - ey);
                        float a = rain * 0.5f * near * far * Math.max(0f, 1f - vd / 14f);
                        if (a <= 0.01f) continue;
                        GL11.glColor4f(cr, cg, cb, 0f);
                        GL11.glVertex3d(px + wx * (top - ey), top, pz + wz * (top - ey));
                        GL11.glColor4f(cr, cg, cb, Math.min(1f, a));
                        GL11.glVertex3d(px + wx * (bot - ey), bot, pz + wz * (bot - ey));
                    }
                }
            }
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }
}
