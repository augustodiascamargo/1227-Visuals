package net.m1227.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;

/**
 * Live effect preview from the menu: the menu steps aside, a client-only
 * stand-in of your own character appears a few blocks in front of you, and
 * the effect is played on it (a kill effect makes it vanish, a hit makes it
 * flinch). The stand-in exists only on your screen and is removed right
 * after; nothing is sent to the server.
 */
public final class PreviewMode {
    private static final int DUMMY_ID = -1227;
    private static long start, impactAt, until;
    private static String label = "";
    private static Runnable action;
    private static boolean kill, hit, impactDone;
    private static double[] spot;
    private static EntityOtherPlayerMP dummy;

    private PreviewMode() {}

    public static boolean active() {
        return until != 0L;
    }

    public static String label() {
        return label;
    }

    /** 0..1 progress of the current preview. */
    public static float progress() {
        return until == 0L ? 0f : Math.min(1f, (System.currentTimeMillis() - start) / (float) Math.max(1L, until - start));
    }

    /** Where Preview.ahead() should put effects while a preview runs (null otherwise). */
    static double[] spot() {
        return spot;
    }

    public static void begin(String moduleName, Runnable effect) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            effect.run();
            return;
        }
        stop();
        label = moduleName;
        action = effect;
        String n = moduleName.toLowerCase(java.util.Locale.ROOT);
        kill = n.contains("kill") || n.contains("defeat");
        hit = n.contains("hit") || n.contains("damage") || n.contains("shockwave") || n.contains("weapon");
        boolean wantsDummy = kill || hit || n.contains("aura") || n.contains("glow");
        double yaw = Math.toRadians(p.rotationYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        spot = new double[]{p.posX + fx * 3.4, p.posY, p.posZ + fz * 3.4, fx, fz};
        if (wantsDummy) {
            try {
                dummy = new EntityOtherPlayerMP(mc.theWorld, p.getGameProfile());
                dummy.setPositionAndRotation(spot[0], spot[1], spot[2], p.rotationYaw + 180f, 0f);
                dummy.rotationYawHead = p.rotationYaw + 180f;
                dummy.renderYawOffset = p.rotationYaw + 180f;
                mc.theWorld.addEntityToWorld(DUMMY_ID, dummy);
            } catch (Throwable t) {
                dummy = null;
            }
        }
        start = System.currentTimeMillis();
        impactAt = start + (dummy != null ? 550L : 250L);
        until = start + 2800L;
        impactDone = false;
    }

    /** Called every menu frame. */
    public static void update() {
        if (until == 0L) return;
        long now = System.currentTimeMillis();
        if (!impactDone && now >= impactAt) {
            impactDone = true;
            if (dummy != null && hit) dummy.performHurtAnimation();
            try {
                action.run();
            } catch (Throwable ignored) {
                // a failing preview must never break the menu
            }
            if (kill) removeDummy();
        }
        if (now >= until) stop();
    }

    public static void stop() {
        removeDummy();
        until = 0L;
        spot = null;
        action = null;
    }

    private static void removeDummy() {
        if (dummy == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (mc.theWorld != null) mc.theWorld.removeEntityFromWorld(DUMMY_ID);
        } catch (Throwable ignored) {
            // world already gone
        }
        dummy = null;
    }
}
