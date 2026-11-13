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
 * - FPS control (1.21): one place decides Minecraft's frame cap each frame: Background FPS while
 *   the window is not focused, the Low Latency mode's cap while it is on, otherwise your own
 *   setting. It only changes the value Minecraft's own limiter reads (Display.sync after the
 *   buffer swap), so nothing sleeps inside our events any more; before 1.21 Background FPS
 *   called Thread.sleep in the render event. The moment the window gets focus back, the next
 *   frame already uses your normal cap.
 * All of it only affects drawing on your screen.
 */
public final class PerfSystem {
    public static final PerfSystem INSTANCE = new PerfSystem();
    private Field fxLayers;
    private boolean fxFailed;
    private int limiterTick;
    /** Minecraft particles alive at the last limiter pass (-1 = unknown). */
    public static volatile int vanillaParticles = -1;
    // FPS control
    private int userLimit = -1, applied = -1;
    private boolean userVsync, vsyncApplied;
    private boolean vsyncOverride;

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
        vanillaParticles = total;
        int over = Math.max(0, total - cap);
        // 1.21: the distance pass walks every particle, so it runs every 5th tick (4 per second);
        // the over-the-cap pass only runs when there is something to remove
        boolean distPass = dist < 64 && (++limiterTick % 5) == 0;
        if (over == 0 && !distPass) return;
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
                    } else if (distPass && fx.getDistanceSqToEntity(view) > d2) {
                        fx.setDead();
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFrameStart(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Occlusion.newFrame();
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        // caps above Minecraft's limiter range (260+ = "unlimited" for Minecraft) use the same
        // LWJGL limiter Minecraft uses, here at the start of the frame
        int extra = ll != null ? ll.extraCap() : 0;
        if (extra > 0 && Display.isActive()) Display.sync(extra);
    }

    @SubscribeEvent
    public void onFrame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Benchmark.frame();
        applyFpsControl();
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        if (ll != null && ll.finishFrames()) org.lwjgl.opengl.GL11.glFinish();
    }

    /** The cap Minecraft should use right now. */
    private int wantedLimit(Minecraft mc, int user) {
        PerformanceModule p = p();
        if (p.backgroundLimit.on() && !Display.isActive() && !Benchmark.running()) return Math.max(5, p.backgroundFps.i());
        // the video settings screen shows and saves your own value
        if (mc.currentScreen instanceof net.minecraft.client.gui.GuiVideoSettings) return user;
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        if (ll != null && ll.isEnabled()) {
            int c = ll.vanillaCap();
            if (c > 0) return c;
        }
        return user;
    }

    private void applyFpsControl() {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.settings.GameSettings gs = mc.gameSettings;
        // anything that changed the value since we last set it (you, a preset, another mod) is
        // the new "own" value
        if (applied < 0 || gs.limitFramerate != applied) userLimit = gs.limitFramerate;
        int want = wantedLimit(mc, userLimit);
        if (gs.limitFramerate != want) gs.limitFramerate = want;
        applied = want;
        // VSync from the Low Latency mode (restored when it is off)
        net.m1227.module.LowLatencyModule ll = net.m1227.module.LowLatencyModule.INSTANCE;
        Boolean v = ll != null && ll.isEnabled() && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiVideoSettings) ? ll.vsyncWanted() : null;
        if (v != null) {
            if (!vsyncOverride) {
                userVsync = gs.enableVsync;
                vsyncOverride = true;
            }
            if (gs.enableVsync != v || !vsyncApplied) {
                gs.enableVsync = v;
                Display.setVSyncEnabled(v);
                vsyncApplied = true;
            }
        } else if (vsyncOverride) {
            vsyncOverride = false;
            vsyncApplied = false;
            gs.enableVsync = userVsync;
            Display.setVSyncEnabled(userVsync);
        }
    }

    /** Your own frame cap and VSync (not the temporary ones), e.g. for presets and reports. */
    public static int userFrameLimit() {
        return INSTANCE.userLimit < 0 ? Minecraft.getMinecraft().gameSettings.limitFramerate : INSTANCE.userLimit;
    }

    /** Puts your own values back before anything saves options.txt. */
    public static void restoreUserVideo() {
        Minecraft mc = Minecraft.getMinecraft();
        if (INSTANCE.userLimit >= 0) mc.gameSettings.limitFramerate = INSTANCE.userLimit;
        if (INSTANCE.vsyncOverride) mc.gameSettings.enableVsync = INSTANCE.userVsync;
        INSTANCE.applied = -1;
    }
}
