package net.m1227.vfx;

/**
 * Fifteen more aura styles (1.26): celestial rings, a crystal orbit, an energy cage, floating
 * runes, a void circle, a digital lock, a spirit halo, an arcane sigil, a gravity ring, a
 * lightning orbit, neon geometry, shattered glass, floating cards, an energy core and a
 * starfield. Built like the others: per-frame luminous lines and sprites that follow the entity
 * exactly (depth tested: never seen through walls) plus a few emitted particles. Every style
 * moves with time, so speed, radius, height (the entity's), amount, size, colours, opacity and
 * the rainbow option all apply.
 */
final class AuraStyles2 {
    private AuraStyles2() {}

    static final String[] STYLES = {"Celestial Rings", "Crystal Orbit", "Energy Cage", "Floating Runes", "Void Circle", "Digital Lock",
            "Spirit Halo", "Arcane Sigil", "Gravity Ring", "Lightning Orbit", "Neon Geometry", "Shattered Glass", "Floating Cards",
            "Energy Core", "Starfield"};

    private static final double TAU = Math.PI * 2;

    static boolean has(String s) {
        for (String x : STYLES) if (x.equals(s)) return true;
        return false;
    }

    static void draw(String style, ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, float q,
                     AuraLibrary.State st, AuraLibrary.Params p) {
        switch (style) {
            case "Celestial Rings":
                celestial(ps, cx, cy, cz, h, rad, t, p);
                break;
            case "Crystal Orbit":
                crystalOrbit(ps, cx, cy, cz, h, rad, t, n, p);
                break;
            case "Energy Cage":
                cage(cx, cy, cz, h, rad, t, p);
                break;
            case "Floating Runes":
                runes(ps, cx, cy, cz, h, rad, t, n, p);
                break;
            case "Void Circle":
                voidCircle(ps, cx, cy, cz, rad, t, q, st, p);
                break;
            case "Digital Lock":
                digital(ps, cx, cy, cz, h, rad, t, p);
                break;
            case "Spirit Halo":
                spirit(ps, cx, cy, cz, h, rad, t, q, st, p);
                break;
            case "Arcane Sigil":
                sigil(ps, cx, cy, cz, h, rad, t, p);
                break;
            case "Gravity Ring":
                gravity(ps, cx, cy, cz, h, rad, t, n, p);
                break;
            case "Lightning Orbit":
                lightning(ps, cx, cy, cz, h, rad, t, n, st, p);
                break;
            case "Neon Geometry":
                neon(cx, cy, cz, h, rad, t, p);
                break;
            case "Shattered Glass":
                glass(ps, cx, cy, cz, h, rad, t, n, p);
                break;
            case "Floating Cards":
                cards(cx, cy, cz, h, rad, t, n, p);
                break;
            case "Energy Core":
                core(ps, cx, cy, cz, h, rad, t, st, p);
                break;
            default:
                starfield(ps, cx, cy, cz, h, rad, t, n, p);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Ring with normal given by yaw / pitch (radians). */
    private static void ring(double cx, double cy, double cz, double r, double yaw, double pitch, int seg, int rgb, float a) {
        double nx = Math.cos(pitch) * Math.cos(yaw), ny = Math.sin(pitch), nz = Math.cos(pitch) * Math.sin(yaw);
        double ux = nz, uz = -nx, l = Math.sqrt(ux * ux + uz * uz);
        if (l < 1e-4) {
            ux = 1;
            uz = 0;
            l = 1;
        }
        ux /= l;
        uz /= l;
        double vx = ny * uz, vy = nz * ux - nx * uz, vz = -ny * ux;
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= seg; i++) {
            double ang = i * TAU / seg, c = Math.cos(ang) * r, s = Math.sin(ang) * r;
            double x = cx + ux * c + vx * s, y = cy + vy * s, z = cz + uz * c + vz * s;
            if (i > 0) Lines.INSTANCE.add(px, py, pz, x, y, z, rgb, a);
            px = x;
            py = y;
            pz = z;
        }
    }

    /** Point on that ring at angle ang. */
    private static double[] onRing(double cx, double cy, double cz, double r, double yaw, double pitch, double ang) {
        double nx = Math.cos(pitch) * Math.cos(yaw), ny = Math.sin(pitch), nz = Math.cos(pitch) * Math.sin(yaw);
        double ux = nz, uz = -nx, l = Math.sqrt(ux * ux + uz * uz);
        if (l < 1e-4) {
            ux = 1;
            uz = 0;
            l = 1;
        }
        ux /= l;
        uz /= l;
        double vx = ny * uz, vy = nz * ux - nx * uz, vz = -ny * ux;
        double c = Math.cos(ang) * r, s = Math.sin(ang) * r;
        return new double[]{cx + ux * c + vx * s, cy + vy * s, cz + uz * c + vz * s};
    }

    /** Stable pseudo-random 0..1 from an index (for fixed positions). */
    private static float hash(int i, int salt) {
        int x = i * 374761393 + salt * 668265263;
        x = (x ^ (x >>> 13)) * 1274126177;
        return ((x ^ (x >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    // ------------------------------------------------------------------ styles

    private static void celestial(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.Params p) {
        double mid = cy + h * 0.55;
        for (int k = 0; k < 3; k++) {
            double r = rad * (1.15 + 0.22 * k), yaw = t * (0.25 + 0.1 * k) + k * 2.1, pitch = 1.1 + 0.25 * Math.sin(t * 0.4 + k);
            int col = p.col(k / 2f, t);
            ring(cx, mid, cz, r, yaw, pitch, 48, col, p.alpha * 0.8f);
            // a planet travelling on each ring, with a faint moon
            double ang = t * (1.2 - 0.3 * k) + k;
            double[] q = onRing(cx, mid, cz, r, yaw, pitch, ang);
            AuraShapes.sprite(ps, Sprite.GLOW, q[0], q[1], q[2], p.size * (2.4f - k * 0.4f), 0f, false, col, p.alpha);
            double[] m = onRing(cx, mid, cz, r, yaw, pitch, ang + 0.35);
            AuraShapes.sprite(ps, Sprite.STAR, m[0], m[1], m[2], p.size * 0.9f, (float) t, false, 0xFFFFFF, p.alpha * 0.8f);
        }
        for (int i = 0; i < 10; i++) {
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 3 + i * 2.3));
            double a = hash(i, 1) * TAU + t * 0.1, r = rad * (1.2 + hash(i, 2) * 0.8);
            AuraShapes.sprite(ps, Sprite.STAR, cx + Math.cos(a) * r, cy + hash(i, 3) * h * 1.2, cz + Math.sin(a) * r, p.size * 0.6f * tw, 0f, false,
                    p.c1, p.alpha * tw);
        }
    }

    private static void crystalOrbit(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        int count = Math.max(3, Math.min(12, n));
        double mid = cy + h * 0.6, r = rad * 1.25, tilt = 0.35;
        ring(cx, mid, cz, r, 0.6, Math.PI / 2 - tilt, 48, p.c1, p.alpha * 0.35f);
        for (int i = 0; i < count; i++) {
            double a = t * 0.9 + i * TAU / count;
            double[] q = onRing(cx, mid, cz, r, 0.6, Math.PI / 2 - tilt, a);
            double bob = 0.06 * Math.sin(t * 2.4 + i);
            AuraShapes.sprite(ps, Sprite.CRYSTAL, q[0], q[1] + bob, q[2], p.size * 2.2f, (float) (t * 1.5 + i), false, p.col(i / (float) count, t), p.alpha);
            AuraShapes.sprite(ps, Sprite.GLOW, q[0], q[1] + bob, q[2], p.size * 3f, 0f, false, p.c1, p.alpha * 0.25f);
        }
    }

    private static void cage(double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.Params p) {
        Lines L = Lines.INSTANCE;
        int bars = 8;
        double r = rad * 1.1, rot = t * 0.35, top = cy + h + 0.2;
        L.polygon(cx, cy + 0.03, cz, r, 32, rot, 0, p.c0, p.alpha);
        L.polygon(cx, top, cz, r * 0.85, 32, rot, 0, p.c0, p.alpha);
        // a band sweeping up the cage
        double u = (t * 0.5) % 1.0;
        L.polygon(cx, cy + 0.03 + u * (top - cy), cz, r * (1 + 0.12 * Math.sin(u * Math.PI)), 32, rot, 0, p.c1, p.alpha * (float) Math.sin(u * Math.PI));
        for (int i = 0; i < bars; i++) {
            double a = rot + i * TAU / bars;
            double px = cx + Math.cos(a) * r, pz = cz + Math.sin(a) * r, py = cy + 0.03;
            int seg = 8;
            for (int s = 1; s <= seg; s++) {
                double v = s / (double) seg;
                double bow = 1 + 0.12 * Math.sin(v * Math.PI);
                double rr = r * (1 - 0.15 * v) * bow;
                double x = cx + Math.cos(a) * rr, z = cz + Math.sin(a) * rr, y = cy + 0.03 + v * (top - cy - 0.03);
                // a pulse running up each bar
                double pulse = Math.max(0, 1 - Math.abs(((t * 1.3 + i * 0.13) % 1.0) - v) * 6);
                L.add(px, py, pz, x, y, z, ParticleSystem.mix(p.c0, 0xFFFFFF, (float) pulse * 0.7f), p.alpha * (0.6f + 0.4f * (float) pulse));
                px = x;
                py = y;
                pz = z;
            }
        }
    }

    private static void runes(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        int count = Math.max(4, Math.min(12, n));
        for (int i = 0; i < count; i++) {
            double a = t * 0.4 * (i % 2 == 0 ? 1 : -1) + i * TAU / count, r = rad * (1.15 + 0.15 * hash(i, 5));
            double y = cy + h * (0.25 + 0.6 * hash(i, 6)) + 0.12 * Math.sin(t * 1.7 + i);
            float fl = (float) (0.65 + 0.35 * Math.sin(t * 5 + i * 1.3));
            AuraShapes.sprite(ps, Sprite.RUNE, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, p.size * 2.4f, (float) (t * 0.3 + i), false,
                    p.col(i / (float) count, t), p.alpha * fl);
        }
        Lines.INSTANCE.polygon(cx, cy + 0.03, cz, rad * 1.3, 40, t * 0.2, 0, p.c1, p.alpha * 0.4f);
    }

    private static void voidCircle(ParticleSystem ps, double cx, double cy, double cz, float rad, double t, float q, AuraLibrary.State st,
                                   AuraLibrary.Params p) {
        double r = rad * 1.5, y = cy + 0.035;
        Particle dark = ps.frame(cx, y, cz);
        if (dark != null) {
            dark.sprite = Sprite.CIRCLE;
            dark.flat = true;
            dark.size0 = dark.size1 = (float) r * 2f;
            dark.setColors(0x07040C, 0x07040C);
            dark.alpha = 0.85f * p.alpha;
            dark.alphaBlend = true;
        }
        Lines L = Lines.INSTANCE;
        L.polygon(cx, y + 0.005, cz, r, 48, t * 0.3, 0, p.c0, p.alpha);
        L.polygon(cx, y + 0.005, cz, r * 1.08, 48, -t * 0.2, 0, p.c1, p.alpha * 0.5f);
        for (int i = 0; i < 6; i++) {
            double a = t * 0.8 + i * TAU / 6;
            L.add(cx + Math.cos(a) * r * 0.25, y + 0.006, cz + Math.sin(a) * r * 0.25, cx + Math.cos(a + 0.5) * r * 0.95, y + 0.006,
                    cz + Math.sin(a + 0.5) * r * 0.95, p.c0, p.alpha * 0.5f);
        }
        // motes sinking into it
        st.acc += ps.dt * 20f * q;
        while (st.acc >= 1f) {
            st.acc -= 1f;
            double a = ps.rand(0f, (float) TAU), rr = r * ps.rand(0.2f, 1f);
            Particle m = ps.glow(cx + Math.cos(a) * rr, cy + ps.rand(0.3f, 1.2f), cz + Math.sin(a) * rr, 0f, -ps.rand(0.6f, 1.2f), 0f, 0.9f,
                    p.size * 0.7f, 0.01f, p.c0, p.c1, p.alpha);
            if (m != null) m.fadePow = p.fade;
        }
    }

    private static void digital(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.Params p) {
        Lines L = Lines.INSTANCE;
        double r = rad * 1.2, rot = t * 0.5, len = r * 0.35;
        float a = p.alpha * (0.75f + 0.25f * (float) Math.sin(t * 7));
        double[] ys = {cy + 0.05, cy + h + 0.15};
        for (int i = 0; i < 4; i++) {
            double ang = rot + Math.PI / 4 + i * Math.PI / 2;
            double x = cx + Math.cos(ang) * r, z = cz + Math.sin(ang) * r;
            double ex = -Math.cos(ang) * len, ez = -Math.sin(ang) * len;
            double side = ang + Math.PI / 2, sx = Math.cos(side) * len * 0.7, sz = Math.sin(side) * len * 0.7;
            for (int lv = 0; lv < 2; lv++) {
                double y = ys[lv], dy = lv == 0 ? len : -len;
                // corner bracket: two arms along the box edges and one up / down
                L.add(x, y, z, x + sx, y, z + sz, p.c0, a);
                L.add(x, y, z, x - sx, y, z - sz, p.c0, a);
                L.add(x, y, z, x, y + dy, z, p.c0, a);
            }
            L.add(x, cy + 0.05, z, x + ex * 0.2, cy + 0.05, z + ez * 0.2, p.c1, a * 0.5f);
        }
        // scan line going up and down
        double u = 0.5 + 0.5 * Math.sin(t * 2.0);
        double y = cy + 0.05 + u * h;
        L.polygon(cx, y, cz, r, 4, rot + Math.PI / 4, 0, ParticleSystem.mix(0x2EF6FF, p.c1, 0.4f), p.alpha * 0.8f);
        // ticking data pixels
        int k = (int) (t * 8);
        for (int i = 0; i < 6; i++) {
            float on = hash(i + k * 7, 9);
            if (on < 0.45f) continue;
            double ang = hash(i, 10) * TAU, rr = r * 1.02;
            AuraShapes.sprite(ps, Sprite.HEX, cx + Math.cos(ang) * rr, cy + hash(i + k, 11) * h, cz + Math.sin(ang) * rr, p.size * 0.9f, 0f, false,
                    i % 2 == 0 ? 0xFF2E5A : 0x2EF6FF, p.alpha * 0.8f);
        }
    }

    private static void spirit(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, float q, AuraLibrary.State st,
                               AuraLibrary.Params p) {
        double top = cy + h + 0.3 + 0.04 * Math.sin(t * 2);
        AuraShapes.sprite(ps, Sprite.HALO, cx, top, cz, rad * 1.1f, (float) (t * 0.6), true, p.c0, p.alpha);
        Lines.INSTANCE.polygon(cx, top, cz, rad * 0.5, 32, t, 0, p.c1, p.alpha * 0.8f);
        st.acc += ps.dt * 14f * q;
        while (st.acc >= 1f) {
            st.acc -= 1f;
            double a = ps.rand(0f, (float) TAU), r = rad * ps.rand(0.6f, 1.1f);
            Particle w = ps.glow(cx + Math.cos(a) * r, cy + ps.rand(0f, 0.6f), cz + Math.sin(a) * r, -(float) Math.sin(a) * 0.3f, ps.rand(0.5f, 0.9f),
                    (float) Math.cos(a) * 0.3f, p.life * 1.4f, p.size * 1.6f, p.size * 0.4f, p.c1, p.c0, p.alpha * 0.6f);
            if (w != null) {
                w.fadePow = p.fade;
                w.drag = 0.6f;
            }
        }
    }

    private static void sigil(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.Params p) {
        Lines L = Lines.INSTANCE;
        double r = rad * 1.6, y = cy + 0.04;
        L.polygon(cx, y, cz, r, 56, 0, 0, p.c0, p.alpha);
        L.polygon(cx, y, cz, r * 0.9, 56, 0, 0, p.c1, p.alpha * 0.6f);
        L.polygon(cx, y + 0.002, cz, r * 0.72, 5, t * 0.4, 0, p.c0, p.alpha * 0.9f);
        L.star(cx, y + 0.003, cz, r * 0.72, 5, 2, t * 0.4, p.c1, p.alpha);
        L.polygon(cx, y + 0.004, cz, r * 0.32, 40, 0, 0, p.c0, p.alpha * 0.8f);
        int glyphs = 10;
        for (int i = 0; i < glyphs; i++) {
            double a = -t * 0.3 + i * TAU / glyphs;
            AuraShapes.sprite(ps, Sprite.RUNE, cx + Math.cos(a) * r * 0.95, y + 0.01, cz + Math.sin(a) * r * 0.95, p.size * 1.6f, (float) (-a), true,
                    p.col(i / (float) glyphs, t), p.alpha);
        }
        // small sigil above the head
        L.star(cx, cy + h + 0.4, cz, rad * 0.45, 5, 2, -t * 0.8, p.c0, p.alpha * 0.9f);
    }

    private static void gravity(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        double u = 0.5 + 0.5 * Math.sin(t * 1.2);
        double y = cy + 0.1 + u * h;
        double squeeze = 1 - 0.25 * Math.abs(Math.cos(t * 1.2));
        double r = rad * 1.2 * squeeze;
        ring(cx, y, cz, r, t * 0.5, Math.PI / 2, 48, p.c0, p.alpha);
        ring(cx, y, cz, r * 1.12, -t * 0.3, Math.PI / 2 - 0.12, 48, p.c1, p.alpha * 0.5f);
        int count = Math.max(4, Math.min(14, n + 2));
        for (int i = 0; i < count; i++) {
            double a = t * 1.4 + i * TAU / count;
            // debris drawn towards the ring's height
            double dy = (hash(i, 12) - 0.5) * 0.6 * (1 - 0.5 * Math.abs(Math.sin(t * 2 + i)));
            double rr = r * (1.05 + 0.25 * hash(i, 13));
            AuraShapes.sprite(ps, i % 3 == 0 ? Sprite.SHARD : Sprite.GLOW, cx + Math.cos(a) * rr, y + dy, cz + Math.sin(a) * rr,
                    p.size * (i % 3 == 0 ? 1.4f : 0.9f), (float) (t * 2 + i), false, p.col(i / (float) count, t), p.alpha * 0.9f);
        }
    }

    private static void lightning(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.State st,
                                  AuraLibrary.Params p) {
        int count = Math.max(3, Math.min(8, n / 2 + 2));
        double mid = cy + h * 0.55, r = rad * 1.2;
        double[][] pts = new double[count][];
        for (int i = 0; i < count; i++) {
            double a = t * 1.6 + i * TAU / count;
            pts[i] = new double[]{cx + Math.cos(a) * r, mid + 0.35 * Math.sin(t * 2.1 + i * 1.7), cz + Math.sin(a) * r};
            AuraShapes.sprite(ps, Sprite.GLOW, pts[i][0], pts[i][1], pts[i][2], p.size * 2f, 0f, false, p.col(i / (float) count, t), p.alpha);
        }
        // a new arc between two neighbours a few times a second
        if (ps.time - st.lastArc > 0.09 / Math.max(0.3, p.speed)) {
            st.lastArc = ps.time;
            int i = st.counter = (st.counter + 1) % count;
            double[] a = pts[i], b = pts[(i + 1) % count];
            Arcs.INSTANCE.spawn(a[0], a[1], a[2], b[0], b[1], b[2], 0.12f, 0.12f, (i & 1) == 0 ? 0xFFFFFF : p.c0, p.alpha);
        }
    }

    private static void neon(double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.Params p) {
        Lines L = Lines.INSTANCE;
        double mid = cy + h * 0.55, r = Math.max(rad * 1.3, h * 0.62);
        // octahedron turning around two axes
        double a = t * 0.6, b = 0.4 * Math.sin(t * 0.5);
        double[][] v = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        double[][] w = new double[6][3];
        for (int i = 0; i < 6; i++) {
            double x = v[i][0], y = v[i][1], z = v[i][2];
            double x1 = x * Math.cos(a) - z * Math.sin(a), z1 = x * Math.sin(a) + z * Math.cos(a);
            double y2 = y * Math.cos(b) - z1 * Math.sin(b), z2 = y * Math.sin(b) + z1 * Math.cos(b);
            w[i][0] = cx + x1 * r;
            w[i][1] = mid + y2 * r * 1.15;
            w[i][2] = cz + z2 * r;
        }
        int[][] e = {{0, 2}, {0, 3}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 4}, {1, 5}, {2, 4}, {4, 3}, {3, 5}, {5, 2}};
        for (int i = 0; i < e.length; i++) {
            int col = p.col(i / (float) e.length, t);
            double[] p0 = w[e[i][0]], p1 = w[e[i][1]];
            L.add(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], col, p.alpha);
        }
        L.polygon(cx, cy + 0.03, cz, rad * 1.2, 4, -a, 0, p.c1, p.alpha * 0.6f);
    }

    private static void glass(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        int count = Math.max(6, Math.min(18, n + 6));
        for (int i = 0; i < count; i++) {
            double a = hash(i, 20) * TAU + t * 0.25 * (hash(i, 21) - 0.5), r = rad * (1.0 + hash(i, 22) * 0.6);
            double y = cy + h * (0.1 + hash(i, 23) * 0.95) + 0.05 * Math.sin(t * 1.5 + i);
            // a glint now and then: the shard catches the light
            double gl = Math.max(0, Math.sin(t * 2.3 + hash(i, 24) * 20));
            float al = p.alpha * (0.45f + 0.55f * (float) Math.pow(gl, 6));
            AuraShapes.sprite(ps, Sprite.SHARD, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, p.size * (1.2f + hash(i, 25) * 1.4f),
                    (float) (t * (hash(i, 26) - 0.5) * 2 + i), false, ParticleSystem.mix(p.c0, 0xFFFFFF, (float) Math.pow(gl, 6) * 0.6f), al);
        }
    }

    private static void cards(double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        Lines L = Lines.INSTANCE;
        int count = Math.max(3, Math.min(8, n / 2 + 2));
        double mid = cy + h * 0.6, r = rad * 1.3, cw = 0.16, ch = 0.24;
        for (int i = 0; i < count; i++) {
            double a = t * 0.8 + i * TAU / count;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r, y = mid + 0.12 * Math.sin(t * 1.6 + i * 2);
            // card plane faces outwards, and flips over now and then
            double flip = Math.cos(t * 1.1 + i * 1.3);
            double sx = -Math.sin(a) * cw * flip, sz = Math.cos(a) * cw * flip;
            int col = p.col(i / (float) count, t);
            double[][] c = {{x - sx, y - ch, z - sz}, {x + sx, y - ch, z + sz}, {x + sx, y + ch, z + sz}, {x - sx, y + ch, z - sz}};
            for (int k = 0; k < 4; k++) {
                double[] p0 = c[k], p1 = c[(k + 1) % 4];
                L.add(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], col, p.alpha);
            }
            // the suit mark: a diamond in the middle
            L.add(x, y + ch * 0.45, z, x + sx * 0.5, y, z + sz * 0.5, p.c1, p.alpha * 0.9f);
            L.add(x + sx * 0.5, y, z + sz * 0.5, x, y - ch * 0.45, z, p.c1, p.alpha * 0.9f);
            L.add(x, y - ch * 0.45, z, x - sx * 0.5, y, z - sz * 0.5, p.c1, p.alpha * 0.9f);
            L.add(x - sx * 0.5, y, z - sz * 0.5, x, y + ch * 0.45, z, p.c1, p.alpha * 0.9f);
        }
    }

