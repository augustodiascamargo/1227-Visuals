package net.m1227.core;

import java.util.List;
import net.m1227.combat.CombatLog;
import net.m1227.combat.HitEvidence;
import net.m1227.net.LatencyMonitor;
import net.m1227.net.NetObserver;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 1227 Hit Registration Monitor (1.21; before it this class only watched hurtTime).
 *
 * Every attack you make is an <b>attempt</b> (Forge's attack event, and the attack packet when it
 * is written to the connection). The monitor then collects evidence about it from what the server
 * sends back (see {@link HitEvidence} for the rules and levels) and classifies it when its window
 * closes. It never decides that a hit was "rejected": the client cannot know that without the
 * server's own data. An authoritative hit registration figure would need a server plugin that
 * reports it; without one it is shown as unavailable.
 *
 * It also still produces the two events the effects use: {@link HitEvent} as soon as damage
 * feedback is matched to your attempt (hit sounds, hit effects, combo) and {@link KillEvent}.
 * Purely observational: it sends nothing and changes no gameplay.
 */
public final class CombatTracker {
    private static final long TRACK_MS = 8000L;
    private static final long KILL_MS = 2500L;

    // --------------------------------------------------------------- public events (unchanged API)

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

    // --------------------------------------------------------------- attempts

    /** One attack attempt and what was observed about it. */
    public static final class Attempt {
        public long at, sentAt, hurtAt, resolvedAt;
        public int targetId;
        public String targetName = "";
        public boolean critChance, finished, hitEventFired;
        public final HitEvidence.Facts facts = new HitEvidence.Facts();
        public HitEvidence.Level level = HitEvidence.Level.UNKNOWN;
        public String reason = "";
        /** Eye to hitbox: at the attempt, and when the damage feedback arrived (-1 = n/a). */
        public float reachAttempt = -1f, reachFeedback = -1f;
        double tx, tz, dirX, dirZ, baseSpeed;
        int kbTicks;

        long time() {
            return sentAt != 0L ? sentAt : at;
        }
    }

    private static final Attempt[] RING = new Attempt[48];
    private static int ringHead;

    static {
        for (int i = 0; i < RING.length; i++) RING[i] = new Attempt();
    }

    // session counters
    public static int attempts, strong, moderate, weak, none, unknown;
    private static Attempt lastFinished;

    // target tracking
    private static EntityLivingBase target;
    private static long attackTimeMs;
    private static int combo;
    private static long lastHitMs;
    private static float lastTargetHealth = -1f;
    private static boolean healthObservable;
    private static long lastHurtSeen = -1L; // nanos, for the target's immunity window
    private static int lastHurtTarget = -1;
    private static long otherSwingAt;
    private static int otherSwingTarget = -1;
    private static float selfHealth = -1f;
    private static boolean likelyHitSinceAttack;
    private static int selfHurtPrev;

    /** One instance is registered on the event bus by the mod. */
    public CombatTracker() {}

    /** Confirmed hits in a row on the current target (reset after 2 s or when you get hit). */
    public static int combo() {
        return System.currentTimeMillis() - lastHitMs > 2000L ? 0 : combo;
    }

    /** The entity we last attacked, if it was within maxAgeMs and still alive. */
    public static EntityLivingBase target(long maxAgeMs) {
        EntityLivingBase t = target;
        if (t == null || t.isDead) return null;
        if (System.currentTimeMillis() - attackTimeMs > maxAgeMs) return null;
        return t;
    }

    /** The newest attempt that has been classified (null = none yet). */
    public static Attempt lastFinished() {
        return lastFinished;
    }

    /** Attempts, newest first (i = 0); may include ones still waiting for evidence. */
    public static Attempt attempt(int i) {
        return RING[(ringHead - 1 - i + RING.length * 2) % RING.length];
    }

    public static int ringSize() {
        return Math.min(attempts, RING.length);
    }

    public static double feedbackRate() {
        return HitEvidence.feedbackRate(strong, moderate, none);
    }

    public static void resetSession() {
        attempts = strong = moderate = weak = none = unknown = 0;
        lastFinished = null;
        CombatLog.INSTANCE.clear();
    }

    private static double windowNs() {
        return HitEvidence.windowMs(LatencyMonitor.INSTANCE.bestRttMs()) * 1e6;
    }

