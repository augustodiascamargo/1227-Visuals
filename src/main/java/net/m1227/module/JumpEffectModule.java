package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.Arcs;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Preview;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 20 effects at your feet when you jump; any of them can also play as a landing impact. */
public final class JumpEffectModule extends Module {
    public static final String[] STYLES = {
            "Lightning", "Orb Ring", "Ripple", "Dust Puff", "Rune Seal", "Shock Ring", "Star Burst", "Crystal Spikes",
            "Flame Burst", "Light Pillar", "Petal Bloom", "Snow Puff", "Hex Burst", "Spark Fountain", "Smoke Ring",
            "Electric Ring", "Comet Drop", "Halo Drop", "Bubble Pop", "Cash Burst"
    };

    private final Setting.Mode style, landStyle;
    private final FxSettings fx;
    private final Setting.Bool onJump, onLand;
    private final Setting.Num minFall, intensity;
    private boolean wasGround = true;
    private float maxFall;

    public JumpEffectModule() {
        super("Jump Effects", "20 effects when you jump, plus a landing impact.", Category.MOVEMENT, false);
        style = add(new Setting.Mode("Jump style", "Lightning", STYLES));
        landStyle = add(new Setting.Mode("Landing style", "Ripple", STYLES));
        fx = new FxSettings(this, 0xFF8A3D, 0xFFD36B, 10, 1.0, 0.12, 0.6);
        intensity = add(new Setting.Num("Intensity", 1.0, 0.3, 2.0, 0.1, "x"));
        onJump = add(new Setting.Bool("On jump", true));
        onLand = add(new Setting.Bool("Landing impact", true));
        minFall = add(new Setting.Num("Landing min height", 2.5, 1, 12, 0.5, "m"));
        add(new Setting.Action("Preview jump style", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(4.5, 3.0);
                if (a != null) play(style.get(), a[0], a[1], a[2], 1f);
            }
        }));
        add(new Setting.Action("Preview landing style", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(4.5, 3.0);
                if (a != null) play(landStyle.get(), a[0], a[1], a[2], 1.3f);
            }
        }));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return;
        boolean ground = p.onGround;
        if (onJump.on() && wasGround && !ground && p.motionY > 0.2 && !p.capabilities.isFlying) {
            play(style.get(), p.posX, p.posY, p.posZ, 1f);
        }
        if (!ground) maxFall = Math.max(maxFall, p.fallDistance);
        if (!wasGround && ground) {
            if (onLand.on() && maxFall >= minFall.f()) {
                play(landStyle.get(), p.posX, p.posY, p.posZ, Math.min(1.8f, 0.7f + maxFall / 10f));
            }
            maxFall = 0f;
        }
        wasGround = ground;
    }

    /** y is the ground (feet) height. */
    public void play(String s, double x, double y, double z, float scale) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        if (!ps.inRange(x, y, z)) return;
        scale *= intensity.f();
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f(), a = fx.alpha();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        int n = Math.max(4, ps.scale(fx.amount.f() * scale));
        double gy = y + 0.06;

        if (s.equals("Lightning")) {
            int k = Math.max(3, ps.scale(fx.amount.f() * 0.7f));
            for (int i = 0; i < k; i++) {
                double ang = i * (Math.PI * 2 / k) + ps.rand(-0.3f, 0.3f);
                float len = ps.rand(0.9f, 1.7f) * scale;
                Arcs.INSTANCE.spawn(x, gy, z, x + Math.cos(ang) * len, gy + ps.rand(-0.02f, 0.25f), z + Math.sin(ang) * len,
                        life * ps.rand(0.5f, 0.9f), 0.18f * scale, ParticleSystem.mix(c0, c1, ps.rng.nextFloat()), a);
            }
            flash(ps, x, gy + 0.1, z, size, c1, c0, a);
        } else if (s.equals("Orb Ring")) {
            ring(ps, x, gy + 0.08, z, n, 2.2f * spd * scale, 0.15f, life, size * 1.3f, size * 0.5f, 0xFFFFFF, c1, a, Sprite.GLOW, fade);
            flat(ps, x, gy, z, Sprite.RING, life, size, size * 11f * scale, c0, c1, a, 0f);
        } else if (s.equals("Ripple")) {
            for (int k = 0; k < 3; k++) {
                flat(ps, x, gy + k * 0.005, z, k == 1 ? Sprite.HALO : Sprite.RING, life * (0.8f + k * 0.35f), size,
                        size * (10f + k * 4f) * scale, k == 0 ? 0xFFFFFF : c0, c1, a * (1f - k * 0.25f), k * 0.7f);
            }
        } else if (s.equals("Dust Puff") || s.equals("Smoke Ring")) {
            boolean ringShape = s.equals("Smoke Ring");
            for (int i = 0; i < n; i++) {
                float ang = ringShape ? i * 6.2831855f / n : ps.rand(0f, 6.28f);
                float sp = (ringShape ? 2.4f : ps.rand(1.2f, 2.4f)) * spd * scale;
                Particle p = ps.glow(x, gy + 0.1, z, (float) Math.cos(ang) * sp, ringShape ? 0.05f : ps.rand(0.1f, 0.4f),
                        (float) Math.sin(ang) * sp, life * ps.rand(1.1f, 1.6f), size * 2f, size * 6f,
                        ParticleSystem.mix(0xD7DCE2, c0, 0.1f), 0x8A929C, 0.35f * a);
                if (p == null) continue;
                p.sprite = Sprite.SMOKE;
                p.alphaBlend = true;
                p.rot = ps.rand(0f, 6.28f);
                p.rotVel = ps.rand(-0.7f, 0.7f);
                p.drag = 3f;
                p.fadePow = 1.2f;
            }
        } else if (s.equals("Rune Seal")) {
            Particle r = flat(ps, x, gy, z, Sprite.RUNE, life * 1.8f, size * 6f * scale, size * 9f * scale, c0, c1, a, 0f);
            if (r != null) r.rotVel = 2.5f;
            flat(ps, x, gy + 0.004, z, Sprite.GLOW, life, size * 8f * scale, size * 10f * scale, c1, c0, a * 0.4f, 0f);
        } else if (s.equals("Shock Ring")) {
            flat(ps, x, gy, z, Sprite.RING, life * 0.7f, size * 2f, size * 18f * scale, 0xFFFFFF, c0, a, 0f);
            ring(ps, x, gy + 0.1, z, n, 5f * spd * scale, 0.4f, life * 0.6f, size * 0.6f, 0f, c0, c1, a, Sprite.GLOW, fade).stretch = 0f;
        } else if (s.equals("Star Burst")) {
            for (int i = 0; i < n; i++) {
                float ang = ps.rand(0f, 6.28f), sp = ps.rand(1.5f, 3f) * spd * scale;
                Particle p = ps.glow(x, gy + 0.2, z, (float) Math.cos(ang) * sp, ps.rand(1.5f, 3.5f), (float) Math.sin(ang) * sp,
                        life * 1.3f, size * 1.2f, size * 0.3f, c0, c1, a);
                if (p == null) continue;
                p.sprite = Sprite.STAR;
                p.rotVel = ps.rand(-6f, 6f);
                p.gravity = 4f;
                p.drag = 1.2f;
                p.fadePow = fade;
            }
        } else if (s.equals("Crystal Spikes")) {
            for (int i = 0; i < Math.max(5, n / 2); i++) {
                float ang = i * 6.2831855f / Math.max(5, n / 2);
                double r = 0.6 * scale;
                Particle p = ps.glow(x + Math.cos(ang) * r, gy + 0.1, z + Math.sin(ang) * r, 0f, 2.2f * spd, 0f,
                        life * 1.2f, size * 2.2f * scale, size * 1.6f, c1, c0, a);
                if (p == null) continue;
                p.sprite = Sprite.CRYSTAL;
                p.drag = 6f;
                p.fadePow = 1.5f;
            }
        } else if (s.equals("Flame Burst")) {
            for (int i = 0; i < n; i++) {
                float ang = ps.rand(0f, 6.28f), r = ps.rand(0.1f, 0.7f) * scale;
                Particle p = ps.glow(x + Math.cos(ang) * r, gy, z + Math.sin(ang) * r, 0f, ps.rand(1.5f, 3f) * spd, 0f,
                        life * ps.rand(0.8f, 1.2f), size * 2.4f, size * 0.4f,
                        ParticleSystem.mix(0xFFB347, c0, 0.3f), ParticleSystem.mix(0xFF3D1F, c1, 0.3f), a);
                if (p == null) continue;
                p.sprite = Sprite.FLAME;
                p.sway = 0.3f;
                p.swayFreq = 5f;
                p.fadePow = fade;
            }
        } else if (s.equals("Light Pillar")) {
            Particle b = ps.glow(x, gy + 1.4 * scale, z, 0f, 0f, 0f, life * 1.4f, 1.6f * scale, 1.8f * scale, 0xFFFFFF, c0, a);
            if (b != null) {
                b.sprite = Sprite.BEAM;
                b.fadePow = 1.6f;
            }
            ring(ps, x, gy + 0.2, z, n, 0.6f, 2.5f, life * 1.2f, size * 0.5f, 0f, c1, c0, a, Sprite.GLOW, fade);
        } else if (s.equals("Petal Bloom")) {
            for (int i = 0; i < n; i++) {
                float ang = i * 6.2831855f / n;
                Particle p = ps.glow(x, gy + 0.1, z, (float) Math.cos(ang) * 1.6f * spd * scale, ps.rand(1f, 2f),
                        (float) Math.sin(ang) * 1.6f * spd * scale, life * 2f, size * 1.2f, size, c0, c1, a);
                if (p == null) continue;
                p.sprite = Sprite.PETAL;
                p.rot = ang;
                p.rotVel = ps.rand(-3f, 3f);
                p.gravity = 1.2f;
                p.drag = 1.5f;
                p.sway = 0.4f;
                p.swayFreq = 3f;
                p.fadePow = fade;
            }
        } else if (s.equals("Snow Puff")) {
            for (int i = 0; i < n; i++) {
                float ang = ps.rand(0f, 6.28f), sp = ps.rand(0.6f, 1.6f) * scale;
                Particle p = ps.glow(x, gy + 0.2, z, (float) Math.cos(ang) * sp, ps.rand(0.8f, 1.8f), (float) Math.sin(ang) * sp,
                        life * 2f, size * 0.6f, size * 0.4f, 0xFFFFFF, c1, a);
                if (p == null) continue;
                p.gravity = 1.4f;
                p.drag = 1.8f;
                p.sway = 0.3f;
                p.swayFreq = 2.5f;
            }
        } else if (s.equals("Hex Burst")) {
            for (int k = 0; k < 2; k++) {
                Particle h = flat(ps, x, gy + k * 0.004, z, Sprite.HEX, life * (1f + k * 0.4f), size, size * (12f + 6f * k) * scale,
                        k == 0 ? 0xFFFFFF : c0, c1, a, k * 0.5f);
                if (h != null) h.rotVel = k == 0 ? 1.5f : -1f;
            }
        } else if (s.equals("Spark Fountain")) {
            for (int i = 0; i < n; i++) {
                float ang = ps.rand(0f, 6.28f), sp = ps.rand(0.5f, 1.5f) * scale;
                Particle p = ps.glow(x, gy + 0.1, z, (float) Math.cos(ang) * sp, ps.rand(4f, 6.5f) * spd, (float) Math.sin(ang) * sp,
                        life * 1.4f, size * 0.5f, size * 0.1f, 0xFFFFFF, c0, a);
                if (p == null) continue;
                p.stretch = 0.05f;
                p.gravity = 12f;
                p.fadePow = fade;
            }
        } else if (s.equals("Electric Ring")) {
            int k = 10;
            double r = 1.2 * scale;
            for (int i = 0; i < k; i++) {
                double a0 = i * Math.PI * 2 / k, a1 = (i + 1) * Math.PI * 2 / k;
                Arcs.INSTANCE.spawn(x + Math.cos(a0) * r, gy + 0.05, z + Math.sin(a0) * r, x + Math.cos(a1) * r, gy + 0.05,
                        z + Math.sin(a1) * r, life * 0.9f, 0.1f, ParticleSystem.mix(c0, c1, i / (float) k), a);
            }
            flash(ps, x, gy + 0.1, z, size, c1, c0, a * 0.6f);
        } else if (s.equals("Comet Drop")) {
            for (int i = 0; i < Math.max(3, n / 3); i++) {
                float ang = ps.rand(0f, 6.28f), r = ps.rand(0.8f, 1.6f) * scale;
                Particle p = ps.glow(x + Math.cos(ang) * r, gy + 2.5, z + Math.sin(ang) * r, (float) -Math.cos(ang) * r * 2f, -5f,
                        (float) -Math.sin(ang) * r * 2f, 0.45f, size * 0.9f, size * 0.3f, 0xFFFFFF, c0, a);
                if (p != null) p.stretch = 0.06f;
            }
            flat(ps, x, gy, z, Sprite.RING, life, size, size * 9f * scale, c0, c1, a, 0f);
        } else if (s.equals("Halo Drop")) {
            Particle h = ps.glow(x, gy + 2.0, z, 0f, -3.6f * spd, 0f, 0.55f, size * 3.5f * scale, size * 6f * scale, 0xFFFFFF, c0, a);
            if (h != null) {
                h.sprite = Sprite.HALO;
                h.flat = true;
                h.rotVel = 3f;
                h.steady = true;
            }
            flat(ps, x, gy, z, Sprite.RING, life * 1.2f, size * 4f, size * 14f * scale, c0, c1, a, 0f);
        } else if (s.equals("Bubble Pop")) {
            for (int i = 0; i < n; i++) {
                float ang = ps.rand(0f, 6.28f), r = ps.rand(0.2f, 0.9f) * scale;
                Particle p = ps.glow(x + Math.cos(ang) * r, gy + 0.1, z + Math.sin(ang) * r, 0f, ps.rand(0.8f, 2f), 0f,
                        life * ps.rand(0.8f, 1.4f), size * 0.6f, size * 1.6f, c0, c1, a);
                if (p == null) continue;
                p.sprite = Sprite.RING;
                p.sway = 0.3f;
                p.swayFreq = 4f;
                p.fadePow = 2f;
            }
        } else { // Cash Burst
            for (int i = 0; i < n; i++) {
                boolean bill = (i % 3) != 0;
                float ang = ps.rand(0f, 6.28f), sp = ps.rand(1f, 2.4f) * scale;
                int c = bill ? 0x7CFF8A : 0xFFD54A;
                float s0 = size * (bill ? 2f : 1.1f);
                Particle p = ps.glow(x, gy + 0.3, z, (float) Math.cos(ang) * sp, ps.rand(2f, 4f), (float) Math.sin(ang) * sp,
                        life * 2.4f, s0, s0, c, c, a);
                if (p == null) continue;
                p.sprite = bill ? Sprite.BILL : Sprite.COIN;
                p.alphaBlend = true;
                p.rotVel = ps.rand(-5f, 5f);
                p.gravity = bill ? 2.2f : 7f;
                p.drag = bill ? 1.6f : 0.5f;
                p.sway = bill ? 0.8f : 0f;
                p.swayFreq = 3f;
                p.fadePow = 2.5f;
            }
        }
    }

    // ------------------------------------------------------------ helpers

    private static Particle flat(ParticleSystem ps, double x, double y, double z, int sprite, float life, float s0, float s1,
                                 int c0, int c1, float a, float rot) {
        Particle p = ps.glow(x, y, z, 0f, 0f, 0f, life, s0, s1, c0, c1, a);
        if (p == null) return null;
        p.sprite = sprite;
        p.flat = true;
        p.rot = rot;
        p.fadePow = 1.4f;
        return p;
    }

    /** n particles thrown outward in a horizontal ring; returns the last one (or a scratch particle). */
    private static Particle ring(ParticleSystem ps, double x, double y, double z, int n, float speed, float vy, float life,
                                 float s0, float s1, int c0, int c1, float a, int sprite, float fade) {
        Particle last = SCRATCH;
        for (int i = 0; i < n; i++) {
            float ang = i * 6.2831855f / n;
            Particle p = ps.glow(x, y, z, (float) Math.cos(ang) * speed, vy, (float) Math.sin(ang) * speed, life, s0, s1, c0, c1, a);
            if (p == null) continue;
            p.sprite = sprite;
            p.drag = 3.5f;
            p.fadePow = fade;
            last = p;
        }
        return last;
    }

    private static final Particle SCRATCH = new Particle();

    private static void flash(ParticleSystem ps, double x, double y, double z, float size, int c0, int c1, float a) {
        Particle f = ps.glow(x, y, z, 0f, 0f, 0f, 0.25f, size * 4f, size * 9f, c0, c1, a * 0.8f);
        if (f != null) f.fadePow = 1.5f;
    }
}
