package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Preview;
import net.m1227.vfx.Arcs;
import net.m1227.vfx.Sprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Plays an effect where the player you were fighting dies. */
public final class KillEffectModule extends Module {
    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Bool red;
    private final Setting.Num sahurSize;

    public KillEffectModule() {
        super("Kill Effect", "Effect when you finish off a target.", Category.COMBAT, true);
        style = add(new Setting.Mode("Style", "Nova", "Nova", "Ascend", "Implode", "Lightning", "Cash",
                "Ragdoll", "Decapitation", "Shatter", "Ice Shatter", "Executioner", "Tung Sahur"));
        fx = new FxSettings(this, 0x8B5CFF, 0x2DD4FF, 26, 1.0, 0.14, 1.0);
        red = add(new Setting.Bool("Red particles (Decapitation)", false)).showIf(() -> style.is("Decapitation"));
        sahurSize = add(new Setting.Num("Tung Sahur size", 2.1, 1.0, 3.0, 0.05, "x")).showIf(() -> style.is("Tung Sahur"));
        add(new Setting.Action("Preview", new Runnable() {
            @Override
            public void run() {
                double[] a = Preview.ahead(4.5, 3.0);
                if (a != null) {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                    // the stand-in faces you; you hit it "forwards"
                    float yaw = (float) Math.toDegrees(Math.atan2(a[3], -a[4]));
                    play(a[0], a[1], a[2], 0.6f, 1.8f, mc.thePlayer, yaw, (float) a[3], (float) a[4]);
                }
            }
        }));
    }

    @SubscribeEvent
    public void onKill(CombatTracker.KillEvent e) {
        EntityLivingBase t = e.target;
        net.minecraft.client.entity.EntityPlayerSP me = net.minecraft.client.Minecraft.getMinecraft().thePlayer;
        float dx = 0f, dz = 1f;
        if (me != null) {
            double ddx = t.posX - me.posX, ddz = t.posZ - me.posZ, l = Math.sqrt(ddx * ddx + ddz * ddz);
            if (l > 1e-3) {
                dx = (float) (ddx / l);
                dz = (float) (ddz / l);
            }
        }
        play(t.posX, t.posY, t.posZ, t.width, t.height, t, t.renderYawOffset, dx, dz);
    }

    /** feetY is the y of the ground under the target. */
    public void play(double x, double feetY, double z, float width, float height) {
        play(x, feetY, z, width, height, null, 0f, 0f, 1f);
    }

