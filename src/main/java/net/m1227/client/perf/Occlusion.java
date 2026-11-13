package net.m1227.client.perf;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * Cheap "is it hidden behind blocks?" test for the optional occlusion culling: up to three rays
 * from your eyes to the object (centre, top, and the corner nearest the line of sight). If any
 * ray arrives, it is visible. Results are cached for 150 ms per object, so a crowd costs a few
 * rays per frame at most. Things closer than 6 m are always drawn (a ray can graze a corner).
 *
 * When unsure the answer is "visible": a wrong "hidden" would make something vanish, a wrong
 * "visible" only costs the time we tried to save.
 *
 * 1.21 audit fixes:
 * - the "60 tests per frame" budget was counted per 8 ms slice, not per frame: at 30 FPS a frame
 *   could run 240 tests (720 rays). It now counts real frames ({@link #newFrame()} is called at
 *   the start of each frame) and the budget is 40 tests (at most 120 rays) per frame;
 * - the cache is cleared when the world changes, so a reused entity id never inherits an old
 *   answer, and it is capped at 4096 entries;
 * - a cache hit creates no objects any more (the eye position is read once per frame and the
 *   cache is an open-addressing table of primitive longs); only a real test builds its rays.
 */
public final class Occlusion {
    private Occlusion() {}

    private static final int CAP = 4096, BUDGET = 40;
    // open addressing: key 0 = empty (keys always have a non-zero marker bit)
    private static final long[] KEYS = new long[CAP * 2];
    private static final long[] AT = new long[CAP * 2];
    private static final boolean[] VIS = new boolean[CAP * 2];
    private static int used;
    private static int testsThisFrame;
    private static long frameMs;
    private static double ex, ey, ez;
    private static boolean eyeValid;
    private static World lastWorld;
    /** Tests run in the last frame (for the dashboard). */
    public static volatile int lastFrameTests;

    /** Called at the start of every frame. */
    public static void newFrame() {
        lastFrameTests = testsThisFrame;
        testsThisFrame = 0;
        eyeValid = false;
        frameMs = System.currentTimeMillis();
        World w = Minecraft.getMinecraft().theWorld;
        if (w != lastWorld) {
            lastWorld = w;
            clear();
        }
    }

    public static void clear() {
        java.util.Arrays.fill(KEYS, 0L);
        used = 0;
    }

    public static boolean hidden(Entity e) {
        AxisAlignedBB b = e.getEntityBoundingBox();
        return hidden((((long) e.getEntityId()) << 2) | 2L, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }

    public static boolean hidden(BlockPos p) {
        return hidden((p.toLong() << 2) | 3L, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
    }

    private static int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        int i = (int) (h >>> 40) & (KEYS.length - 1);
        while (KEYS[i] != 0L && KEYS[i] != key) i = (i + 1) & (KEYS.length - 1);
        return i;
    }

    private static boolean hidden(long key, double x0, double y0, double z0, double x1, double y1, double z1) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World w = mc.theWorld;
        if (view == null || w == null) return false;
        if (!eyeValid) {
            ex = view.posX;
            ey = view.posY + view.getEyeHeight();
            ez = view.posZ;
            eyeValid = true;
        }
        long now = frameMs;
        double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        double dx = cx - ex, dy = cy - ey, dz = cz - ez;
        if (dx * dx + dy * dy + dz * dz < 36) return false;
        int i = slot(key);
        boolean known = KEYS[i] == key;
        if (known && now - AT[i] < 150L) return !VIS[i];
        // spread the work: a fixed number of new tests per frame, the rest keep their last answer
        if (testsThisFrame >= BUDGET) return known && !VIS[i];
        testsThisFrame++;
        Vec3 eye = new Vec3(ex, ey, ez);
        boolean vis = ray(w, eye, new Vec3(cx, cy, cz), x0, y0, z0, x1, y1, z1)
                || ray(w, eye, new Vec3(cx, y1 - 0.05, cz), x0, y0, z0, x1, y1, z1)
                || ray(w, eye, new Vec3(clamp(ex, x0, x1), clamp(ey, y0, y1), clamp(ez, z0, z1)), x0, y0, z0, x1, y1, z1);
        if (!known) {
            if (used >= CAP) {
                clear(); // simplest safe eviction: forget everything (answers default to visible)
                i = slot(key);
            }
            KEYS[i] = key;
            used++;
        }
        VIS[i] = vis;
        AT[i] = now;
        return !vis;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo + 0.01, Math.min(hi - 0.01, v));
    }

    private static boolean ray(World w, Vec3 from, Vec3 to, double x0, double y0, double z0, double x1, double y1, double z1) {
        MovingObjectPosition hit = w.rayTraceBlocks(from, to, false, true, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return true;
        // the ray stopped at the object's own block (chests) or inside its box: visible
        BlockPos hp = hit.getBlockPos();
        double hx = hp.getX() + 0.5, hy = hp.getY() + 0.5, hz = hp.getZ() + 0.5;
        return hx > x0 - 0.5 && hx < x1 + 0.5 && hy > y0 - 0.5 && hy < y1 + 0.5 && hz > z0 - 0.5 && hz < z1 + 0.5;
    }
}
