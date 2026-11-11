package net.m1227.core;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

/**
 * Health of other players as the client can know it. Many PvP servers don't send other
 * players' real health (the client keeps a fixed value), but show it on the scoreboard
 * under the name tag or in the tab list instead. When such a health objective exists it is
 * used; otherwise the entity's own value is returned.
 */
public final class Health {
    private Health() {}

    public static float of(EntityLivingBase e) {
        float s = scoreboard(e);
        return s >= 0f ? s : e.getHealth();
    }

    /** Health from a scoreboard objective (below name, then tab list), or -1 if there is none. */
    public static float scoreboard(EntityLivingBase e) {
        if (!(e instanceof EntityPlayer) || e.worldObj == null) return -1f;
        try {
            Scoreboard sb = e.worldObj.getScoreboard();
            if (sb == null) return -1f;
            String name = e.getName();
            int[] slots = {2, 0}; // 2 = below name, 0 = player list
            for (int slot : slots) {
                ScoreObjective o = sb.getObjectiveInDisplaySlot(slot);
                if (o == null || !looksLikeHealth(o) || !sb.entityHasObjective(name, o)) continue;
                int v = sb.getValueFromObjective(name, o).getScorePoints();
                float max = Math.max(1f, e.getMaxHealth());
                if (v >= 0 && v <= max * 1.01f) return v;
            }
        } catch (Throwable ignored) {
            // scoreboard can change mid-frame on some servers
        }
        return -1f;
    }

    private static boolean looksLikeHealth(ScoreObjective o) {
        if (o.getCriteria() == IScoreObjectiveCriteria.health) return true;
        String n = (o.getDisplayName() + " " + o.getName()).toLowerCase();
        return n.contains("health") || n.contains("vida") || n.contains("hp") || n.contains("❤") || n.contains("♥");
    }
}
