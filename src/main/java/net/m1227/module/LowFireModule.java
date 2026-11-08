package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Draws the first-person fire overlay lower and more transparent (or hides it). */
public final class LowFireModule extends Module {
    private final Setting.Num height, opacity;

    public LowFireModule() {
        super("Low Fire", "Lower, see-through burning overlay.", Category.CAMERA, false);
        height = add(new Setting.Num("Lower by", 0.3, 0.0, 0.8, 0.05, ""));
        opacity = add(new Setting.Num("Opacity", 0.6, 0.0, 1.0, 0.05, ""));
    }

    @SubscribeEvent
    public void onOverlay(RenderBlockOverlayEvent e) {
        if (e.overlayType != RenderBlockOverlayEvent.OverlayType.FIRE) return;
        e.setCanceled(true);
        if (opacity.f() <= 0.01f) return;
        Minecraft mc = Minecraft.getMinecraft();
        Tessellator t = Tessellator.getInstance();
        WorldRenderer wr = t.getWorldRenderer();
        GlStateManager.color(1f, 1f, 1f, 0.9f * opacity.f());
        GlStateManager.depthFunc(519);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        TextureAtlasSprite s = mc.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/fire_layer_1");
        float u0 = s.getMinU(), u1 = s.getMaxU(), v0 = s.getMinV(), v1 = s.getMaxV();
        for (int i = 0; i < 2; i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-(i * 2 - 1) * 0.24f, -0.3f - height.f(), 0f);
            GlStateManager.rotate((i * 2 - 1) * 10f, 0f, 1f, 0f);
            wr.begin(7, DefaultVertexFormats.POSITION_TEX);
            wr.pos(-0.5, -0.5, -0.5).tex(u1, v1).endVertex();
            wr.pos(0.5, -0.5, -0.5).tex(u0, v1).endVertex();
            wr.pos(0.5, 0.5, -0.5).tex(u0, v0).endVertex();
            wr.pos(-0.5, 0.5, -0.5).tex(u1, v0).endVertex();
            t.draw();
            GlStateManager.popMatrix();
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
    }
}
