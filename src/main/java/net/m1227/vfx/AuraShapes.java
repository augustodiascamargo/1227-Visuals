package net.m1227.vfx;

/**
 * Shapes drawn as per-frame sprites around an entity: orbiting crystals and
 * stars, rising energy rings and a rotating magic circle on the ground.
 * Because they are redrawn every frame from the entity's interpolated
 * position they follow movement exactly and cost a few dozen quads.
 */
public final class AuraShapes {
    private AuraShapes() {}

    public static void sprite(ParticleSystem ps, int sprite, double x, double y, double z,
                              float size, float rot, boolean flat, int rgb, float alpha) {
        Particle p = ps.frame(x, y, z);
        if (p == null) return;
        p.sprite = sprite;
        p.size0 = p.size1 = size;
        p.rot = rot;
        p.flat = flat;
        p.setColors(rgb, rgb);
        p.alpha = alpha;
    }

    public static void crystals(ParticleSystem ps, double cx, double cy, double cz, float h, float rad,
                                int n, float size, float spd, int c0, int c1, float alpha) {
        double t = ps.time;
        for (int i = 0; i < n; i++) {
            float f = n > 1 ? i / (float) (n - 1) : 0f;
            double a = t * spd * 0.8 + i * (Math.PI * 2.0 / n);
            double y = cy + h * (0.22 + 0.62 * (0.5 + 0.5 * Math.sin(t * spd * 0.9 + i * 1.7)));
            double r = rad * (0.9 + 0.22 * Math.sin(t * 1.3 + i));
            float sz = size * (1.0f + 0.7f * ((i * 37) % 10) / 10f);
            sprite(ps, Sprite.CRYSTAL, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, sz,
                    (float) (t * (1.1 + 0.2 * (i % 3)) + i), false, ParticleSystem.mix(c0, c1, f), alpha);
        }
        int stars = n + 3;
        for (int i = 0; i < stars; i++) {
            double a = -t * spd * 0.5 + i * (Math.PI * 2.0 / stars);
            double y = cy + h * (0.1 + 0.85 * ((i * 0.618) % 1.0));
            double r = rad * (1.15 + 0.3 * Math.sin(t * 0.9 + i * 2.1));
            float tw = (float) (0.5 + 0.5 * Math.sin(t * 3.0 + i * 1.3));
            sprite(ps, Sprite.STAR, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                    size * (0.35f + 0.35f * tw), (float) (t * 0.6 + i), false,
                    ParticleSystem.mix(c1, 0xFFFFFF, 0.5f), alpha * (0.4f + 0.6f * tw));
        }
    }

    /** Energy rings that rise along the body and fade out. */
    public static void halo(ParticleSystem ps, double cx, double cy, double cz, float h, float rad,
                            float spd, int c0, int c1, float alpha) {
        double t = ps.time;
        for (int k = 0; k < 3; k++) {
            double u = (t * spd * 0.28 + k / 3.0) % 1.0;
            float a = (float) Math.sin(Math.PI * u);
            sprite(ps, Sprite.HALO, cx, cy + u * h, cz, rad * (1.2f + 0.2f * (float) Math.sin(t + k)),
                    (float) (t * (k % 2 == 0 ? 0.7 : -0.9)), true,
                    ParticleSystem.mix(c0, c1, (float) u), alpha * a * 0.9f);
        }
    }

    public static void magicCircle(ParticleSystem ps, double cx, double cy, double cz, float radius,
                                   float spd, int c0, int c1, float alpha) {
        double t = ps.time;
        float pulse = (float) (0.85 + 0.15 * Math.sin(t * 2.0));
        sprite(ps, Sprite.CIRCLE, cx, cy + 0.03, cz, radius * 1.05f, (float) (t * 0.45 * spd), true, c0, alpha * pulse);
        sprite(ps, Sprite.HALO, cx, cy + 0.035, cz, radius * 0.72f, (float) (-t * 0.8 * spd), true, c1, alpha * 0.8f);
    }
}
