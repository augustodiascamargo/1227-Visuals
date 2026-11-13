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
 *
 * Styles: Classic (converging column / outward burst), and since 1.20.2 the styles of the old
 * Arrival Effects module, which did the same job and was merged here: Beam, Portal, Smoke,
 * Pixels and Rift.
 */
public final class PlayerFxModule extends Module {
    private final Setting.Mode style;
    private final Setting.Bool appear, vanish, teleport, self;
    private final Setting.Num range, minJump;
    private final FxSettings fx;
    private final Map<Integer, double[]> last = new HashMap<Integer, double[]>();
    private int warmup;

    public PlayerFxModule() {
        super("Player FX", "Effects when players appear, vanish or teleport.", Category.MOVEMENT, false);
        style = add(new Setting.Mode("Style", "Classic", "Classic", "Beam", "Portal", "Smoke", "Pixels", "Rift"));
        appear = add(new Setting.Bool("Appear", true));
        vanish = add(new Setting.Bool("Vanish", true));
        teleport = add(new Setting.Bool("Teleport", true));
        self = add(new Setting.Bool("Include yourself (teleport)", true));
        range = add(new Setting.Num("Max distance", 32, 8, 64, 2, "m"));
        minJump = add(new Setting.Num("Teleport distance", 6, 3, 20, 1, "m"));
        fx = new FxSettings(this, 0x8B5CFF, 0x4DE1FF, 22, 1.0, 0.1, 0.9);
        add(new Setting.Action("Preview teleport", new Runnable() {
            @Override
            public void run() {
                double[] a = net.m1227.vfx.Preview.ahead(4.5, 3.0);
                if (a == null) return;
                double sx = -a[4] * 1.6, sz = a[3] * 1.6; // one step to the side
                burst(a[0] - sx, a[1], a[2] - sz, 1.8f, false);
                burst(a[0] + sx, a[1], a[2] + sz, 1.8f, true);
            }
        }));
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
        if (!style.is("Classic")) {
            styled(ps, x, y, z, in);
            return;
        }
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

    /** The styles that came from Arrival Effects; size follows the module's Size (0.1 = 1x). */
    private void styled(ParticleSystem ps, double x, double y, double z, boolean in) {
        float s = Math.max(0.3f, fx.size.f() / 0.1f);
        int c0 = in ? fx.color1.rgb() : fx.color2.rgb(), c1 = in ? fx.color2.rgb() : fx.color1.rgb();
        float a = fx.alpha();
        String st = style.get();
        if (st.equals("Beam")) {
            for (int i = 0; i < 14; i++) {
                Particle p = ps.glow(x, y + i * 0.22 * s, z, 0f, in ? -0.4f : 1.4f, 0f, 0.6f, 0.9f * s, 0.2f, c0, c1, 0.75f * a);
                if (p != null) p.fadePow = 1.6f;
            }
            ring(ps, x, y + 0.05, z, 0.2f, 2.4f * s, c0, c1, a);
            for (int i = 0; i < 24; i++) {
                double an = Math.random() * Math.PI * 2, r = Math.random() * 0.5 * s;
                Particle p = ps.glow(x + Math.cos(an) * r, y + Math.random() * 0.4, z + Math.sin(an) * r, 0f, 1.5f + (float) Math.random() * 2f, 0f,
                        0.9f, 0.18f * s, 0.02f, c1, c0, 0.9f * a);
                if (p != null) p.drag = 1.2f;
            }
        } else if (st.equals("Portal")) {
            for (int i = 0; i < 48; i++) {
                double an = Math.PI * 2 * i / 48, r = 1.1 * s, h = 0.1 + Math.random() * 1.8;
                float vx = (float) (-Math.cos(an) * 1.6 - Math.sin(an) * 1.2), vz = (float) (-Math.sin(an) * 1.6 + Math.cos(an) * 1.2);
                if (!in) {
                    vx = -vx;
                    vz = -vz;
                }
                double sx = in ? x + Math.cos(an) * r : x, sz = in ? z + Math.sin(an) * r : z;
                Particle p = ps.glow(sx, y + h, sz, vx * s, 0f, vz * s, 0.65f, 0.22f * s, 0.04f, c0, c1, 0.9f * a);
                if (p != null) p.fadePow = 1.3f;
            }
            ring(ps, x, y + 1.0, z, 1.6f * s, 0.2f, c0, c1, a);
        } else if (st.equals("Smoke")) {
            for (int i = 0; i < 26; i++) {
                double an = Math.random() * Math.PI * 2;
                Particle p = ps.glow(x + Math.cos(an) * 0.3, y + Math.random() * 1.6, z + Math.sin(an) * 0.3,
                        (float) Math.cos(an) * 0.9f, 0.3f + (float) Math.random() * 0.4f, (float) Math.sin(an) * 0.9f, 1.4f, 0.5f * s, 1.3f * s,
                        0xC8CCD6, c0, 0.45f * a);
                if (p != null) {
                    p.sprite = Sprite.SMOKE;
                    p.alphaBlend = true;
                    p.drag = 1.6f;
                }
            }
        } else if (st.equals("Pixels")) {
            net.m1227.vfx.CubeBurst.INSTANCE.spawn(x, y + 0.3, z, (int) (36 * s), s, 0.7f, c0, c1);
            ps.glow(x, y + 1, z, 0f, 0f, 0f, 0.3f, 2.5f * s, 5f * s, c1, c0, 0.5f * a);
        } else { // Rift: a bright vertical slit that opens and closes
            for (int i = 0; i < 20; i++) {
                double h = i / 19.0 * 2.0 * s;
                float wdt = (float) Math.sin(i / 19.0 * Math.PI) * 0.7f * s;
                Particle p = ps.glow(x, y + h, z, 0f, 0f, 0f, 0.55f, wdt * 0.2f, wdt, 0xFFFFFF, c0, 0.9f * a);
                if (p != null) p.fadePow = 1.2f;
            }
            ring(ps, x, y + 1.0 * s, z, 0.3f, 2.2f * s, c0, c1, a);
        }
    }

    private static void ring(ParticleSystem ps, double x, double y, double z, float from, float to, int c0, int c1, float a) {
        Particle p = ps.glow(x, y, z, 0f, 0f, 0f, 0.55f, from, to, c0, c1, 0.85f * a);
        if (p != null) {
            p.sprite = Sprite.RING;
            p.flat = true;
            p.fadePow = 1.4f;
        }
    }
}