    private static void core(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, AuraLibrary.State st,
                             AuraLibrary.Params p) {
        double mid = cy + h * 0.6;
        float beat = (float) Math.pow(0.5 + 0.5 * Math.sin(t * 4.0), 3);
        AuraShapes.sprite(ps, Sprite.GLOW, cx, mid, cz, p.size * (4f + 2.5f * beat), 0f, false, 0xFFFFFF, p.alpha * 0.9f);
        AuraShapes.sprite(ps, Sprite.GLOW, cx, mid, cz, p.size * (8f + 4f * beat), 0f, false, p.c0, p.alpha * 0.45f);
        double r = rad * 0.75;
        for (int k = 0; k < 3; k++) ring(cx, mid, cz, r * (1 + 0.1 * k), t * (1.3 + 0.4 * k) + k * 2.1, 0.3 + k * 0.9, 36, p.col(k / 2f, t), p.alpha * 0.8f);
        // the shell flashes on each beat
        if (beat > 0.85f) ring(cx, mid, cz, r * 1.5, t, Math.PI / 2, 40, p.c1, p.alpha * (beat - 0.85f) * 6f);
        if (ps.time - st.lastArc > 0.35 / Math.max(0.3, p.speed)) {
            st.lastArc = ps.time;
            double a = ps.rand(0f, (float) TAU);
            Arcs.INSTANCE.spawn(cx, mid, cz, cx + Math.cos(a) * r * 1.4, mid + ps.rand(-0.3f, 0.3f), cz + Math.sin(a) * r * 1.4, 0.1f, 0.08f,
                    p.c1, p.alpha);
        }
    }

