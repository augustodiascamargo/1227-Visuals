package net.m1227.vfx;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * Kill effects built on a visual copy of the victim's body (with their skin when it is a
 * player): the soul rising out of it, a black hole swallowing it, disintegration top to bottom,
 * breaking into skin-coloured voxels, dissolving into shadow, turning to crystal and exploding,
 * electrocution, sinking into a portal, being lifted and crushed, vaporised by a beam, plus a
 * firework finale. The real entity is untouched: Minecraft's death happens as always, its model
 * is only hidden while the copy plays. Time is real seconds.
 */
public final class KillFx {
    private KillFx() {}

    /** Look settings from the Kill Effect module. */
    public static final class Look {
        public int c0 = 0x8B5CFF, c1 = 0x2DD4FF;
        public float size = 0.14f, speed = 1f, amount = 26f, duration = 1f, alpha = 1f;
        /** Smaller pieces (voxels of 2 skin pixels instead of 4). */
        public boolean fine = true;
    }

    public static final String[] STYLES = {"Soul Ascension", "Black Hole", "Disintegration", "Pixel Disintegration", "Shadow Dissolve",
            "Crystal Explosion", "Electric Explosion", "Portal Collapse", "Gravity Pull", "Energy Beam", "Firework Finale"};

    public static boolean has(String style) {
        for (String s : STYLES) if (s.equals(style)) return true;
        return false;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    private static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    private static float b(int c) {
        return (c & 255) / 255f;
    }

    /** Plays a style. (x, feetY, z) feet of the victim, yaw its body yaw, (dirX, dirZ) from you to it. */
    public static void play(String style, EntityLivingBase victim, double x, double feetY, double z, float yaw, float height,
                            float dirX, float dirZ, Look k) {
        BodyFx bf = BodyFx.INSTANCE;
        ResourceLocation skin = victim == null ? null : BodyFx.skinOf(victim);
        switch (style) {
            case "Soul Ascension":
                soul(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            case "Black Hole":
                blackHole(bf, victim, skin, x, feetY, z, yaw, height, dirX, dirZ, k);
                break;
            case "Disintegration":
                disintegrate(bf, victim, skin, x, feetY, z, yaw, height, dirX, dirZ, k);
                break;
            case "Pixel Disintegration":
                pixels(bf, victim, skin, x, feetY, z, yaw, height, dirX, dirZ, k);
                break;
            case "Shadow Dissolve":
                shadow(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            case "Crystal Explosion":
                crystal(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            case "Electric Explosion":
                electric(bf, victim, skin, x, feetY, z, yaw, height, dirX, dirZ, k);
                break;
            case "Portal Collapse":
                portal(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            case "Gravity Pull":
                gravity(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            case "Energy Beam":
                beam(bf, victim, skin, x, feetY, z, yaw, height, k);
                break;
            default:
                fireworks(x, feetY, z, height, k);
        }
    }

    /** The whole body as one standing piece, no physics, no end sparks. */
    private static BodyFx.Group body(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height) {
        BodyFx.Group gr = bf.group(victim, skin, x, y, z, yaw, height);
        for (BodyFx.Box bx : BodyFx.parts(skin != null)) gr.boxes.add(bx);
        gr.dissolved = true;
        return gr;
    }

    // ------------------------------------------------------------------ styles

    private static void soul(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height, final Look k) {
        final float dur = 1.5f * k.duration;
        BodyFx.Group body = body(bf, victim, skin, x, y, z, yaw, height);
        body.life = dur;
        body.script = (gr, dt) -> {
            float grey = 1f - 0.55f * smooth(gr.age / (0.5f * dur));
            gr.tintR = gr.tintG = gr.tintB = grey;
            gr.alphaMul = 1f - smooth((gr.age - 0.45f * dur) / (0.5f * dur));
            return true;
        };
        bf.add(body, victim);
        final double y0 = y;
        BodyFx.Group ghost = body(bf, victim, skin, x, y, z, yaw, height);
        ghost.additive = true;
        ghost.life = 2.4f * k.duration;
        ghost.tintR = 0.55f + r(k.c0) * 0.7f;
        ghost.tintG = 0.55f + g(k.c0) * 0.7f;
        ghost.tintB = 0.55f + b(k.c0) * 0.7f;
        ghost.alphaMul = 0f;
        ghost.script = (gr, dt) -> {
            float t = gr.age;
            gr.y = y0 + 0.15 + 1.1 * Math.pow(Math.max(0f, t - 0.2f), 1.3) * k.speed;
            gr.alphaMul = 0.65f * smooth((t - 0.1f) / 0.35f) * (1f - smooth((t - gr.life * 0.6f) / (gr.life * 0.4f)));
            gr.scale = gr.k[0] == 0f ? (gr.k[0] = gr.scale) : gr.k[0] * (1f + 0.08f * t);
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.k[1] += dt * 30f;
            while (gr.k[1] >= 1f) {
                gr.k[1] -= 1f;
                Particle p = ps.glow(gr.x + ps.rand(-0.35f, 0.35f), gr.y + ps.rand(0f, 1.8f) * gr.scale, gr.z + ps.rand(-0.35f, 0.35f), 0f,
                        ps.rand(0.4f, 1f), 0f, ps.rand(0.5f, 0.9f), k.size * ps.rand(0.4f, 0.8f), 0.01f, k.c1, k.c0, k.alpha * 0.8f);
                if (p == null) break;
                p.drag = 1f;
            }
            return true;
        };
        bf.add(ghost, victim);
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Particle ring = ps.glow(x, y + 0.05, z, 0f, 0f, 0f, 0.6f, k.size * 3f, k.size * 18f, k.c1, k.c0, k.alpha * 0.8f);
        if (ring != null) {
            ring.sprite = Sprite.RING;
            ring.flat = true;
        }
    }

    private static void blackHole(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height,
                                  float dirX, float dirZ, final Look k) {
        final double cx = x + dirX * 1.3, cy = y + height * 0.6, cz = z + dirZ * 1.3;
        FxShapes.INSTANCE.blackHole(cx, cy, cz, 0.9f, 1.8f * k.duration, k.c0, k.c1, k.alpha);
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final double x0 = x, y0 = y, z0 = z;
        final float pull = 1.1f * k.duration;
        gr0.life = 0.3f + pull + 0.05f;
        gr0.script = (gr, dt) -> {
            if (gr.k[0] == 0f) gr.k[0] = gr.scale;
            float u = smooth((gr.age - 0.3f) / pull);
            float ue = u * u;
            gr.x = x0 + (cx - x0) * ue;
            gr.z = z0 + (cz - z0) * ue;
            gr.y = y0 + (cy - 0.9 * gr.k[0] * (1f - u) - y0) * ue;
            gr.scale = gr.k[0] * (1f - u);
            float spin = (2f + 16f * u) * k.speed;
            gr.wy = spin;
            gr.wx = 2f * u;
            gr.tintR = gr.tintG = gr.tintB = 1f - 0.6f * u;
            BodyFx.rotateBy(gr, dt);
            if (u >= 0.98f && gr.k[1] == 0f) {
                gr.k[1] = 1f;
                ParticleSystem ps = ParticleSystem.INSTANCE;
                ps.glow(cx, cy, cz, 0f, 0f, 0f, 0.2f, k.size * 3f, k.size * 14f, 0xFFFFFF, k.c0, k.alpha);
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** Cut plane sweeping from the head down; what is above it turns into embers and ash. */
    private static void disintegrate(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height,
                                     final float dirX, final float dirZ, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float dur = 1.3f * k.duration, top = (float) (y + height * 1.08), feet = (float) y;
        gr0.life = 0.15f + dur + 0.1f;
        gr0.keepBelow = true;
        gr0.clipY = top;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            float u = smooth((gr.age - 0.15f) / dur);
            gr.clipY = top - (top - feet) * u;
            gr.glow = 0.25f * smooth(gr.age / 0.15f);
            if (u <= 0f || u >= 1f) return true;
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.k[0] += dt * 170f * Math.min(1.5f, k.amount / 26f);
            while (gr.k[0] >= 1f) {
                gr.k[0] -= 1f;
                float a = ps.rand(0f, 6.2831855f), rr = ps.rand(0f, 0.32f) * gr.scale;
                boolean ash = ps.rand(0f, 1f) < 0.35f;
                Particle p = ps.glow(gr.x + Math.cos(a) * rr, gr.clipY, gr.z + Math.sin(a) * rr, dirX * ps.rand(0.8f, 2f) + ps.rand(-0.4f, 0.4f),
                        ps.rand(0.3f, 1.2f), dirZ * ps.rand(0.8f, 2f) + ps.rand(-0.4f, 0.4f), ps.rand(0.5f, 1f), k.size * ps.rand(0.35f, 0.7f), 0.01f,
                        ash ? 0x2A2630 : k.c0, ash ? 0x141218 : k.c1, ash ? 0.7f : k.alpha);
                if (p == null) break;
                p.drag = 1.4f;
                p.gravity = ash ? -0.4f : -0.8f;
                if (ash) {
                    p.alphaBlend = true;
                    p.sprite = Sprite.SMOKE;
                }
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** The body breaks into voxels that carry its skin, top first, and scatter. */
    private static void pixels(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height,
                               float dirX, float dirZ, Look k) {
        BodyFx.Group ref = bf.group(victim, skin, x, y, z, yaw, height);
        int vs = k.fine ? 2 : 4;
        ParticleSystem ps = ParticleSystem.INSTANCE;
        BodyFx.Box[] parts = BodyFx.parts(skin != null);
        for (BodyFx.Box p : parts) {
            if (p.u == 32 && p.v == 0) continue; // hat layer: its voxels would float off the head
            for (BodyFx.Box v : voxels(p, vs)) {
                BodyFx.Group gr = bf.group(victim, skin, x, y, z, yaw, height);
                // the group sits at the voxel's centre so it tumbles around itself
                float lx = v.cx * gr.scale, ly = v.cy * gr.scale, lz = v.cz * gr.scale;
                double wx = gr.r[0] * lx + gr.r[1] * ly + gr.r[2] * lz, wy = gr.r[3] * lx + gr.r[4] * ly + gr.r[5] * lz,
                        wz = gr.r[6] * lx + gr.r[7] * ly + gr.r[8] * lz;
                gr.x = x + wx;
                gr.y = y + wy;
                gr.z = z + wz;
                v.cx = v.cy = v.cz = 0f;
                gr.boxes.add(v);
                float rel = (float) (wy / Math.max(0.5, height));
                gr.delay = 0.1f + (1f - rel) * 0.7f * k.duration + ps.rand(0f, 0.08f);
                float ol = (float) Math.sqrt(wx * wx + wz * wz) + 1e-3f;
                float sp = k.speed;
                gr.vx = (float) (wx / ol) * ps.rand(0.6f, 1.8f) * sp + dirX * ps.rand(1f, 2.4f) * sp;
                gr.vz = (float) (wz / ol) * ps.rand(0.6f, 1.8f) * sp + dirZ * ps.rand(1f, 2.4f) * sp;
                gr.vy = ps.rand(1f, 3f) * sp;
                gr.wx = ps.rand(-8f, 8f);
                gr.wy = ps.rand(-8f, 8f);
                gr.wz = ps.rand(-8f, 8f);
                gr.gravity = 7f;
                gr.life = gr.delay + 1.1f * k.duration;
                gr.dissolved = true;
                bf.add(gr, victim);
            }
        }
        if (ref != null) ps.glow(x, y + height * 0.5, z, 0f, 0f, 0f, 0.25f, k.size * 4f, k.size * 12f, k.c1, k.c0, k.alpha * 0.5f);
    }

    /**
     * Splits a skinned part into cubes of 'vs' skin pixels on its surface, each keeping the
     * texels of the faces it came from (standard 64x64 skin layout).
     */
    static java.util.List<BodyFx.Box> voxels(BodyFx.Box p, int vs) {
        java.util.List<BodyFx.Box> out = new java.util.ArrayList<>();
        int w = p.w, h = p.h, d = p.d;
        float px = 0.9375f / 16f;
        // part min corner in model pixels (centre minus half size)
        float x0 = p.cx / px - w / 2f, y0 = p.cy / px - h / 2f, z0 = p.cz / px - d / 2f;
        for (int xi = 0; xi < w; xi += vs) {
            for (int yi = 0; yi < h; yi += vs) {
                for (int zi = 0; zi < d; zi += vs) {
                    boolean shell = xi == 0 || yi == 0 || zi == 0 || xi + vs >= w || yi + vs >= h || zi + vs >= d;
                    if (!shell) continue;
                    int sx = Math.min(vs, w - xi), sy = Math.min(vs, h - yi), sz = Math.min(vs, d - zi);
                    BodyFx.Box v = new BodyFx.Box(x0 + xi + sx / 2f, y0 + yi + sy / 2f, z0 + zi + sz / 2f, sx, sy, sz, p.u, p.v, 0f);
                    v.color = p.color;
                    float[][] uv = new float[6][];
                    int u = p.u, vv = p.v;
                    // +x (image left = +z): column from the +z side
                    uv[0] = rect(u + (d - zi - sz), vv + d + (h - yi - sy), sz, sy);
                    // -x (image left = -z)
                    uv[1] = rect(u + d + w + zi, vv + d + (h - yi - sy), sz, sy);
                    // +y (image left = +x, top = +z)
                    uv[2] = rect(u + d + (w - xi - sx), vv + (d - zi - sz), sx, sz);
                    // -y (image left = +x, top = -z)
                    uv[3] = rect(u + d + w + (w - xi - sx), vv + zi, sx, sz);
                    // -z front (image left = +x)
                    uv[4] = rect(u + d + (w - xi - sx), vv + d + (h - yi - sy), sx, sy);
                    // +z back (image left = -x)
                    uv[5] = rect(u + d + w + d + xi, vv + d + (h - yi - sy), sx, sy);
                    v.uvs = uv;
                    out.add(v);
                }
            }
        }
        return out;
    }

    private static float[] rect(int u, int v, int w, int h) {
        return new float[]{u, v, u + w, v + h};
    }

    private static void shadow(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float dur = 1.2f * k.duration, feet = (float) y, top = (float) (y + height * 1.08);
        gr0.life = 0.45f + dur + 0.1f;
        gr0.keepBelow = false;
        gr0.clipY = feet - 0.1f;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            float dark = smooth(gr.age / 0.4f);
            gr.tintR = 1f - 0.9f * dark;
            gr.tintG = 1f - 0.92f * dark;
            gr.tintB = 1f - 0.85f * dark;
            gr.glow = 0.12f * dark;
            float u = smooth((gr.age - 0.45f) / dur);
            gr.clipY = feet - 0.1f + (top - feet + 0.1f) * u;
            if (u <= 0f || u >= 1f) return true;
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.k[0] += dt * 120f;
            while (gr.k[0] >= 1f) {
                gr.k[0] -= 1f;
                float a = ps.rand(0f, 6.2831855f), rr = ps.rand(0f, 0.34f) * gr.scale;
                boolean glint = ps.rand(0f, 1f) < 0.2f;
                Particle p = ps.glow(gr.x + Math.cos(a) * rr, gr.clipY, gr.z + Math.sin(a) * rr, ps.rand(-0.3f, 0.3f), ps.rand(0.6f, 1.6f),
                        ps.rand(-0.3f, 0.3f), ps.rand(0.7f, 1.3f), k.size * (glint ? 0.5f : ps.rand(1.4f, 2.4f)), glint ? 0.01f : k.size * 3f,
                        glint ? k.c0 : 0x1A1024, glint ? k.c1 : 0x060309, glint ? k.alpha : 0.65f);
                if (p == null) break;
                p.drag = 1.2f;
                if (!glint) {
                    p.alphaBlend = true;
                    p.sprite = Sprite.SMOKE;
                    p.rotVel = ps.rand(-1f, 1f);
                }
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** Turns to crystal (glowing, bluish), then bursts into crystal pieces and shards. */
    private static void crystal(final BodyFx bf, final EntityLivingBase victim, final ResourceLocation skin, final double x, final double y, final double z,
                                final float yaw, final float height, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float form = 0.5f * k.duration;
        gr0.life = form + 0.02f;
        gr0.glowColor = k.c1;
        gr0.script = (gr, dt) -> {
            float u = smooth(gr.age / form);
            gr.tintR = 1f - 0.35f * u;
            gr.tintG = 1f - 0.1f * u;
            gr.tintB = 1f;
            gr.glow = 0.7f * u;
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.k[0] += dt * 25f;
            while (gr.k[0] >= 1f) {
                gr.k[0] -= 1f;
                Particle p = ps.glow(gr.x + ps.rand(-0.45f, 0.45f), gr.y + ps.rand(0f, 1.9f) * gr.scale, gr.z + ps.rand(-0.45f, 0.45f), 0f, 0f, 0f,
                        0.3f, k.size * ps.rand(0.8f, 1.4f), 0.01f, 0xFFFFFF, k.c1, k.alpha);
                if (p != null) p.sprite = Sprite.STAR;
            }
            if (gr.age >= form - 0.02f && gr.k[1] == 0f) {
                gr.k[1] = 1f;
                int before = bf.groupCount();
                bf.shatter(victim, x, y, z, yaw, height, true, k.c0, k.c1);
                bf.tintNewGroups(before, 0.7f, 0.9f, 1.15f, 0.45f, k.c1);
                ps.glow(x, y + height * 0.5, z, 0f, 0f, 0f, 0.25f, k.size * 4f, k.size * 20f, 0xFFFFFF, k.c1, k.alpha);
                int n = ps.scale(k.amount * 1.2f);
                for (int i = 0; i < n; i++) {
                    float a = ps.rand(0f, 6.2831855f), sp = ps.rand(2f, 5f) * k.speed;
                    Particle p = ps.glow(x, y + ps.rand(0.3f, height), z, (float) Math.cos(a) * sp, ps.rand(1f, 4f), (float) Math.sin(a) * sp,
                            ps.rand(0.7f, 1.2f), k.size * ps.rand(1.2f, 2.2f), k.size, k.c1, k.c0, k.alpha);
                    if (p == null) continue;
                    p.sprite = Sprite.CRYSTAL;
                    p.rotVel = ps.rand(-8f, 8f);
                    p.gravity = 9f;
                }
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** Arcs crawl over the body while it flickers, then it blows apart. */
    private static void electric(final BodyFx bf, final EntityLivingBase victim, final ResourceLocation skin, final double x, final double y, final double z,
                                 final float yaw, final float height, final float dirX, final float dirZ, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float charge = 0.8f * k.duration;
        gr0.life = charge + 0.02f;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.glow = ps.rand(0.15f, 0.9f) * smooth(gr.age / 0.2f);
            gr.tintR = 0.75f;
            gr.tintG = 0.85f;
            gr.tintB = 1.1f;
            gr.k[0] += dt * 28f;
            while (gr.k[0] >= 1f) {
                gr.k[0] -= 1f;
                Arcs.INSTANCE.spawn(gr.x + ps.rand(-0.4f, 0.4f), gr.y + ps.rand(0f, 1.9f) * gr.scale, gr.z + ps.rand(-0.4f, 0.4f),
                        gr.x + ps.rand(-0.4f, 0.4f), gr.y + ps.rand(0f, 1.9f) * gr.scale, gr.z + ps.rand(-0.4f, 0.4f), 0.07f, 0.12f,
                        ps.rand(0f, 1f) < 0.5f ? 0xFFFFFF : k.c0, k.alpha);
            }
            if (gr.age >= charge - 0.02f && gr.k[1] == 0f) {
                gr.k[1] = 1f;
                int before = bf.groupCount();
                bf.shatter(victim, x, y, z, yaw, height, false, k.c0, k.c1);
                bf.tintNewGroups(before, 0.85f, 0.9f, 1.1f, 0.6f, k.c0);
                ps.glow(x, y + height * 0.5, z, 0f, 0f, 0f, 0.3f, k.size * 5f, k.size * 24f, 0xFFFFFF, k.c0, k.alpha);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + ps.rand(-0.3f, 0.3f);
                    Arcs.INSTANCE.spawn(x, y + 0.08, z, x + Math.cos(a) * 2.2, y + 0.1, z + Math.sin(a) * 2.2, 0.35f, 0.25f, k.c1, k.alpha);
                }
                int n = ps.scale(k.amount * 1.5f);
                for (int i = 0; i < n; i++) {
                    float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-0.3f, 1f), rr = (float) Math.sqrt(1f - zz * zz), sp = ps.rand(4f, 9f) * k.speed;
                    Particle p = ps.glow(x, y + height * 0.5, z, rr * (float) Math.cos(a) * sp + dirX * 2f, zz * sp, rr * (float) Math.sin(a) * sp + dirZ * 2f,
                            ps.rand(0.3f, 0.6f), k.size * 0.6f, 0.01f, 0xFFFFFF, k.c0, k.alpha);
                    if (p == null) continue;
                    p.stretch = 0.05f;
                    p.drag = 2.5f;
                    p.gravity = 6f;
                }
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    private static void portal(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float ground = gr0.groundY, sink = 1.1f * k.duration;
        FxShapes.INSTANCE.portal(x, ground, z, 1.2f, 0.35f + sink + 0.5f, k.c0, k.c1, k.alpha);
        final double y0 = y;
        gr0.life = 0.35f + sink + 0.05f;
        gr0.keepBelow = false;
        gr0.clipY = ground + 0.005f;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            float u = Math.max(0f, Math.min(1f, (gr.age - 0.35f) / sink));
            gr.y = y0 - u * u * height * 1.15;
            gr.wy = 3f * u * k.speed;
            gr.glow = 0.3f * u;
            BodyFx.rotateBy(gr, dt);
            return true;
        };
        bf.add(gr0, victim);
    }

    /** Lifted off the ground, turning slowly, then crushed to a point. */
    private static void gravity(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, final double x, double y, final double z, float yaw,
                                final float height, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final double y0 = y;
        final float lift = 1.1f * k.duration, crush = 0.2f;
        gr0.life = lift + crush + 0.02f;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            if (gr.k[0] == 0f) gr.k[0] = gr.scale;
            float t = gr.age;
            gr.y = y0 + 1.4 * smooth(t / lift) * k.speed;
            gr.wy = 1.4f;
            gr.wx = 0.5f;
            BodyFx.rotateBy(gr, dt);
            ParticleSystem ps = ParticleSystem.INSTANCE;
            if (t < lift) {
                gr.k[1] += dt * 40f;
                while (gr.k[1] >= 1f) {
                    gr.k[1] -= 1f;
                    float a = ps.rand(0f, 6.2831855f), rr = ps.rand(0.4f, 1.6f);
                    Particle p = ps.glow(x + Math.cos(a) * rr, y0 + 0.05, z + Math.sin(a) * rr, 0f, ps.rand(1f, 2.5f), 0f, ps.rand(0.6f, 1f),
                            k.size * ps.rand(0.4f, 0.9f), 0.01f, k.c1, k.c0, k.alpha * 0.8f);
                    if (p == null) break;
                    p.drag = 0.8f;
                }
            } else {
                float u = smooth((t - lift) / crush);
                gr.scale = gr.k[0] * (1f - u);
                gr.squash = 1f - 0.6f * u;
                gr.glow = u;
                if (u >= 0.95f && gr.k[2] == 0f) {
                    gr.k[2] = 1f;
                    double cy = gr.y + 0.9 * gr.k[0];
                    ps.glow(x, cy, z, 0f, 0f, 0f, 0.25f, k.size * 3f, k.size * 18f, 0xFFFFFF, k.c0, k.alpha);
                    Particle ring = ps.glow(x, cy, z, 0f, 0f, 0f, 0.4f, k.size * 2f, k.size * 22f, k.c1, k.c0, k.alpha * 0.8f);
                    if (ring != null) ring.sprite = Sprite.RING;
                    int n = ps.scale(k.amount);
                    for (int i = 0; i < n; i++) {
                        float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1f - zz * zz), sp = ps.rand(3f, 6f) * k.speed;
                        Particle p = ps.glow(x, cy, z, rr * (float) Math.cos(a) * sp, zz * sp, rr * (float) Math.sin(a) * sp, ps.rand(0.4f, 0.8f),
                                k.size * ps.rand(0.6f, 1.2f), 0.01f, k.c0, k.c1, k.alpha);
                        if (p != null) p.drag = 2.2f;
                    }
                }
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** A beam from the sky; the body lights up and is vaporised from the feet up. */
    private static void beam(BodyFx bf, EntityLivingBase victim, ResourceLocation skin, double x, double y, double z, float yaw, float height, final Look k) {
        BodyFx.Group gr0 = body(bf, victim, skin, x, y, z, yaw, height);
        final float dur = 0.9f * k.duration, feet = (float) y, top = (float) (y + height * 1.08);
        FxShapes.INSTANCE.beam(x, gr0.groundY, z, 0.55f, 18f, 0.35f + dur + 0.5f, k.c0, k.c1, k.alpha);
        gr0.life = 0.35f + dur + 0.05f;
        gr0.keepBelow = false;
        gr0.clipY = feet - 0.1f;
        gr0.glowColor = k.c0;
        gr0.script = (gr, dt) -> {
            gr.glow = smooth(gr.age / 0.3f);
            float u = smooth((gr.age - 0.35f) / dur);
            gr.clipY = feet - 0.1f + (top - feet + 0.1f) * u;
            if (u <= 0f || u >= 1f) return true;
            ParticleSystem ps = ParticleSystem.INSTANCE;
            gr.k[0] += dt * 150f;
            while (gr.k[0] >= 1f) {
                gr.k[0] -= 1f;
                float a = ps.rand(0f, 6.2831855f), rr = ps.rand(0f, 0.33f) * gr.scale;
                Particle p = ps.glow(gr.x + Math.cos(a) * rr, gr.clipY, gr.z + Math.sin(a) * rr, 0f, ps.rand(3f, 7f), 0f, ps.rand(0.3f, 0.6f),
                        k.size * ps.rand(0.4f, 0.8f), 0.01f, 0xFFFFFF, k.c0, k.alpha);
                if (p == null) break;
                p.stretch = 0.05f;
            }
            return true;
        };
        bf.add(gr0, victim);
    }

    /** Three rockets from where the target fell, bursting in your colours. */
    private static void fireworks(double x, double y, double z, float height, Look k) {
        FxShapes f = FxShapes.INSTANCE;
        f.rocket(x, y + 0.2, z, 6f, 0f, k.size, k.c0, k.c1, k.alpha);
        f.rocket(x + 0.6, y + 0.2, z - 0.4, 7.5f, 0.28f, k.size, k.c1, k.c0, k.alpha);
        f.rocket(x - 0.5, y + 0.2, z + 0.5, 9f, 0.56f, k.size, 0xFFFFFF, k.c0, k.alpha);
        ParticleSystem ps = ParticleSystem.INSTANCE;
        int n = ps.scale(k.amount * 0.6f);
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), sp = ps.rand(1.5f, 3.5f) * k.speed;
            Particle p = ps.glow(x, y + 0.2, z, (float) Math.cos(a) * sp, ps.rand(1f, 3f), (float) Math.sin(a) * sp, ps.rand(0.4f, 0.8f),
                    k.size * 0.6f, 0.01f, 0xFFE0A0, k.c0, k.alpha);
            if (p == null) continue;
            p.gravity = 8f;
            p.stretch = 0.03f;
        }
    }
}
