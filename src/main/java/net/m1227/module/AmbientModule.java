package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Shards and sparkles drifting through the air around the camera. */
public final class AmbientModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Num area, wind;
    private float acc;

    public AmbientModule() {
        super("Ambient Particles", "Drifting sparkles, or lights flying around with trails.", Category.AMBIENCE, false);
        style = add(new Setting.Mode("Style", "Mixed", "Shards", "Sparkles", "Mixed", "Embers", "Fireflies", "Snow",
                "Shooting Stars", "Wisps", "Darting Sparks", "Flyers Mix"));
        fx = new FxSettings(this, 0xFFFFFF, 0xBFEFFF, 14, 1.0, 0.12, 3.0);
        area = add(new Setting.Num("Area", 18, 8, 40, 1, "m"));
        wind = add(new Setting.Num("Wind", 0.6, 0.0, 3.0, 0.1, ""));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (ps.dt <= 0f) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        acc += ps.dt * fx.amount.f() * 2f * PerformanceModule.qualityScale();
        int whole = (int) acc;
        acc -= whole;
        int m = Math.min(whole, 24);
        float a = area.f(), w = wind.f();
        float size = fx.size.f(), life = fx.duration.f();
        String st = style.get();
        if (st.equals("Shooting Stars") || st.equals("Wisps") || st.equals("Darting Sparks") || st.equals("Flyers Mix")) {
            flyers(ps, st, m, a, size, life);
            return;
        }
        flyers.clear();

        for (int i = 0; i < m; i++) {
            double x = ps.camX + ps.rand(-a, a), y = ps.camY + ps.rand(-4f, 8f), z = ps.camZ + ps.rand(-a, a);
            float vx = ps.rand(-0.3f, 0.3f) + w * 0.4f, vy = ps.rand(-0.15f, 0.1f), vz = ps.rand(-0.3f, 0.3f);
            if (st.equals("Embers") || st.equals("Fireflies") || st.equals("Snow")) {
                special(ps, st, x, y, z, w, size, life);
                continue;
            }
            int sprite;
            float roll = ps.rng.nextFloat();
            if (st.equals("Shards")) sprite = Sprite.PETAL;
            else if (st.equals("Sparkles")) sprite = roll < 0.5f ? Sprite.STAR : Sprite.GLOW;
            else sprite = roll < 0.55f ? Sprite.PETAL : (roll < 0.8f ? Sprite.STAR : Sprite.GLOW);

            int c0 = ParticleSystem.mix(fx.color1.rgb(), fx.color2.rgb(), ps.rng.nextFloat());
            float s = size * ps.rand(0.9f, 1.8f);
            Particle p = ps.glow(x, y, z, vx, vy, vz, life * ps.rand(0.7f, 1.1f),
                    s, s * (sprite == Sprite.STAR ? 0.4f : 0.9f), c0, fx.color2.rgb(), fx.alpha() * 0.85f);
            if (p == null) continue;
            p.sprite = sprite;
            p.rot = ps.rand(0f, 6.2831855f);
            p.rotVel = ps.rand(-2.5f, 2.5f);
            p.fadePow = fx.fade.f();
        }
    }

    /** Embers rise and flicker, fireflies wander and blink, snow drifts down. */
    private void special(ParticleSystem ps, String st, double x, double y, double z, float w, float size, float life) {
        Particle p;
        if (st.equals("Embers")) {
            int c = ParticleSystem.mix(0xFF7A2E, fx.color1.rgb(), 0.25f);
            p = ps.glow(x, y - 3, z, w * 0.3f, ps.rand(0.25f, 0.7f), 0f, life * ps.rand(1f, 1.6f),
                    size * ps.rand(0.5f, 1f), size * 0.2f, c, 0xFFC36B, fx.alpha());
            if (p == null) return;
            p.sway = 0.35f;
            p.swayFreq = ps.rand(1.5f, 3f);
            p.pulse = 0.5f;
            p.pulseFreq = ps.rand(6f, 12f);
        } else if (st.equals("Fireflies")) {
            int c = ParticleSystem.mix(0xD8FF6B, fx.color1.rgb(), 0.25f);
            p = ps.glow(x, y - 3, z, 0f, ps.rand(-0.05f, 0.1f), 0f, life * ps.rand(1.4f, 2.2f),
                    size * 0.6f, size * 0.6f, c, c, fx.alpha());
            if (p == null) return;
            p.sway = 0.6f;
            p.swayFreq = ps.rand(0.8f, 1.6f);
            p.pulse = 0.9f;
            p.pulseFreq = ps.rand(2f, 4f);
        } else {
            p = ps.glow(x, y + 3, z, w * 0.4f, ps.rand(-0.9f, -0.5f), 0f, life * 2f,
                    size * ps.rand(0.4f, 0.8f), size * 0.4f, 0xFFFFFF, fx.color2.rgb(), fx.alpha() * 0.9f);
            if (p == null) return;
            p.sway = 0.3f;
            p.swayFreq = ps.rand(1f, 2f);
        }
        p.swayPhase = ps.rand(0f, 6.28f);
        p.pulsePhase = ps.rand(0f, 6.28f);
        p.fadePow = fx.fade.f();
    }

    // ------------------------------------------------------------- flyers

    /**
     * A light that flies through the air leaving a fading trail:
     * kind 0 = straight streak (shooting star), 1 = smooth curves (wisp), 2 = sharp turns (dart).
     */
    private static final class Flyer {
        double x, y, z;
        float vx, vy, vz, age, life, turn, nextTurn, phase, emit;
        int kind, c0, c1;
    }

    private final java.util.List<Flyer> flyers = new java.util.ArrayList<Flyer>();

    private void flyers(ParticleSystem ps, String st, int spawnNow, float area, float size, float life) {
        float dt = Math.min(0.05f, ps.dt);
        int cap = (int) (36 * PerformanceModule.qualityScale());
        // spawn: flyers are fewer than drifting sparkles, but live longer
        for (int i = 0; i < spawnNow && flyers.size() < cap; i++) {
            if (ps.rng.nextFloat() > 0.35f) continue;
            Flyer f = new Flyer();
            f.kind = st.equals("Shooting Stars") ? 0 : st.equals("Wisps") ? 1 : st.equals("Darting Sparks") ? 2 : ps.rng.nextInt(3);
            f.x = ps.camX + ps.rand(-area, area);
            f.y = ps.camY + (f.kind == 0 ? ps.rand(6f, 16f) : ps.rand(-1f, 6f));
            f.z = ps.camZ + ps.rand(-area, area);
            double ang = ps.rand(0f, 6.2831855f);
            float spd = (f.kind == 0 ? ps.rand(9f, 16f) : f.kind == 1 ? ps.rand(2.5f, 4.5f) : ps.rand(4f, 7f)) * fx.speed.f();
            f.vx = (float) Math.cos(ang) * spd;
            f.vz = (float) Math.sin(ang) * spd;
            f.vy = f.kind == 0 ? -spd * ps.rand(0.25f, 0.45f) : ps.rand(-0.4f, 0.4f);
            f.life = f.kind == 0 ? ps.rand(0.7f, 1.2f) : life * ps.rand(1.2f, 2f);
            f.turn = ps.rand(1.2f, 2.6f) * (ps.rng.nextBoolean() ? 1 : -1);
            f.nextTurn = ps.rand(0.25f, 0.6f);
            f.phase = ps.rand(0f, 6.28f);
            f.c0 = ParticleSystem.mix(fx.color1.rgb(), fx.color2.rgb(), ps.rng.nextFloat() * 0.5f);
            f.c1 = fx.color2.rgb();
            flyers.add(f);
        }
        java.util.Iterator<Flyer> it = flyers.iterator();
        while (it.hasNext()) {
            Flyer f = it.next();
            f.age += dt;
            if (f.age >= f.life) {
                it.remove();
                continue;
            }
            if (f.kind == 1) {
                // smooth curve: the velocity keeps turning, with a gentle up-and-down
                double c = Math.cos(f.turn * dt), sn = Math.sin(f.turn * dt);
                float nx = (float) (f.vx * c - f.vz * sn), nz = (float) (f.vx * sn + f.vz * c);
                f.vx = nx;
                f.vz = nz;
                f.vy = (float) Math.sin(f.age * 2.2 + f.phase) * 0.9f;
                if (ps.rng.nextFloat() < dt * 0.6f) f.turn = -f.turn;
            } else if (f.kind == 2) {
                // dart: holds a heading, then snaps to a new one
                f.nextTurn -= dt;
                if (f.nextTurn <= 0f) {
                    f.nextTurn = ps.rand(0.18f, 0.5f);
                    float sp = (float) Math.sqrt(f.vx * f.vx + f.vz * f.vz);
                    double ang = Math.atan2(f.vz, f.vx) + (ps.rng.nextBoolean() ? 1 : -1) * ps.rand(1.0f, 2.3f);
                    f.vx = (float) Math.cos(ang) * sp;
                    f.vz = (float) Math.sin(ang) * sp;
                    f.vy = ps.rand(-1.2f, 1.2f);
                }
            }
            double ox = f.x, oy = f.y, oz = f.z;
            f.x += f.vx * dt;
            f.y += f.vy * dt;
            f.z += f.vz * dt;
            float fade = Math.min(1f, Math.min(f.age / 0.15f, (f.life - f.age) / 0.25f));
            float al = fx.alpha() * fade;
            // trail: short-lived dots laid along the path, shrinking as they fade
            double d = Math.sqrt((f.x - ox) * (f.x - ox) + (f.y - oy) * (f.y - oy) + (f.z - oz) * (f.z - oz));
            f.emit += (float) d;
            float stepLen = f.kind == 0 ? 0.18f : 0.1f;
            while (f.emit >= stepLen) {
                f.emit -= stepLen;
                float t = f.emit / (float) Math.max(1e-4, d);
                Particle p = ps.glow(f.x - (f.x - ox) * t, f.y - (f.y - oy) * t, f.z - (f.z - oz) * t, 0f, 0f, 0f,
                        f.kind == 0 ? 0.45f : 0.6f, size * 0.9f, 0f, f.c0, f.c1, al * 0.85f);
                if (p != null) p.fadePow = 1.6f;
            }
            // bright head
            Particle h = ps.glow(f.x, f.y, f.z, 0f, 0f, 0f, 0.05f, size * 1.8f, size * 1.6f, 0xFFFFFF, f.c0, al);
            if (h != null && f.kind == 0) {
                h.vx = f.vx;
                h.vy = f.vy;
                h.vz = f.vz;
                h.stretch = 0.04f;
            }
        }
    }
}