    /** victim (may be null) gives the body its skin; (dirX, dirZ) = from you towards the target. */
    public void play(double x, double feetY, double z, float width, float height, EntityLivingBase victim, float yaw, float dirX, float dirZ) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        double y = feetY + height * 0.5;
        if (!ps.inRange(x, y, z)) return;
        String s = style.get();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        net.m1227.vfx.BodyFx bf = net.m1227.vfx.BodyFx.INSTANCE;
        if (s.equals("Ragdoll")) {
            bf.ragdoll(victim, x, feetY, z, yaw, height, dirX, dirZ, fx.speed.f(), c0, c1);
            return;
        }
        if (s.equals("Decapitation")) {
            bf.decapitate(victim, x, feetY, z, yaw, height, dirX, dirZ, c0, c1, red.on());
            return;
        }
        if (s.equals("Shatter") || s.equals("Ice Shatter")) {
            bf.shatter(victim, x, feetY, z, yaw, height, s.equals("Ice Shatter"), c0, c1);
            return;
        }
        if (s.equals("Tung Sahur")) {
            net.m1227.vfx.BodyFx.sahurScale = sahurSize.f();
            bf.sahur(victim, x, feetY, z, yaw, height, dirX, dirZ, c0, c1);
            return;
        }
        if (s.equals("Executioner")) {
            bf.executioner(victim, x, feetY, z, yaw, height, dirX, dirZ, 0xFF000000 | c0, c1);
            return;
        }
        if (s.equals("Nova")) nova(ps, x, y, z, feetY);
        else if (s.equals("Ascend")) ascend(ps, x, z, feetY, width);
        else if (s.equals("Lightning")) lightning(ps, x, feetY, z, height);
        else if (s.equals("Cash")) cash(ps, x, y, z);
        else implode(ps, x, y, z);
    }

    private void nova(ParticleSystem ps, double x, double y, double z, double feetY) {
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f();
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        ps.glow(x, y, z, 0f, 0f, 0f, 0.3f, size * 8f, size * 18f, c1, c0, fx.alpha() * 0.8f);

        double fy = feetY + 0.15;
        int ring = ps.scale(fx.amount.f() * 1.4f);
        for (int i = 0; i < ring; i++) {
            float a = i * (6.2831855f / ring);
            Particle p = ps.glow(x, fy, z, (float) Math.cos(a) * spd * 4f, 0.25f, (float) Math.sin(a) * spd * 4f,
                    life * 0.8f, size * 1.2f, size * 0.2f, c1, c0, fx.alpha());
            if (p != null) {
                p.drag = 2.0f;
                p.fadePow = fade;
            }
        }
        int n = ps.scale(fx.amount.f());
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-1f, 1f);
            float rr = (float) Math.sqrt(1f - zz * zz), sp = spd * ps.rand(2f, 5f);
            Particle p = ps.glow(x, y, z, rr * (float) Math.cos(a) * sp, zz * sp + 1f, rr * (float) Math.sin(a) * sp,
                    life * ps.rand(0.6f, 1.1f), size * ps.rand(0.7f, 1.4f), 0f, c0, c1, fx.alpha());
            if (p != null) {
                p.drag = 1.8f;
                p.gravity = 0.8f;
                p.fadePow = fade;
            }
        }
    }

    private void ascend(ParticleSystem ps, double x, double z, double feetY, float width) {
        float size = fx.size.f(), life = fx.duration.f(), spd = fx.speed.f(), fade = fx.fade.f();
        int n = ps.scale(fx.amount.f() * 2f);
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f);
            float r = width * 0.5f + ps.rand(0f, 0.4f);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            Particle p = ps.glow(x + c * r, feetY + ps.rand(0f, 0.3f), z + s * r,
                    -s * 0.6f, spd * ps.rand(1.2f, 2.4f), c * 0.6f,
                    life * ps.rand(0.8f, 1.3f), size * ps.rand(0.8f, 1.4f), 0f,
                    fx.color1.rgb(), fx.color2.rgb(), fx.alpha());
            if (p != null) {
                p.drag = 0.5f;
                p.gravity = -0.3f;
                p.fadePow = fade;
            }
        }
    }

    private void implode(ParticleSystem ps, double x, double y, double z) {
        float size = fx.size.f(), life = fx.duration.f() * 0.55f, fade = fx.fade.f();
        int n = ps.scale(fx.amount.f() * 1.5f);
        float radius = 1.5f;
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.2831855f), zz = ps.rand(-1f, 1f);
            float rr = (float) Math.sqrt(1f - zz * zz);
            float dx = rr * (float) Math.cos(a), dy = zz, dz = rr * (float) Math.sin(a);
            float k = radius / life;
            Particle p = ps.glow(x + dx * radius, y + dy * radius, z + dz * radius, -dx * k, -dy * k, -dz * k,
                    life, size * 0.7f, size * 1.2f, fx.color2.rgb(), fx.color1.rgb(), fx.alpha());
            if (p != null) p.fadePow = fade;
        }
        ps.glow(x, y, z, 0f, 0f, 0f, life * 1.1f, size * 2f, size * 14f,
                fx.color1.rgb(), fx.color2.rgb(), fx.alpha() * 0.7f);
    }

    /** A bolt from the sky onto the target, a flash and crackling ground arcs. */
    private void lightning(ParticleSystem ps, double x, double feetY, double z, float height) {
        int c0 = fx.color1.rgb(), c1 = fx.color2.rgb();
        float life = fx.duration.f() * 0.6f, size = fx.size.f();
        for (int k = 0; k < 2; k++) {
            Arcs.INSTANCE.spawn(x + ps.rand(-1.5f, 1.5f), feetY + 14, z + ps.rand(-1.5f, 1.5f), x, feetY + 0.1, z,
                    life, 1.1f, k == 0 ? 0xFFFFFF : c0, fx.alpha());
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + ps.rand(-0.3f, 0.3f);
            Arcs.INSTANCE.spawn(x, feetY + 0.08, z, x + Math.cos(a) * 1.8, feetY + 0.1, z + Math.sin(a) * 1.8,
                    life * 0.8f, 0.2f, c1, fx.alpha());
        }
        ps.glow(x, feetY + height * 0.5, z, 0f, 0f, 0f, 0.35f, size * 10f, size * 22f, 0xFFFFFF, c0, fx.alpha() * 0.9f);
        int n = ps.scale(fx.amount.f());
        for (int i = 0; i < n; i++) {
            float a = ps.rand(0f, 6.28f), sp = ps.rand(2f, 5f) * fx.speed.f();
            Particle p = ps.glow(x, feetY + 0.2, z, (float) Math.cos(a) * sp, ps.rand(2f, 5f), (float) Math.sin(a) * sp,
                    life * 1.5f, size * 0.5f, 0f, 0xFFFFFF, c1, fx.alpha());
            if (p != null) {
                p.stretch = 0.05f;
                p.gravity = 9f;
                p.fadePow = fx.fade.f();
            }
        }
    }

    /** Banknotes and coins bursting out and fluttering down (always green and gold). */
    private void cash(ParticleSystem ps, double x, double y, double z) {
        int n = ps.scale(fx.amount.f());
        float size = fx.size.f(), life = fx.duration.f() * 2.2f, spd = fx.speed.f();
        for (int i = 0; i < n; i++) {
            boolean bill = (i % 3) != 0;
            float a = ps.rand(0f, 6.28f), sp = ps.rand(1.5f, 3.5f) * spd;
            float s = size * (bill ? ps.rand(1.8f, 2.4f) : ps.rand(1f, 1.3f));
            int c = bill ? 0x7CFF8A : 0xFFD54A;
            Particle p = ps.glow(x, y, z, (float) Math.cos(a) * sp, ps.rand(2.5f, 5f) * spd, (float) Math.sin(a) * sp,
                    life * ps.rand(0.8f, 1.2f), s, s, c, ParticleSystem.mix(c, 0xFFFFFF, 0.2f), fx.alpha());
            if (p == null) continue;
            p.sprite = bill ? Sprite.BILL : Sprite.COIN;
            p.alphaBlend = true;
            p.rot = ps.rand(0f, 6.28f);
            p.rotVel = ps.rand(-5f, 5f);
            p.gravity = bill ? 2.2f : 7f;
            p.drag = bill ? 1.6f : 0.5f;
            p.sway = bill ? 0.8f : 0f;
            p.swayFreq = ps.rand(2f, 4f);
            p.swayPhase = ps.rand(0f, 6.28f);
            p.fadePow = 2.5f;
        }
    }
}
