package net.m1227.client.perf;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
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
 */
public final class Occlusion {
    private Occlusion() {}

    private static final class Entry {
        boolean visible;
        long at;
    }

    private static final Map<Long, Entry> CACHE = new HashMap<Long, Entry>();
    private static long lastSweep;
    private static int raysThisFrame;
    private static long frameStamp;

    public static boolean hidden(Entity e) {
        AxisAlignedBB b = e.getEntityBoundingBox();
        return hidden(((long) e.getEntityId()) << 1, b);
    }

    public static boolean hidden(BlockPos p) {
        return hidden((p.toLong() << 1) | 1L, new AxisAlignedBB(p, p.add(1, 1, 1)));
    }

    private static boolean hidden(long key, AxisAlignedBB box) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        World w = mc.theWorld;
        if (view == null || w == null) return false;
        long now = System.currentTimeMillis();
        Vec3 eye = view.getPositionEyes(1f);
        double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
        if (eye.squareDistanceTo(new Vec3(cx, cy, cz)) < 36) return false;
        Entry en = CACHE.get(key);
        if (en != null && now - en.at < 150L) return !en.visible;
        // spread the work: at most 60 new tests per frame, the rest keep their last answer
        long frame = System.nanoTime() / 8_000_000L;
        if (frame != frameStamp) {
            frameStamp = frame;
            raysThisFrame = 0;
        }
        if (raysThisFrame >= 60) return en != null && !en.visible;
        raysThisFrame++;
        boolean vis = ray(w, eye, new Vec3(cx, cy, cz), box)
                || ray(w, eye, new Vec3(cx, box.maxY - 0.05, cz), box)
                || ray(w, eye, new Vec3(clamp(eye.xCoord, box.minX, box.maxX), clamp(eye.yCoord, box.minY, box.maxY), clamp(eye.zCoord, box.minZ, box.maxZ)), box);
        if (en == null) {
            en = new Entry();
            CACHE.put(key, en);
        }
        en.visible = vis;
        en.at = now;
        if (now - lastSweep > 5000L) {
            lastSweep = now;
            for (Iterator<Entry> it = CACHE.values().iterator(); it.hasNext(); ) {
                if (now - it.next().at > 5000L) it.remove();
            }
        }
        return !vis;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo + 0.01, Math.min(hi - 0.01, v));
    }

    private static boolean ray(World w, Vec3 from, Vec3 to, AxisAlignedBB box) {
        MovingObjectPosition hit = w.rayTraceBlocks(from, to, false, true, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return true;
        // the ray stopped at the object's own block (chests) or inside its box: visible
        BlockPos hp = hit.getBlockPos();
        return box.expand(0.5, 0.5, 0.5).isVecInside(new Vec3(hp.getX() + 0.5, hp.getY() + 0.5, hp.getZ() + 0.5));
    }
}
