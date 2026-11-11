package net.m1227.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Client-side "bodies" for kill effects: the defeated player's model rebuilt
 * from six skinned boxes (head, body, arms, legs) as rigid bodies with
 * gravity, spin, ground bounce and friction - a ragdoll, a decapitation, a
 * shatter - plus the Executioner, an original hammer-wielding obsidian giant
 * that rises from the ground and finishes the target. Everything here exists
 * only on your screen; the real (dead) entity is just hidden while its body
 * is shown, nothing is sent to the server.
 */
public final class BodyFx {
    public static BodyFx INSTANCE = new BodyFx(false);
    private static final float PX = 0.9375f / 16f;
    /** Entity ids whose vanilla model is hidden (their body is shown by us). Value = until (ms). */
    private static final Map<Integer, Long> HIDDEN = new HashMap<Integer, Long>();

    private final List<Group> groups = new ArrayList<Group>();
    private final List<Executioner> actors = new ArrayList<Executioner>();
    private final List<Sahur> sahurs = new ArrayList<Sahur>();
    private final boolean stage;
    /** Preview stages: the stand-in model is hidden while a body replaces it. */
    public boolean standInHidden;

    private BodyFx(boolean stage) {
        this.stage = stage;
    }

    public static BodyFx isolated() {
        return new BodyFx(true);
    }

    public boolean active() {
        return !groups.isEmpty() || !actors.isEmpty() || !sahurs.isEmpty();
    }

    public void clear() {
        groups.clear();
        actors.clear();
        sahurs.clear();
        standInHidden = false;
    }

    // ------------------------------------------------------------ model

    /** One skinned box. Local coordinates are y-up, front = -z, the body's right = +x. */
    static final class Box {
        float cx, cy, cz, hx, hy, hz;
        int u, v, w, h, d; // skin texture rect (pixels) for a w x h x d box
        int color = -1;    // untextured colour (actors / mobs)
        boolean glow;      // emissive (drawn additively)

        Box(float cx, float cy, float cz, int w, int h, int d, int u, int v, float inflate) {
            this.cx = cx * PX;
            this.cy = cy * PX;
            this.cz = cz * PX;
            this.hx = (w / 2f + inflate) * PX;
            this.hy = (h / 2f + inflate) * PX;
            this.hz = (d / 2f + inflate) * PX;
            this.u = u;
            this.v = v;
            this.w = w;
            this.h = h;
            this.d = d;
        }
    }

    /** A rigid body: several boxes moving together. */
    static final Group NEUTRAL = new Group();

    static final class Group {
        double x, y, z, vx, vy, vz;
        final float[] r = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        float wx, wy, wz;
        final List<Box> boxes = new ArrayList<Box>();
        ResourceLocation skin;
        float age, life = 2.2f, groundY, tintR = 1f, tintG = 1f, tintB = 1f, scale = 1f, squash = 1f;
        int dissolve0 = 0xFFFFFF, dissolve1 = 0xFFFFFF;
        boolean dissolved;
        /** Held in place (standing) until released - used while the Sahur is hitting it. */
        boolean pinned;
    }

    /** The six parts of a player, each as its own list so they can be grouped freely. */
    private static Box[] parts(boolean skinned) {
        Box head = new Box(0, 28, 0, 8, 8, 8, 0, 0, 0);
        Box hat = new Box(0, 28, 0, 8, 8, 8, 32, 0, 0.5f);
        Box body = new Box(0, 18, 0, 8, 12, 4, 16, 16, 0);
        Box rArm = new Box(6, 18, 0, 4, 12, 4, 40, 16, 0);
        Box lArm = new Box(-6, 18, 0, 4, 12, 4, 32, 48, 0);
        Box rLeg = new Box(2, 6, 0, 4, 12, 4, 0, 16, 0);
        Box lLeg = new Box(-2, 6, 0, 4, 12, 4, 16, 48, 0);
        Box[] all = {head, hat, body, rArm, lArm, rLeg, lLeg};
        if (!skinned) for (Box b : all) b.color = b == hat ? 0x00000000 : 0x6B6F7A;
        return all;
    }

