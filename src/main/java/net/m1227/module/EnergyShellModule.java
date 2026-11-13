package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.CosmeticMesh;
import net.m1227.vfx.MaterialShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Cosmetic energy around the body: a see-through shell that glows at its edges (plasma, aurora,
 * galaxy, electric...), rings orbiting the body, or a Saturn ring around the waist. Only on your
 * screen, never through walls (depth tested).
 */
public final class EnergyShellModule extends Module {
    private final Setting.Mode style, material;
    private final Setting.Col color1, color2;
    private final Setting.Num size, opacity, speed, rings;
    private final Setting.Bool others;

    public EnergyShellModule() {
        super("Energy Shell", "A glowing energy shell or orbiting rings around the body.", Category.COSMETICS, false);
        style = add(new Setting.Mode("Style", "Shell", "Shell", "Orbit Rings", "Shell + Rings", "Saturn Ring"));
        material = add(new Setting.Mode("Material", "Plasma", MaterialShader.MATERIALS));
        color1 = add(new Setting.Col("Color", 0x6A5CFF));
        color2 = add(new Setting.Col("Second color", 0x3DF5FF));
        size = add(new Setting.Num("Size", 1.0, 0.6, 1.6, 0.05, "x"));
        opacity = add(new Setting.Num("Opacity", 0.7, 0.1, 1.0, 0.05, ""));
        speed = add(new Setting.Num("Speed", 1.0, 0.0, 3.0, 0.1, "x"));
        rings = add(new Setting.Num("Rings", 3, 1, 5, 1, ""));
        others = add(new Setting.Bool("Show on other players", false));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.gameSettings.thirdPersonView != 0 && !mc.thePlayer.isInvisible()) draw(mc, mc.thePlayer, e.partialTicks);
        if (others.on()) {
            List<EntityPlayer> list = mc.theWorld.playerEntities;
            for (int i = 0; i < list.size(); i++) {
                EntityPlayer p = list.get(i);
                if (p == mc.thePlayer || p.isInvisible() || p.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) continue;
                draw(mc, p, e.partialTicks);
            }
        }
    }

    private void draw(Minecraft mc, EntityPlayer p, float pt) {
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - mc.getRenderManager().viewerPosX;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - mc.getRenderManager().viewerPosY;
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - mc.getRenderManager().viewerPosZ;
        double t = System.nanoTime() / 1e9 * speed.get() + p.getEntityId() * 0.37;
        float sc = size.f(), a = opacity.f(), h = p.height + 0.15f;
        int c0 = color1.rgb(), c1 = color2.rgb();
        String st = style.get();
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.enableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(false);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        int mat = MaterialShader.index(material);

        if (st.equals("Shell") || st.equals("Shell + Rings")) {
            boolean shaded = MaterialShader.begin(mat, t, c0, c1, 3f, 1f, 0f);
            float breathe = 1f + 0.03f * (float) Math.sin(t * 2.0);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, -0.08f, 0f);
            CosmeticMesh.capsule(0.55f * sc * breathe, h + 0.08f, shaded, c0, c1, a * (shaded ? 1f : 0.6f));
            GlStateManager.popMatrix();
            if (shaded) MaterialShader.end();
        }
        if (st.equals("Orbit Rings") || st.equals("Shell + Rings")) {
            boolean shaded = MaterialShader.begin(mat, t, c0, c1, 6f, 0.3f, 0.3f);
            int n = rings.i();
            for (int i = 0; i < n; i++) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0f, h * (0.25f + 0.5f * (n == 1 ? 0.5f : i / (float) (n - 1))), 0f);
                GlStateManager.rotate((float) (t * 40.0 + i * 360.0 / n), 0f, 1f, 0f);
                GlStateManager.rotate((float) (25 + 20 * Math.sin(t * 0.7 + i)), 1f, 0f, 0f);
                CosmeticMesh.torus(0.62f * sc, 0.018f * sc, shaded, c0, c1, a);
                GlStateManager.popMatrix();
            }
            if (shaded) MaterialShader.end();
        }
        if (st.equals("Saturn Ring")) {
            boolean shaded = MaterialShader.begin(mat, t, c0, c1, 5f, 0.2f, 0.4f);
            GlStateManager.pushMatrix();
            GlStateManager.translate(0f, h * 0.55f + 0.05f * (float) Math.sin(t * 1.3), 0f);
            GlStateManager.rotate((float) (12 * Math.sin(t * 0.5)), 1f, 0f, 0f);
            GlStateManager.rotate((float) (t * 25.0), 0f, 1f, 0f);
            for (int k = 0; k < 3; k++) {
                // three flat bands side by side make a wide planetary ring
                GlStateManager.pushMatrix();
                GlStateManager.scale(1f, 0.25f, 1f);
                CosmeticMesh.torus((0.7f + k * 0.09f) * sc, 0.04f * sc, shaded, c0, c1, a * (1f - k * 0.25f));
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
            if (shaded) MaterialShader.end();
        }

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.popMatrix();
    }
}
