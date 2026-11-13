package net.m1227.client.perf;

import java.lang.reflect.Field;
import java.util.List;
import net.m1227.Mod1227;
import net.m1227.module.PerformanceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;

/**
 * Performance System, the parts that change what Minecraft draws:
 * - Entity render distance, with separate limits for players, mobs and dropped items (players
 *   are never hidden unless you lower their own limit; never you, your mount or your rider);
 * - optional occlusion culling of mobs hidden behind blocks (never players), see {@link Occlusion};
 * - block entity culling (chests, signs...), see {@link BlockEntityCulling};
 * - Minecraft particle limit and distance: the oldest particles above the cap and the ones beyond
 *   the distance are removed each tick (the internal particle lists are found by type and checked
 *   before use; if they do not look as expected the limiter switches itself off);
 * - Background FPS: while the window is not focused, frames are paced to the chosen FPS (never
 *   while focused, so it cannot add input lag).
 * All of it only affects drawing on your screen.
 */
public final class PerfSystem {
    public static final PerfSystem INSTANCE = new PerfSystem();
    private Field fxLayers;
    private boolean fxFailed;
    private long lastFrame;

    private PerfSystem() {}

    private static PerformanceModule p() {
        return PerformanceModule.INSTANCE;
    }

    /** True when this entity should not be drawn this frame (also used for dropped items). */
    public static boolean cull(Entity e) {
        PerformanceModule p = p();
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || e == view || e == view.ridingEntity || e.riddenByEntity == view) return false;
        double d;
        if (e instanceof EntityPlayer) {
            d = p.playerDistance.get();
            if (p.cullPlayers.on()) d = Math.min(d, p.entityDistance.get());
        } else if (e instanceof net.minecraft.entity.item.EntityItem) {
            d = Math.min(p.itemDistance.get(), p.entityDistance.get());
        } else {
            d = p.entityDistance.get();
        }
        if (d < 256 && e.getDistanceSqToEntity(view) > d * d) return true;
        // occlusion: mobs only, never players (they matter in PvP)
        return p.hideOccludedMobs.on() && !(e instanceof EntityPlayer) && e instanceof net.minecraft.entity.EntityLiving && Occlusion.hidden(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onRenderLiving(RenderLivingEvent.Pre e) {
        if (cull(e.entity)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onNameplate(RenderLivingEvent.Specials.Pre e) {
        if (cull(e.entity)) e.setCanceled(true);
    }

    @SuppressWarnings("unchecked")
    private List<EntityFX>[][] layers(EffectRenderer r) {
        if (fxFailed) return null;
        try {
            if (fxLayers == null) {
                for (Field f : EffectRenderer.class.getDeclaredFields()) {
                    // List[][]: the 4 layers x 2 (normal / no depth) particle lists of 1.8.9
                    if (f.getType().isArray() && f.getType().getComponentType().isArray()
                            && List.class.isAssignableFrom(f.getType().getComponentType().getComponentType())) {
                        fxLayers = f;
                        fxLayers.setAccessible(true);
                        break;
                    }
                }
                if (fxLayers == null) {
                    fxFailed = true;
                    Mod1227.LOGGER.warn("1227 particle limiter: particle lists not found, limiter off");
                    return null;
                }
            }
            Object v = fxLayers.get(r);
            if (!(v instanceof List[][])) {
                fxFailed = true;
                return null;
            }
            List<EntityFX>[][] l = (List<EntityFX>[][]) v;
            for (List<EntityFX>[] row : l) {
                if (row == null) {
                    fxFailed = true;
                    Mod1227.LOGGER.warn("1227 particle limiter: unexpected particle layout, limiter off");
                    return null;
                }
                for (List<EntityFX> list : row) {
                    if (list == null) {
                        fxFailed = true;
                        return null;
                    }
                }
            }
            return l;
        } catch (Throwable t) {
            fxFailed = true;
            Mod1227.LOGGER.warn("1227 particle limiter unavailable", t);
            return null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        BlockEntityCulling.ensureWrapped();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.effectRenderer == null || mc.getRenderViewEntity() == null) return;
        PerformanceModule p = p();
        int cap = p.particleCap.i();
        double dist = p.particleDistance.get();
        if (cap >= 4000 && dist >= 64) return;
        List<EntityFX>[][] l = layers(mc.effectRenderer);
        if (l == null) return;
        Entity view = mc.getRenderViewEntity();
        double d2 = dist * dist;
        int total = 0;
        for (List<EntityFX>[] row : l) for (List<EntityFX> list : row) total += list.size();
        int over = Math.max(0, total - cap);
        for (List<EntityFX>[] row : l) {
            for (List<EntityFX> list : row) {
                for (int i = 0; i < list.size(); i++) {
                    Object o = list.get(i);
                    if (!(o instanceof EntityFX)) continue;
                    EntityFX fx = (EntityFX) o;
                    if (over > 0) {
                        // oldest first: lists are in spawn order
                        fx.setDead();
                        over--;
                    } else if (dist < 64 && fx.getDistanceSqToEntity(view) > d2) {
                        fx.setDead();
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onFrame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Benchmark.frame();
        PerformanceModule p = p();
        long now = System.nanoTime();
        if (p.backgroundLimit.on() && !Display.isActive() && !Benchmark.running()) {
            long frame = (long) (1e9 / Math.max(5, p.backgroundFps.get()));
            long wait = Math.min(200_000_000L, frame - (now - lastFrame));
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1000000L, (int) (wait % 1000000L));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            now = System.nanoTime();
        }
        lastFrame = now;
    }
}
