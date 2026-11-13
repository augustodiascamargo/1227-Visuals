package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Block Overlay: your own colour, thickness and fill for the outline of the block you look at. */
public final class BlockOverlayModule extends Module {
    private final Setting.Col color, fillColor;
    private final Setting.Num width, opacity, fillOpacity;
    private final Setting.Bool fill, chroma;

    public BlockOverlayModule() {
        super("Block Overlay", "Colour, thickness and fill of the block selection outline.", Category.AMBIENCE, false);
        color = add(new Setting.Col("Outline color", 0x8B5CFF));
        chroma = add(new Setting.Bool("Rainbow outline", false));
        width = add(new Setting.Num("Outline width", 2.5, 1.0, 6.0, 0.5, "px"));
        opacity = add(new Setting.Num("Outline opacity", 0.9, 0.1, 1.0, 0.05, ""));
        fill = add(new Setting.Bool("Fill", true));
        fillColor = add(new Setting.Col("Fill color", 0x8B5CFF)).showIf(() -> fill.on());
        fillOpacity = add(new Setting.Num("Fill opacity", 0.15, 0.02, 0.6, 0.01, "")).showIf(() -> fill.on());
    }

    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent e) {
        if (e.subID != 0 || e.target == null || e.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return;
        Minecraft mc = Minecraft.getMinecraft();
        BlockPos pos = e.target.getBlockPos();
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block.getMaterial() == Material.air || !mc.theWorld.getWorldBorder().contains(pos)) return;
        e.setCanceled(true);
        EntityPlayer p = e.player;
        float pt = e.partialTicks;
        double dx = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt, dy = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt,
                dz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
        block.setBlockBoundsBasedOnState(mc.theWorld, pos);
        AxisAlignedBB bb = block.getSelectedBoundingBox(mc.theWorld, pos).expand(0.002, 0.002, 0.002).offset(-dx, -dy, -dz);
        int c = chroma.on() ? java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000) / 4000f, 0.7f, 1f) & 0xFFFFFF : color.rgb();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);
        if (fill.on()) {
            int f = fillColor.rgb();
            GlStateManager.color(((f >> 16) & 255) / 255f, ((f >> 8) & 255) / 255f, (f & 255) / 255f, fillOpacity.f());
            GlStateManager.disableCull();
            box(bb);
            GlStateManager.enableCull();
        }
        GlStateManager.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, opacity.f());
        GL11.glLineWidth(width.f());
        RenderGlobal.drawSelectionBoundingBox(bb);
        GL11.glLineWidth(1f);
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static void box(AxisAlignedBB b) {
        GL11.glBegin(GL11.GL_QUADS);
        double[][] f = {
                {b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ},
                {b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ},
                {b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ},
                {b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ},
                {b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ}};
        for (double[] q : f) for (int i = 0; i < 12; i += 3) GL11.glVertex3d(q[i], q[i + 1], q[i + 2]);
        GL11.glEnd();
    }
}
