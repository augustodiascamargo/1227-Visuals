package net.m1227.core;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Turns "the local player swung at X" into two reliable visual events:
 * HitEvent (damage actually happened) and KillEvent (target died shortly
 * after our last hit). Purely observational: reads client state only, sends
 * nothing and changes no gameplay.
 */
public final class CombatTracker {
    private static final long TRACK_MS = 8000L;
    private static final long CONFIRM_MS = 700L;
    private static final long KILL_MS = 2500L;

    private static EntityLivingBase target;
    private static long attackTime;
    private static boolean hitConfirmed;
    private static int prevHurt;
    private static int combo;
    private static boolean crit;
    private static long lastHit;
    private static int selfHurtPrev;

    /** Confirmed hits in a row on the current target (reset after 2 s or when you get hit). */
    public static int combo() {
        return System.currentTimeMillis() - lastHit > 2000L ? 0 : combo;
    }

    public static class HitEvent extends Event {
        public final EntityLivingBase target;
        /** Unit vector from attacker towards target (horizontal bias). */
        public final float dx, dy, dz;
        /** The swing met vanilla's critical-hit conditions (falling, not on ground/ladder/water, not blind). */
        public final boolean critical;

        public HitEvent(EntityLivingBase target, float dx, float dy, float dz, boolean critical) {
            this.target = target;
            this.critical = critical;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    public static class KillEvent extends Event {
        public final EntityLivingBase target;

        public KillEvent(EntityLivingBase target) {
            this.target = target;
        }
    }

    /** The entity we last attacked, if it was within maxAgeMs and still alive. */
    public static EntityLivingBase target(long maxAgeMs) {
        EntityLivingBase t = target;
        if (t == null || t.isDead) return null;
        if (System.currentTimeMillis() - attackTime > maxAgeMs) return null;
        return t;
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.entityPlayer != mc.thePlayer) return; // ignores the integrated server's copy
        if (!(e.target instanceof EntityLivingBase)) return;
        EntityLivingBase t = (EntityLivingBase) e.target;
        if (t != target) prevHurt = t.hurtTime;
        target = t;
        net.minecraft.client.entity.EntityPlayerSP p = mc.thePlayer;
        crit = p.fallDistance > 0.0F && !p.onGround && !p.isOnLadder() && !p.isInWater()
                && !p.isPotionActive(net.minecraft.potion.Potion.blindness) && p.ridingEntity == null;
        attackTime = System.currentTimeMillis();
        hitConfirmed = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft m = Minecraft.getMinecraft();
        if (m.thePlayer != null) {
            int h = m.thePlayer.hurtTime;
            if (h > selfHurtPrev) combo = 0; // taking a hit breaks your combo
            selfHurtPrev = h;
        }
        EntityLivingBase t = target;
        if (t == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || t.worldObj != mc.theWorld) {
            target = null;
            return;
        }
        long age = System.currentTimeMillis() - attackTime;
        if (age > TRACK_MS) {
            target = null;
            return;
        }

        boolean rose = t.hurtTime > prevHurt;
        prevHurt = t.hurtTime;
        if (!hitConfirmed && rose && age < CONFIRM_MS) {
            hitConfirmed = true;
            float dx = (float) (t.posX - mc.thePlayer.posX);
            float dz = (float) (t.posZ - mc.thePlayer.posZ);
            float l = (float) Math.sqrt(dx * dx + dz * dz);
            if (l < 1.0E-3f) {
                dx = 0f;
                dz = 1f;
            } else {
                dx /= l;
                dz /= l;
            }
            long now = System.currentTimeMillis();
            combo = now - lastHit > 2000L ? 1 : combo + 1;
            lastHit = now;
            MinecraftForge.EVENT_BUS.post(new HitEvent(t, dx, 0.2f, dz, crit));
        }

        if (hitConfirmed && age < KILL_MS && (t.isDead || t.getHealth() <= 0f)) {
            MinecraftForge.EVENT_BUS.post(new KillEvent(t));
            target = null;
        }
    }
}
