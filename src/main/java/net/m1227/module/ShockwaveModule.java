package net.m1227.module;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Preview;
import net.m1227.vfx.ShockwaveSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Expanding energy domes with ground rings, debris and dust. Triggers:
 * when you finish a target, when a TNT you can see explodes, and on hard
 * landings. The "Preview" button plays one beside the menu.
 */
public final class ShockwaveModule extends Module {
    private final FxSettings fx;
    private final Setting.Num radius, minFall;
    private final Setting.Bool onKill, onTnt, onLanding;

    private final Map<Integer, double[]> tnt = new HashMap<Integer, double[]>();
    private int tick;
    private float maxFall;
    private boolean wasAir;

    public ShockwaveModule() {
        super("Shockwave", "Expanding energy domes with rings, debris and dust.", Category.COMBAT, true);
        fx = new FxSettings(this, 0xFFFFFF, 0xBFEFFF, 24, 1.0, 0.12, 1.4);
        radius = add(new Setting.Num("Radius", 7, 2, 24, 1, "m"));
        onKill = add(new Setting.Bool("On kill", true));
        onTnt = add(new Setting.Bool("On TNT explosion", true));
        onLanding = add(new Setting.Bool("On hard landing", false));
        minFall = add(new Setting.Num("Landing min height", 4, 2, 20, 1, "m"));
        add(new Setting.Action("Preview", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(5.0, 3.4);
                if (a != null) trigger(a[0], a[1], a[2], 1f);
            }
        }));
    }

    @SubscribeEvent
    public void onKillEvent(CombatTracker.KillEvent e) {
        if (!onKill.on()) return;
        trigger(e.target.posX, e.target.posY, e.target.posZ, 0.6f);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            tnt.clear();
            return;
        }
        tick++;

        if (onTnt.on()) {
            List<Entity> list = mc.theWorld.loadedEntityList;
            for (int i = 0; i < list.size(); i++) {
                Entity en = list.get(i);
                if (en instanceof EntityTNTPrimed) {
                    double[] rec = tnt.get(en.getEntityId());
                    if (rec == null) {
                        rec = new double[4];
                        tnt.put(en.getEntityId(), rec);
                    }
                    rec[0] = en.posX;
                    rec[1] = en.posY;
                    rec[2] = en.posZ;
                    rec[3] = tick;
                }
            }
            if (!tnt.isEmpty()) {
                Iterator<Map.Entry<Integer, double[]>> it = tnt.entrySet().iterator();
                while (it.hasNext()) {
                    double[] rec = it.next().getValue();
                    if (rec[3] != tick) {
                        trigger(rec[0], rec[1], rec[2], 1.2f);
                        it.remove();
                    }
                }
            }
        }

        EntityPlayerSP p = mc.thePlayer;
        if (!p.onGround) {
            maxFall = Math.max(maxFall, p.fallDistance);
            wasAir = true;
        } else {
            if (wasAir && onLanding.on() && maxFall >= minFall.f()) {
                trigger(p.posX, p.posY, p.posZ, Math.min(1f, maxFall / 12f) * 0.8f);
            }
            maxFall = 0f;
            wasAir = false;
        }
    }

    /** Plays the full effect at a ground position. */
    public void trigger(double x, double y, double z, float scale) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (!ps.inRange(x, y, z)) return;
        float life = fx.duration.f(), r = radius.f() * Math.max(0.2f, scale);
        float size = fx.size.f(), fade = fx.fade.f();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        ShockwaveSystem.INSTANCE.spawn(x, y + 0.05, z, r, life, c0, c1, fx.alpha());

        // debris: crystals, petals and stars thrown outward
        int n = ps.scale(fx.amount.f() * 1.5f);
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f);
            float out = r / life * ps.rand(0.45f, 0.9f);
            float roll = ps.rng.nextFloat();
            int sprite = roll < 0.4f ? Sprite.CRYSTAL : (roll < 0.75f ? Sprite.PETAL : Sprite.STAR);
            float s = size * ps.rand(0.7f, 1.6f);
            Particle p = ps.glow(x + Math.cos(a) * 0.5, y + 0.1, z + Math.sin(a) * 0.5,
                    (float) Math.cos(a) * out, ps.rand(2f, 5f), (float) Math.sin(a) * out,
                    life * ps.rand(0.7f, 1.1f), s, s * 0.6f, c0, c1, fx.alpha());
            if (p == null) continue;
            p.sprite = sprite;
            p.rot = ps.rand(0f, 6.28f);
            p.rotVel = ps.rand(-6f, 6f);
            p.gravity = 8f;
            p.drag = 0.8f;
            p.fadePow = fade;
        }

        // dust ring (normal alpha blending)
        int d = ps.scale(fx.amount.f() * 0.8f);
        for (int i = 0; i < d; i++) {
            float a = i * (6.2831855f / Math.max(1, d)) + ps.rand(-0.1f, 0.1f);
            float out = r / life * ps.rand(0.3f, 0.6f);
            Particle p = ps.glow(x + Math.cos(a) * 0.4, y + 0.15, z + Math.sin(a) * 0.4,
                    (float) Math.cos(a) * out, 0.3f, (float) Math.sin(a) * out,
                    life * ps.rand(0.8f, 1.2f), size * 2f, size * 8f,
                    ParticleSystem.mix(0xD7DCE2, c0, 0.15f), 0x8A929C, 0.38f);
            if (p == null) continue;
            p.sprite = Sprite.SMOKE;
            p.alphaBlend = true;
            p.rot = ps.rand(0f, 6.28f);
            p.rotVel = ps.rand(-0.5f, 0.5f);
            p.drag = 2.2f;
            p.fadePow = 1.2f;
        }
    }
}
