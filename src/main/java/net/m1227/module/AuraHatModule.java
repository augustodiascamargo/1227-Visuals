package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Cosmetic: a rotating cone of aura particles floating above the head. */
public final class AuraHatModule extends Module {
    private final Setting.Num height, radius, rings, lift;
    private final FxSettings fx;
    private final Setting.Bool rainbow, others;
    private float step;

    public AuraHatModule() {
        super("Aura Hat", "A cone of glowing particles above the head.", Category.COSMETICS, false);
        height = add(new Setting.Num("Height", 0.8, 0.3, 1.8, 0.05, ""));
        radius = add(new Setting.Num("Radius", 0.5, 0.2, 1.2, 0.05, ""));
        rings = add(new Setting.Num("Rings", 7, 3, 14, 1, ""));
        lift = add(new Setting.Num("Lift", 0.12, 0.0, 0.6, 0.02, ""));
        fx = new FxSettings(this, 0xFF4FA3, 0x8B5CFF, 7, 1.0, 0.07, 0.2);
        rainbow = add(new Setting.Bool("Rainbow", false));
        others = add(new Setting.Bool("Show on other players", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (ps.dt <= 0f) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        step += ps.dt * 50f; // fixed 50 Hz emission keeps the particle count flat at any FPS
        if (step < 1f) return;
        step -= (int) step;

        if (mc.gameSettings.thirdPersonView != 0) hat(mc.thePlayer, e.partialTicks);
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p != mc.thePlayer && !p.isInvisible() && net.m1227.client.perf.EffectBudget.allow(net.m1227.client.perf.EffectBudget.Kind.AURA, p)) hat(p, e.partialTicks);
            }
        }
    }

    private void hat(EntityPlayer p, float pt) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double cx = ParticleSystem.lerp(p.lastTickPosX, p.posX, pt);
        double cz = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, pt);
        double cy = ParticleSystem.lerp(p.lastTickPosY, p.posY, pt) + p.height + lift.get() - (p.isSneaking() ? 0.15 : 0.0);
        if (!ps.inRange(cx, cy, cz)) return;

        int rn = rings.i();
        float hh = height.f(), rr = radius.f(), q = PerformanceModule.qualityScale();
        float size = fx.size.f(), life = fx.duration.f(), spin = fx.speed.f();
        double t = ps.time;
        boolean rb = rainbow.on();

        for (int k = 0; k < rn; k++) {
            float f = rn > 1 ? k / (float) (rn - 1) : 0f;
            float r = rr * (1f - f) + 0.02f;
            int pts = Math.max(3, Math.round(fx.amount.f() * (1f - f * 0.55f) * q));
            double base = t * spin * 2.0 + k * 0.55;
            double y = cy + f * hh;
            int c0 = rb ? ParticleSystem.rainbow((float) (f + t * 0.2)) : ParticleSystem.mix(fx.color1.rgb(), fx.color2.rgb(), f);
            int c1 = rb ? ParticleSystem.rainbow((float) (f + t * 0.2 + 0.3)) : fx.color2.rgb();
            for (int i = 0; i < pts; i++) {
                double a = base + i * (Math.PI * 2.0 / pts);
                Particle part = ps.glow(cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 0f, 0f, 0f,
                        life, size, size * 0.6f, c0, c1, fx.alpha());
                if (part != null) part.fadePow = fx.fade.f();
            }
        }
        // bright tip
        ps.glow(cx, cy + hh + 0.03, cz, 0f, 0f, 0f, life, size * 2.6f, size * 1.6f,
                fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
    }
}
