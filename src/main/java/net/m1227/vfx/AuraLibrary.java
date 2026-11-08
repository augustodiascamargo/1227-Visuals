package net.m1227.vfx;

import net.m1227.module.PerformanceModule;
import net.minecraft.entity.Entity;

/**
 * 24 aura styles shared by Target Aura (on the player you fight) and
 * Player Aura (on yourself / others). Three kinds of building blocks:
 * per-frame sprites (follow the entity exactly), per-frame luminous lines
 * (geometry) and emitted particles (leave a wake when the entity moves).
 */
public final class AuraLibrary {
    public static final String[] STYLES = {
            "Orbit", "Helix", "Rings", "Crystals", "Magic Circle", "Chains",
            "Rune Circle", "Halo Stack", "Gyro Rings", "Lightning Cage", "Flame", "Pulse Waves",
            "Star Orbit", "Hex Shield", "Triangle Seal", "Vortex", "Fireflies", "Star Crown",
            "Ghost Wisps", "Comet Ring", "Atom", "Petal Storm", "Light Pillars", "Target Lock"
    };

    /** Values read from the module's settings each frame (reused, no allocation). */
    public static final class Params {
        public int c0, c1;
        public float alpha, size, speed, amount, radius, life, fade;
        public boolean rainbow;

        int col(float phase, double t) {
            return rainbow ? ParticleSystem.rainbow((float) (t * 0.15 + phase)) : ParticleSystem.mix(c0, c1, phase);
        }
    }

    /** Emission bookkeeping per module (so styles that spawn particles keep a steady rate at any FPS). */
    public static final class State {
        float acc;
        int counter;
        double lastPulse = -1, lastArc = -1;
    }

    private static final double TAU = Math.PI * 2;
    // basis scratch for tilted rings
    private static final double[] U = new double[3], V = new double[3];

    private AuraLibrary() {}

    public static void draw(String style, Entity e, float pt, State st, Params p) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double cx = ParticleSystem.lerp(e.lastTickPosX, e.posX, pt);
        double cy = ParticleSystem.lerp(e.lastTickPosY, e.posY, pt);
        double cz = ParticleSystem.lerp(e.lastTickPosZ, e.posZ, pt);
        if (!ps.inRange(cx, cy, cz)) return;
        float h = e.height, rad = (e.width * 0.5f + 0.3f) * p.radius;
        double t = ps.time * p.speed;
        float q = PerformanceModule.qualityScale();
        int n = Math.max(1, Math.round(p.amount));

