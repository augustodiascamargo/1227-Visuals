package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.Arcs;
import net.m1227.vfx.AuraShapes;
import net.m1227.vfx.FxSettings;
import net.m1227.vfx.Particle;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 40 weapon effects: 10 effect types (each with its own particles and
 * motion) in 4 variants (palette and strength). Effects come off the blade
 * while you hold a sword, sweep along your swing, burst on confirmed hits
 * and grow during combos. Purely visual: reach, timing and hits are vanilla.
 *
 * The blade position is estimated from the camera (first person) or the
 * body yaw (third person); the 3D item model itself is not changed.
 */
public final class WeaponEffectsModule extends Module {
    private static final String[] TYPES = {"Aura", "Trail", "Sparks", "Flame", "Frost", "Lightning", "Void", "Starlight", "Petals", "Runes"};
    private static final String[][] VARIANTS = {
            {"Celestial Aura", "Crimson Aura", "Emerald Aura", "Shadow Aura"},
            {"White Arc", "Azure Arc", "Golden Arc", "Rose Arc"},
            {"Forge Sparks", "Neon Sparks", "Toxic Sparks", "Royal Sparks"},
            {"Ember Flame", "Blue Fire", "Soul Fire", "Hellfire"},
            {"Glacier", "Snowfall", "Crystal Frost", "Polar"},
            {"Storm", "Plasma", "Overcharge", "Volt"},
            {"Abyss", "Nebula", "Eclipse", "Phantom"},
            {"Starfall", "Galaxy", "Comet", "Supernova"},
            {"Sakura", "Lotus", "Wisteria", "Autumn"},
            {"Arcane Runes", "Ancient Runes", "Holy Runes", "Cursed Runes"}
    };
    private static final int[][] PALETTE = {
            {0xBFEFFF, 0xFFFFFF, 0xFF4D5E, 0xFFA08B, 0x3DFFA0, 0xB8FFD9, 0x7B5CFF, 0xC9B8FF},
            {0xFFFFFF, 0xBFEFFF, 0x2D8CFF, 0x9AD6FF, 0xFFC94D, 0xFFF1B8, 0xFF5FA2, 0xFFC2DC},
            {0xFF8A3D, 0xFFD36B, 0x2DF6FF, 0xB8FFFF, 0x9BFF3D, 0xE5FF9A, 0xA35CFF, 0xE0C2FF},
            {0xFF8A3D, 0xFF3D1F, 0x4DA3FF, 0x2D4DFF, 0x3DFFD0, 0x2D9C8A, 0xFF3D3D, 0x8A0F0F},
            {0xBFEFFF, 0xFFFFFF, 0xFFFFFF, 0xDDE8FF, 0x9AF0FF, 0x5ED1FF, 0xE6F7FF, 0x9AC8FF},
            {0xBFD9FF, 0xFFFFFF, 0xC77DFF, 0xFFB8F3, 0xFFF06B, 0xFFFFFF, 0x2DD4FF, 0xE0FFFF},
            {0x7B5CFF, 0x2D1F66, 0xFF4FD8, 0x5C2DFF, 0xFFD36B, 0x6B2D00, 0x9AA0B8, 0x3A3F55},
            {0xFFFFFF, 0xFFF1B8, 0xC77DFF, 0x4DA3FF, 0xBFEFFF, 0x2DD4FF, 0xFFD36B, 0xFF6B3D},
            {0xFFB8D9, 0xFFFFFF, 0xFFD6E8, 0xF59AC8, 0xC9A0FF, 0xEBDBFF, 0xFF9A3D, 0xFFD36B},
            {0x8B5CFF, 0x2DD4FF, 0xFFD36B, 0xFF8A3D, 0xFFFFFF, 0xFFF1B8, 0xFF3D5E, 0x7B0F2D}
    };
    private static final String[] NAMES = new String[40];

    static {
        for (int t = 0; t < 10; t++) {
            for (int v = 0; v < 4; v++) NAMES[t * 4 + v] = VARIANTS[t][v];
        }
    }

