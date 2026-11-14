package net.m1227.module;

import net.minecraft.entity.player.EntityPlayer;

/**
 * While a cosmetic is drawn for another 1227 player (1227 Link), the colours that player shares
 * replace yours; the shape and style stay yours. Main thread only, always cleared after the draw.
 */
public final class RemoteLook {
    private RemoteLook() {}

    private static int c1 = -1, c2 = -1;

    public static int c1(int mine) {
        return c1 >= 0 ? c1 : mine;
    }

    public static int c2(int mine) {
        return c2 >= 0 ? c2 : mine;
    }

    static void set(int a, int b) {
        c1 = a & 0xFFFFFF;
        c2 = b & 0xFFFFFF;
    }

    static void clear() {
        c1 = -1;
        c2 = -1;
    }

    /** True when 1227 Link draws this cosmetic on that player (so the module's own loop skips it). */
    public static boolean handled(EntityPlayer p, int flag) {
        SocialModule s = SocialModule.INSTANCE;
        return s != null && s.draws(p, flag);
    }
}
