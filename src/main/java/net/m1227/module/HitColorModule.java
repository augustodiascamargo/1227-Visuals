package net.m1227.module;

import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.render.Silhouette;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Hit colour: while an entity flashes from being hurt, a tint in your colour is laid over it
 * (on top of Minecraft's red flash, which can't be removed without a core mod). It follows the
 * same hurt timer as vanilla, so it shows exactly when the game registers the hit.
 */
public final class HitColorModule extends Module {
    private final Setting.Mode who, colorMode;
    private final Setting.Col color;
    private final Setting.Num opacity;

    public HitColorModule() {
        super("Hit Color", "Tint hurt players and mobs with your own colour.", Category.COMBAT, false);
        who = add(new Setting.Mode("Entities", "Players", "Players", "Players and mobs"));
        colorMode = add(new Setting.Mode("Color", "Custom", "Custom", "Accent", "Rainbow"));
        color = add(new Setting.Col("Hit color", 0x8B5CFF)).showIf(() -> colorMode.is("Custom"));
        opacity = add(new Setting.Num("Opacity", 0.55, 0.1, 0.9, 0.05, ""));
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        int c = colorMode.is("Accent") ? ClientModule.accent()
                : colorMode.is("Rainbow") ? ParticleSystem.rainbow((float) (ParticleSystem.INSTANCE.time * 0.15)) : color.rgb();
        boolean any = false;
        List<Entity> list = mc.theWorld.loadedEntityList;
        for (int i = 0; i < list.size(); i++) {
            Entity en = list.get(i);
            if (!(en instanceof EntityLivingBase) || en.isInvisible()) continue;
            if (en == mc.thePlayer && mc.gameSettings.thirdPersonView == 0) continue;
            if (who.is("Players") && !(en instanceof EntityPlayer)) continue;
            EntityLivingBase l = (EntityLivingBase) en;
            if (l.hurtTime <= 0 || l.deathTime > 0 || l.getDistanceSqToEntity(mc.thePlayer) > 48 * 48) continue;
            float k = (l.hurtTime - e.partialTicks) / Math.max(1f, l.maxHurtTime);
            if (k <= 0f) continue;
            if (!any) {
                Silhouette.entityState(true);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.depthMask(false);
                any = true;
            }
            Silhouette.begin(c, opacity.f() * Math.min(1f, k * 1.4f));
            Silhouette.render(l, e.partialTicks, 0, 0, 0);
        }
        if (any) {
            Silhouette.end();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            Silhouette.entityState(false);
        }
    }
}
