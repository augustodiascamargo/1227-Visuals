package net.m1227.client.perf;

import java.util.List;
import net.m1227.module.PvpVisualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Limits for cosmetic effects on OTHER players, from PvP Visual Optimization. Your own effects,
 * players themselves, name tags and combat feedback are never limited here.
 *
 * Once per frame the other players within the cosmetic effect distance are ranked by distance
 * (nearest first). A module asks {@link #allow(Kind, Entity)} before drawing a cosmetic on a
 * player; the answer is yes for the nearest N of that kind. Without the module every answer is yes.
 * No allocation: the ranking reuses two fixed arrays.
 */
public final class EffectBudget {
    private EffectBudget() {}

    public enum Kind { AURA, WEAPON }

    private static final int MAX = 256;
    private static final int[] IDS = new int[MAX];
    private static final double[] D2 = new double[MAX];
    private static int count;
    private static long rankedFrame = -1;
    private static long hitTokensAt;
    private static float hitTokens = 8f;

    private static void rank(Minecraft mc, double maxD) {
        long f = FrameStats.INSTANCE.frameNumber();
        if (f == rankedFrame) return;
        rankedFrame = f;
        count = 0;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        List<EntityPlayer> list = mc.theWorld.playerEntities;
        double max2 = maxD * maxD;
        for (int i = 0; i < list.size() && count < MAX; i++) {
            EntityPlayer p = list.get(i);
            if (p == mc.thePlayer) continue;
            double d2 = p.getDistanceSqToEntity(mc.thePlayer);
            if (d2 > max2) continue;
            // insertion sort, nearest first (a few dozen players at most)
            int k = count++;
            while (k > 0 && D2[k - 1] > d2) {
                D2[k] = D2[k - 1];
                IDS[k] = IDS[k - 1];
                k--;
            }
            D2[k] = d2;
            IDS[k] = p.getEntityId();
        }
    }

    /** May this cosmetic be drawn on this (other) player this frame? */
    public static boolean allow(Kind k, Entity e) {
        PvpVisualModule m = PvpVisualModule.INSTANCE;
        if (m == null || !m.isEnabled()) return true;
        Minecraft mc = Minecraft.getMinecraft();
        if (e == mc.thePlayer) return true;
        int limit = k == Kind.AURA ? m.maxAuras() : m.maxWeaponFx();
        if (limit <= 0) return false;
        rank(mc, m.cosmeticDistance());
        int id = e.getEntityId();
        for (int i = 0; i < count && i < limit; i++) if (IDS[i] == id) return true;
        return false;
    }

    /** Projectile trails allowed per tick (Integer.MAX_VALUE without the module). */
    public static int maxTrails() {
        PvpVisualModule m = PvpVisualModule.INSTANCE;
        return m == null || !m.isEnabled() ? Integer.MAX_VALUE : m.maxTrails();
    }

    /**
     * Hit effects (the particle burst on a hit) allowed right now: a token bucket refilled at the
     * chosen rate per second. Sounds, hit markers, damage numbers and the hit colour are separate
     * and never limited.
     */
    public static boolean hitEffect() {
        PvpVisualModule m = PvpVisualModule.INSTANCE;
        if (m == null || !m.isEnabled()) return true;
        long now = System.nanoTime();
        float rate = m.maxHitEffectsPerSecond();
        if (hitTokensAt != 0L) hitTokens = Math.min(rate, hitTokens + (now - hitTokensAt) / 1e9f * rate);
        hitTokensAt = now;
        if (hitTokens < 1f) return false;
        hitTokens -= 1f;
        return true;
    }
}
