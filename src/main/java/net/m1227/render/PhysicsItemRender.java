package net.m1227.render;

import java.util.Random;
import net.m1227.module.ItemPhysicsModule;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;

/**
 * Dropped items with "physics" look: items lie flat on the ground at a random angle, blocks sit on
 * the ground instead of floating and bobbing, and both tumble while falling. Only the drawing
 * changes; the item entity, pickup and everything else stay vanilla. When the Item Physics module
 * is off this behaves exactly like the vanilla renderer.
 */
public final class PhysicsItemRender extends RenderEntityItem {
    private final RenderItem items;
    private final Random rand = new Random();

    public PhysicsItemRender(RenderManager manager, RenderItem items) {
        super(manager, items);
        this.items = items;
    }

    private static int copies(ItemStack st) {
        int n = st.stackSize;
        return n > 48 ? 5 : n > 32 ? 4 : n > 16 ? 3 : n > 1 ? 2 : 1;
    }

    @Override
    public void doRender(EntityItem e, double x, double y, double z, float yaw, float pt) {
        if (net.m1227.client.perf.PerfSystem.cull(e)) return; // Performance › Entity render distance
        ItemPhysicsModule m = ItemPhysicsModule.INSTANCE;
        ItemStack st = e.getEntityItem();
        if (m == null || !m.isEnabled() || st == null || st.getItem() == null) {
            super.doRender(e, x, y, z, yaw, pt);
            return;
        }
        IBakedModel model = items.getItemModelMesher().getItemModel(st);
        boolean block = model.isGui3d();
        rand.setSeed(e.getEntityId() * 31L + 187L);
        float base = rand.nextFloat() * 360f;
        boolean air = !e.onGround;
        float age = e.getAge() + pt;
        float scaleY = model.getItemCameraTransforms().getTransform(ItemCameraTransforms.TransformType.GROUND).scale.y;

        bindEntityTexture(e);
        renderManager.renderEngine.getTexture(TextureMap.locationBlocksTexture).setBlurMipmap(false, false);
        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y + (block ? 0.25f * scaleY : 0.035f), (float) z);
        GlStateManager.rotate(base, 0f, 1f, 0f);
        if (air && m.tumble()) {
            float spin = age * 14f * m.spinSpeed() * (e.isInWater() ? 0.3f : 1f);
            GlStateManager.rotate(spin, 1f, 0f, 0.35f);
        } else if (!block) {
            GlStateManager.rotate(90f, 1f, 0f, 0f); // lie flat
        }
        int n = copies(st);
        for (int i = 0; i < n; i++) {
            GlStateManager.pushMatrix();
            if (i > 0) {
                float ox = (rand.nextFloat() * 2f - 1f) * 0.15f, oz = (rand.nextFloat() * 2f - 1f) * 0.15f;
                if (block) GlStateManager.translate(ox, 0f, oz);
                else GlStateManager.translate(ox, oz, -0.03f * i); // after the flat rotation, -Z points up
            }
            IBakedModel tm = ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.GROUND);
            items.renderItem(st, tm);
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        bindEntityTexture(e);
        renderManager.renderEngine.getTexture(TextureMap.locationBlocksTexture).restoreLastBlurMipmap();
    }
}
