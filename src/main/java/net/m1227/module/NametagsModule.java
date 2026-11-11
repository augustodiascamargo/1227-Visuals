package net.m1227.module;

import java.util.Locale;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Restyled player name tags: rounded background, team-coloured name,
 * health and optional ping / health bar. They follow the same visibility
 * rules as vanilla (team settings, invisibility, sneaking) and, unlike
 * vanilla, are depth-tested: they never show through walls.
 */
public final class NametagsModule extends Module {
    private final Setting.Mode health, bg;
    private final Setting.Bool bar, ping, self, constant;
    private final Setting.Num scale, opacity;

    public NametagsModule() {
        super("Nametags", "Cleaner player name tags with health.", Category.HUD, false);
        health = add(new Setting.Mode("Health", "Number", "Number", "Hearts", "Off"));
        bar = add(new Setting.Bool("Health bar", true));
        ping = add(new Setting.Bool("Ping", false));
        bg = add(new Setting.Mode("Background", "Rounded", "Rounded", "Square", "None"));
        opacity = add(new Setting.Num("Background opacity", 0.5, 0.0, 1.0, 0.05, "")).showIf(() -> !bg.is("None"));
        scale = add(new Setting.Num("Size", 1.0, 0.5, 2.0, 0.05, "x"));
        constant = add(new Setting.Bool("Same size at any distance", false));
        self = add(new Setting.Bool("Show your own (third person)", false));
    }

    private static boolean teamAllows(EntityPlayer p, EntityPlayer me) {
        Team t = p.getTeam();
        if (t == null) return true;
        Team mine = me.getTeam();
        switch (t.getNameTagVisibility()) {
            case NEVER:
                return false;
            case HIDE_FOR_OTHER_TEAMS:
                return mine == null || t.isSameTeam(mine);
            case HIDE_FOR_OWN_TEAM:
                return mine == null || !t.isSameTeam(mine);
            default:
                return true;
        }
    }

    @SubscribeEvent
    @SuppressWarnings("rawtypes")
    public void onName(RenderLivingEvent.Specials.Pre e) {
        if (!(e.entity instanceof EntityPlayer)) return;
        if (net.m1227.vfx.PreviewStage.rendering) {
            e.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = (EntityPlayer) e.entity;
        if (mc.thePlayer == null) return;
        e.setCanceled(true); // we take over players' tags (vanilla keeps mobs, armor stands, etc.)
        boolean isSelf = p == mc.getRenderViewEntity();
        if (isSelf && !self.on()) return;
        if (!Minecraft.isGuiEnabled() || p.isInvisibleToPlayer(mc.thePlayer) || p.riddenByEntity != null) return;
        if (!isSelf && !teamAllows(p, mc.thePlayer)) return;
        double dist = p.getDistanceToEntity(mc.getRenderViewEntity());
        if (dist > (p.isSneaking() ? 32 : 64)) return;
        draw(mc, p, e.x, e.y + p.height + 0.5, e.z, dist, p.isSneaking());
    }

    private void draw(Minecraft mc, EntityPlayer p, double x, double y, double z, double dist, boolean sneak) {
        RenderManager rm = mc.getRenderManager();
        FontRenderer fr = mc.fontRendererObj;
        String name = p.getDisplayName().getFormattedText();
        float hp = p.getHealth() + p.getAbsorptionAmount(), max = Math.max(1f, p.getMaxHealth());
        String hs = "";
        if (health.is("Number")) hs = String.format(Locale.ROOT, "%.1f", hp / 2f) + "❤";
        else if (health.is("Hearts")) hs = String.valueOf((int) Math.ceil(hp / 2f)) + "❤";
        String ps = "";
        if (ping.on() && mc.getNetHandler() != null) {
            NetworkPlayerInfo ni = mc.getNetHandler().getPlayerInfo(p.getUniqueID());
            if (ni != null) ps = ni.getResponseTime() + "ms";
        }
        float f = hp / max;
        int hc = f > 0.5f ? Gfx.mix(0xFFFFC857, 0xFF3DDC84, (f - 0.5f) * 2f) : Gfx.mix(0xFFFF5A6A, 0xFFFFC857, f * 2f);
        float nw = fr.getStringWidth(name), hw = hs.isEmpty() ? 0 : fr.getStringWidth(hs) + 6, pw = ps.isEmpty() ? 0 : fr.getStringWidth(ps) + 6;
        float w = nw + hw + pw + 8, h = bar.on() ? 13 : 11;
        float s = 0.026f * scale.f();
        if (constant.on()) s *= (float) Math.max(1.0, dist / 6.0);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GL11Normal();
        GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(rm.playerViewX * (mc.gameSettings.thirdPersonView == 2 ? -1f : 1f), 1f, 0f, 0f);
        GlStateManager.scale(-s, -s, s);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        float x0 = -w / 2f, y0 = -h + 1;
        float al = sneak ? 0.45f : 1f;
        if (!bg.is("None")) {
            int c = Gfx.alpha(0xFF0B0C11, opacity.f() * al);
            if (bg.is("Rounded")) Gfx.roundedRect(x0, y0, w, h, 3f, c);
            else Gfx.rect(x0, y0, w, h, c);
        }
        int ta = (int) (255 * al) << 24;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        fr.drawString(name, (int) (x0 + 4), (int) (y0 + 2), ta | 0xFFFFFF);
        float cx = x0 + 4 + nw + 6;
        if (!hs.isEmpty()) {
            fr.drawString(hs, (int) cx, (int) (y0 + 2), ta | (hc & 0xFFFFFF));
            cx += hw;
        }
        if (!ps.isEmpty()) fr.drawString(ps, (int) cx, (int) (y0 + 2), ta | 0xA9ADBA);
        if (bar.on()) {
            GlStateManager.enableBlend();
            Gfx.rect(x0 + 2, y0 + h - 2.5f, w - 4, 1.5f, Gfx.alpha(0xFF000000, 0.5f * al));
            Gfx.rect(x0 + 2, y0 + h - 2.5f, (w - 4) * Math.min(1f, f), 1.5f, Gfx.alpha(hc, al));
        }
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    private static void GL11Normal() {
        org.lwjgl.opengl.GL11.glNormal3f(0f, 1f, 0f);
    }
}
