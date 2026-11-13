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
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
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
    private final Setting.Bool onKill, onTnt, onLanding, styleColors;
    private final Setting.Mode style;
    public static final String[] STYLES = {"Crystal Dome", "Frost Nova", "Inferno", "Thunder", "Void Rift", "Sakura", "Holy Light", "Shatter Ring"};

    private final Map<Integer, double[]> tnt = new HashMap<Integer, double[]>();
    private int tick;
    private float maxFall;
    private boolean wasAir;

    public ShockwaveModule() {
        super("Shockwave", "8 shockwave styles: crystal, frost, inferno, thunder, void, sakura, holy, shatter.", Category.COMBAT, true);
        style = add(new Setting.Mode("Style", "Crystal Dome", STYLES));
        styleColors = add(new Setting.Bool("Use the style's own colors", true)).showIf(() -> !style.is("Crystal Dome"));
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
        net.m1227.vfx.ParticleSystem.beginCombat();
        try {
            if (!onKill.on()) return;
            trigger(e.target.posX, e.target.posY, e.target.posZ, 0.6f);
        } finally {
            net.m1227.vfx.ParticleSystem.endCombat();
        }
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
        String st = style.get();
        if (!st.equals("Crystal Dome")) {
            if (styleColors.on()) {
                int[] pal = palette(st);
                c0 = pal[0];
                c1 = pal[1];
            }
            styled(st, x, y, z, r, life, size, fade, c0, c1);
            return;
        }
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

    private static int[] palette(String st) {
        if (st.equals("Frost Nova")) return new int[]{0xE6FBFF, 0x5CC8FF};
        if (st.equals("Inferno")) return new int[]{0xFFD36B, 0xFF3D00};
        if (st.equals("Thunder")) return new int[]{0xFFFFFF, 0x8FB8FF};
        if (st.equals("Void Rift")) return new int[]{0xC77DFF, 0x2A0A5C};
        if (st.equals("Sakura")) return new int[]{0xFFD6E8, 0xFF6FAE};
        if (st.equals("Holy Light")) return new int[]{0xFFF6D6, 0xFFC94D};
        return new int[]{0xFFFFFF, 0x9AA4B8}; // Shatter Ring
    }

    /** Flat ring sprite lying on the ground that grows from r0 to r1. */
    private Particle groundRing(ParticleSystem ps, double x, double y, double z, float r0, float r1, float life, int c0, int c1, float a, int sprite) {
        Particle p = ps.glow(x, y, z, 0f, 0f, 0f, life, r0, r1, c0, c1, a);
        if (p == null) return null;
        p.flat = true;
        p.sprite = sprite;
        p.rot = ps.rand(0f, 6.28f);
        p.fadePow = 1.4f;
        return p;
    }

    private void styled(String st, double x, double y, double z, float r, float life, float size, float fade, int c0, int c1) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        float al = fx.alpha();
        int n = ps.scale(fx.amount.f() * 1.5f);
        double gy = y + 0.06;
        if (st.equals("Frost Nova")) {
            groundRing(ps, x, gy, z, 0.5f, r * 1.1f, life * 0.8f, c0, c1, al, Sprite.RING);
            groundRing(ps, x, gy + 0.01, z, 0.3f, r * 0.7f, life, 0xFFFFFF, c1, al * 0.7f, Sprite.HEX);
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, 6.2831855f), out = r / life * ps.rand(0.5f, 1f);
                Particle p = ps.glow(x, y + 0.1, z, (float) Math.cos(a) * out, ps.rand(0.5f, 2.5f), (float) Math.sin(a) * out,
                        life * ps.rand(0.6f, 1f), size * ps.rand(0.8f, 1.5f), size * 0.4f, c0, c1, al);
                if (p == null) continue;
                p.sprite = i % 3 == 0 ? Sprite.SHARD : Sprite.CRYSTAL;
                p.rot = ps.rand(0f, 6.28f);
                p.rotVel = ps.rand(-3f, 3f);
                p.drag = 1.6f;
                p.gravity = 2f;
                p.fadePow = fade;
            }
            // snow drifting down
            for (int i = 0; i < n / 2; i++) {
                Particle p = ps.glow(x + ps.rand(-r, r) * 0.6, y + ps.rand(1.5f, 3.5f), z + ps.rand(-r, r) * 0.6, 0f, -0.6f, 0f,
                        life * 1.6f, size * 0.5f, size * 0.4f, 0xFFFFFF, c0, al * 0.8f);
                if (p == null) continue;
                p.sprite = Sprite.GLOW;
                p.sway = 0.4f;
                p.swayFreq = 2f;
            }
        } else if (st.equals("Inferno")) {
            groundRing(ps, x, gy, z, 0.6f, r, life * 0.7f, c0, c1, al, Sprite.RING);
            for (int i = 0; i < n * 2; i++) {
                float a = ps.rand(0f, 6.2831855f), d = ps.rand(0.2f, r * 0.8f);
                Particle p = ps.glow(x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0f, ps.rand(2f, 6f), 0f,
                        life * ps.rand(0.5f, 1f), size * ps.rand(1f, 2.2f), size * 0.3f, c0, c1, al);
                if (p == null) continue;
                p.sprite = Sprite.FLAME;
                p.drag = 1.2f;
                p.fadePow = fade;
            }
            int d = ps.scale(fx.amount.f() * 0.8f);
            for (int i = 0; i < d; i++) {
                Particle p = ps.glow(x + ps.rand(-1f, 1f), y + 0.3, z + ps.rand(-1f, 1f), ps.rand(-0.5f, 0.5f), ps.rand(1f, 2.5f), ps.rand(-0.5f, 0.5f),
                        life * 1.5f, size * 2.5f, size * 7f, 0x3A2A24, 0x111111, 0.35f);
                if (p == null) continue;
                p.sprite = Sprite.SMOKE;
                p.alphaBlend = true;
                p.drag = 1.5f;
            }
        } else if (st.equals("Thunder")) {
            groundRing(ps, x, gy, z, 0.4f, r, life * 0.6f, c0, c1, al, Sprite.RING);
            int bolts = 6 + (int) (fx.amount.f() / 8f);
            for (int i = 0; i < bolts; i++) {
                double a = i * Math.PI * 2 / bolts + ps.rand(-0.2f, 0.2f);
                double ex = x + Math.cos(a) * r * 0.8, ez = z + Math.sin(a) * r * 0.8;
                net.m1227.vfx.Arcs.INSTANCE.spawn(x, y + 0.2, z, ex, y + 0.1, ez, 0.35f, 0.35f, c1, al);
            }
            net.m1227.vfx.Arcs.INSTANCE.spawn(x, y + 12, z, x, y + 0.2, z, 0.45f, 0.8f, c0, al);
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, 6.2831855f), out = r / life * ps.rand(0.6f, 1.2f);
                Particle p = ps.glow(x, y + 0.2, z, (float) Math.cos(a) * out, ps.rand(1f, 4f), (float) Math.sin(a) * out,
                        life * 0.5f, size * 0.6f, 0f, 0xFFFFFF, c1, al);
                if (p == null) continue;
                p.stretch = 0.08f;
                p.gravity = 6f;
            }
        } else if (st.equals("Void Rift")) {
            // pulled in first, then thrown out
            groundRing(ps, x, gy, z, r * 1.1f, 0.2f, life * 0.5f, c0, c1, al, Sprite.RUNE);
            groundRing(ps, x, gy + 0.01, z, 0.2f, r, life, c1, c0, al * 0.8f, Sprite.RING);
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, 6.2831855f), d = r * ps.rand(0.6f, 1f);
                double sx = x + Math.cos(a) * d, sz = z + Math.sin(a) * d;
                float in = d / (life * 0.45f);
                Particle p = ps.glow(sx, y + ps.rand(0.2f, 2f), sz, (float) -Math.cos(a) * in, 0f, (float) -Math.sin(a) * in,
                        life * 0.45f, size * 1.2f, size * 0.2f, c0, c1, al);
                if (p == null) continue;
                p.sprite = Sprite.GLOW;
                p.stretch = 0.05f;
            }
            Particle core = ps.glow(x, y + 1.0, z, 0f, 0f, 0f, life * 0.6f, size * 2f, size * 7f, c1, c0, al);
            if (core != null) core.sprite = Sprite.HALO;
        } else if (st.equals("Sakura")) {
            groundRing(ps, x, gy, z, 0.5f, r * 0.9f, life, c0, c1, al * 0.7f, Sprite.HALO);
            for (int i = 0; i < n * 2; i++) {
                float a = ps.rand(0f, 6.2831855f), out = r / life * ps.rand(0.3f, 0.8f);
                Particle p = ps.glow(x, y + 0.4, z, (float) Math.cos(a) * out, ps.rand(1.5f, 4f), (float) Math.sin(a) * out,
                        life * ps.rand(1f, 1.6f), size * ps.rand(0.8f, 1.4f), size, c0, c1, al);
                if (p == null) continue;
                p.sprite = Sprite.PETAL;
                p.rot = ps.rand(0f, 6.28f);
                p.rotVel = ps.rand(-4f, 4f);
                p.gravity = 1.2f;
                p.drag = 1.4f;
                p.sway = 0.6f;
                p.swayFreq = 1.6f;
            }
        } else if (st.equals("Holy Light")) {
            groundRing(ps, x, gy, z, 0.5f, r * 0.8f, life, c0, c1, al, Sprite.RUNE);
            groundRing(ps, x, gy + 0.01, z, 0.2f, r * 1.1f, life * 0.7f, 0xFFFFFF, c1, al * 0.7f, Sprite.RING);
            for (int i = 0; i < 6; i++) {
                Particle p = ps.glow(x, y + 0.1 + i * 1.4, z, 0f, 0f, 0f, life, size * 3f, size * 1.5f, 0xFFFFFF, c1, al * 0.6f);
                if (p != null) p.sprite = Sprite.BEAM;
            }
            for (int i = 0; i < n; i++) {
                float a = ps.rand(0f, 6.2831855f), d = ps.rand(0.2f, r * 0.6f);
                Particle p = ps.glow(x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d, 0f, ps.rand(1.5f, 4f), 0f,
                        life * ps.rand(0.8f, 1.3f), size * 0.8f, 0f, 0xFFFFFF, c0, al);
                if (p == null) continue;
                p.sprite = Sprite.STAR;
                p.rot = ps.rand(0f, 6.28f);
                p.drag = 1f;
            }
        } else { // Shatter Ring
            groundRing(ps, x, gy, z, 0.5f, r, life * 0.6f, c0, c1, al, Sprite.RING);
            for (int i = 0; i < n * 2; i++) {
                float a = i * (6.2831855f / Math.max(1, n * 2)), out = r / life * ps.rand(0.8f, 1.1f);
                Particle p = ps.glow(x + Math.cos(a) * 0.4, y + 0.2, z + Math.sin(a) * 0.4, (float) Math.cos(a) * out, ps.rand(0.5f, 3f), (float) Math.sin(a) * out,
                        life * ps.rand(0.6f, 1f), size * ps.rand(0.8f, 1.6f), size * 0.5f, c0, c1, al);
                if (p == null) continue;
                p.sprite = Sprite.SHARD;
                p.rot = ps.rand(0f, 6.28f);
                p.rotVel = ps.rand(-8f, 8f);
                p.gravity = 9f;
                p.drag = 0.6f;
                p.fadePow = fade;
            }
        }
    }

}
