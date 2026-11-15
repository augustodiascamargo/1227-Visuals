package net.m1227.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short animated shapes for combat effects, drawn every frame as luminous lines (depth tested,
 * so never through walls) plus particles they emit: slash arcs that sweep and fade, rune circles
 * that expand and turn, glitch bars, a vortex, a portal on the ground, a beam from the sky, an
 * accretion ring around a black hole and firework rockets. Time is real seconds; nothing here
 * touches the game state.
 */
public final class FxShapes {
    public static FxShapes INSTANCE = new FxShapes();
    private static final double TAU = Math.PI * 2;
    private static final int MAX = 64;

    public static final int SLASH = 0, RUNE = 1, GLITCH = 2, VORTEX = 3, PORTAL = 4, BEAM = 5, BLACK_HOLE = 6, ROCKET = 7, COLLAPSE = 8;

    /** One shape. Positions are world coordinates; (ux,uy,uz) and (vx,vy,vz) span its plane. */
    public static final class Shape {
        int type;
        double x, y, z;
        float ux, uy, uz, vx, vy, vz;
        float r, w, h, age, life, alpha = 1f, a0, a1, speed = 1f, size = 0.12f;
        int c0, c1;
        float acc;
        boolean fired;
        int n = 3;

        /** Speed (multiplier) and particle size used by the shape's sparks. */
        public void setLook(float speed, float size) {
            this.speed = speed;
            this.size = size;
        }
    }

    private final List<Shape> shapes = new ArrayList<>();

    FxShapes() {}

    public boolean active() {
        return !shapes.isEmpty();
    }

    public void clear() {
        shapes.clear();
    }

    private Shape add(int type, double x, double y, double z, float life, int c0, int c1, float alpha) {
        if (shapes.size() >= MAX) shapes.remove(0);
        Shape s = new Shape();
        s.type = type;
        s.x = x;
        s.y = y;
        s.z = z;
        s.life = Math.max(0.05f, life);
        s.c0 = c0;
        s.c1 = c1;
        s.alpha = alpha;
        shapes.add(s);
        return s;
    }

    // ------------------------------------------------------------------ spawning

