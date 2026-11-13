package net.m1227.hud;

import net.m1227.core.CombatTracker;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;

/**
 * Aim Helper (information only): for the player under your crosshair, or your current target,
 * shows the distance, how fast they are moving and whether they are jumping or falling. It never
 * moves your camera, aims or attacks.
 */
public final class AimHelperHud extends HudModule {
    private final Setting.Bool speed, air, bar;
    private final Setting.Num reachRef;

    public AimHelperHud() {
        super("Target Info", "Distance, speed and jump state of the player you are looking at.", 0.44, 0.55);
        speed = add(new Setting.Bool("Movement speed", true));
        air = add(new Setting.Bool("Jump / fall state", true));
        bar = add(new Setting.Bool("Distance bar", true));
        reachRef = add(new Setting.Num("Bar range", 6, 3, 12, 0.5, "m")).showIf(() -> bar.on());
    }

    private EntityLivingBase subject(Minecraft mc) {
        MovingObjectPosition m = mc.objectMouseOver;
        if (m != null && m.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && m.entityHit instanceof EntityLivingBase) {
            return (EntityLivingBase) m.entityHit;
        }
        Entity t = CombatTracker.target(4000L);
        return t instanceof EntityLivingBase && !t.isDead ? (EntityLivingBase) t : null;
    }

    private int rows() {
        return 1 + (speed.on() ? 1 : 0) + (air.on() ? 1 : 0);
    }

    @Override
    public float width() {
        return 100;
    }

    @Override
    public float height() {
        return 6 + 10 * rows() + (bar.on() ? 6 : 0);
    }

    @Override
    protected boolean hasContent() {
        return subject(Minecraft.getMinecraft()) != null;
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase e = subject(mc);
        double dist = 3.1, spd = 5.6;
        String state = "Jumping";
        if (e != null && mc.thePlayer != null) {
            dist = mc.thePlayer.getDistanceToEntity(e);
            spd = Math.sqrt(sq(e.posX - e.lastTickPosX) + sq(e.posZ - e.lastTickPosZ)) * 20.0;
            double vy = e.posY - e.lastTickPosY;
            state = e.onGround ? "On ground" : vy > 0.02 ? "Jumping" : vy < -0.02 ? "Falling" : "In the air";
        } else if (!editing) {
            return;
        }
        panel(x, y, width(), height());
        float ry = y + 4;
        String d = String.format(java.util.Locale.ROOT, "%.2f m", dist);
        Gfx.text("Distance", x + 5, ry, LABEL);
        Gfx.text(d, x + width() - 5 - Gfx.textWidth(d), ry, dist <= 3.0 ? 0xFF3DDC97 : dist <= 4.5 ? 0xFFE7D24A : 0xFFFF6B6B);
        if (speed.on()) {
            ry += 10;
            String s = String.format(java.util.Locale.ROOT, "%.1f b/s", spd);
            Gfx.text("Speed", x + 5, ry, LABEL);
            Gfx.text(s, x + width() - 5 - Gfx.textWidth(s), ry, value());
        }
        if (air.on()) {
            ry += 10;
            Gfx.text("State", x + 5, ry, LABEL);
            Gfx.text(state, x + width() - 5 - Gfx.textWidth(state), ry, 0xFFFFFFFF);
        }
        if (bar.on()) {
            float f = (float) Math.min(1.0, dist / reachRef.f());
            Gfx.roundedRect(x + 5, ry + 11, width() - 10, 3, 1.5f, 0x40FFFFFF);
            Gfx.roundedRect(x + 5, ry + 11, (width() - 10) * f, 3, 1.5f, value());
        }
    }

    private static double sq(double v) {
        return v * v;
    }
}