    private static float[] rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        return new float[]{c, 0, s, 0, 1, 0, -s, 0, c};
    }

    private static ResourceLocation skinOf(EntityLivingBase e) {
        return e instanceof AbstractClientPlayer ? ((AbstractClientPlayer) e).getLocationSkin() : null;
    }

    private float ground(double x, double y, double z) {
        if (stage) return 0f;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) return (float) y;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int by = (int) Math.floor(y + 0.5); by > y - 8; by--) {
            BlockPos p = new BlockPos(bx, by, bz);
            if (mc.theWorld.getBlockState(p).getBlock().getMaterial().blocksMovement()) {
                return by + 1f;
            }
        }
        return (float) y - 8f;
    }

    // ---------------------------------------------------------- spawning

    public static void hide(EntityLivingBase e, long ms) {
        if (e != null) HIDDEN.put(e.getEntityId(), System.currentTimeMillis() + ms);
    }

    private Group group(EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yawDeg, float height) {
        Group g = new Group();
        g.x = x;
        g.y = y;
        g.z = z;
        System.arraycopy(rotY((float) Math.PI - (float) Math.toRadians(yawDeg)), 0, g.r, 0, 9);
        g.skin = skin;
        g.scale = height / 1.8f;
        g.groundY = ground(x, y, z);
        return g;
    }

    /** Whole body as one piece: knocked back, topples over, slides and dissolves. */
    public void ragdoll(EntityLivingBase victim, double x, double y, double z, float yaw, float height,
                        float dirX, float dirZ, float power, int c0, int c1) {
        Group g = group(victim, victim == null ? null : skinOf(victim), x, y, z, yaw, height);
        for (Box b : parts(g.skin != null)) g.boxes.add(b);
        g.vx = dirX * 3.2f * power;
        g.vz = dirZ * 3.2f * power;
        g.vy = 3.6f * power;
        // tip over backwards: spin around the horizontal axis perpendicular to the push
        g.wx = dirZ * 7f * power;
        g.wz = -dirX * 7f * power;
        g.wy = (float) (Math.random() - 0.5) * 3f;
        g.dissolve0 = c0;
        g.dissolve1 = c1;
        g.life = 2.4f;
        add(g, victim);
    }

    /** Head pops off spinning; the body crumples the other way. */
    public void decapitate(EntityLivingBase victim, double x, double y, double z, float yaw, float height,
                           float dirX, float dirZ, int c0, int c1, boolean blood) {
        ResourceLocation skin = victim == null ? null : skinOf(victim);
        Box[] p = parts(skin != null);
        Group head = group(victim, skin, x, y, z, yaw, height);
        head.boxes.add(p[0]);
        head.boxes.add(p[1]);
        head.vx = dirX * 2.5f + (float) (Math.random() - 0.5);
        head.vz = dirZ * 2.5f + (float) (Math.random() - 0.5);
        head.vy = 6.5f;
        head.wx = (float) (Math.random() - 0.5) * 18f;
        head.wy = (float) (Math.random() - 0.5) * 12f;
        head.wz = (float) (Math.random() - 0.5) * 18f;
        head.life = 2.6f;
        head.dissolve0 = c0;
        head.dissolve1 = c1;
        Group body = group(victim, skin, x, y, z, yaw, height);
        for (int i = 2; i < p.length; i++) body.boxes.add(p[i]);
        body.vx = dirX * 1.2f;
        body.vz = dirZ * 1.2f;
        body.vy = 1.2f;
        body.wx = dirZ * 4.5f;
        body.wz = -dirX * 4.5f;
        body.life = 2.4f;
        body.dissolve0 = c0;
        body.dissolve1 = c1;
        add(head, victim);
        add(body, null);
        // a burst at the neck: sparks in the effect colours, or a short red spray if chosen
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double ny = y + height * 0.78;
        for (int i = 0; i < 22; i++) {
            float a = ps.rand(0f, 6.28f), sp = ps.rand(1f, 3.2f);
            Particle q = ps.glow(x, ny, z, (float) Math.cos(a) * sp * 0.6f + dirX, ps.rand(1.5f, 4f), (float) Math.sin(a) * sp * 0.6f + dirZ,
                    ps.rand(0.5f, 0.9f), blood ? ps.rand(0.06f, 0.12f) : 0.1f, 0.02f, blood ? 0xB3121F : c0, blood ? 0x4A0710 : c1, 1f);
            if (q == null) continue;
            q.gravity = 9f;
            q.drag = 0.8f;
            if (blood) q.alphaBlend = true;
        }
    }

    /** Every part flies apart; ice = frozen tint and shards. */
    public void shatter(EntityLivingBase victim, double x, double y, double z, float yaw, float height, boolean ice, int c0, int c1) {
        ResourceLocation skin = victim == null ? null : skinOf(victim);
        Box[] p = parts(skin != null);
        int[][] sets = {{0, 1}, {2}, {3}, {4}, {5}, {6}};
        ParticleSystem ps = ParticleSystem.INSTANCE;
        for (int[] set : sets) {
            Group g = group(victim, skin, x, y, z, yaw, height);
            float cxl = 0, cyl = 0;
            for (int i : set) {
                g.boxes.add(p[i]);
                cxl = p[i].cx;
                cyl = p[i].cy;
            }
            // push each part away from the body centre
            float ox = g.r[0] * cxl, oz = g.r[6] * cxl;
            g.vx = ox * 9f + ps.rand(-0.8f, 0.8f);
            g.vz = oz * 9f + ps.rand(-0.8f, 0.8f);
            g.vy = 2.5f + (cyl - 1.0f) * 2.5f + ps.rand(0f, 1.5f);
            g.wx = ps.rand(-10f, 10f);
            g.wy = ps.rand(-10f, 10f);
            g.wz = ps.rand(-10f, 10f);
            g.life = 2.0f;
            if (ice) {
                g.tintR = 0.62f;
                g.tintG = 0.86f;
                g.tintB = 1.15f;
            }
            g.dissolve0 = ice ? 0xBFEFFF : c0;
            g.dissolve1 = ice ? 0x6FC8FF : c1;
            add(g, victim);
        }
        if (ice) {
            for (int i = 0; i < 40; i++) {
                Particle q = ps.glow(x + ps.rand(-0.4f, 0.4f), y + ps.rand(0.2f, height), z + ps.rand(-0.4f, 0.4f),
                        ps.rand(-3f, 3f), ps.rand(0f, 4f), ps.rand(-3f, 3f), ps.rand(0.6f, 1.2f), ps.rand(0.08f, 0.16f), 0.02f,
                        0xE6FBFF, 0x8FD9FF, 1f);
                if (q == null) continue;
                q.sprite = Sprite.SHARD;
                q.gravity = 10f;
                q.rotVel = ps.rand(-12f, 12f);
            }
        }
    }

    /** The Executioner: rises behind the target, swings its hammer down, then sinks away. */
    public void executioner(EntityLivingBase victim, double x, double y, double z, float yaw, float height,
                            float dirX, float dirZ, int accent, int c1) {
        Executioner a = new Executioner();
        a.victim = victim;
        a.skin = victim == null ? null : skinOf(victim);
        a.vx = x;
        a.vy = y;
        a.vz = z;
        a.vyaw = yaw;
        a.vheight = height;
        a.dirX = dirX;
        a.dirZ = dirZ;
        a.x = x + dirX * 1.6;
        a.z = z + dirZ * 1.6;
        a.y = y;
        a.groundY = ground(x, y, z);
        a.accent = accent;
        a.c1 = c1;
        actors.add(a);
        ShockwaveSystem.INSTANCE.spawn(a.x, a.groundY + 0.05, a.z, 2.2f, 0.7f, accent, 0x000000, 0.8f);
    }

    /**
     * The Sahur kill: the character model you supplied rises behind the target, bonks it twice
     * with its bat and sends it flying with the third hit, then sinks back into the ground.
     */
    /** Height of the Tung Sahur relative to the victim (set from the Kill Effect setting). */
    public static float sahurScale = 2.1f;

    public void sahur(EntityLivingBase victim, double x, double y, double z, float yaw, float height,
                      float dirX, float dirZ, int c0, int c1) {
        if (MeshAssets.sahur() == null) {
            ragdoll(victim, x, y, z, yaw, height, dirX, dirZ, 1f, c0, c1);
            return;
        }
        Sahur s = new Sahur();
        s.h = 1.8f * sahurScale * Math.max(0.6f, height / 1.8f);
        s.fx = -dirX;
        s.fz = -dirZ;
        // model +x in world = (fz, -fx); put the bat's impact point on the target
        float ax = s.fz, az = -s.fx;
        s.x = x + dirX * 0.45f * s.h + ax * 0.2f * s.h;
        s.z = z + dirZ * 0.45f * s.h + az * 0.2f * s.h;
        s.groundY = ground(x, y, z);
        s.y = s.groundY;
        s.c0 = c0;
        s.c1 = c1;
        s.vx = x;
        s.vy = y;
        s.vz = z;
        s.dirX = dirX;
        s.dirZ = dirZ;
        // the target's body stands there (pinned) until the last hit
        Group g = group(victim, victim == null ? null : skinOf(victim), x, y, z, yaw, height);
        for (Box b : parts(g.skin != null)) g.boxes.add(b);
        g.pinned = true;
        g.life = 4.2f;
        g.dissolve0 = c0;
        g.dissolve1 = c1;
        s.body = g;
        add(g, victim);
        sahurs.add(s);
        ParticleSystem ps = ParticleSystem.INSTANCE;
        for (int i = 0; i < 26; i++) {
            float a = ps.rand(0f, 6.28f), r = ps.rand(0.2f, 1f);
            Particle q = ps.glow(s.x + Math.cos(a) * r, s.groundY + 0.05, s.z + Math.sin(a) * r, (float) Math.cos(a) * 1.5f,
                    ps.rand(0.5f, 2f), (float) Math.sin(a) * 1.5f, ps.rand(0.6f, 1.1f), ps.rand(0.15f, 0.3f), 0.05f, 0x6B4A2B, 0x3A2A1A, 0.9f);
            if (q == null) continue;
            q.sprite = Sprite.SMOKE;
            q.alphaBlend = true;
            q.drag = 2f;
        }
    }

    static final class Sahur {
        static final float[] HITS = {0.71f, 1.21f, 1.71f};
        static final float END = 2.6f;
        double x, y, z, vx, vy, vz;
        float groundY, fx, fz, h, age, dirX, dirZ;
        int c0, c1, hit;
        Group body;
    }

    private void add(Group g, EntityLivingBase victim) {
        groups.add(g);
        if (stage) standInHidden = true;
        else if (victim != null) hide(victim, 4000L);
    }

    // ----------------------------------------------------------- update

    private static void rotate(float[] r, float ax, float ay, float az, float dt) {
        float ang = (float) Math.sqrt(ax * ax + ay * ay + az * az) * dt;
        if (ang < 1e-6f) return;
        float l = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        float x = ax / l, y = ay / l, z = az / l, c = (float) Math.cos(ang), s = (float) Math.sin(ang), t = 1 - c;
        float[] m = {t * x * x + c, t * x * y - s * z, t * x * z + s * y,
                t * x * y + s * z, t * y * y + c, t * y * z - s * x,
                t * x * z - s * y, t * y * z + s * x, t * z * z + c};
        float[] o = r.clone();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = m[i * 3] * o[j] + m[i * 3 + 1] * o[3 + j] + m[i * 3 + 2] * o[6 + j];
            }
        }
    }

    private void step(float dt) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        for (Iterator<Group> it = groups.iterator(); it.hasNext(); ) {
            Group g = it.next();
            g.age += dt;
            if (g.age >= g.life) {
                it.remove();
                continue;
            }
            if (g.pinned) {
                g.squash += (1f - g.squash) * Math.min(1f, dt * 10f);
                continue;
            }
            g.vy -= 18f * dt;
            g.x += g.vx * dt;
            g.y += g.vy * dt;
            g.z += g.vz * dt;
            rotate(g.r, g.wx, g.wy, g.wz, dt);
            // ground contact: lowest corner of any box
            float minY = Float.MAX_VALUE;
            for (Box b : g.boxes) {
                for (int k = 0; k < 8; k++) {
                    float lx = (b.cx + ((k & 1) == 0 ? -b.hx : b.hx)) * g.scale;
                    float ly = (b.cy + ((k & 2) == 0 ? -b.hy : b.hy)) * g.scale;
                    float lz = (b.cz + ((k & 4) == 0 ? -b.hz : b.hz)) * g.scale;
                    float wy = g.r[3] * lx + g.r[4] * ly + g.r[5] * lz;
                    if (wy < minY) minY = wy;
                }
            }
            if (g.y + minY < g.groundY) {
                g.y += g.groundY - (g.y + minY);
                if (g.vy < 0) g.vy = -g.vy * 0.28f;
                float f = (float) Math.pow(0.08, dt);
                g.vx *= 0.82f * f + 0.18f;
                g.vz *= 0.82f * f + 0.18f;
                g.wx *= 0.72f;
                g.wy *= 0.72f;
                g.wz *= 0.72f;
            }
            // dissolve into sparks at the end
            if (!g.dissolved && g.age > g.life - 0.55f) {
                g.dissolved = true;
                for (int i = 0; i < 18; i++) {
                    Particle q = ps.glow(g.x + ps.rand(-0.3f, 0.3f), g.y + ps.rand(0f, 0.6f) * g.scale, g.z + ps.rand(-0.3f, 0.3f),
                            ps.rand(-0.5f, 0.5f), ps.rand(0.6f, 1.8f), ps.rand(-0.5f, 0.5f), ps.rand(0.6f, 1.1f), ps.rand(0.06f, 0.12f), 0.01f,
                            g.dissolve0, g.dissolve1, 1f);
                    if (q != null) q.drag = 1.2f;
                }
            }
        }
        for (Iterator<Sahur> it = sahurs.iterator(); it.hasNext(); ) {
            Sahur s = it.next();
            s.age += dt;
            while (s.hit < Sahur.HITS.length && s.age >= Sahur.HITS[s.hit]) {
                boolean last = s.hit == Sahur.HITS.length - 1;
                double hx = s.vx, hy = s.vy + 1.2, hz = s.vz;
                for (int i = 0; i < (last ? 34 : 14); i++) {
                    Particle q = ps.glow(hx, hy, hz, ps.rand(-2.5f, 2.5f) - s.dirX * 1.5f, ps.rand(0.5f, 3f), ps.rand(-2.5f, 2.5f) - s.dirZ * 1.5f,
                            ps.rand(0.3f, 0.6f), ps.rand(0.08f, 0.16f), 0.02f, i % 3 == 0 ? 0xFFFFFF : s.c0, s.c1, 1f);
                    if (q == null) continue;
                    q.drag = 2f;
                    if (i % 4 == 0) q.sprite = Sprite.STAR;
                }
                ps.glow(hx, hy, hz, 0f, 0f, 0f, 0.18f, 0.4f, last ? 2.4f : 1.4f, 0xFFFFFF, s.c0, 0.9f);
                if (s.body != null) {
                    if (last) {
                        s.body.pinned = false;
                        s.body.vx = -s.dirX * 4.5f;
                        s.body.vz = -s.dirZ * 4.5f;
                        s.body.vy = 3.5f;
                        s.body.wx = -s.dirZ * 9f;
                        s.body.wz = s.dirX * 9f;
                        s.body.life = s.body.age + 2.0f;
                        ShockwaveSystem.INSTANCE.spawn(s.vx, s.groundY + 0.05, s.vz, 3f, 0.6f, s.c0, s.c1, 0.9f);
                    } else {
                        s.body.squash = 0.82f; // flinch
                    }
                }
                s.hit++;
            }
            if (s.age >= Sahur.END) it.remove();
        }
        for (Iterator<Executioner> it = actors.iterator(); it.hasNext(); ) {
            Executioner a = it.next();
            a.age += dt;
            if (!a.struck && a.age >= Executioner.STRIKE) {
                a.struck = true;
                ShockwaveSystem.INSTANCE.spawn(a.vx, a.groundY + 0.05, a.vz, 4f, 0.8f, a.accent, a.c1, 1f);
                for (int i = 0; i < 30; i++) {
                    float ang = ps.rand(0f, 6.28f), sp = ps.rand(2f, 5f);
                    Particle q = ps.glow(a.vx, a.groundY + 0.1, a.vz, (float) Math.cos(ang) * sp, ps.rand(1f, 3f), (float) Math.sin(ang) * sp,
                            ps.rand(0.5f, 1f), ps.rand(0.1f, 0.2f), 0.03f, 0x4A4458, 0x1A1622, 0.9f);
                    if (q == null) continue;
                    q.sprite = Sprite.SMOKE;
                    q.alphaBlend = true;
                    q.gravity = 6f;
                    q.drag = 2f;
                }
                // the target is flattened and launched away from the hammer
                ragdoll(a.victim, a.vx, a.vy, a.vz, a.vyaw, a.vheight, -a.dirX, -a.dirZ, 1.25f, a.accent, a.c1);
                Group last = groups.get(groups.size() - 1);
                last.squash = 0.55f;
                last.skin = a.skin;
            }
            if (a.age >= Executioner.END) it.remove();
        }
    }

    // ----------------------------------------------------------- render

    /** Called by the particle system each frame (world) or by a preview stage. */
    public void render(float dt, double rX, double rY, double rZ) {
        if (!active()) return;
        step(dt);
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.02f);
        for (Group g : groups) {
            float fade = g.age > g.life - 0.45f ? Math.max(0f, (g.life - g.age) / 0.45f) : 1f;
            float light = 1f;
            if (!stage && mc.theWorld != null) {
                light = Math.max(0.35f, mc.theWorld.getLightBrightness(new BlockPos(g.x, g.y + 0.5, g.z)) * 1.1f);
            }
            if (g.skin != null) {
                mc.getTextureManager().bindTexture(g.skin);
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GL11.glBegin(GL11.GL_QUADS);
            for (Box b : g.boxes) {
                if (b.color == 0x00000000) continue;
                box(g, b, (float) (g.x - rX), (float) (g.y - rY), (float) (g.z - rZ), g.r, g.scale, g.squash, light, fade);
            }
            GL11.glEnd();
        }
        MeshAssets.Animated model = sahurs.isEmpty() ? null : MeshAssets.sahur();
        for (Sahur s : sahurs) {
            if (model == null) break;
            // rises out of the ground, sinks back after the last hit
            float up = s.age < 0.35f ? s.age / 0.35f : s.age > 2.1f ? 1f - (s.age - 2.1f) / 0.5f : 1f;
            up = Math.max(0f, Math.min(1f, up));
            up = up * up * (3f - 2f * up);
            float light = 1f;
            if (!stage && mc.theWorld != null) light = Math.max(0.35f, mc.theWorld.getLightBrightness(new BlockPos(s.x, s.y + 1, s.z)) * 1.1f);
            model.draw(s.age, s.x - rX, s.groundY - rY - (1f - up) * s.h, s.z - rZ, s.fx, s.fz, s.h, light, 1f);
        }
        GlStateManager.disableTexture2D();
        for (Executioner a : actors) a.draw(this, (float) (a.x - rX), (float) (a.groundY - rY), (float) (a.z - rZ));
        GlStateManager.enableTexture2D();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static final float[] SHADE = {0.62f, 0.62f, 1.0f, 0.5f, 0.9f, 0.75f}; // +x -x +y -y -z +z

    /** Emits the 6 faces of a box from 8 world corners (bit0 = +x, bit1 = +y, bit2 = +z). */
    static void boxFaces(float[][] c, Box b, boolean tex, float light, float fade) {
        Group g = NEUTRAL;
        int w = b.w, h = b.h, d = b.d, u = b.u, v = b.v;
        face(c, 3, 2, 0, 1, u + d, v + d, w, h, tex, b, 4, g, light, fade);
        face(c, 6, 7, 5, 4, u + d + w + d, v + d, w, h, tex, b, 5, g, light, fade);
        face(c, 7, 3, 1, 5, u, v + d, d, h, tex, b, 0, g, light, fade);
        face(c, 2, 6, 4, 0, u + d + w, v + d, d, h, tex, b, 1, g, light, fade);
        face(c, 7, 6, 2, 3, u + d, v, w, d, tex, b, 2, g, light, fade);
        face(c, 1, 0, 4, 5, u + d + w, v, w, d, tex, b, 3, g, light, fade);
    }

    /** Emits the 6 faces of a box (inside glBegin(GL_QUADS)). */
    private static void box(Group g, Box b, float ox, float oy, float oz, float[] r, float scale, float squash, float light, float fade) {
        float s = scale;
        float[][] c = new float[8][3];
        for (int k = 0; k < 8; k++) {
            float lx = (b.cx + ((k & 1) == 0 ? -b.hx : b.hx)) * s;
            float ly = (b.cy + ((k & 2) == 0 ? -b.hy : b.hy)) * s * squash;
            float lz = (b.cz + ((k & 4) == 0 ? -b.hz : b.hz)) * s;
            c[k][0] = ox + r[0] * lx + r[1] * ly + r[2] * lz;
            c[k][1] = oy + r[3] * lx + r[4] * ly + r[5] * lz;
            c[k][2] = oz + r[6] * lx + r[7] * ly + r[8] * lz;
        }
        boolean tex = b.color == -1 && g.skin != null;
        int w = b.w, h = b.h, d = b.d, u = b.u, v = b.v;
        // corner index: bit0 = +x, bit1 = +y, bit2 = +z
        // front (-z): image left = +x
        face(c, 3, 2, 0, 1, u + d, v + d, w, h, tex, b, 4, g, light, fade);
        // back (+z): image left = -x
        face(c, 6, 7, 5, 4, u + d + w + d, v + d, w, h, tex, b, 5, g, light, fade);
        // right (+x): image left = +z
        face(c, 7, 3, 1, 5, u, v + d, d, h, tex, b, 0, g, light, fade);
        // left (-x): image left = -z
        face(c, 2, 6, 4, 0, u + d + w, v + d, d, h, tex, b, 1, g, light, fade);
        // top (+y): image top = +z (back), left = +x
        face(c, 7, 6, 2, 3, u + d, v, w, d, tex, b, 2, g, light, fade);
        // bottom (-y)
        face(c, 1, 0, 4, 5, u + d + w, v, w, d, tex, b, 3, g, light, fade);
    }

    /** Quad with corners top-left, top-right, bottom-right, bottom-left of the texture rect. */
    static void face(float[][] c, int tl, int tr, int br, int bl, int u, int v, int w, int h, boolean tex,
                             Box b, int side, Group g, float light, float fade) {
        float sh = SHADE[side] * light;
        if (tex) {
            GL11.glColor4f(sh * g.tintR, sh * g.tintG, Math.min(1f, sh * g.tintB), fade);
        } else {
            int col = b.color;
            GL11.glColor4f(((col >> 16) & 255) / 255f * sh * g.tintR, ((col >> 8) & 255) / 255f * sh * g.tintG,
                    Math.min(1f, (col & 255) / 255f * sh * g.tintB), fade);
        }
        float u0 = u / 64f, v0 = v / 64f, u1 = (u + w) / 64f, v1 = (v + h) / 64f;
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(c[tl][0], c[tl][1], c[tl][2]);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(c[tr][0], c[tr][1], c[tr][2]);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(c[br][0], c[br][1], c[br][2]);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(c[bl][0], c[bl][1], c[bl][2]);
    }

    // -------------------------------------------------------- the giant

    /** Original character: an obsidian giant with glowing eyes and a war hammer (untextured boxes). */
    static final class Executioner {
        static final float RISE = 0.45f, RAISE = 0.85f, STRIKE = 1.0f, HOLD = 1.45f, END = 2.0f;
        EntityLivingBase victim;
        ResourceLocation skin;
        double x, y, z, vx, vy, vz, groundY;
        float vyaw, vheight, dirX, dirZ, age;
        int accent, c1;
        boolean struck;

        private static float ease(float t) {
            t = Math.max(0f, Math.min(1f, t));
            return t * t * (3f - 2f * t);
        }

        void draw(BodyFx fx, float ox, float oy, float oz) {
            // rise out of the ground, then sink back
            float up = age < RISE ? ease(age / RISE) : age > HOLD ? 1f - ease((age - HOLD) / (END - HOLD)) : 1f;
            float sink = (1f - up) * 3.4f;
            // face the target: local -z towards it
            float yaw = (float) Math.atan2(-dirX, -dirZ);
            float[] r = rotY(yaw + (float) Math.PI);
            // hammer arm pitch: raised behind the head, then smashed down
            float pitch;
            if (age < RISE) pitch = -0.4f;
            else if (age < RAISE) pitch = -0.4f - 2.4f * ease((age - RISE) / (RAISE - RISE));
            else if (age < STRIKE) {
                float k = (age - RAISE) / (STRIKE - RAISE);
                pitch = -2.8f - 2.85f * k * k; // over the top and down onto the target (accelerating)
            } else {
                pitch = -5.65f + 0.4f * ease((age - STRIKE) / 0.5f);
            }
            float alpha = Math.min(1f, up * 1.5f);
            int body = 0x221C2C, dark = 0x15111C;
            GL11.glBegin(GL11.GL_QUADS);
            float baseY = oy - sink;
            solid(ox, baseY, oz, r, 0, 0, 0, -0.42f, 0.0f, 0f, 0.42f, 1.2f, 0.42f, dark, alpha, 0f);   // legs
            solid(ox, baseY, oz, r, 0, 0, 0, 0.42f, 0.0f, 0f, 0.42f, 1.2f, 0.42f, dark, alpha, 0f);
            solid(ox, baseY, oz, r, 0, 0, 0, 0f, 1.85f, 0f, 1.45f, 1.35f, 0.85f, body, alpha, 0f);    // torso
            solid(ox, baseY, oz, r, 0, 0, 0, -0.95f, 2.45f, 0f, 0.55f, 0.35f, 0.75f, dark, alpha, 0f); // shoulders
            solid(ox, baseY, oz, r, 0, 0, 0, 0.95f, 2.45f, 0f, 0.55f, 0.35f, 0.75f, dark, alpha, 0f);
            solid(ox, baseY, oz, r, 0, 0, 0, 0f, 2.95f, -0.05f, 0.75f, 0.75f, 0.75f, body, alpha, 0f); // head
            solid(ox, baseY, oz, r, 0, 0, 0, -0.95f, 1.75f, 0f, 0.38f, 1.2f, 0.38f, body, alpha, 0f); // left arm (idle)
            // right arm + hammer, rotated around the shoulder
            float sy = 2.45f, sx = 0.95f;
            solid(ox, baseY, oz, r, pitch, sx, sy, sx, sy - 0.65f, 0f, 0.38f, 1.25f, 0.38f, body, alpha, 0f);
            solid(ox, baseY, oz, r, pitch, sx, sy, sx, sy - 1.9f, 0f, 0.14f, 1.6f, 0.14f, 0x3A2E24, alpha, 0f);   // handle
            solid(ox, baseY, oz, r, pitch, sx, sy, sx, sy - 2.75f, 0f, 0.6f, 0.55f, 1.15f, 0x2B2533, alpha, 0f);  // hammer head
            GL11.glEnd();
            // glowing bits: eyes, chest rune, hammer band (additive)
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GlStateManager.depthMask(false);
            GL11.glBegin(GL11.GL_QUADS);
            float pulse = 0.7f + 0.3f * (float) Math.sin(age * 12f);
            solid(ox, baseY, oz, r, 0, 0, 0, -0.17f, 3.02f, -0.44f, 0.12f, 0.06f, 0.02f, accent, alpha * pulse, 1f);
            solid(ox, baseY, oz, r, 0, 0, 0, 0.17f, 3.02f, -0.44f, 0.12f, 0.06f, 0.02f, accent, alpha * pulse, 1f);
            solid(ox, baseY, oz, r, 0, 0, 0, 0f, 2.0f, -0.44f, 0.3f, 0.45f, 0.02f, accent, alpha * 0.55f * pulse, 1f);
            solid(ox, baseY, oz, r, pitch, sx, sy, sx, sy - 2.75f, 0f, 0.62f, 0.12f, 1.17f, accent, alpha * 0.8f, 1f);
            GL11.glEnd();
            GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        }

        /**
         * Box centred at (cx, cy, cz) with full sizes (sx, sy, sz) in the giant's local space,
         * optionally rotated by pitch around the x axis through (px, py).
         */
        private static void solid(float ox, float oy, float oz, float[] r, float pitch, float px, float py,
                                  float cx, float cy, float cz, float sx, float sy, float sz, int col, float a, float glow) {
            float[][] c = new float[8][3];
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
            for (int k = 0; k < 8; k++) {
                float lx = cx + ((k & 1) == 0 ? -sx : sx) / 2f;
                float ly = cy + ((k & 2) == 0 ? -sy : sy) / 2f;
                float lz = cz + ((k & 4) == 0 ? -sz : sz) / 2f;
                if (pitch != 0f) {
                    float dy = ly - py, dz = lz;
                    ly = py + dy * cp - dz * sp;
                    lz = dy * sp + dz * cp;
                }
                c[k][0] = ox + r[0] * lx + r[1] * ly + r[2] * lz;
                c[k][1] = oy + r[3] * lx + r[4] * ly + r[5] * lz;
                c[k][2] = oz + r[6] * lx + r[7] * ly + r[8] * lz;
            }
            int[][] faces = {{3, 2, 0, 1}, {6, 7, 5, 4}, {7, 3, 1, 5}, {2, 6, 4, 0}, {7, 6, 2, 3}, {1, 0, 4, 5}};
            int[] side = {4, 5, 0, 1, 2, 3};
            for (int f = 0; f < 6; f++) {
                float sh = glow > 0f ? 1f : SHADE[side[f]];
                GL11.glColor4f(((col >> 16) & 255) / 255f * sh, ((col >> 8) & 255) / 255f * sh, (col & 255) / 255f * sh, a);
                for (int i = 0; i < 4; i++) {
                    float[] p = c[faces[f][i]];
                    GL11.glVertex3f(p[0], p[1], p[2]);
                }
            }
        }
    }

    // ------------------------------------------------------- hide victims

    /** Hides the vanilla model of a defeated player while its body is shown by us. */
    public static final class Hider {
        @SubscribeEvent
        @SuppressWarnings("rawtypes")
        public void onRender(RenderLivingEvent.Pre e) {
            if (HIDDEN.isEmpty()) return;
            Long until = HIDDEN.get(e.entity.getEntityId());
            if (until == null) return;
            if (System.currentTimeMillis() > until) {
                HIDDEN.remove(e.entity.getEntityId());
                return;
            }
            // only while it is dead (a respawned player is visible again right away)
            if (e.entity.getHealth() <= 0f || e.entity.isDead) e.setCanceled(true);
            else HIDDEN.remove(e.entity.getEntityId());
        }
    }
}