    /**
     * A slash arc across the point (x, y, z), facing direction (dx, dz): the cut sweeps through
     * 'span' degrees, tilted by 'tiltDeg' around the facing (0 = horizontal, 90 = vertical,
     * negative = the other way). crescent = a wide moon shape instead of a thin cut.
     */
    public Shape slash(double x, double y, double z, float dx, float dz, float tiltDeg, float radius, float span, boolean crescent,
                       float life, int c0, int c1, float alpha) {
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) {
            dx = 0f;
            dz = 1f;
            l = 1f;
        }
        dx /= l;
        dz /= l;
        // side vector (horizontal, perpendicular to the facing) and up, turned by the tilt
        float sx = -dz, sz = dx;
        double t = Math.toRadians(tiltDeg);
        float c = (float) Math.cos(t), s = (float) Math.sin(t);
        Shape sh = add(SLASH, x - dx * radius * 0.55f, y, z - dz * radius * 0.55f, life, c0, c1, alpha);
        // u: forward (towards the target), v: the side turned by the tilt
        sh.ux = dx;
        sh.uy = 0f;
        sh.uz = dz;
        sh.vx = sx * c;
        sh.vy = s;
        sh.vz = sz * c;
        sh.r = radius;
        sh.a0 = (float) Math.toRadians(-span / 2f);
        sh.a1 = (float) Math.toRadians(span / 2f);
        sh.w = crescent ? radius * 0.32f : radius * 0.1f;
        return sh;
    }

    /** A rune circle facing (dx, dz) that expands and turns, throwing rune glyphs outwards. */
    public Shape rune(double x, double y, double z, float dx, float dz, float radius, float life, int c0, int c1, float alpha) {
        Shape sh = add(RUNE, x, y, z, life, c0, c1, alpha);
        plane(sh, dx, dz);
        sh.r = radius;
        return sh;
    }

    /** Glitch bars across a box (width w, height h, bottom at y) for a moment. */
    public Shape glitch(double x, double y, double z, float w, float h, float life, int c0, int c1, float alpha) {
        Shape sh = add(GLITCH, x, y, z, life, c0, c1, alpha);
        sh.w = w;
        sh.h = h;
        return sh;
    }

    /** Spiral arms turning around the hit direction and closing in. */
    public Shape vortex(double x, double y, double z, float dx, float dz, float radius, float life, int c0, int c1, float alpha) {
        Shape sh = add(VORTEX, x, y, z, life, c0, c1, alpha);
        plane(sh, dx, dz);
        sh.r = radius;
        return sh;
    }

    /** Particles converging on a point, then a burst when they meet. */
    public Shape collapse(double x, double y, double z, float radius, float life, float size, int c0, int c1, float alpha) {
        Shape sh = add(COLLAPSE, x, y, z, life, c0, c1, alpha);
        sh.r = radius;
        sh.size = size;
        return sh;
    }

    /** A portal on the ground: rings and runes that open, swirl and close. */
    public Shape portal(double x, double groundY, double z, float radius, float life, int c0, int c1, float alpha) {
        Shape sh = add(PORTAL, x, groundY + 0.03, z, life, c0, c1, alpha);
        sh.r = radius;
        return sh;
    }

    /** A beam of light from the sky onto (x, groundY, z). */
    public Shape beam(double x, double groundY, double z, float radius, float height, float life, int c0, int c1, float alpha) {
        Shape sh = add(BEAM, x, groundY, z, life, c0, c1, alpha);
        sh.r = radius;
        sh.h = height;
        return sh;
    }

    /** The bright ring and inward spiral around a black hole at (x, y, z). */
    public Shape blackHole(double x, double y, double z, float radius, float life, int c0, int c1, float alpha) {
        Shape sh = add(BLACK_HOLE, x, y, z, life, c0, c1, alpha);
        sh.r = radius;
        return sh;
    }

    /** A firework rocket that climbs 'height' blocks and bursts. */
    public Shape rocket(double x, double y, double z, float height, float delay, float size, int c0, int c1, float alpha) {
        Shape sh = add(ROCKET, x, y, z, 0.75f + delay + 1.2f, c0, c1, alpha);
        sh.h = height;
        sh.a0 = delay;
        sh.size = size;
        return sh;
    }

    private static void plane(Shape sh, float dx, float dz) {
        float l = (float) Math.sqrt(dx * dx + dz * dz);
        if (l < 1e-4f) {
            dx = 0f;
            dz = 1f;
            l = 1f;
        }
        // the circle faces the attacker: spanned by the side vector and up
        sh.ux = -dz / l;
        sh.uy = 0f;
        sh.uz = dx / l;
        sh.vx = 0f;
        sh.vy = 1f;
        sh.vz = 0f;
    }

    // ------------------------------------------------------------------ update + draw

    public void update(float dt, ParticleSystem ps, Lines lines) {
        for (Iterator<Shape> it = shapes.iterator(); it.hasNext(); ) {
            Shape s = it.next();
            s.age += dt;
            if (s.age >= s.life) {
                it.remove();
                continue;
            }
            float t = s.age / s.life;
            switch (s.type) {
                case SLASH:
                    slash(s, t, ps, lines, dt);
                    break;
                case RUNE:
                    rune(s, t, ps, lines, dt);
                    break;
                case GLITCH:
                    glitch(s, t, ps, lines);
                    break;
                case VORTEX:
                    vortex(s, t, ps, lines, dt);
                    break;
                case PORTAL:
                    portal(s, t, ps, lines, dt);
                    break;
                case BEAM:
                    beam(s, t, ps, lines, dt);
                    break;
                case BLACK_HOLE:
                    blackHole(s, t, ps, lines, dt);
                    break;
                case ROCKET:
                    rocket(s, ps, lines, dt);
                    break;
                default:
                    collapse(s, t, ps, dt);
            }
        }
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static float easeOut(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    /** Point on the shape's plane: centre + u * a + v * b. */
    private static double px(Shape s, double a, double b) {
        return s.x + s.ux * a + s.vx * b;
    }

    private static double py(Shape s, double a, double b) {
        return s.y + s.uy * a + s.vy * b;
    }

    private static double pz(Shape s, double a, double b) {
        return s.z + s.uz * a + s.vz * b;
    }

    /** Sweep: the head runs through the arc in the first 40 %, the tail follows and the stroke fades. */
    private void slash(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float head = easeOut(t / 0.4f), tail = smooth((t - 0.12f) / 0.6f);
        float h0 = s.a0 + (s.a1 - s.a0) * tail, h1 = s.a0 + (s.a1 - s.a0) * head;
        if (h1 - h0 < 1e-3f) return;
        float fade = s.alpha * (1f - smooth((t - 0.55f) / 0.45f));
        int seg = 22, layers = 6;
        for (int k = 0; k < layers; k++) {
            float lk = k / (float) (layers - 1);
            int col = ParticleSystem.mix(0xFFFFFF, ParticleSystem.mix(s.c0, s.c1, lk), Math.min(1f, 0.25f + lk));
            float a = fade * (1f - lk * 0.75f);
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= seg; i++) {
                float u = i / (float) seg;
                double ang = h0 + (h1 - h0) * u;
                // thickest in the middle of the drawn stroke, thin at both ends (a crescent)
                double width = s.w * Math.sin(u * Math.PI);
                double rr = s.r - width * lk;
                double ax = Math.cos(ang) * rr, bx = Math.sin(ang) * rr;
                double X = px(s, ax, bx), Y = py(s, ax, bx), Z = pz(s, ax, bx);
                if (i > 0) L.add(prevX, prevY, prevZ, X, Y, Z, col, a * (0.35f + 0.65f * (float) Math.sin(u * Math.PI)));
                prevX = X;
                prevY = Y;
                prevZ = Z;
            }
        }
        // sparks thrown off the leading edge while it moves
        if (t < 0.45f) {
            s.acc += dt * 90f;
            while (s.acc >= 1f) {
                s.acc -= 1f;
                double ax = Math.cos(h1) * s.r, bx = Math.sin(h1) * s.r;
                float tx = (float) (-Math.sin(h1)), ty = (float) Math.cos(h1);
                float vx = s.ux * tx + s.vx * ty, vy = s.uy * tx + s.vy * ty, vz = s.uz * tx + s.vz * ty;
                float sp = ps.rand(1.5f, 4f) * s.speed;
                Particle p = ps.glow(px(s, ax, bx), py(s, ax, bx), pz(s, ax, bx), vx * sp + ps.rand(-0.5f, 0.5f), vy * sp + ps.rand(-0.3f, 0.8f),
                        vz * sp + ps.rand(-0.5f, 0.5f), ps.rand(0.25f, 0.5f), s.size * ps.rand(0.4f, 0.8f), 0.01f, 0xFFFFFF, s.c1, s.alpha);
                if (p == null) break;
                p.stretch = 0.04f;
                p.drag = 2.5f;
                p.gravity = 5f;
            }
        }
    }

    private void rune(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float grow = easeOut(t / 0.35f);
        float fade = s.alpha * (1f - smooth((t - 0.5f) / 0.5f));
        double rot = s.age * 2.2 * s.speed;
        double r = s.r * (0.25 + 0.75 * grow) * (1 + 0.25 * smooth((t - 0.5f) / 0.5f));
        ring(s, L, r, 40, rot, s.c0, fade);
        ring(s, L, r * 0.82, 40, -rot, s.c1, fade * 0.8f);
        // hexagram
        for (int i = 0; i < 6; i++) {
            double a0 = rot * 0.6 + i * TAU / 6, a1 = rot * 0.6 + ((i + 2) % 6) * TAU / 6;
            double R = r * 0.8;
            L.add(px(s, Math.cos(a0) * R, Math.sin(a0) * R), py(s, Math.cos(a0) * R, Math.sin(a0) * R), pz(s, Math.cos(a0) * R, Math.sin(a0) * R),
                    px(s, Math.cos(a1) * R, Math.sin(a1) * R), py(s, Math.cos(a1) * R, Math.sin(a1) * R), pz(s, Math.cos(a1) * R, Math.sin(a1) * R),
                    ParticleSystem.mix(s.c0, 0xFFFFFF, 0.3f), fade * 0.9f);
        }
        if (!s.fired && t > 0.25f) {
            s.fired = true;
            for (int i = 0; i < 10; i++) {
                double a = i * TAU / 10;
                float ox = (float) (s.ux * Math.cos(a) + s.vx * Math.sin(a)), oy = (float) (s.uy * Math.cos(a) + s.vy * Math.sin(a)),
                        oz = (float) (s.uz * Math.cos(a) + s.vz * Math.sin(a));
                Particle p = ps.glow(px(s, Math.cos(a) * r, Math.sin(a) * r), py(s, Math.cos(a) * r, Math.sin(a) * r), pz(s, Math.cos(a) * r, Math.sin(a) * r),
                        ox * 2.5f * s.speed, oy * 2.5f * s.speed + 0.4f, oz * 2.5f * s.speed, 0.7f, s.size * 2.2f, s.size * 1.2f, s.c1, s.c0, s.alpha);
                if (p == null) continue;
                p.sprite = Sprite.RUNE;
                p.rotVel = ps.rand(-3f, 3f);
                p.drag = 2f;
            }
        }
    }

    private static void ring(Shape s, Lines L, double r, int n, double rot, int col, float a) {
        double prevX = 0, prevY = 0, prevZ = 0;
        for (int i = 0; i <= n; i++) {
            double ang = rot + i * TAU / n;
            double ax = Math.cos(ang) * r, bx = Math.sin(ang) * r;
            double X = px(s, ax, bx), Y = py(s, ax, bx), Z = pz(s, ax, bx);
            if (i > 0) L.add(prevX, prevY, prevZ, X, Y, Z, col, a);
            prevX = X;
            prevY = Y;
            prevZ = Z;
        }
    }

    /** Horizontal bars that jump around the box, red / cyan split, flickering. */
    private void glitch(Shape s, float t, ParticleSystem ps, Lines L) {
        float fade = s.alpha * (1f - smooth((t - 0.6f) / 0.4f));
        java.util.Random r = new java.util.Random((long) (s.age * 30f) * 31L + s.c0);
        int bars = 7;
        for (int i = 0; i < bars; i++) {
            if (r.nextFloat() < 0.3f) continue;
            double y = s.y + r.nextFloat() * s.h;
            double half = s.w * (0.3 + r.nextFloat() * 0.6);
            double off = (r.nextFloat() - 0.5) * s.w * 0.6;
            double shift = 0.05 + r.nextFloat() * 0.06;
            // seen from any side: one bar along X and one along Z
            int red = 0xFF2E5A, cyan = 0x2EF6FF;
            L.add(s.x - half + off, y, s.z, s.x + half + off, y, s.z, ParticleSystem.mix(red, s.c0, 0.3f), fade);
            L.add(s.x - half + off + shift, y + 0.02, s.z, s.x + half + off + shift, y + 0.02, s.z, ParticleSystem.mix(cyan, s.c1, 0.3f), fade);
            L.add(s.x, y, s.z - half + off, s.x, y, s.z + half + off, ParticleSystem.mix(red, s.c0, 0.3f), fade);
            L.add(s.x, y + 0.02, s.z - half + off + shift, s.x, y + 0.02, s.z + half + off + shift, ParticleSystem.mix(cyan, s.c1, 0.3f), fade);
        }
    }

    private void vortex(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float fade = s.alpha * (1f - smooth((t - 0.55f) / 0.45f)) * smooth(t / 0.12f);
        double spin = s.age * 9.0 * s.speed;
        double close = 1.0 - 0.75 * easeOut(t);
        for (int arm = 0; arm < 3; arm++) {
            double prevX = 0, prevY = 0, prevZ = 0;
            for (int i = 0; i <= 20; i++) {
                double u = i / 20.0;
                double ang = spin + arm * TAU / 3 + u * 4.0;
                double r = s.r * close * (1 - u * 0.85);
                double ax = Math.cos(ang) * r, bx = Math.sin(ang) * r;
                // the spiral funnels into the hit (depth along the facing)
                double depth = -u * s.r * 0.6;
                float fx = -s.uz, fz = s.ux; // facing = up x side
                double X = px(s, ax, bx) + fx * depth, Y = py(s, ax, bx), Z = pz(s, ax, bx) + fz * depth;
                if (i > 0) L.add(prevX, prevY, prevZ, X, Y, Z, ParticleSystem.mix(s.c0, s.c1, (float) u), fade * (float) (1 - u * 0.6));
                prevX = X;
                prevY = Y;
                prevZ = Z;
            }
        }
    }

    private void collapse(Shape s, float t, ParticleSystem ps, float dt) {
        if (s.age < dt * 1.5f) {
            int n = ps.scale(28f);
            float fl = s.life * 0.6f;
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, (float) TAU), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1f - zz * zz);
                float dx = rr * (float) Math.cos(a), dy = zz, dz = rr * (float) Math.sin(a);
                float k = s.r / fl;
                Particle p = ps.glow(s.x + dx * s.r, s.y + dy * s.r, s.z + dz * s.r, -dx * k, -dy * k, -dz * k, fl, s.size * 0.8f, s.size * 0.3f,
                        s.c1, s.c0, s.alpha);
                if (p != null) p.stretch = 0.03f;
            }
        }
        if (!s.fired && t >= 0.6f) {
            s.fired = true;
            ps.glow(s.x, s.y, s.z, 0f, 0f, 0f, 0.22f, s.size * 3f, s.size * 16f, 0xFFFFFF, s.c0, s.alpha);
            Particle ring = ps.glow(s.x, s.y, s.z, 0f, 0f, 0f, 0.35f, s.size * 2f, s.size * 20f, s.c1, s.c0, s.alpha * 0.8f);
            if (ring != null) ring.sprite = Sprite.RING;
            int n = ps.scale(30f);
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, (float) TAU), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1f - zz * zz), sp = ps.rand(3f, 7f) * s.speed;
                Particle p = ps.glow(s.x, s.y, s.z, rr * (float) Math.cos(a) * sp, zz * sp, rr * (float) Math.sin(a) * sp, ps.rand(0.3f, 0.6f),
                        s.size * ps.rand(0.6f, 1.1f), 0.01f, 0xFFFFFF, s.c1, s.alpha);
                if (p == null) continue;
                p.stretch = 0.05f;
                p.drag = 3f;
            }
        }
    }

    private void portal(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float open = easeOut(t / 0.2f) * (1f - smooth((t - 0.8f) / 0.2f));
        float a = s.alpha * open;
        double rot = s.age * 1.6 * s.speed;
        double R = s.r * open;
        L.polygon(s.x, s.y, s.z, R, 48, rot, 0, s.c0, a);
        L.polygon(s.x, s.y, s.z, R * 0.86, 48, -rot, 0, s.c1, a * 0.8f);
        L.polygon(s.x, s.y + 0.01, s.z, R * 0.62, 6, rot * 1.5, 0, ParticleSystem.mix(s.c0, 0xFFFFFF, 0.4f), a * 0.7f);
        L.star(s.x, s.y + 0.01, s.z, R * 0.62, 6, 2, -rot * 1.5, s.c1, a * 0.6f);
        s.acc += dt * 60f * open;
        while (s.acc >= 1f) {
            s.acc -= 1f;
            float ang = ps.rand(0f, (float) TAU), rr = (float) R * ps.rand(0.3f, 1f);
            float tx = -(float) Math.sin(ang), tz = (float) Math.cos(ang);
            Particle p = ps.glow(s.x + Math.cos(ang) * rr, s.y + 0.05, s.z + Math.sin(ang) * rr, tx * 1.2f - (float) Math.cos(ang) * 0.6f,
                    ps.rand(0.2f, 1.2f), tz * 1.2f - (float) Math.sin(ang) * 0.6f, ps.rand(0.4f, 0.8f), s.size * ps.rand(0.6f, 1.2f), 0.01f, s.c0, s.c1, s.alpha);
            if (p == null) break;
            p.drag = 1.5f;
        }
        if (!s.fired && t >= 0.8f) {
            s.fired = true;
            ps.glow(s.x, s.y + 0.3, s.z, 0f, 0f, 0f, 0.25f, s.size * 4f, s.size * 18f, 0xFFFFFF, s.c0, s.alpha);
        }
    }

    private void beam(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float on = easeOut(t / 0.12f) * (1f - smooth((t - 0.7f) / 0.3f));
        float a = s.alpha * on;
        int n = 10;
        double R = s.r * (0.6 + 0.4 * on);
        for (int i = 0; i < n; i++) {
            double ang = i * TAU / n + s.age * 3.0;
            double rr = R * (i % 2 == 0 ? 1.0 : 0.55);
            double x = s.x + Math.cos(ang) * rr, z = s.z + Math.sin(ang) * rr;
            L.add(x, s.y, z, x, s.y + s.h, z, i % 2 == 0 ? s.c0 : 0xFFFFFF, a * (i % 2 == 0 ? 0.7f : 1f));
        }
        L.polygon(s.x, s.y + 0.03, s.z, R * 1.6, 32, s.age * 2.0, 0, s.c1, a * 0.8f);
        s.acc += dt * 70f * on;
        while (s.acc >= 1f) {
            s.acc -= 1f;
            float ang = ps.rand(0f, (float) TAU), rr = (float) R * ps.rand(0f, 1.1f);
            Particle p = ps.glow(s.x + Math.cos(ang) * rr, s.y + ps.rand(0f, 2.2f), s.z + Math.sin(ang) * rr, 0f, ps.rand(2f, 6f), 0f,
                    ps.rand(0.3f, 0.7f), s.size * ps.rand(0.6f, 1.2f), 0.01f, 0xFFFFFF, s.c0, s.alpha);
            if (p == null) break;
            p.stretch = 0.05f;
        }
    }

    private void blackHole(Shape s, float t, ParticleSystem ps, Lines L, float dt) {
        float on = easeOut(t / 0.2f) * (1f - smooth((t - 0.82f) / 0.18f));
        float a = s.alpha * on;
        double rot = s.age * 5.0 * s.speed;
        // tilted accretion rings
        for (int k = 0; k < 3; k++) {
            double R = s.r * (0.55 + 0.22 * k) * on;
            double tilt = 0.35 + k * 0.15;
            L.polygon(s.x, s.y, s.z, R, 40, rot * (1 + k * 0.4), tilt, ParticleSystem.mix(s.c0, s.c1, k / 2f), a * (1f - k * 0.25f));
        }
        // the dark core (alpha-blended, so it darkens what is behind it)
        Particle core = ps.frame(s.x, s.y, s.z);
        if (core != null) {
            core.size0 = core.size1 = s.r * 0.75f * on;
            core.setColors(0x05030A, 0x05030A);
            core.alpha = 0.95f * on;
            core.alphaBlend = true;
            core.sprite = Sprite.CIRCLE;
        }
        // matter spiralling in
        s.acc += dt * 80f * on;
        while (s.acc >= 1f) {
            s.acc -= 1f;
            float ang = ps.rand(0f, (float) TAU), rr = s.r * ps.rand(1.3f, 2.2f);
            double x = s.x + Math.cos(ang) * rr, z = s.z + Math.sin(ang) * rr, y = s.y + ps.rand(-0.3f, 0.3f);
            float tx = -(float) Math.sin(ang), tz = (float) Math.cos(ang), life = ps.rand(0.35f, 0.6f);
            float k = rr / life;
            Particle p = ps.glow(x, y, z, (float) (s.x - x) / life + tx * 2f, (float) (s.y - y) / life, (float) (s.z - z) / life + tz * 2f,
                    life, s.size * ps.rand(0.7f, 1.2f), 0.01f, s.c1, s.c0, s.alpha);
            if (p == null) break;
            p.stretch = 0.04f;
        }
    }

    private void rocket(Shape s, ParticleSystem ps, Lines L, float dt) {
        float t = s.age - s.a0;
        if (t < 0f) return;
        float rise = 0.75f;
        if (t < rise) {
            float u = t / rise;
            double y = s.y + s.h * (1 - (1 - u) * (1 - u));
            s.acc += dt * 60f;
            while (s.acc >= 1f) {
                s.acc -= 1f;
                Particle p = ps.glow(s.x + ps.rand(-0.03f, 0.03f), y, s.z + ps.rand(-0.03f, 0.03f), ps.rand(-0.3f, 0.3f), ps.rand(-1.5f, -0.5f),
                        ps.rand(-0.3f, 0.3f), ps.rand(0.25f, 0.45f), s.size * 0.6f, 0.01f, 0xFFE0A0, s.c0, s.alpha);
                if (p == null) break;
                p.gravity = 3f;
            }
            return;
        }
        if (!s.fired) {
            s.fired = true;
            double y = s.y + s.h;
            ps.glow(s.x, y, s.z, 0f, 0f, 0f, 0.25f, s.size * 4f, s.size * 20f, 0xFFFFFF, s.c0, s.alpha);
            int n = ps.scale(70f);
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, (float) TAU), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1f - zz * zz), sp = ps.rand(5f, 7f);
                int c = i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? s.c0 : s.c1;
                Particle p = ps.glow(s.x, y, s.z, rr * (float) Math.cos(a) * sp, zz * sp, rr * (float) Math.sin(a) * sp, ps.rand(0.9f, 1.3f),
                        s.size * ps.rand(0.8f, 1.3f), s.size * 0.2f, c, ParticleSystem.mix(c, 0x000000, 0.4f), s.alpha);
                if (p == null) continue;
                p.drag = 2.6f;
                p.gravity = 2.5f;
                p.stretch = 0.03f;
                if (i % 5 == 0) p.sprite = Sprite.STAR;
            }
        }
        // late crackles
        float c = t - rise;
        if (c > 0.45f && c < 0.9f) {
            s.acc += dt * 40f;
            while (s.acc >= 1f) {
                s.acc -= 1f;
                float a = ps.rand(0f, (float) TAU), r = ps.rand(1f, 3.2f);
                ps.glow(s.x + Math.cos(a) * r, s.y + s.h - ps.rand(0f, 1.5f), s.z + Math.sin(a) * r, 0f, 0f, 0f, 0.08f, s.size * 1.6f, 0.01f,
                        0xFFFFFF, s.c1, s.alpha);
            }
        }
    }
}
