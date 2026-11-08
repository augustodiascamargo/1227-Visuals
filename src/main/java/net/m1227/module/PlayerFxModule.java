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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Effects when a nearby player appears (join, respawn, un-vanish), disappears
 * close to you (leave, death, vanish) or teleports more than a few blocks in
 * one tick (ender pearls, /tp, respawn points). Players simply walking into
 * or out of render distance are ignored by the distance limit.
 */
public final class PlayerFxModule extends Module {
    private final Setting.Bool appear, vanish, teleport, self;
    private final Setting.Num range, minJump;
    private final FxSettings fx;
    private final Map<Integer, double[]> last = new HashMap<Integer, double[]>();
    private int warmup;

    public PlayerFxModule() {
        super("Player FX", "Effects when players appear, vanish or teleport.", Category.MOVEMENT, false);
        appear = add(new Setting.Bool("Appear", true));
        vanish = add(new Setting.Bool("Vanish", true));
        teleport = add(new Setting.Bool("Teleport", true));
        self = add(new Setting.Bool("Include yourself (teleport)", true));
        range = add(new Setting.Num("Max distance", 32, 8, 64, 2, "m"));
        minJump = add(new Setting.Num("Teleport distance", 6, 3, 20, 1, "m"));
        fx = new FxSettings(this, 0x8B5CFF, 0x4DE1FF, 22, 1.0, 0.1, 0.9);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            last.clear();
            warmup = 0;
            return;
        }
        double r2 = range.f() * range.f(), jump2 = minJump.f() * minJump.f();
        boolean live = warmup >= 40; // ignore everything loaded with the world
        if (!live) warmup++;
        List<EntityPlayer> list = mc.theWorld.playerEntities;
        Map<Integer, double[]> now = new HashMap<Integer, double[]>();
        for (int i = 0; i < list.size(); i++) {
            EntityPlayer p = list.get(i);
            if (p.isInvisible() && p != mc.thePlayer) continue;
            double[] pos = {p.posX, p.posY, p.posZ, p.height};
            now.put(p.getEntityId(), pos);
            double[] before = last.get(p.getEntityId());
            boolean near = p.getDistanceSqToEntity(mc.thePlayer) < r2;
            if (!live) continue;
            if (before == null) {
                if (appear.on() && near && p != mc.thePlayer) burst(pos[0], pos[1], pos[2], p.height, true);
            } else if (teleport.on() && (p != mc.thePlayer || self.on())) {
                double dx = pos[0] - before[0], dy = pos[1] - before[1], dz = pos[2] - before[2];
                if (dx * dx + dy * dy + dz * dz > jump2) {
                    if (mc.thePlayer.getDistanceSq(before[0], before[1], before[2]) < r2)
                        burst(before[0], before[1], before[2], p.height, false);
                    if (near) burst(pos[0], pos[1], pos[2], p.height, true);
                }
            }
        }
        if (live && vanish.on()) {
            for (Iterator<Map.Entry<Integer, double[]>> it = last.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, double[]> en = it.next();
                if (now.containsKey(en.getKey())) continue;
                double[] b = en.getValue();
                if (mc.thePlayer.getDistanceSq(b[0], b[1], b[2]) < r2) burst(b[0], b[1], b[2], (float) b[3], false);
            }
        }
        last.clear();
        last.putAll(now);
    }

    /** in = particles converge into a column; out = column bursts outward and rises. */
    private void burst(double x, double y, double z, float height, boolean in) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (!ps.inRange(x, y + 1, z)) return;
        int n = ps.scale(fx.amount.f());
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f();
        int c1 = fx.color1.rgb(), c2 = fx.color2.rgb();
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), h = ps.rand(0f, height), rad = ps.rand(0.6f, 1.2f);
            float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
            Particle p;
            if (in) {
                p = ps.glow(x + cx * rad, y + h, z + cz * rad, -cx * rad / life * 1.4f * spd, ps.rand(-0.2f, 0.4f),
                        -cz * rad / life * 1.4f * spd, life * ps.rand(0.5f, 0.75f), size * 1.2f, size * 0.2f, c1, c2, fx.alpha());
            } else {
                p = ps.glow(x + cx * 0.25f, y + h, z + cz * 0.25f, cx * spd * ps.rand(1f, 2.5f), ps.rand(0.5f, 2.2f) * spd,
                        cz * spd * ps.rand(1f, 2.5f), life * ps.rand(0.6f, 1f), size, size * 0.15f, c2, c1, fx.alpha());
            }
            if (p == null) continue;
            p.drag = 1.5f;
            p.fadePow = fx.fade.f();
            if (i % 3 == 0) {
                p.sprite = Sprite.STAR;
                p.rotVel = ps.rand(-5f, 5f);
            }
        }
        // vertical beam + floor ring
        for (int k = 0; k < 6; k++) {
            ps.glow(x, y + height * k / 5f, z, 0f, in ? 0f : 1.2f, 0f, life * 0.6f, size * 3.5f, size * (in ? 1f : 5f), c1, c2,
                    fx.alpha() * 0.55f);
        }
        Particle ring = ps.glow(x, y + 0.04, z, 0f, 0f, 0f, life * 0.8f, in ? size * 9f : size * 1.5f, in ? size * 1.5f : size * 10f,
                c2, c1, fx.alpha());
        if (ring != null) {
            ring.sprite = Sprite.RING;
            ring.flat = true;
        }
    }
}
