package net.m1227.weapons;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.m1227.Mod1227;
import net.m1227.module.WeaponModelsModule;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Third person: wraps each player renderer's held-item layer so a sword is drawn with your
 * weapon model (yours, and other players' when you turn that on). It goes through exactly the
 * same arm and hand transforms as Minecraft's own layer; anything that is not a replaced sword
 * is passed to the original layer untouched. Purely visual, on your screen only.
 */
public final class HeldWeaponLayer implements LayerRenderer<EntityLivingBase> {
    private final RendererLivingEntity<?> renderer;
    private final LayerRenderer<EntityLivingBase> original;

    private HeldWeaponLayer(RendererLivingEntity<?> renderer, LayerRenderer<EntityLivingBase> original) {
        this.renderer = renderer;
        this.original = original;
    }

    @Override
    public void doRenderLayer(EntityLivingBase e, float a, float b, float pt, float c, float d, float f, float scale) {
        ItemStack st = e.getHeldItem();
        WeaponModelsModule wm = WeaponModelsModule.INSTANCE;
        if (wm == null || st == null || !wm.thirdPersonFor(e, st) || !(renderer.getMainModel() instanceof ModelBiped)) {
            original.doRenderLayer(e, a, b, pt, c, d, f, scale);
            return;
        }
        GlStateManager.pushMatrix();
        try {
            if (renderer.getMainModel().isChild) {
                GlStateManager.translate(0.0F, 0.625F, 0.0F);
                GlStateManager.rotate(-20.0F, -1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.5F, 0.5F, 0.5F);
            }
            ((ModelBiped) renderer.getMainModel()).postRenderArm(0.0625F);
            GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);
            if (e.isSneaking()) GlStateManager.translate(0.0F, 0.203125F, 0.0F);
            wm.renderThirdPerson(e, st);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return original.shouldCombineTextures();
    }

    // ------------------------------------------------------------------ install

    private static final Map<Object, Boolean> DONE = new WeakHashMap<>();
    private static Field layers;
    private static boolean broken;

    /** Installs the wrapper into a player renderer the first time it draws. */
    public static final class Hook {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onRender(RenderPlayerEvent.Pre e) {
            if (broken || DONE.containsKey(e.renderer)) return;
            DONE.put(e.renderer, Boolean.TRUE);
            install(e.renderer);
        }
    }

    @SuppressWarnings("unchecked")
    static void install(RendererLivingEntity<?> r) {
        try {
            if (layers == null) {
                // the only List field of RendererLivingEntity is its layer list
                for (Field f : RendererLivingEntity.class.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        layers = f;
                        break;
                    }
                }
                if (layers == null) throw new IllegalStateException("layer list not found");
            }
            List<LayerRenderer<EntityLivingBase>> list = (List<LayerRenderer<EntityLivingBase>>) layers.get(r);
            for (int i = 0; i < list.size(); i++) {
                LayerRenderer<EntityLivingBase> l = list.get(i);
                if (l != null && l.getClass() == LayerHeldItem.class) {
                    list.set(i, new HeldWeaponLayer(r, l));
                    return;
                }
            }
        } catch (Throwable t) {
            broken = true;
            Mod1227.LOGGER.warn("1227: third-person weapon models unavailable: " + t);
        }
    }
}
