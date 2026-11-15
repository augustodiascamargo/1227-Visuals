package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Preview;
import net.m1227.vfx.Sprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Impact particles where one of your hits lands, following the hit direction. */
public final class HitEffectModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Bool flash, directional, critFx;
    private final Setting.Col critColor;

    public static final String[] STYLES = {"Burst", "Sparks", "Shockwave", "Crystal", "Glass Shatter", "Combo",
            "Directional Slash", "Crescent Slash", "Energy Burst", "Arc Lightning", "Rune Explosion", "Digital Distortion",
            "Flame Burst", "Vortex", "Energy Collapse"};
    private int slashCount;

    public HitEffectModule() {
        super("Hit Effect", "Particle impact where your hits land.", Category.COMBAT, true);
        style = add(new Setting.Mode("Style", "Combo", STYLES));
        fx = new FxSettings(this, 0xFF4FA3, 0xFFB45C, 16, 1.0, 0.12, 0.55);
        flash = add(new Setting.Bool("Impact flash", true));
        directional = add(new Setting.Bool("Follow hit direction", true));
        critFx = add(new Setting.Bool("Critical hit burst", true));
        critColor = add(new Setting.Col("Critical color", 0xFFC857));
        add(new Setting.Action("Preview", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(4.5, 3.0);
                if (a != null) play(a[0] - a[3] * 0.3, a[1] + 1.1, a[2] - a[4] * 0.3, (float) a[3], 0.2f, (float) a[4], a[1]);
            }
        }));
        add(new Setting.Action("Preview critical", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(4.5, 3.0);
                if (a == null) return;
                play(a[0] - a[3] * 0.3, a[1] + 1.1, a[2] - a[4] * 0.3, (float) a[3], 0.2f, (float) a[4], a[1]);
                crit(a[0], a[1] + 1.45, a[2], (float) a[3], (float) a[4]);
            }
        }));
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        if (!net.m1227.client.perf.EffectBudget.hitEffect()) return; // Max hit effects per second (PvP Visual Optimization)
        net.m1227.vfx.ParticleSystem.beginCombat();
        try {
            EntityLivingBase t = e.target;
            play(t.posX - e.dx * t.width * 0.5, t.posY + t.height * 0.65, t.posZ - e.dz * t.width * 0.5,
                    e.dx, e.dy, e.dz, t.posY);
            if (e.critical && critFx.on()) crit(t.posX, t.posY + t.height * 0.8, t.posZ, e.dx, e.dz);
        } finally {
            net.m1227.vfx.ParticleSystem.endCombat();
        }
    }

    /** Gold star burst plus a bright ring, only for real critical hits (falling, not on ground). */
    private void crit(double x, double y, double z, float dx, float dz) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (!ps.inRange(x, y, z)) return;
        int c = critColor.rgb();
        float size = fx.size.f();
        int n = ps.scale(Math.max(8f, fx.amount.f() * 0.9f));
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), up = ps.rand(0.4f, 1.6f), sp = fx.speed.f() * ps.rand(1.8f, 3.6f);
            Particle p = ps.glow(x, y, z, (float) Math.cos(a) * sp + dx, up * sp * 0.6f, (float) Math.sin(a) * sp + dz,
                    fx.duration.f() * ps.rand(0.7f, 1.2f), size * ps.rand(1.0f, 1.8f), size * 0.2f, c, 0xFFFFFF, fx.alpha());
            if (p == null) continue;
            p.sprite = Sprite.STAR;
            p.rot = ps.rand(0f, 6.28f);
            p.rotVel = ps.rand(-8f, 8f);
            p.gravity = 4f;
            p.drag = 2f;
        }
        Particle ring = ps.glow(x, y, z, 0f, 0f, 0f, 0.35f, size * 1.5f, size * 11f, 0xFFFFFF, c, fx.alpha());
        if (ring != null) ring.sprite = Sprite.RING;
        ps.glow(x, y, z, 0f, 0f, 0f, 0.16f, size * 6f, size * 10f, 0xFFFFFF, c, fx.alpha() * 0.8f);
    }

    /** Plays the effect at an impact point. dx/dy/dz is the unit hit direction. */
    public void play(double x, double y, double z, float dx, float dy, float dz, double feetY) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (!ps.inRange(x, y, z)) return;
        String s = style.get();
        boolean combo = s.equals("Combo");
        if (s.equals("Burst") || combo) burst(ps, x, y, z, dx, dy, dz, combo ? 0.55f : 1f);
        if (s.equals("Sparks") || combo) sparks(ps, x, y, z, dx, dy, dz);
        if (s.equals("Shockwave") || combo) shockwave(ps, x, y, z, dx, dy, dz);
        if (s.equals("Crystal")) crystal(ps, x, y, z, dx, dy, dz, feetY);
        if (s.equals("Glass Shatter")) glass(ps, x, y, z, dx, dz);
        if (s.equals("Directional Slash") || s.equals("Crescent Slash")) slash(x, y, z, dx, dz, s.equals("Crescent Slash"));
        if (s.equals("Energy Burst")) energyBurst(ps, x, y, z, dx, dy, dz);
        if (s.equals("Arc Lightning")) arcLightning(ps, x, y, z, dx, dz, feetY);
        if (s.equals("Rune Explosion")) {
            net.m1227.vfx.FxShapes.INSTANCE.rune(x, y, z, dx, dz, 0.75f * fx.size.f() / 0.12f, 0.75f * fx.duration.f() / 0.55f + 0.2f,
                    fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
            burst(ps, x, y, z, dx, dy, dz, 0.4f);
        }
        if (s.equals("Digital Distortion")) digital(ps, x, y, z, feetY);
        if (s.equals("Flame Burst")) flame(ps, x, y, z, dx, dy, dz);
        if (s.equals("Vortex")) {
            net.m1227.vfx.FxShapes.INSTANCE.vortex(x, y, z, dx, dz, 0.7f * fx.size.f() / 0.12f, 0.55f,
                    fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
            sparks(ps, x, y, z, dx, dy, dz);
        }
        if (s.equals("Energy Collapse")) {
            net.m1227.vfx.FxShapes.INSTANCE.collapse(x, y, z, 1.1f, 0.55f, fx.size.f(), fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
        }
        if (flash.on()) {
            float size = fx.size.f();
            ps.glow(x, y, z, 0f, 0f, 0f, 0.14f, size * 5f, size * 9f,
                    fx.color2.rgb(), fx.color1.rgb(), fx.alpha() * 0.75f);
        }
    }

    /**
     * A luminous cut through the impact. Its angle follows your weapon's swing when Weapon Models
     * draws it (horizontal, vertical, diagonal, backhand...); otherwise it alternates sides.
     */
    private void slash(double x, double y, double z, float dx, float dz, boolean crescent) {
        WeaponModelsModule wm = WeaponModelsModule.INSTANCE;
        float tilt;
        if (wm != null && wm.motionInUse()) tilt = wm.slashTiltDeg();
        else tilt = (slashCount++ & 1) == 0 ? 35f : -35f;
        float r = (crescent ? 1.0f : 0.85f) * (fx.size.f() / 0.12f);
        net.m1227.vfx.FxShapes.Shape sh = net.m1227.vfx.FxShapes.INSTANCE.slash(x, y, z, dx, dz, tilt, r, crescent ? 150f : 120f, crescent,
                0.42f * fx.duration.f() / 0.55f, fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
        if (sh != null) sh.setLook(fx.speed.f(), fx.size.f());
        if (flash.on()) ParticleSystem.INSTANCE.glow(x, y, z, 0f, 0f, 0f, 0.1f, fx.size.f() * 3f, fx.size.f() * 6f, 0xFFFFFF, fx.color1.rgb(), fx.alpha() * 0.6f);
    }

    /** White core that swells into the colour, a ring and stretched rays, then slow embers. */
    private void energyBurst(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz) {
        float size = fx.size.f(), spd = fx.speed.f();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        ps.glow(x, y, z, 0f, 0f, 0f, 0.2f, size * 2f, size * 12f, 0xFFFFFF, c0, fx.alpha());
        Particle ring = ps.glow(x, y, z, 0f, 0f, 0f, 0.3f, size * 2f, size * 16f, c1, c0, fx.alpha() * 0.8f);
        if (ring != null) ring.sprite = Sprite.RING;
        int n = ps.scale(fx.amount.f() * 1.2f);
        boolean dir = directional.on();
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-1f, 1f), rr = (float) Math.sqrt(1f - zz * zz);
            float vx = rr * (float) Math.cos(a), vy = zz, vz = rr * (float) Math.sin(a);
            if (dir) {
                vx += dx * 0.6f;
                vy += dy * 0.6f;
                vz += dz * 0.6f;
            }
            float sp = spd * ps.rand(4f, 8f);
            Particle p = ps.glow(x, y, z, vx * sp, vy * sp, vz * sp, ps.rand(0.18f, 0.35f), size * ps.rand(0.6f, 1f), 0.01f, 0xFFFFFF, c0, fx.alpha());
            if (p != null) {
                p.stretch = 0.06f;
                p.drag = 4f;
            }
        }
        for (int i = 0; i < n / 2; i++) {
            Particle e = ps.glow(x, y, z, ps.rand(-1f, 1f) * spd, ps.rand(0f, 1.5f) * spd, ps.rand(-1f, 1f) * spd, fx.duration.f() * ps.rand(0.8f, 1.4f),
                    size * ps.rand(0.5f, 0.9f), 0.01f, c1, c0, fx.alpha() * 0.8f);
            if (e != null) {
                e.drag = 1.5f;
                e.gravity = -0.5f;
                e.fadePow = fx.fade.f();
            }
        }
    }

    /** Short electric arcs from the impact over the target's body and down to the ground. */
    private void arcLightning(ParticleSystem ps, double x, double y, double z, float dx, float dz, double feetY) {
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        net.m1227.vfx.Arcs arcs = net.m1227.vfx.Arcs.INSTANCE;
        int n = 3 + Math.round(fx.amount.f() / 8f);
        for (int i = 0; i < n; i++) {
            arcs.spawn(x, y, z, x + dx * 0.3 + ps.rand(-0.6f, 0.6f), y + ps.rand(-0.8f, 0.5f), z + dz * 0.3 + ps.rand(-0.6f, 0.6f),
                    ps.rand(0.15f, 0.3f), 0.18f, i % 2 == 0 ? 0xFFFFFF : c0, fx.alpha());
        }
        arcs.spawn(x, y, z, x + ps.rand(-0.5f, 0.5f), feetY + 0.05, z + ps.rand(-0.5f, 0.5f), 0.25f, 0.3f, c1, fx.alpha());
        ps.glow(x, y, z, 0f, 0f, 0f, 0.12f, fx.size.f() * 4f, fx.size.f() * 8f, 0xFFFFFF, c0, fx.alpha() * 0.8f);
        sparks(ps, x, y, z, dx, 0.2f, dz);
    }

    /** Glitch bars over the target and red / cyan pixel cubes falling apart. */
    private void digital(ParticleSystem ps, double x, double y, double z, double feetY) {
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        net.m1227.vfx.FxShapes.INSTANCE.glitch(x, feetY, z, 0.9f, (float) (y - feetY) * 1.5f, 0.35f, c0, c1, fx.alpha());
        net.m1227.vfx.CubeBurst.INSTANCE.spawn(x, y, z, Math.max(8, Math.round(fx.amount.f())), fx.size.f() * 0.7f, fx.speed.f() * 0.8f, 0xFF2E5A, 0x2EF6FF);
        ps.glow(x, y, z, 0f, 0f, 0f, 0.1f, fx.size.f() * 4f, fx.size.f() * 7f, c1, c0, fx.alpha() * 0.6f);
    }

    /** A cone of flames along the hit, a hot core and a little smoke. */
    private void flame(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz) {
        float size = fx.size.f(), spd = fx.speed.f();
        int hot = ParticleSystem.mix(0xFFC46B, fx.color2.rgb(), 0.25f), deep = ParticleSystem.mix(0xFF3D1F, fx.color1.rgb(), 0.25f);
        int n = ps.scale(fx.amount.f() * 1.1f);
        for (int i = 0; i < n; i++) {
            float vx = dx + ps.rand(-0.6f, 0.6f), vy = dy + ps.rand(-0.1f, 0.8f), vz = dz + ps.rand(-0.6f, 0.6f);
            float sp = spd * ps.rand(1.5f, 3.5f), s2 = size * ps.rand(1.6f, 2.8f);
            Particle p = ps.glow(x, y, z, vx * sp, vy * sp, vz * sp, fx.duration.f() * ps.rand(0.5f, 0.9f), s2, s2 * 0.3f, hot, deep, fx.alpha());
            if (p == null) continue;
            p.sprite = Sprite.FLAME;
            p.drag = 2.2f;
            p.gravity = -1.8f;
            p.rot = ps.rand(-0.4f, 0.4f);
        }
        ps.glow(x, y, z, 0f, 0f, 0f, 0.15f, size * 4f, size * 8f, 0xFFFFFF, hot, fx.alpha() * 0.8f);
        for (int i = 0; i < 4; i++) {
            Particle sm = ps.glow(x, y, z, ps.rand(-0.4f, 0.4f), ps.rand(0.6f, 1.2f), ps.rand(-0.4f, 0.4f), ps.rand(0.8f, 1.2f),
                    size * 2f, size * 4f, 0x3A3236, 0x1A1618, 0.45f);
            if (sm == null) continue;
            sm.sprite = Sprite.SMOKE;
            sm.alphaBlend = true;
            sm.drag = 1f;
        }
    }

    private void burst(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz, float share) {
        int n = ps.scale(fx.amount.f() * share);
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f();
        boolean dir = directional.on();
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-1f, 1f);
            float rr = (float) Math.sqrt(1f - zz * zz);
            float sp = spd * ps.rand(1.2f, 3.2f);
            float vx = rr * (float) Math.cos(a) * sp, vy = zz * sp, vz = rr * (float) Math.sin(a) * sp;
            if (dir) {
                vx += dx * sp * 0.7f;
                vy += dy * sp * 0.7f;
                vz += dz * sp * 0.7f;
            }
            Particle p = ps.glow(x, y, z, vx, vy, vz, life * ps.rand(0.6f, 1f),
                    size * ps.rand(0.7f, 1.3f), size * 0.15f,
                    fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
            if (p != null) {
                p.drag = 2.4f;
                p.gravity = 1.2f;
                p.fadePow = fade;
            }
        }
    }

    private void sparks(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz) {
        int n = ps.scale(fx.amount.f() * 0.8f);
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f();
        boolean dir = directional.on();
        for (int i = 0; i < n; i++) {
            float vx = ps.rand(-0.7f, 0.7f), vy = ps.rand(-0.2f, 0.9f), vz = ps.rand(-0.7f, 0.7f);
            if (dir) {
                vx += dx;
                vy += dy;
                vz += dz;
            }
            float sp = spd * ps.rand(2.5f, 5.5f);
            Particle p = ps.glow(x, y, z, vx * sp, vy * sp, vz * sp, life * ps.rand(0.5f, 1f),
                    size * 0.55f, size * 0.1f, fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
            if (p != null) {
                p.stretch = 0.05f;
                p.gravity = 9f;
                p.drag = 1.4f;
                p.fadePow = fade;
            }
        }
    }

    private void shockwave(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz) {
        // Ring in the plane perpendicular to the hit direction.
        float ul = (float) Math.sqrt(dx * dx + dz * dz);
        if (ul < 1.0E-4f) return;
        float ux = -dz / ul, uz = dx / ul;                    // u = d x up
        float vx = dy * uz, vy = dz * ux - dx * uz, vz = -dy * ux; // v = d x u
        int n = ps.scale(Math.max(12f, fx.amount.f() * 1.6f));
        float size = fx.size.f(), life = fx.duration.f() * 0.7f, spd = fx.speed.f() * 3.2f, fade = fx.fade.f();
        for (int i = 0; i < n; i++) {
            float a = i * (6.2831855f / n);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            Particle p = ps.glow(x, y, z,
                    (ux * c + vx * s) * spd, (vy * s) * spd, (uz * c + vz * s) * spd,
                    life, size * 1.1f, size * 0.3f, fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
            if (p != null) {
                p.drag = 3.2f;
                p.fadePow = fade;
            }
        }
    }

    /** Spinning crystal shards, sparkles and an expanding ring on the ground. */
    private void crystal(ParticleSystem ps, double x, double y, double z, float dx, float dy, float dz, double feetY) {
        int n = ps.scale(fx.amount.f() * 0.5f);
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f();
        boolean dir = directional.on();
        for (int i = 0; i < n; i++) {
            float vx = ps.rand(-1f, 1f), vy = ps.rand(0.2f, 1.2f), vz = ps.rand(-1f, 1f);
            if (dir) {
                vx += dx * 1.2f;
                vz += dz * 1.2f;
            }
            float sp = spd * ps.rand(1.5f, 3.5f);
            float s = size * ps.rand(1.2f, 2.2f);
            Particle p = ps.glow(x, y, z, vx * sp, vy * sp, vz * sp, life * ps.rand(0.7f, 1.1f),
                    s, s * ps.rand(0.8f, 1.2f), fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
            if (p != null) {
                p.sprite = Sprite.CRYSTAL;
                p.rot = ps.rand(0f, 6.28f);
                p.rotVel = ps.rand(-6f, 6f);
                p.gravity = 7f;
                p.drag = 1.2f;
                p.fadePow = fade * 1.5f;
            }
            Particle st = ps.glow(x, y, z, vx * sp * 0.7f, vy * sp * 0.7f, vz * sp * 0.7f, life * ps.rand(0.6f, 1f),
                    size * ps.rand(0.5f, 1.1f), 0f, fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
            if (st != null) {
                st.sprite = Sprite.STAR;
                st.rotVel = ps.rand(-4f, 4f);
                st.gravity = 2f;
                st.drag = 1.5f;
                st.fadePow = fade;
            }
        }
        Particle ring = ps.glow(x, feetY + 0.04, z, 0f, 0f, 0f, life * 0.9f, size * 1.6f, size * 14f,
                fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
        if (ring != null) {
            ring.sprite = Sprite.RING;
            ring.flat = true;
            ring.fadePow = 1.6f;
        }
    }

    /** Big translucent glass shards burst out along the hit direction and tumble down. */
    private void glass(ParticleSystem ps, double x, double y, double z, float dx, float dz) {
        int n = ps.scale(Math.max(5f, fx.amount.f() * 0.7f));
        float size = fx.size.f(), life = fx.duration.f() * 1.6f, spd = fx.speed.f();
        boolean dir = directional.on();
        for (int i = 0; i < n; i++) {
            float vx = ps.rand(-1.2f, 1.2f), vy = ps.rand(0.3f, 1.6f), vz = ps.rand(-1.2f, 1.2f);
            if (dir) {
                vx += dx * 1.6f;
                vz += dz * 1.6f;
            }
            float sp = spd * ps.rand(1.4f, 2.6f), s = size * ps.rand(1.4f, 2.8f);
            Particle p = ps.glow(x + ps.rand(-0.2f, 0.2f), y + ps.rand(-0.3f, 0.3f), z + ps.rand(-0.2f, 0.2f),
                    vx * sp, vy * sp, vz * sp, life * ps.rand(0.7f, 1.1f), s, s * 0.85f,
                    fx.color1.rgb(), fx.color2.rgb(), fx.alpha() * 0.85f);
            if (p == null) continue;
            p.sprite = Sprite.SHARD;
            p.rot = ps.rand(0f, 6.28f);
            p.rotVel = ps.rand(-7f, 7f);
            p.gravity = 9.5f;
            p.drag = 0.6f;
            p.fadePow = 2f * fx.fade.f();
        }
    }
}