    private final Setting.Mode style;
    private final FxSettings fx;
    private final Setting.Bool customColors, anyItem, others, onHit, comboBoost;
    private final Setting.Num firstPerson, trailLen, bladeStart, bladeEnd, bladeGlowAlpha;
    private final Setting.Bool swingTrail, bladeGlow;
    public static WeaponEffectsModule INSTANCE;
    private Setting.Mode lightning;
    private float acc;
    private float lastSwing;
    private final double[] base = new double[3], tip = new double[3];

    public WeaponEffectsModule() {
        super("Weapon Effects", "40 sword effects: blade particles, swing trails, hit and combo effects.", Category.WEAPONS, false);
        style = add(new Setting.Mode("Style", "Celestial Aura", NAMES));
        customColors = add(new Setting.Bool("Use custom colors", false));
        fx = new FxSettings(this, 0x8B5CFF, 0x2DD4FF, 8, 1.0, 0.1, 0.6);
        INSTANCE = this;
        firstPerson = add(new Setting.Num("First person strength", 0.7, 0.0, 1.0, 0.05, ""));
        bladeGlow = add(new Setting.Bool("Blade glow (first person)", true));
        bladeGlowAlpha = add(new Setting.Num("Blade glow intensity", 0.55, 0.05, 1.0, 0.05, ""));
        swingTrail = add(new Setting.Bool("Swing trail (first person)", true));
        lightning = add(new Setting.Mode("Lightning arcs on blade", "Auto", "Auto", "On", "Off"));
        trailLen = add(new Setting.Num("Swing trail length", 0.18, 0.05, 0.5, 0.01, "s"));
        bladeStart = add(new Setting.Num("Blade start", 0.28, 0.0, 0.6, 0.01, ""));
        bladeEnd = add(new Setting.Num("Blade end", 0.97, 0.5, 1.0, 0.01, ""));
        onHit = add(new Setting.Bool("Effect on confirmed hit", true));
        comboBoost = add(new Setting.Bool("Grow with combo", true));
        anyItem = add(new Setting.Bool("Any held item (not just swords)", false));
        others = add(new Setting.Bool("Show on other players", false));
    }

    private int type() {
        int i = 0;
        String s = style.get();
        for (int k = 0; k < NAMES.length; k++) {
            if (NAMES[k].equals(s)) i = k;
        }
        return i;
    }

    private int c0() {
        int i = type();
        return customColors.on() ? fx.color1.rgb() : PALETTE[i / 4][(i % 4) * 2];
    }

    private int c1() {
        int i = type();
        return customColors.on() ? fx.color2.rgb() : PALETTE[i / 4][(i % 4) * 2 + 1];
    }

