package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.boss.BossStatus;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Boss bar restyle: slim rounded bar with your accent (or the classic purple), optional
 * percentage, adjustable height from the top, scale, or hidden entirely. Same data as vanilla
 * (servers often use the boss bar for announcements).
 */
public final class BossBarModule extends Module {
    private final Setting.Mode style;
    private final Setting.Bool percent, hide;
    private final Setting.Num offsetY, scale, width;

    public BossBarModule() {
        super("Boss Bar", "Restyle, move, resize or hide the boss bar.", Category.HUD, false);
        hide = add(new Setting.Bool("Hide completely", false));
        style = add(new Setting.Mode("Color", "Accent", "Accent", "Classic purple", "Health (green to red)")).showIf(() -> !hide.on());
        percent = add(new Setting.Bool("Show percentage", false)).showIf(() -> !hide.on());
        offsetY = add(new Setting.Num("Distance from top", 12, 2, 80, 1, "px")).showIf(() -> !hide.on());
        width = add(new Setting.Num("Bar width", 182, 100, 300, 2, "px")).showIf(() -> !hide.on());
        scale = add(new Setting.Num("Text scale", 1.0, 0.6, 1.5, 0.05, "x")).showIf(() -> !hide.on());
    }

    @SubscribeEvent
    public void onBoss(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH) return;
        e.setCanceled(true);
        if (BossStatus.bossName == null || BossStatus.statusBarTime <= 0) return;
        --BossStatus.statusBarTime; // vanilla counts this down while drawing; keep the same behaviour
        if (hide.on()) return;
        Minecraft mc = Minecraft.getMinecraft();
        int sw = e.resolution.getScaledWidth();
        float w = width.f(), x = sw / 2f - w / 2f, y = offsetY.f();
        float hp = Math.max(0f, Math.min(1f, BossStatus.healthScale));
        int c = style.is("Classic purple") ? 0xFFB23CE0 : style.is("Accent") ? 0xFF000000 | ClientModule.accent()
                : Gfx.mix(0xFFFF4A4A, 0xFF3DDC97, hp);
        String name = BossStatus.bossName + (percent.on() ? "  §7" + Math.round(hp * 100) + "%" : "");
        float s = scale.f();
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(s, s, 1f);
        mc.fontRendererObj.drawStringWithShadow(name, (sw / 2f) / s - mc.fontRendererObj.getStringWidth(name) / 2f, (y - 10) / s, 0xFFFFFFFF);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        Gfx.roundedRect(x, y, w, 5, 2.5f, 0x90101218);
        if (hp > 0f) Gfx.roundedRect(x, y, Math.max(5f, w * hp), 5, 2.5f, c);
        Gfx.roundedOutline(x, y, w, 5, 2.5f, 0.5f, 0x30FFFFFF);
        net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