    private static void starfield(ParticleSystem ps, double cx, double cy, double cz, float h, float rad, double t, int n, AuraLibrary.Params p) {
        int count = Math.max(10, Math.min(36, n * 2 + 8));
        double[][] pos = new double[count][];
        for (int i = 0; i < count; i++) {
            double a = hash(i, 30) * TAU + t * 0.05, r = rad * (0.9 + hash(i, 31) * 0.9);
            double y = cy + ((hash(i, 32) + t * 0.04 * (0.5 + hash(i, 33))) % 1.0) * (h + 0.6);
            pos[i] = new double[]{cx + Math.cos(a) * r, y, cz + Math.sin(a) * r};
            float tw = (float) (0.35 + 0.65 * Math.pow(0.5 + 0.5 * Math.sin(t * (2 + hash(i, 34) * 3) + i), 2));
            AuraShapes.sprite(ps, Sprite.STAR, pos[i][0], pos[i][1], pos[i][2], p.size * (0.4f + 0.7f * hash(i, 35)) * (0.7f + 0.3f * tw), (float) i,
                    false, p.col(hash(i, 36), t), p.alpha * tw);
        }
        // a few constellation lines between near stars
        Lines L = Lines.INSTANCE;
        for (int i = 0; i + 1 < count; i += 3) {
            double[] a = pos[i], b = pos[i + 1];
            double d = (a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]);
            if (d < 0.8) L.add(a[0], a[1], a[2], b[0], b[1], b[2], p.c1, p.alpha * 0.35f);
        }
    }
}