        if (style.equals("Orbit")) orbit(ps, cx, cy, cz, h, rad, t, n, q, st, p);
        else if (style.equals("Helix")) helix(ps, cx, cy, cz, h, rad, t, n, q, st, p);
        else if (style.equals("Rings")) rings(ps, cx, cy, cz, h, rad, t, n, q, st, p);
        else if (style.equals("Crystals")) AuraShapes.crystals(ps, cx, cy, cz, h, rad + 0.2f, Math.max(2, Math.round(n * q)), p.size, p.speed, p.c0, p.c1, p.alpha);
        else if (style.equals("Magic Circle")) {
            AuraShapes.magicCircle(ps, cx, cy, cz, rad * 1.6f, p.speed, p.c0, p.c1, p.alpha);
            AuraShapes.halo(ps, cx, cy, cz, h, rad, p.speed, p.c0, p.c1, p.alpha);
        } else if (style.equals("Chains")) chains(ps, cx, cy, cz, h, rad, t, q, p);
        else if (style.equals("Rune Circle")) runeCircle(ps, cx, cy, cz, h, rad, t, p);
        else if (style.equals("Halo Stack")) haloStack(ps, cx, cy, cz, h, rad, t, p);
        else if (style.equals("Gyro Rings")) gyro(cx, cy + h * 0.55, cz, rad * 1.15, t, p);
        else if (style.equals("Lightning Cage")) lightningCage(ps, cx, cy, cz, h, rad, st, p);
        else if (style.equals("Flame")) flame(ps, cx, cy, cz, rad, n, q, st, p);
        else if (style.equals("Pulse Waves")) pulses(ps, cx, cy, cz, h, rad, st, p);
        else if (style.equals("Star Orbit")) starOrbit(ps, cx, cy, cz, h, rad, t, n, p);
        else if (style.equals("Hex Shield")) hexShield(ps, cx, cy, cz, h, rad, t, p);
        else if (style.equals("Triangle Seal")) triangleSeal(cx, cy, cz, h, rad, t, p);
        else if (style.equals("Vortex")) vortex(ps, cx, cy, cz, h, rad, t, q, p);
        else if (style.equals("Fireflies")) fireflies(ps, cx, cy, cz, h, rad, t, n, p);
        else if (style.equals("Star Crown")) starCrown(ps, cx, cy, cz, h, t, n, p, e.isSneaking());
        else if (style.equals("Ghost Wisps")) wisps(ps, cx, cy, cz, h, rad, n, q, st, p);
        else if (style.equals("Comet Ring")) comets(ps, cx, cy, cz, h, rad, t, p);
        else if (style.equals("Atom")) atom(ps, cx, cy + h * 0.55, cz, rad * 1.1, t, p);
        else if (style.equals("Petal Storm")) petals(ps, cx, cy, cz, h, rad, t, q, p);
        else if (style.equals("Light Pillars")) pillars(ps, cx, cy, cz, h, rad, t, st, p);
        else targetLock(cx, cy, cz, h, rad, t, p);
    }

    // ---------------------------------------------------------- emitters

    private static void emit(ParticleSystem ps, double x, double y, double z, int c0, int c1, Params p, float life) {
        Particle q = ps.glow(x, y, z, 0f, 0.04f, 0f, life * ps.rand(0.8f, 1f), p.size, p.size * 0.2f, c0, c1, p.alpha);
        if (q != null) q.fadePow = p.fade;
    }

    private static int steps(ParticleSystem ps, State st, float rate, int cap) {
        st.acc += ps.dt * rate;
        int whole = (int) st.acc;
        st.acc -= whole;
        return Math.min(whole, cap);
    }

    private static void orbit(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, float q, State st, Params p) {
        int m = steps(ps, st, n * 45f * q, 32);
        for (int j = 0; j < m; j++) {
            st.counter = (st.counter + 1) % 1000;
            int k = st.counter % n;
            double a = t * 2.2 + k * (TAU / n);
            double y = cy + h * (0.5 + 0.42 * Math.sin(t * 1.4 + k * 1.9));
            emit(ps, cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad, p.col(k / (float) n, t), p.c1, p, p.life);
        }
    }

    private static void helix(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, float q, State st, Params p) {
        int m = steps(ps, st, n * 45f * q, 32);
        for (int j = 0; j < m; j++) {
            double u = (t * 0.35) % 1.0;
            double a = u * Math.PI * 5.0 + (j & 1) * Math.PI;
            float r = rad * (float) (0.8 + 0.2 * Math.sin(u * Math.PI));
            emit(ps, cx + Math.cos(a) * r, cy + u * h, cz + Math.sin(a) * r, p.col((float) u, t), p.c1, p, p.life);
        }
    }

    private static void rings(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, float q, State st, Params p) {
        if (steps(ps, st, 26f, 1) == 0) return;
        double u = (t * 0.45) % 1.0;
        float r = rad * (float) (0.85 + 0.25 * Math.sin(u * Math.PI));
        int pts = Math.max(8, Math.round((8 + n * 2) * q));
        for (int i = 0; i < pts; i++) {
            double a = i * (TAU / pts) + t;
            emit(ps, cx + Math.cos(a) * r, cy + u * h, cz + Math.sin(a) * r, p.col((float) u, t), p.c1, p, p.life * 0.45f);
        }
    }

    private static void flame(ParticleSystem ps, double cx, double cy, double cz, float rad, int n, float q, State st, Params p) {
        int m = steps(ps, st, n * 14f * q, 16);
        int hot = ParticleSystem.mix(0xFFB347, p.c0, 0.3f), deep = ParticleSystem.mix(0xFF3D1F, p.c1, 0.3f);
        for (int j = 0; j < m; j++) {
            float a = ps.rand(0f, (float) TAU), r = rad * ps.rand(0.5f, 1.05f);
            float s = p.size * ps.rand(1.6f, 2.6f);
            Particle f = ps.glow(cx + Math.cos(a) * r, cy + 0.05, cz + Math.sin(a) * r, 0f, ps.rand(0.9f, 1.8f) * p.speed, 0f,
                    p.life * ps.rand(0.7f, 1.1f), s, s * 0.25f, hot, deep, p.alpha);
            if (f == null) continue;
            f.sprite = Sprite.FLAME;
            f.sway = 0.25f;
            f.swayFreq = ps.rand(3f, 6f);
            f.swayPhase = ps.rand(0f, 6.28f);
            f.fadePow = p.fade;
        }
    }

    private static void pulses(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, State st, Params p) {
        double interval = 0.6 / Math.max(0.2, p.speed);
        if (st.lastPulse >= 0 && ps.time - st.lastPulse < interval) return;
        st.lastPulse = ps.time;
        Particle r = ps.glow(cx, cy + 0.05, cz, 0f, 0f, 0f, 0.9f, rad * 0.4f, rad * 2.4f, p.c0, p.c1, p.alpha);
        if (r != null) {
            r.sprite = Sprite.RING;
            r.flat = true;
            r.fadePow = 1.4f;
        }
        Particle v = ps.glow(cx, cy + h * 0.5, cz, 0f, 0f, 0f, 0.5f, rad * 0.6f, rad * 1.8f, p.c1, p.c0, p.alpha * 0.4f);
        if (v != null) {
            v.sprite = Sprite.HALO;
            v.fadePow = 1.6f;
        }
    }

    private static void lightningCage(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, State st, Params p) {
        if (st.lastArc >= 0 && ps.time - st.lastArc < 0.1 / Math.max(0.2, p.speed)) return;
        st.lastArc = ps.time;
        double a = ps.rand(0f, (float) TAU);
        double r = rad * 1.05;
        Arcs.INSTANCE.spawn(cx + Math.cos(a) * r, cy + 0.05, cz + Math.sin(a) * r,
                cx + Math.cos(a + 0.5) * r, cy + h + 0.1, cz + Math.sin(a + 0.5) * r,
                0.18f, 0.12f, ParticleSystem.mix(p.c0, p.c1, ps.rng.nextFloat()), p.alpha);
    }

    private static void wisps(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, int n, float q, State st, Params p) {
        int m = steps(ps, st, n * 6f * q, 8);
        for (int j = 0; j < m; j++) {
            float a = ps.rand(0f, (float) TAU), r = rad * ps.rand(0.6f, 1.2f);
            Particle w = ps.glow(cx + Math.cos(a) * r, cy + ps.rand(0.1f, h * 0.6f), cz + Math.sin(a) * r,
                    0f, ps.rand(0.3f, 0.6f) * p.speed, 0f, p.life * 2.5f, p.size * 2f, p.size * 4f, p.c0, p.c1, p.alpha * 0.35f);
            if (w == null) continue;
            w.sprite = Sprite.SMOKE;
            w.rot = ps.rand(0f, 6.28f);
            w.rotVel = ps.rand(-1f, 1f);
            w.sway = 0.4f;
            w.swayFreq = ps.rand(1f, 2f);
            w.swayPhase = ps.rand(0f, 6.28f);
            w.fadePow = 1.3f;
        }
    }

    // --------------------------------------------------- per-frame shapes

    private static void chains(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, float q, Params p) {
        int links = Math.max(10, Math.round(22 * q));
        for (int c = 0; c < 2; c++) {
            double tilt = c == 0 ? 0.35 : -0.35;
            double mid = cy + h * (c == 0 ? 0.62 : 0.38);
            for (int i = 0; i < links; i++) {
                double a = t * 0.6 * (c == 0 ? 1 : -1) + i * (TAU / links);
                float rot = (float) ((i & 1) == 0 ? a : a + Math.PI / 2);
                AuraShapes.sprite(ps, Sprite.LINK, cx + Math.cos(a) * rad, mid + Math.sin(a) * tilt, cz + Math.sin(a) * rad,
                        p.size * ((i & 1) == 0 ? 1.1f : 0.8f), rot, false, p.col((i & 1) * 0.5f, t), p.alpha);
            }
        }
    }

    private static void runeCircle(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, Params p) {
        float pulse = (float) (0.85 + 0.15 * Math.sin(t * 2.0));
        AuraShapes.sprite(ps, Sprite.RUNE, cx, cy + 0.03, cz, rad * 1.7f, (float) (t * 0.5), true, p.c0, p.alpha * pulse);
        AuraShapes.sprite(ps, Sprite.GLOW, cx, cy + 0.035, cz, rad * 2f, 0f, true, p.c1, p.alpha * 0.25f);
        for (int i = 0; i < 3; i++) {
            double a = -t * 0.9 + i * TAU / 3;
            AuraShapes.sprite(ps, Sprite.RUNE, cx + Math.cos(a) * rad * 1.1, cy + h * 0.6 + Math.sin(t * 2 + i) * 0.08,
                    cz + Math.sin(a) * rad * 1.1, p.size * 1.4f, (float) (t + i), false, p.c1, p.alpha * 0.9f);
        }
    }

    private static void haloStack(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, Params p) {
        for (int k = 0; k < 3; k++) {
            float f = (k + 1) / 4f;
            float s = rad * (1.05f + 0.12f * (float) Math.sin(t * 2.5 + k * 1.3));
            AuraShapes.sprite(ps, Sprite.HALO, cx, cy + h * f * 1.1, cz, s, (float) (t * (k % 2 == 0 ? 0.8 : -0.8)), true,
                    p.col(k / 2f, t), p.alpha * (0.6f + 0.4f * (float) Math.sin(t * 3 + k)));
        }
    }

    /** Fills U and V with two unit vectors perpendicular to the normal given by yaw/pitch. */
    private static void basis(double yaw, double pitch) {
        double nx = Math.cos(pitch) * Math.cos(yaw), ny = Math.sin(pitch), nz = Math.cos(pitch) * Math.sin(yaw);
        double ux = nz, uy = 0, uz = -nx;
        double l = Math.sqrt(ux * ux + uz * uz);
        if (l < 1e-4) {
            ux = 1;
            uz = 0;
            l = 1;
        }
        U[0] = ux / l;
        U[1] = uy;
        U[2] = uz / l;
        V[0] = ny * U[2] - nz * U[1];
        V[1] = nz * U[0] - nx * U[2];
        V[2] = nx * U[1] - ny * U[0];
    }

    private static void ring3d(double cx, double cy, double cz, double r, double yaw, double pitch, int seg, int rgb, float a) {
        basis(yaw, pitch);
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= seg; i++) {
            double ang = i * TAU / seg, c = Math.cos(ang) * r, s = Math.sin(ang) * r;
            double x = cx + U[0] * c + V[0] * s, y = cy + U[1] * c + V[1] * s, z = cz + U[2] * c + V[2] * s;
            if (i > 0) Lines.INSTANCE.add(px, py, pz, x, y, z, rgb, a);
            px = x;
            py = y;
            pz = z;
        }
    }

    private static void gyro(double cx, double cy, double cz, double r, double t, Params p) {
        for (int k = 0; k < 3; k++) {
            ring3d(cx, cy, cz, r * (1 - k * 0.08), t * (0.7 + k * 0.3) + k * 2.1, 0.5 + 0.6 * Math.sin(t * 0.5 + k), 40,
                    p.col(k / 2f, t), p.alpha);
        }
    }

    private static void starOrbit(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, Params p) {
        int count = Math.max(4, Math.min(16, n + 4));
        for (int o = 0; o < 2; o++) {
            double tilt = o == 0 ? 0.3 : -0.3, mid = cy + h * (o == 0 ? 0.7 : 0.35);
            for (int i = 0; i < count; i++) {
                double a = t * (o == 0 ? 1.1 : -0.8) + i * TAU / count;
                float tw = (float) (0.55 + 0.45 * Math.sin(t * 4 + i * 1.7));
                AuraShapes.sprite(ps, Sprite.STAR, cx + Math.cos(a) * rad * 1.15, mid + Math.sin(a) * tilt, cz + Math.sin(a) * rad * 1.15,
                        p.size * (0.6f + 0.5f * tw), (float) (t + i), false, p.col(i / (float) count, t), p.alpha * tw);
            }
        }
    }

    private static void hexShield(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, Params p) {
        double r = rad * 1.15, rot = t * 0.5;
        Lines L = Lines.INSTANCE;
        float a = p.alpha * (0.75f + 0.25f * (float) Math.sin(t * 3));
        for (int level = 0; level < 3; level++) {
            double y = cy + 0.05 + level * (h + 0.1) / 2;
            L.polygon(cx, y, cz, r, 6, rot + level * 0.0, 0, p.col(level / 2f, t), a);
        }
        for (int i = 0; i < 6; i++) {
            double ang = rot + i * TAU / 6;
            double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
            L.add(x, cy + 0.05, z, x, cy + h + 0.15, z, p.c1, a * 0.7f);
        }
        AuraShapes.sprite(ps, Sprite.HEX, cx, cy + 0.04, cz, (float) r * 1.2f, (float) rot, true, p.c0, a * 0.6f);
    }

    private static void triangleSeal(double cx, double cy, double cz, float h, float rad, double t, Params p) {
        Lines L = Lines.INSTANCE;
        double r = rad * 1.5;
        L.polygon(cx, cy + 0.04, cz, r, 48, 0, 0, p.c0, p.alpha);
        L.polygon(cx, cy + 0.04, cz, r * 0.82, 48, 0, 0, p.c1, p.alpha * 0.6f);
        L.star(cx, cy + 0.045, cz, r * 0.82, 6, 2, t * 0.6, p.c0, p.alpha);
        L.star(cx, cy + 0.045, cz, r * 0.42, 6, 2, -t * 0.9, p.c1, p.alpha * 0.8f);
        L.star(cx, cy + h + 0.35, cz, rad * 0.55, 3, 1, -t, p.c1, p.alpha * 0.8f);
    }

    private static void vortex(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, float q, Params p) {
        int pts = Math.max(16, Math.round(40 * q));
        for (int arm = 0; arm < 2; arm++) {
            for (int i = 0; i < pts; i++) {
                double u = ((i / (double) pts) + t * 0.25) % 1.0;
                double a = u * TAU * 2 + t * 2 + arm * Math.PI;
                double r = rad * (1.35 - 0.85 * u);
                float al = (float) Math.sin(Math.PI * u);
                AuraShapes.sprite(ps, Sprite.GLOW, cx + Math.cos(a) * r, cy + u * h * 1.15, cz + Math.sin(a) * r,
                        p.size * (0.9f - 0.4f * (float) u), 0f, false, p.col((float) u, t), p.alpha * al);
            }
        }
    }

    private static void fireflies(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, Params p) {
        int count = Math.max(4, Math.min(20, n * 2));
        for (int i = 0; i < count; i++) {
            double s = i * 12.9898;
            double x = cx + Math.sin(t * 0.7 + s) * rad * 1.3 + Math.sin(t * 1.3 + s * 2) * 0.15;
            double y = cy + h * (0.15 + 0.85 * (0.5 + 0.5 * Math.sin(t * 0.5 + s * 3)));
            double z = cz + Math.cos(t * 0.6 + s * 1.7) * rad * 1.3;
            float blink = (float) Math.max(0, Math.sin(t * 2.2 + s * 5));
            AuraShapes.sprite(ps, Sprite.GLOW, x, y, z, p.size * 0.7f, 0f, false, p.col(i / (float) count, t), p.alpha * (0.15f + 0.85f * blink));
        }
    }

    private static void starCrown(ParticleSystem ps, double cx, double cy, double cz, float h, double t, int n, Params p, boolean sneak) {
        double head = cy + h - (sneak ? 0.2 : 0) + 0.2 + Math.sin(t * 2) * 0.03;
        int count = Math.max(5, Math.min(12, n + 4));
        for (int i = 0; i < count; i++) {
            double a = t * 1.2 + i * TAU / count;
            float tw = (float) (0.6 + 0.4 * Math.sin(t * 4 + i));
            AuraShapes.sprite(ps, Sprite.STAR, cx + Math.cos(a) * 0.38, head + Math.sin(t * 3 + i) * 0.03, cz + Math.sin(a) * 0.38,
                    p.size * 0.9f * tw, (float) (t + i), false, p.col(i / (float) count, t), p.alpha * tw);
        }
        AuraShapes.sprite(ps, Sprite.RING, cx, head - 0.02, cz, 0.42f, (float) t, true, p.c0, p.alpha * 0.6f);
    }

    private static void comets(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, Params p) {
        for (int c = 0; c < 3; c++) {
            double tilt = (c - 1) * 0.45, mid = cy + h * 0.55;
            for (int k = 0; k < 12; k++) {
                double a = t * 2.4 + c * TAU / 3 - k * 0.09;
                float f = 1f - k / 12f;
                AuraShapes.sprite(ps, Sprite.GLOW, cx + Math.cos(a) * rad * 1.2, mid + Math.sin(a) * tilt, cz + Math.sin(a) * rad * 1.2,
                        p.size * (0.3f + 0.9f * f), 0f, false, k == 0 ? 0xFFFFFF : p.col(c / 2f, t), p.alpha * f);
            }
        }
    }

    private static void atom(ParticleSystem ps, double cx, double cy, double cz, double r, double t, Params p) {
        AuraShapes.sprite(ps, Sprite.GLOW, cx, cy, cz, p.size * 1.6f, 0f, false, p.c1, p.alpha * 0.7f);
        for (int k = 0; k < 3; k++) {
            double yaw = k * Math.PI / 3 + t * 0.2, pitch = 1.1;
            int col = p.col(k / 2f, t);
            ring3d(cx, cy, cz, r, yaw, pitch, 36, col, p.alpha * 0.55f);
            basis(yaw, pitch);
            double ang = t * (2.5 + k * 0.4) + k * 2;
            double c = Math.cos(ang) * r, s = Math.sin(ang) * r;
            AuraShapes.sprite(ps, Sprite.GLOW, cx + U[0] * c + V[0] * s, cy + U[1] * c + V[1] * s, cz + U[2] * c + V[2] * s,
                    p.size * 0.8f, 0f, false, 0xFFFFFF, p.alpha);
        }
    }

    private static void petals(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, float q, Params p) {
        int count = Math.max(10, Math.round(26 * q));
        for (int i = 0; i < count; i++) {
            double s = i * 7.31;
            double u = ((i / (double) count) + t * 0.12) % 1.0;
            double a = t * 1.4 + s + u * 3;
            double r = rad * (1.0 + 0.35 * Math.sin(s + t));
            AuraShapes.sprite(ps, Sprite.PETAL, cx + Math.cos(a) * r, cy + u * (h + 0.3), cz + Math.sin(a) * r,
                    p.size * 0.9f, (float) (t * 3 + s), false, p.col((float) u, t), p.alpha * (float) Math.sin(Math.PI * u));
        }
    }

    private static void pillars(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, State st, Params p) {
        for (int i = 0; i < 4; i++) {
            double a = t * 0.4 + i * TAU / 4;
            double x = cx + Math.cos(a) * rad * 1.3, z = cz + Math.sin(a) * rad * 1.3;
            float pulse = (float) (0.6 + 0.4 * Math.sin(t * 3 + i * 1.6));
            AuraShapes.sprite(ps, Sprite.BEAM, x, cy + h * 0.6, z, h * 0.75f, 0f, false, p.col(i / 3f, t), p.alpha * pulse);
            AuraShapes.sprite(ps, Sprite.RING, x, cy + 0.04, z, 0.25f, (float) t, true, p.c1, p.alpha * pulse);
        }
        int m = steps(ps, st, 18f * PerformanceModule.qualityScale(), 4);
        for (int j = 0; j < m; j++) {
            double a = ps.rand(0f, (float) TAU);
            Particle s = ps.glow(cx + Math.cos(a) * rad * 1.3, cy + 0.1, cz + Math.sin(a) * rad * 1.3, 0f, ps.rand(1f, 2f), 0f,
                    p.life, p.size * 0.4f, 0f, 0xFFFFFF, p.c1, p.alpha);
            if (s != null) s.fadePow = p.fade;
        }
    }

    private static void targetLock(double cx, double cy, double cz, float h, float rad, double t, Params p) {
        Lines L = Lines.INSTANCE;
        double r = rad * 1.25, rot = t * 0.6, len = 0.28;
        float a = p.alpha * (0.7f + 0.3f * (float) Math.sin(t * 5));
        for (int i = 0; i < 4; i++) {
            double ang = rot + i * Math.PI / 2;
            double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
            double tx = -Math.sin(ang) * len, tz = Math.cos(ang) * len;
            for (int lv = 0; lv < 2; lv++) {
                double y = lv == 0 ? cy + 0.05 : cy + h + 0.1;
                double dy = lv == 0 ? len : -len;
                L.add(x - tx, y, z - tz, x + tx, y, z + tz, p.c0, a);
                L.add(x, y, z, x, y + dy, z, p.c0, a);
            }
        }
        L.polygon(cx, cy + 0.04, cz, r * 0.7, 40, 0, 0, p.c1, a * 0.6f);
    }
}
