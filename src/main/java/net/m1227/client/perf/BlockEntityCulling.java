package net.m1227.client.perf;

import java.util.Map;
import net.m1227.Mod1227;
import net.m1227.module.PerformanceModule;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Block entity culling: chests, ender chests, signs, banners, skulls and other block entities
 * drawn by a special renderer can be skipped when they are farther than a chosen distance, or
 * (optional) hidden behind blocks. Each special renderer registered with Minecraft is wrapped
 * once; the wrapper asks before drawing and otherwise calls the original unchanged.
 *
 * Never skipped: beacons and anything that asks to always render, items in your inventory (no
 * world), and anything within 6 m. With the defaults (64 m, occlusion off) nothing changes from
 * vanilla, which already stops at 64 m.
 */
public final class BlockEntityCulling {
    private BlockEntityCulling() {}

    private static long lastWrap;
    private static boolean failed;

    /** A pass-through renderer that can say "not this frame". */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static final class Wrapper extends TileEntitySpecialRenderer<TileEntity> {
        final TileEntitySpecialRenderer inner;

        Wrapper(TileEntitySpecialRenderer inner) {
            this.inner = inner;
        }

        @Override
        public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage) {
            if (te != null && te.hasWorldObj() && !inner.forceTileEntityRender() && skip(te, x, y, z)) return;
            inner.renderTileEntityAt(te, x, y, z, partialTicks, destroyStage);
        }

        /** Forge's batched path (renderers that draw into a shared buffer). */
        @Override
        public void renderTileEntityFast(TileEntity te, double x, double y, double z, float partialTicks, int destroyStage,
                                         net.minecraft.client.renderer.WorldRenderer buffer) {
            if (te != null && te.hasWorldObj() && !inner.forceTileEntityRender() && skip(te, x, y, z)) return;
            inner.renderTileEntityFast(te, x, y, z, partialTicks, destroyStage, buffer);
        }

        @Override
        public void setRendererDispatcher(TileEntityRendererDispatcher d) {
            super.setRendererDispatcher(d);
            inner.setRendererDispatcher(d);
        }

        @Override
        public boolean forceTileEntityRender() {
            return inner.forceTileEntityRender();
        }
    }

    static boolean skip(TileEntity te, double x, double y, double z) {
        PerformanceModule p = PerformanceModule.INSTANCE;
        double d2 = x * x + y * y + z * z;
        if (d2 < 36) return false;
        double max = p.blockEntityDistance.get();
        if (max < 64 && d2 > max * max) return true;
        return p.hideOccludedBlockEntities.on() && Occlusion.hidden(te.getPos());
    }

    /** Wraps every special renderer not wrapped yet (mods can add theirs during startup). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void ensureWrapped() {
        if (failed) return;
        long now = System.currentTimeMillis();
        if (now - lastWrap < 5000L) return;
        lastWrap = now;
        PerformanceModule p = PerformanceModule.INSTANCE;
        if (p.blockEntityDistance.get() >= 64 && !p.hideOccludedBlockEntities.on()) return; // nothing to do: stay vanilla
        try {
            TileEntityRendererDispatcher d = TileEntityRendererDispatcher.instance;
            Map map = d.mapSpecialRenderers;
            for (Object o : map.entrySet()) {
                Map.Entry e = (Map.Entry) o;
                Object r = e.getValue();
                if (r instanceof Wrapper || !(r instanceof TileEntitySpecialRenderer)) continue;
                Wrapper w = new Wrapper((TileEntitySpecialRenderer) r);
                w.setRendererDispatcher(d);
                e.setValue(w);
            }
        } catch (Throwable t) {
            failed = true;
            Mod1227.LOGGER.warn("1227 block entity culling unavailable", t);
        }
    }
}
