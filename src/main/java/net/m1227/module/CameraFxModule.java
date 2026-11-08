package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.CombatTracker;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Small render-only camera motions: a kick when your hit lands, a punch on a
 * kill, a shake when you take damage and a roll into strafes. Only the
 * rendered view moves; your real look direction and aim never change.
 */
public final class CameraFxModule extends Module {
    private final Setting.Bool hitKick, killPunch, damageShake, strafeTilt;
    private final Setting.Num strength, tiltAmount;
    private float shake, kick, roll, prevHealth = -1f;
    private long last;

    public CameraFxModule() {
        super("Camera FX", "Render-only camera kick, shake and strafe tilt.", Category.CAMERA, false);
        hitKick = add(new Setting.Bool("Kick on hit", true));
        killPunch = add(new Setting.Bool("Punch on kill", true));
        damageShake = add(new Setting.Bool("Shake when damaged", true));
        strafeTilt = add(new Setting.Bool("Strafe tilt", false));
        strength = add(new Setting.Num("Strength", 1.0, 0.1, 3.0, 0.1, "x"));
        tiltAmount = add(new Setting.Num("Tilt angle", 2.0, 0.5, 6.0, 0.5, "°")).showIf(() -> strafeTilt.on());
    }

    @SubscribeEvent
    public void onHit(CombatTracker.HitEvent e) {
        if (hitKick.on()) kick = Math.max(kick, e.critical ? 1.6f : 1f);
    }

    @SubscribeEvent
    public void onKill(CombatTracker.KillEvent e) {
        if (killPunch.on()) {
            kick = Math.max(kick, 2.6f);
            shake = Math.max(shake, 0.6f);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) {
            prevHealth = -1f;
            return;
        }
        float h = p.getHealth();
        if (damageShake.on() && prevHealth > 0f && h < prevHealth - 0.01f) {
            shake = Math.max(shake, Math.min(2f, 0.6f + (prevHealth - h) * 0.15f));
        }
        prevHealth = h;
    }

    @SubscribeEvent
    public void onCamera(EntityViewRenderEvent.CameraSetup e) {
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        shake = Math.max(0f, shake - dt * 3.2f);
        kick = Math.max(0f, kick - dt * 6f);
        float s = strength.f();
        double t = now / 1e9;
        if (shake > 0f) {
            float a = shake * shake * s;
            e.pitch += (float) (Math.sin(t * 61.0) * 0.9 + Math.sin(t * 37.0) * 0.5) * a;
            e.yaw += (float) (Math.sin(t * 53.0 + 1.3) * 0.9) * a;
            e.roll += (float) (Math.sin(t * 47.0 + 2.1) * 1.2) * a;
        }
        if (kick > 0f) {
            // quick ease-out pitch dip
            float k = kick * kick * 0.35f * s;
            e.pitch += k;
        }
        float target = 0f;
        if (strafeTilt.on() && mc.thePlayer != null) target = -mc.thePlayer.moveStrafing * tiltAmount.f();
        roll += (target - roll) * (1f - (float) Math.exp(-8f * dt));
        e.roll += roll;
    }
}
