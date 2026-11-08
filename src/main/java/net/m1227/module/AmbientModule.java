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
        super("Ambient Particles", "Shards and sparkles drifting through the air.", Category.AMBIENCE, false);
        style = add(new Setting.Mode("Style", "Mixed", "Shards", "Sparkles", "Mixed", "Embers", "Fireflies", "Snow"));
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
}
