package net.m1227.module;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Trails behind arrows, snowballs, eggs, ender pearls, splash potions and fishing hooks, while
 * they are in flight. Drawn only on your screen; projectiles themselves are untouched.
 */
public final class ProjectileTrailsModule extends Module {
    private final Setting.Mode style, who;
    private final Setting.Bool arrows, throwables, pearls, rods;
    private final FxSettings fx;
    private final Map<Integer, double[]> last = new HashMap<Integer, double[]>();

    public ProjectileTrailsModule() {
        super("Projectile Trails", "Trails behind arrows, pearls, snowballs, potions and rods.", Category.COMBAT, false);
        style = add(new Setting.Mode("Style", "Glow", "Glow", "Sparkle", "Smoke", "Rainbow", "Flame"));
        who = add(new Setting.Mode("Projectiles from", "Everyone", "Everyone", "Only me"));
        arrows = add(new Setting.Bool("Arrows", true));
        throwables = add(new Setting.Bool("Snowballs, eggs and potions", true));
        pearls = add(new Setting.Bool("Ender pearls", true));
        rods = add(new Setting.Bool("Fishing rod hook", false));
        fx = new FxSettings(this, 0xB89CFF, 0x5CC8FF, 6, 1.0, 0.25, 0.6);
    }

    private boolean wanted(Entity e) {
        if (e instanceof EntityArrow) return arrows.on();
        if (e instanceof EntityEnderPearl) return pearls.on();
        if (e instanceof EntitySnowball || e instanceof EntityEgg || e instanceof EntityPotion) return throwables.on();
        if (e instanceof EntityFishHook) return rods.on();
        return false;
    }

    private static Entity owner(Entity e) {
        if (e instanceof EntityArrow) return ((EntityArrow) e).shootingEntity;
        if (e instanceof net.minecraft.entity.projectile.EntityThrowable) return ((net.minecraft.entity.projectile.EntityThrowable) e).getThrower();
        if (e instanceof EntityFishHook) return ((EntityFishHook) e).angler;
        return null;
    }

    @Override
    protected void onDisable() {
        last.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.isGamePaused()) {
            last.clear();
            return;
        }
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Map<Integer, double[]> seen = new HashMap<Integer, double[]>();
        List<Entity> list = mc.theWorld.loadedEntityList;
        for (int i = 0; i < list.size(); i++) {
            Entity en = list.get(i);
            if (!wanted(en) || en.isDead) continue;
            if (who.is("Only me") && owner(en) != mc.thePlayer) continue;
            if (!ps.inRange(en.posX, en.posY, en.posZ)) continue;
            double[] prev = last.get(en.getEntityId());
            seen.put(en.getEntityId(), new double[]{en.posX, en.posY, en.posZ});
            if (prev == null) continue;
            double dx = en.posX - prev[0], dy = en.posY - prev[1], dz = en.posZ - prev[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.08) continue; // landed / stuck: no trail
            int n = Math.max(1, Math.min(12, (int) Math.round(d * fx.amount.f() / 3.0)));
            for (int k = 0; k < n; k++) {
                double t = k / (double) n;
                spawn(ps, prev[0] + dx * t, prev[1] + dy * t + en.height * 0.5, prev[2] + dz * t, (float) (ps.time + i * 0.1 + t));
            }
        }
        last.clear();
        last.putAll(seen);
    }

    private void spawn(ParticleSystem ps, double x, double y, double z, float phase) {
        String st = style.get();
        float size = fx.size.f() * 0.5f, life = fx.duration.f();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        if (st.equals("Rainbow")) {
            c0 = ParticleSystem.rainbow(phase * 0.15f);
            c1 = ParticleSystem.rainbow(phase * 0.15f + 0.1f);
        }
        Particle p;
        if (st.equals("Smoke")) {
            p = ps.glow(x, y, z, ps.rand(-0.05f, 0.05f), 0.08f, ps.rand(-0.05f, 0.05f), life * 1.4f, size * 1.4f, size * 3f, 0x9A9EA8, 0x4A4E57, 0.35f * fx.alpha());
            if (p != null) {
                p.sprite = Sprite.SMOKE;
                p.alphaBlend = true;
            }
        } else if (st.equals("Flame")) {
            p = ps.glow(x, y, z, 0f, 0.15f, 0f, life * 0.7f, size * 1.4f, size * 0.3f, 0xFFD36B, 0xFF3D00, fx.alpha());
            if (p != null) p.sprite = Sprite.FLAME;
        } else if (st.equals("Sparkle")) {
            p = ps.glow(x + ps.rand(-0.06f, 0.06f), y, z + ps.rand(-0.06f, 0.06f), ps.rand(-0.1f, 0.1f), ps.rand(-0.1f, 0.1f), ps.rand(-0.1f, 0.1f),
                    life, size * 1.1f, 0f, 0xFFFFFF, c0, fx.alpha());
            if (p != null) {
                p.sprite = Sprite.STAR;
                p.rot = ps.rand(0f, 6.28f);
            }
        } else {
            p = ps.glow(x, y, z, 0f, 0f, 0f, life, size, size * 0.2f, c0, c1, fx.alpha());
        }
    }
}