    private boolean holding(EntityPlayer p) {
        ItemStack s = p.getHeldItem();
        if (s == null) return false;
        return anyItem.on() || s.getItem() instanceof ItemSword || s.getItem() instanceof ItemAxe;
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        ParticleSystem ps = ParticleSystem.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || ps.dt <= 0f) return;
        boolean fp = mc.gameSettings.thirdPersonView == 0;
        if (!fp && holding(mc.thePlayer)) { // first person is drawn on the item itself by HandFx
            float strength = fp ? firstPerson.f() : 1f;
            if (strength > 0f) {
                blade(mc.thePlayer, e.partialTicks, fp);
                idle(ps, strength);
                float sw = mc.thePlayer.getSwingProgress(e.partialTicks);
                if (sw > 0f && (lastSwing == 0f || sw < lastSwing)) swing(ps, mc.thePlayer, e.partialTicks, strength);
                lastSwing = sw;
            }
        }
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer o = list.get(i);
                if (o == mc.thePlayer || o.isInvisible() || !holding(o)) continue;
                blade(o, e.partialTicks, false);
                if (ps.inRange(tip[0], tip[1], tip[2])) idle(ps, 0.6f);
            }
        }
    }

    /** Estimates the blade (base and tip) of the held item in world space. */
    private void blade(EntityPlayer p, float pt, boolean firstPerson) {
        double x = ParticleSystem.lerp(p.lastTickPosX, p.posX, pt);
        double y = ParticleSystem.lerp(p.lastTickPosY, p.posY, pt);
        double z = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, pt);
        float swing = p.getSwingProgress(pt);
        double sw = Math.sin(Math.sqrt(swing) * Math.PI);
        if (firstPerson) {
            double yaw = Math.toRadians(p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt);
            double pitch = Math.toRadians(p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt);
            double fx0 = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
            // camera up = right x forward
            double ux = -Math.sin(yaw) * Math.sin(pitch), uy = Math.cos(pitch), uz = Math.cos(yaw) * Math.sin(pitch);
            double ex = x, ey = y + p.getEyeHeight(), ez = z;
            base[0] = ex + fx0 * 0.55 + rx * 0.3 - ux * 0.3;
            base[1] = ey + fy * 0.55 - uy * 0.3;
            base[2] = ez + fz * 0.55 + rz * 0.3 - uz * 0.3;
            tip[0] = base[0] + fx0 * 0.35 + ux * 0.4 - rx * (0.08 + sw * 0.55);
            tip[1] = base[1] + fy * 0.35 + uy * 0.4 - sw * 0.2;
            tip[2] = base[2] + fz * 0.35 + uz * 0.4 - rz * (0.08 + sw * 0.55);
        } else {
            // follow the right arm like ModelBiped does: shoulder pivot, arm raised forward
            // while holding an item, plus the swing; the blade sticks out forward from the hand
            double by = Math.toRadians(p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * pt);
            double fx0 = -Math.sin(by), fz = Math.cos(by), rx = -Math.cos(by), rz = -Math.sin(by);
            double sh = p.isSneaking() ? 1.17 : 1.375;
            double theta = 0.31 + sw * 1.25 + (p.isBlocking() ? 0.6 : 0.0);
            double ax = fx0 * Math.sin(theta) * 0.62, ay = -Math.cos(theta) * 0.62, az = fz * Math.sin(theta) * 0.62;
            double hx = x + rx * 0.33 + ax, hy = y + sh + ay, hz = z + rz * 0.33 + az;
            double bdx = fx0 * Math.cos(theta), bdy = Math.sin(theta), bdz = fz * Math.cos(theta);
            if (p.isBlocking()) { // sword turned across the body
                bdx = -rx * 0.8 + fx0 * 0.3;
                bdy = 0.45;
                bdz = -rz * 0.8 + fz * 0.3;
            }
            base[0] = hx + bdx * 0.08;
            base[1] = hy + bdy * 0.08;
            base[2] = hz + bdz * 0.08;
            tip[0] = hx + bdx * 0.78;
            tip[1] = hy + bdy * 0.78;
            tip[2] = hz + bdz * 0.78;
        }
    }

    /** Continuous emission along the blade. */
    private void idle(ParticleSystem ps, float strength) {
        int t = type() / 4;
        float rate = fx.amount.f() * 6f * strength * boost() * PerformanceModule.qualityScale();
        if (t == 1) rate *= 0.3f; // trails are mostly about the swing
        acc += ps.dt * rate;
        int m = Math.min((int) acc, 10);
        acc -= (int) acc;
        int c0 = c0(), c1 = c1();
        float size = fx.size.f() * (0.7f + 0.3f * strength), life = fx.duration.f(), a = fx.alpha() * strength;
        for (int j = 0; j < m; j++) {
            float u = ps.rng.nextFloat();
            double x = base[0] + (tip[0] - base[0]) * u, y = base[1] + (tip[1] - base[1]) * u, z = base[2] + (tip[2] - base[2]) * u;
            emit(ps, t, x, y, z, 0f, 0f, 0f, size, life, c0, c1, a);
        }
        if (t == 5 && ps.rng.nextFloat() < ps.dt * 6f * strength) {
            Arcs.INSTANCE.spawn(base[0], base[1], base[2], tip[0], tip[1], tip[2], 0.1f, 0.06f, c0, a);
        }
        if (t == 9) {
            double tt = ps.time * fx.speed.f();
            for (int k = 0; k < 3; k++) {
                double ang = tt * 2.5 + k * 2.094;
                AuraShapes.sprite(ps, Sprite.RUNE, tip[0] + Math.cos(ang) * 0.18, tip[1] + Math.sin(ang * 1.3) * 0.08,
                        tip[2] + Math.sin(ang) * 0.18, size * 1.1f, (float) tt, false, k == 0 ? c1 : c0, a);
            }
        }
    }

    private void emit(ParticleSystem ps, int t, double x, double y, double z, float vx, float vy, float vz,
                      float size, float life, int c0, int c1, float a) {
        float spd = fx.speed.f();
        Particle p;
        switch (t) {
            case 2: // sparks
                p = ps.glow(x, y, z, vx + ps.rand(-1f, 1f), vy + ps.rand(0f, 1.5f), vz + ps.rand(-1f, 1f), life * 0.8f, size * 0.5f, 0f, c0, c1, a);
                if (p != null) {
                    p.stretch = 0.05f;
                    p.gravity = 8f;
                }
                return;
            case 3: // flame
                p = ps.glow(x, y, z, vx, vy + ps.rand(0.4f, 0.9f) * spd, vz, life, size * 1.8f, size * 0.3f, c0, c1, a);
                if (p != null) {
                    p.sprite = Sprite.FLAME;
                    p.sway = 0.2f;
                    p.swayFreq = 6f;
                }
                return;
            case 4: // frost
                p = ps.glow(x, y, z, vx + ps.rand(-0.2f, 0.2f), vy - ps.rand(0.1f, 0.4f), vz + ps.rand(-0.2f, 0.2f), life * 1.4f,
                        size * ps.rand(0.6f, 1.1f), size * 0.4f, c0, c1, a);
                if (p != null) {
                    p.sprite = ps.rng.nextFloat() < 0.4f ? Sprite.SHARD : Sprite.STAR;
                    p.rotVel = ps.rand(-3f, 3f);
                }
                return;
            case 6: // void
                p = ps.glow(x, y, z, vx, vy + ps.rand(0.05f, 0.3f), vz, life * 1.8f, size * 1.6f, size * 2.6f, c0, c1, a * 0.45f);
                if (p != null) {
                    p.sprite = Sprite.SMOKE;
                    p.rot = ps.rand(0f, 6.28f);
                    p.rotVel = ps.rand(-1.5f, 1.5f);
                }
                return;
            case 7: // starlight
                p = ps.glow(x, y, z, vx, vy + ps.rand(-0.1f, 0.2f), vz, life * 1.3f, size * ps.rand(0.7f, 1.3f), 0f, c0, c1, a);
                if (p != null) {
                    p.sprite = Sprite.STAR;
                    p.rotVel = ps.rand(-4f, 4f);
                    p.pulse = 0.6f;
                    p.pulseFreq = ps.rand(10f, 16f);
                }
                return;
            case 8: // petals
                p = ps.glow(x, y, z, vx + ps.rand(-0.3f, 0.3f), vy - ps.rand(0.2f, 0.5f), vz + ps.rand(-0.3f, 0.3f), life * 2f,
                        size * 1.1f, size * 0.9f, c0, c1, a);
                if (p != null) {
                    p.sprite = Sprite.PETAL;
                    p.rotVel = ps.rand(-3f, 3f);
                    p.sway = 0.4f;
                    p.swayFreq = 3f;
                }
                return;
            default: // aura, trail, lightning, runes: soft glow motes
                p = ps.glow(x, y, z, vx, vy + 0.15f * spd, vz, life, size, size * 0.2f, c0, c1, a);
                if (p != null) p.fadePow = fx.fade.f();
        }
    }

    /** A sweep of particles along the swing, in front of the player. */
    private void swing(ParticleSystem ps, EntityPlayer p, float pt, float strength) {
        int t = type() / 4;
        double yaw = Math.toRadians(p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt);
        double ex = ParticleSystem.lerp(p.lastTickPosX, p.posX, pt), ey = ParticleSystem.lerp(p.lastTickPosY, p.posY, pt) + p.getEyeHeight() - 0.35;
        double ez = ParticleSystem.lerp(p.lastTickPosZ, p.posZ, pt);
        int n = Math.round((t == 1 ? 26 : 12) * strength * boost() * PerformanceModule.qualityScale());
        int c0 = c0(), c1 = c1();
        float a = fx.alpha() * strength, size = fx.size.f();
        for (int i = 0; i < n; i++) {
            double u = i / (double) Math.max(1, n - 1);
            double ang = yaw + Math.toRadians(-70 + 140 * u); // right to left
            double r = 1.05;
            double x = ex - Math.sin(ang) * r, z = ez + Math.cos(ang) * r, y = ey + 0.25 - 0.4 * u;
            float tx = (float) -Math.cos(ang) * 2f, tz = (float) -Math.sin(ang) * 2f;
            if (t == 1) {
                Particle q = ps.glow(x, y, z, tx, 0f, tz, 0.22f, size * 1.2f, size * 0.3f, u < 0.5 ? c1 : c0, 0xFFFFFF, a);
                if (q != null) q.stretch = 0.04f;
            } else {
                emit(ps, t, x, y, z, tx * 0.3f, 0f, tz * 0.3f, size, fx.duration.f() * 0.6f, c0, c1, a);
            }
        }
    }

    @SubscribeEvent
    public void onHitEvent(CombatTracker.HitEvent e) {
        if (!onHit.on()) return;
        ParticleSystem ps = ParticleSystem.INSTANCE;
        EntityLivingBase tgt = e.target;
        double x = tgt.posX - e.dx * tgt.width * 0.5, y = tgt.posY + tgt.height * 0.6, z = tgt.posZ - e.dz * tgt.width * 0.5;
        if (!ps.inRange(x, y, z)) return;
        int t = type() / 4, c0 = c0(), c1 = c1();
        float b = boost();
        int n = Math.round(10 * b * PerformanceModule.qualityScale());
        for (int i = 0; i < n; i++) {
            float vx = e.dx * 2f + ps.rand(-1.5f, 1.5f), vy = ps.rand(0f, 2f), vz = e.dz * 2f + ps.rand(-1.5f, 1.5f);
            emit(ps, t, x, y, z, vx, vy, vz, fx.size.f() * 1.2f, fx.duration.f(), c0, c1, fx.alpha());
        }
        if (t == 5) {
            Arcs.INSTANCE.spawn(x, y + 0.6, z, x + ps.rand(-0.6f, 0.6f), y - 0.6, z + ps.rand(-0.6f, 0.6f), 0.2f, 0.15f, c0, fx.alpha());
        }
        if (CombatTracker.combo() >= 3) {
            Particle ring = ps.glow(x, y, z, 0f, 0f, 0f, 0.35f, 0.2f, 0.9f * b, c1, c0, fx.alpha());
            if (ring != null) ring.sprite = Sprite.HALO;
        }
    }

    // ---- read by the first-person hand effects (HandFx)
    public boolean firstPersonActive(EntityPlayer p) {
        return isEnabled() && firstPerson.f() > 0f && holding(p);
    }

    public int typeIndex() {
        return type() / 4;
    }

    public int col0() {
        return c0();
    }

    public int col1() {
        return c1();
    }

    public float boost() {
        if (!comboBoost.on()) return 1f;
        return 1f + 0.2f * Math.min(6, CombatTracker.combo());
    }

    public float amount() {
        return fx.amount.f() * firstPerson.f();
    }

    public float alpha() {
        return fx.alpha() * firstPerson.f();
    }

    public float life() {
        return fx.duration.f();
    }

    /** Particle size in hand (camera) space units. */
    public float handSize() {
        return fx.size.f() * 0.22f;
    }

    public boolean swingTrail() {
        return swingTrail.on();
    }

    /** Real electric arcs crawling over the blade in first person (Auto = Lightning effects only). */
    public boolean lightningArcs() {
        if (lightning == null || lightning.is("Off")) return false;
        return lightning.is("On") || TYPES[typeIndex()].equals("Lightning");
    }

    public boolean bladeGlow() {
        return bladeGlow.on();
    }

    public float bladeGlowAlpha() {
        return bladeGlowAlpha.f() * firstPerson.f();
    }

    public float trailLength() {
        return trailLen.f();
    }

    public float bladeStart() {
        return bladeStart.f();
    }

    public float bladeEnd() {
        return Math.max(bladeStart.f() + 0.1f, bladeEnd.f());
    }
}