    // --------------------------------------------------------------- attempt

    private static float reach(Entity eyeOf, EntityLivingBase t) {
        Vec3 eye = eyeOf.getPositionEyes(1f);
        float b = t.getCollisionBorderSize();
        AxisAlignedBB bb = t.getEntityBoundingBox().expand(b, b, b);
        double cx = Math.max(bb.minX, Math.min(eye.xCoord, bb.maxX)), cy = Math.max(bb.minY, Math.min(eye.yCoord, bb.maxY)),
                cz = Math.max(bb.minZ, Math.min(eye.zCoord, bb.maxZ));
        double dx = cx - eye.xCoord, dy = cy - eye.yCoord, dz = cz - eye.zCoord;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.entityPlayer != mc.thePlayer) return; // ignores the integrated server's copy
        if (!(e.target instanceof EntityLivingBase)) return;
        newAttempt(mc, (EntityLivingBase) e.target, System.nanoTime());
    }

    private static Attempt newAttempt(Minecraft mc, EntityLivingBase t, long now) {
        if (t != target) {
            lastTargetHealth = t.getHealth();
            healthObservable = t.getHealth() < t.getMaxHealth() - 0.01f;
        }
        target = t;
        attackTimeMs = System.currentTimeMillis();
        likelyHitSinceAttack = false;
        net.minecraft.client.entity.EntityPlayerSP p = mc.thePlayer;
        Attempt a = RING[ringHead];
        ringHead = (ringHead + 1) % RING.length;
        attempts++;
        a.at = now;
        a.sentAt = 0L;
        a.hurtAt = 0L;
        a.resolvedAt = 0L;
        a.finished = false;
        a.hitEventFired = false;
        a.targetId = t.getEntityId();
        a.targetName = t.getName();
        a.critChance = p.fallDistance > 0.0F && !p.onGround && !p.isOnLadder() && !p.isInWater()
                && !p.isPotionActive(Potion.blindness) && p.ridingEntity == null;
        HitEvidence.Facts f = a.facts;
        f.hurt = f.healthDrop = f.knockback = f.critical = false;
        f.otherAttacker = f.projectile = f.environment = f.tooFast = false;
        f.targetGone = f.clientHurtOnly = false;
        f.observer = NetObserver.INSTANCE.attached();
        f.cooldown = lastHurtTarget == a.targetId
                && HitEvidence.inCooldown(now / 1e6, lastHurtSeen < 0 ? -1 : lastHurtSeen / 1e6, LatencyMonitor.INSTANCE.bestRttMs());
        a.level = HitEvidence.Level.UNKNOWN;
        a.reason = "waiting";
        a.reachAttempt = reach(p, t);
        a.reachFeedback = -1f;
        a.tx = t.posX;
        a.tz = t.posZ;
        double dx = t.posX - p.posX, dz = t.posZ - p.posZ, l = Math.sqrt(dx * dx + dz * dz);
        a.dirX = l < 1e-3 ? 0 : dx / l;
        a.dirZ = l < 1e-3 ? 1 : dz / l;
        a.baseSpeed = (t.posX - t.prevPosX) * a.dirX + (t.posZ - t.prevPosZ) * a.dirZ;
        a.kbTicks = 0;
        CombatLog.INSTANCE.attempt(now);
        CombatLog.INSTANCE.add(now, CombatLog.Kind.ATTEMPT, a.targetName + String.format("  %.2f m", a.reachAttempt), 0xFFECEEF4);
        return a;
    }

    // --------------------------------------------------------------- packets (main thread, in arrival order)

    /** Called by the Latency Monitor for every observed packet, on the main thread. */
    public static void onPacket(NetObserver.Seen s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        CombatLog log = CombatLog.INSTANCE;
        if (s.packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity p = (C02PacketUseEntity) s.packet;
            if (p.getAction() != C02PacketUseEntity.Action.ATTACK) return;
            Entity en = p.getEntityFromWorld(mc.theWorld);
            Attempt a = null;
            for (int i = 0; i < Math.min(attempts, 4); i++) {
                Attempt c = attempt(i);
                if (!c.finished && c.sentAt == 0L && en != null && c.targetId == en.getEntityId()) {
                    a = c;
                    break;
                }
            }
            // an attack that was sent without the attack event (another mod cancelled the event)
            if (a == null && en instanceof EntityLivingBase) a = newAttempt(mc, (EntityLivingBase) en, s.nanos);
            if (a != null) a.sentAt = s.nanos;
            CombatLog.Reset r = log.attackSent(s.nanos);
            log.add(s.nanos, CombatLog.Kind.SENT, r.grade, r.color);
            return;
        }
        if (s.packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction.Action act = ((C0BPacketEntityAction) s.packet).getAction();
            if (act == C0BPacketEntityAction.Action.START_SPRINTING) log.sprintPacket(s.nanos, true);
            else if (act == C0BPacketEntityAction.Action.STOP_SPRINTING) log.sprintPacket(s.nanos, false);
            return;
        }
        if (s.packet instanceof S19PacketEntityStatus) {
            S19PacketEntityStatus p = (S19PacketEntityStatus) s.packet;
            Entity en = p.getEntity(mc.theWorld);
            if (en == null) return;
            if (p.getOpCode() == 2) {
                if (en == mc.thePlayer) selfHurt(s.nanos);
                else hurtSignal(mc, en, s.nanos);
            } else if (p.getOpCode() == 3 && en == target) {
                killCheck(true);
            }
            return;
        }
        if (s.packet instanceof S0BPacketAnimation) {
            S0BPacketAnimation p = (S0BPacketAnimation) s.packet;
            Entity en = mc.theWorld.getEntityByID(p.getEntityID());
            if (en == null) return;
            int type = p.getAnimationType();
            if (type == 1 && en != mc.thePlayer) hurtSignal(mc, en, s.nanos);
            else if (type == 4) critSignal(en, s.nanos);
            else if (type == 0 && en instanceof EntityPlayer && en != mc.thePlayer) {
                // another player swinging near the target we are tracking
                EntityLivingBase t = target;
                if (t != null && en.getDistanceSqToEntity(t) < 25) {
                    otherSwingAt = s.nanos;
                    otherSwingTarget = t.getEntityId();
                    for (int i = 0; i < Math.min(attempts, 8); i++) {
                        Attempt a = attempt(i);
                        if (!a.finished && a.targetId == otherSwingTarget && Math.abs(s.nanos - a.time()) < windowNs()) a.facts.otherAttacker = true;
                    }
                }
            }
            return;
        }
        if (s.packet instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity p = (S12PacketEntityVelocity) s.packet;
            if (p.getEntityID() == mc.thePlayer.getEntityId()) return;
            Attempt a = pendingFor(p.getEntityID(), s.nanos, true);
            if (a != null && !a.facts.knockback && a.hurtAt != 0L) {
                a.facts.knockback = true;
                log.add(s.nanos, CombatLog.Kind.KNOCKBACK, "velocity packet", 0xFF9AA0AE);
            }
            return;
        }
        if (s.packet instanceof S06PacketUpdateHealth) {
            float h = ((S06PacketUpdateHealth) s.packet).getHealth();
            if (selfHealth >= 0 && h < selfHealth - 0.01f) selfHurt(s.nanos);
            selfHealth = h;
            return;
        }
        if (s.packet instanceof S13PacketDestroyEntities) {
            for (int id : ((S13PacketDestroyEntities) s.packet).getEntityIDs()) {
                for (int i = 0; i < Math.min(attempts, RING.length); i++) {
                    Attempt a = attempt(i);
                    if (!a.finished && a.targetId == id) a.facts.targetGone = true;
                }
            }
        }
    }

    /** The oldest unfinished attempt on this entity whose window contains `now`. */
    private static Attempt pendingFor(int entityId, long now, boolean allowMatched) {
        double w = windowNs();
        double floor = HitEvidence.floorMs(LatencyMonitor.INSTANCE.floorRttMs()) * 1e6;
        Attempt best = null;
        for (int i = 0; i < Math.min(attempts, RING.length); i++) {
            Attempt a = attempt(i);
            if (a.finished || a.targetId != entityId) continue;
            long dt = now - a.time();
            if (dt < 0 || dt > w) continue;
            if (!allowMatched && a.hurtAt != 0L) continue;
            if (dt < floor && a.hurtAt == 0L && !allowMatched) continue;
            best = a; // keep going: older attempts come later in this loop
        }
        return best;
    }

    private static void hurtSignal(Minecraft mc, Entity en, long now) {
        boolean ours = false;
        Attempt a = pendingFor(en.getEntityId(), now, false);
        if (a == null) {
            // arrived before any reply could: note it on attempts still waiting
            for (int i = 0; i < Math.min(attempts, 4); i++) {
                Attempt c = attempt(i);
                if (!c.finished && c.targetId == en.getEntityId() && c.hurtAt == 0L && now >= c.time()) c.facts.tooFast = true;
            }
        } else {
            ours = true;
            a.hurtAt = now;
            a.facts.hurt = true;
            if (en instanceof EntityLivingBase) {
                EntityLivingBase t = (EntityLivingBase) en;
                a.reachFeedback = reach(mc.thePlayer, t);
                a.facts.environment = t.isBurning() || t.isInLava() || t.isPotionActive(Potion.poison) || t.isPotionActive(Potion.wither);
                a.facts.projectile = projectileNear(mc, t);
                if (otherSwingTarget == a.targetId && Math.abs(otherSwingAt - a.time()) < windowNs()) a.facts.otherAttacker = true;
            }
            CombatLog.INSTANCE.add(now, CombatLog.Kind.HURT, String.format("%s  +%d ms", a.targetName, (now - a.time()) / 1_000_000L), 0xFF8FE6FF);
            fireHit(mc, a, en);
        }
        if (en.getEntityId() == (target == null ? -2 : target.getEntityId())) {
            lastHurtSeen = now;
            lastHurtTarget = en.getEntityId();
        }
        if (!ours && en == target) CombatLog.INSTANCE.add(now, CombatLog.Kind.HURT, en.getName() + "  (not matched to your attack)", 0xFF646978);
    }

    private static void critSignal(Entity en, long now) {
        Attempt a = pendingFor(en.getEntityId(), now, true);
        if (a != null && a.critChance && !a.facts.critical) {
            a.facts.critical = true;
            CombatLog.INSTANCE.add(now, CombatLog.Kind.CRIT, a.targetName, 0xFFFFC24A);
        }
    }

    private static boolean projectileNear(Minecraft mc, EntityLivingBase t) {
        List<Entity> list = mc.theWorld.loadedEntityList;
        for (int i = 0; i < list.size(); i++) {
            Entity e = list.get(i);
            if ((e instanceof EntityArrow || e instanceof EntityThrowable || e instanceof EntityFishHook) && e.getDistanceSqToEntity(t) < 9) {
                if (e instanceof EntityFishHook && ((EntityFishHook) e).angler == mc.thePlayer) continue;
                return true;
            }
        }
        return false;
    }

    private static void selfHurt(long now) {
        combo = 0; // taking a hit breaks your combo
        CombatLog.INSTANCE.add(now, CombatLog.Kind.SELF_HURT, "", 0xFFFF6B6B);
    }

    private static void fireHit(Minecraft mc, Attempt a, Entity en) {
        if (a.hitEventFired || !(en instanceof EntityLivingBase)) return;
        a.hitEventFired = true;
        EntityLivingBase t = (EntityLivingBase) en;
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
        combo = now - lastHitMs > 2000L ? 1 : combo + 1;
        lastHitMs = now;
        likelyHitSinceAttack = true;
        CombatLog.INSTANCE.hit(System.nanoTime());
        if (combo >= 2) CombatLog.INSTANCE.add(System.nanoTime(), CombatLog.Kind.COMBO, combo + " hits", 0xFFB6E35A);
        MinecraftForge.EVENT_BUS.post(new HitEvent(t, dx, 0.2f, dz, a.critChance));
    }

    // --------------------------------------------------------------- per tick

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        EntityLivingBase t = target;
        if (mc.theWorld == null || mc.thePlayer == null || (t != null && t.worldObj != mc.theWorld)) {
            finishAll(true);
            target = null;
            return;
        }
        if (t != null && System.currentTimeMillis() - attackTimeMs > TRACK_MS) target = null;
        // without the network monitor, your own hurt animation is the only sign you were hit
        if (!NetObserver.INSTANCE.attached()) {
            int h = mc.thePlayer.hurtTime;
            if (h > selfHurtPrev) selfHurt(now);
            selfHurtPrev = h;
        }
        // health and knockback of the current target
        if (t != null) {
            float h = t.getHealth();
            if (lastTargetHealth >= 0 && Math.abs(h - lastTargetHealth) > 0.01f) healthObservable = true;
            if (healthObservable && lastTargetHealth >= 0 && h < lastTargetHealth - 0.01f) {
                Attempt a = pendingFor(t.getEntityId(), now, true);
                if (a != null && !a.facts.healthDrop) {
                    a.facts.healthDrop = true;
                    CombatLog.INSTANCE.add(now, CombatLog.Kind.HEALTH, String.format("%s  -%.1f", a.targetName, lastTargetHealth - h), 0xFF9AA0AE);
                    if (!a.facts.hurt) fireHit(mc, a, t);
                }
            }
            lastTargetHealth = h;
        }
        double w = windowNs();
        for (int i = 0; i < Math.min(attempts, RING.length); i++) {
            Attempt a = attempt(i);
            if (a.finished) continue;
            Entity en = mc.theWorld.getEntityByID(a.targetId);
            if (en == null) a.facts.targetGone = true;
            // knockback seen as movement: the target speeds up away from you after the hurt signal
            if (en != null && a.hurtAt != 0L && !a.facts.knockback && a.kbTicks < 6) {
                a.kbTicks++;
                double v = (en.posX - en.prevPosX) * a.dirX + (en.posZ - en.prevPosZ) * a.dirZ;
                if (v - a.baseSpeed > 0.12) {
                    a.facts.knockback = true;
                    CombatLog.INSTANCE.add(now, CombatLog.Kind.KNOCKBACK, "movement", 0xFF9AA0AE);
                }
            }
            // fallback without the network monitor: the client-side hurt animation
            if (!a.facts.observer && en instanceof EntityLivingBase && ((EntityLivingBase) en).hurtTime >= 9 && now - a.time() < w) {
                a.facts.clientHurtOnly = true;
                fireHit(mc, a, en);
            }
            boolean windowOver = now - a.time() > w;
            boolean kbDone = a.hurtAt == 0L || a.facts.knockback || a.kbTicks >= 6;
            if ((windowOver && kbDone) || now - a.time() > w + 400_000_000L) finish(a, now);
        }
        killCheck(false);
    }

    private static void finish(Attempt a, long now) {
        HitEvidence.Result r = HitEvidence.classify(a.facts);
        a.level = r.level;
        a.reason = r.reason;
        a.finished = true;
        a.resolvedAt = now;
        switch (r.level) {
            case STRONG: strong++; break;
            case MODERATE: moderate++; break;
            case WEAK: weak++; break;
            case NONE: none++; break;
            default: unknown++;
        }
        lastFinished = a;
        int c = r.level.likely() ? 0xFF3DDC97 : r.level == HitEvidence.Level.WEAK ? 0xFFFFC24A
                : r.level == HitEvidence.Level.NONE ? 0xFFFF6B6B : 0xFF9AA0AE;
        CombatLog.INSTANCE.add(now, CombatLog.Kind.RESULT, r.level.label + ": " + r.reason, c);
    }

    private static void finishAll(boolean gone) {
        long now = System.nanoTime();
        for (int i = 0; i < Math.min(attempts, RING.length); i++) {
            Attempt a = attempt(i);
            if (!a.finished) {
                if (gone) a.facts.targetGone = true;
                finish(a, now);
            }
        }
    }

    private static void killCheck(boolean deathPacket) {
        EntityLivingBase t = target;
        if (t == null || !likelyHitSinceAttack) return;
        if (System.currentTimeMillis() - lastHitMs > KILL_MS) return;
        if (deathPacket || t.isDead || t.getHealth() <= 0f) {
            CombatLog.INSTANCE.add(System.nanoTime(), CombatLog.Kind.KILL, t.getName(), 0xFF3DDC97);
            MinecraftForge.EVENT_BUS.post(new KillEvent(t));
            target = null;
            likelyHitSinceAttack = false;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        finishAll(true);
        target = null;
        lastHurtTarget = -1;
        selfHealth = -1f;
    }
}
