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
 * - Entity render distance: living entities farther than the limit are skipped (players only
 *   when chosen; never you, your mount or what you are riding);
 * - Minecraft particle limit and distance: the oldest particles above the cap and the ones beyond
 *   the distance are removed each tick;
 * - Background FPS: while the window is not focused, frames are paced to the chosen FPS.
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

    /** True when an entity at this distance should not be drawn (also used for dropped items). */
    public static boolean cull(Entity e) {
        PerformanceModule p = p();
        double d = p.entityDistance.get();
        if (d >= 256) return false;
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || e == view || e == view.ridingEntity || e.riddenByEntity == view) return false;
        if (e instanceof EntityPlayer && !p.cullPlayers.on()) return false;
        return e.getDistanceSqToEntity(view) > d * d;
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
                    if (f.getType().isArray() && f.getType().getComponentType().isArray()) {
                        fxLayers = f;
                        fxLayers.setAccessible(true);
                        break;
                    }
                }
                if (fxLayers == null) {
                    fxFailed = true;
                    return null;
                }
            }
            return (List<EntityFX>[][]) fxLayers.get(r);
        } catch (Throwable t) {
            fxFailed = true;
            Mod1227.LOGGER.warn("1227 particle limiter unavailable", t);
            return null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
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
                    EntityFX fx = list.get(i);
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
        if (p.backgroundLimit.on() && !Display.isActive()) {
            long frame = (long) (1e9 / Math.max(5, p.backgroundFps.get()));
            long wait = frame - (now - lastFrame);
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
