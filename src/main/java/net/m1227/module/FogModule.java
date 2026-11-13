package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Fog: remove Minecraft's distance fog, push it far away, or make it thick and coloured (like a
 * green or red ambience); optional clear water and lava. Only changes how far you see on your
 * screen with your render distance; it does not reveal anything the game would not draw anyway.
 */
public final class FogModule extends Module {
    private final Setting.Mode mode;
    private final Setting.Bool clearWater, customColor;
    private final Setting.Col color;
    private final Setting.Num thickness;

    public FogModule() {
        super("Fog", "Remove the fog, push it away, or tint it with your own colour.", Category.AMBIENCE, false);
        mode = add(new Setting.Mode("Fog", "None", "None", "Light", "Vanilla", "Thick"));
        thickness = add(new Setting.Num("Thick fog distance", 0.35, 0.1, 0.9, 0.05, "x")).showIf(() -> mode.is("Thick"));
        clearWater = add(new Setting.Bool("Clear water and lava", false));
        customColor = add(new Setting.Bool("Custom fog color", false));
        color = add(new Setting.Col("Fog color", 0x2E8B57)).showIf(() -> customColor.on());
    }

    private static boolean liquid(EntityViewRenderEvent e) {
        return e.block != null && (e.block.getMaterial() == Material.water || e.block.getMaterial() == Material.lava);
    }

    @SubscribeEvent
    public void onDensity(EntityViewRenderEvent.FogDensity e) {
        if (liquid(e)) {
            if (!clearWater.on()) return;
            // keep a little haze so the surface still reads as water
            GlStateManager.setFog(GL11.GL_EXP);
            e.density = e.block.getMaterial() == Material.lava ? 0.15f : 0.012f;
            e.setCanceled(true);
        } else if (mode.is("None")) {
            GlStateManager.setFog(GL11.GL_EXP);
            e.density = 0f;
            e.setCanceled(true);
        }
    }

    /** Fires after Minecraft sets its normal (linear) fog; moves the start and end. */
    @SubscribeEvent
    public void onFog(EntityViewRenderEvent.RenderFogEvent e) {
        if (liquid(e)) return;
        float far = e.farPlaneDistance;
        if (mode.is("Light")) {
            GlStateManager.setFogStart(far * 0.9f);
            GlStateManager.setFogEnd(far * 1.4f);
        } else if (mode.is("Thick")) {
            GlStateManager.setFogStart(0f);
            GlStateManager.setFogEnd(far * thickness.f());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onColor(EntityViewRenderEvent.FogColors e) {
        if (!customColor.on() || liquid(e)) return;
        int c = color.rgb();
        e.red = ((c >> 16) & 255) / 255f;
        e.green = ((c >> 8) & 255) / 255f;
        e.blue = (c & 255) / 255f;
    }
}
