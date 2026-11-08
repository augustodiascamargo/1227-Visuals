package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Cosmetics above the head or around the body: halo, crown of stars, orbiting orbs. */
public final class HaloModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Num height;
    private final Setting.Bool others;
    private float trailAcc;

    public HaloModule() {
        super("Halo", "Halo, crown of stars or orbiting orbs.", Category.COSMETICS, false);
        style = add(new Setting.Mode("Style", "Angel Halo", "Angel Halo", "Star Crown", "Orbiting Orbs"));
        fx = new FxSettings(this, 0xFFF4C2, 0xFFFFFF, 3, 1.0, 0.3, 0.35);
        height = add(new Setting.Num("Height above head", 0.28, 0.05, 0.8, 0.01, ""));
        others = add(new Setting.Bool("Show on other players", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.gameSettings.thirdPersonView != 0) draw(mc.thePlayer, e.partialTicks);
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p != mc.thePlayer && !p.isInvisible()) draw(p, e.partialTicks);
            }
        }
    }

    private void draw(EntityPlayer p, float pt) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double cx = ParticleSystem.lerp(p.lastTickPosX, p.posX, pt);
        double cy = ParticleSystem.lerp(p.lastTickPosY, p.posY, pt);
        double cz = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, pt);
        if (!ps.inRange(cx, cy, cz)) return;
        double t = ps.time;
        float spd = fx.speed.f(), size = fx.size.f(), alpha = fx.alpha();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        double head = cy + p.height - (p.isSneaking() ? 0.2 : 0.0);
        double bob = Math.sin(t * 2.2 * spd) * 0.03;
        String st = style.get();

        if (st.equals("Angel Halo")) {
            double y = head + height.get() + bob;
            AuraShapes.sprite(ps, Sprite.GLOW, cx, y, cz, size * 1.35f, 0f, true, c0, alpha * 0.35f);
            AuraShapes.sprite(ps, Sprite.RING, cx, y, cz, size, (float) (t * 0.6 * spd), true, c0, alpha);
            AuraShapes.sprite(ps, Sprite.RING, cx, y + 0.002, cz, size * 0.96f, 0f, true, c1, alpha * 0.6f);
        } else if (st.equals("Star Crown")) {
            int n = Math.max(3, Math.min(12, fx.amount.i() + 3));
            double y = head + height.get() * 0.6 + bob;
            for (int i = 0; i < n; i++) {
                double a = t * 1.2 * spd + i * (Math.PI * 2 / n);
                float tw = (float) (0.6 + 0.4 * Math.sin(t * 4 + i));
                AuraShapes.sprite(ps, Sprite.STAR, cx + Math.cos(a) * size * 1.1, y + Math.sin(t * 3 + i) * 0.03,
                        cz + Math.sin(a) * size * 1.1, size * 0.35f * tw, (float) (t + i), false,
                        ParticleSystem.mix(c0, c1, i / (float) n), alpha * tw);
            }
        } else {
            int n = Math.max(1, Math.min(6, fx.amount.i()));
            trailAcc += ps.dt * 40f * PerformanceModule.qualityScale();
            boolean emit = trailAcc >= 1f;
            if (emit) trailAcc = 0f;
            for (int i = 0; i < n; i++) {
                double a = t * 2.4 * spd + i * (Math.PI * 2 / n);
                double r = p.width * 0.5 + 0.5;
                double ox = cx + Math.cos(a) * r, oz = cz + Math.sin(a) * r;
                double oy = cy + p.height * (0.55 + 0.25 * Math.sin(t * 1.7 * spd + i * 2.1));
                int c = ParticleSystem.mix(c0, c1, i / (float) n);
                AuraShapes.sprite(ps, Sprite.GLOW, ox, oy, oz, size * 0.45f, 0f, false, 0xFFFFFF, alpha);
                AuraShapes.sprite(ps, Sprite.GLOW, ox, oy, oz, size * 0.9f, 0f, false, c, alpha * 0.6f);
                if (emit && ps.dt > 0f) {
                    Particle tr = ps.glow(ox, oy, oz, 0f, 0f, 0f, fx.duration.f(), size * 0.4f, 0f, c, c1, alpha * 0.7f);
                    if (tr != null) tr.fadePow = fx.fade.f();
                }
            }
        }
    }
}
