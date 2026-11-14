package net.m1227.module;

import java.util.Map;
import java.util.UUID;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.m1227.social.LinkProtocol;
import net.m1227.social.SocialLink;
import net.m1227.vfx.Puppet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * 1227 Link: see the emotes, dances and cosmetics (Halo, Aura Hat, Wings) of other 1227 players,
 * and let them see yours. Needs the 1227 Relay plugin on the server (server/1227-relay); without
 * it nothing is exchanged and everything else in 1227 works as usual.
 *
 * Shared cosmetics are drawn with YOUR shape and style settings for that cosmetic, in THEIR colours.
 * Viewing is under your control: emotes, each cosmetic, the 1227 badge and the number of players
 * shown (nearest first) each have their own switch.
 */
public final class SocialModule extends Module {
    public static SocialModule INSTANCE;
    private final Setting.Bool shareEmotes, shareCosmetics, seeEmotes, seeCosmetics, seeHalo, seeHat, seeWings, badge;
    private final Setting.Num maxPlayers;
    private final Puppet.Pose pose = new Puppet.Pose();
    // nearest linked players drawn this frame
    private final int[] drawnIds = new int[64];
    private int drawnCount;
    private final double[] d2s = new double[64];

    public SocialModule() {
        super("1227 Link", "See other 1227 players' emotes and cosmetics (server needs the 1227 Relay plugin).", Category.COSMETICS, true);
        INSTANCE = this;
        shareEmotes = add(new Setting.Bool("Share my emotes", true));
        shareCosmetics = add(new Setting.Bool("Share my cosmetics", true));
        seeEmotes = add(new Setting.Bool("See other players' emotes", true));
        seeCosmetics = add(new Setting.Bool("See other players' cosmetics", true));
        seeHalo = add(new Setting.Bool("Halos", true)).showIf(() -> seeCosmetics.on());
        seeHat = add(new Setting.Bool("Aura hats", true)).showIf(() -> seeCosmetics.on());
        seeWings = add(new Setting.Bool("Wings", true)).showIf(() -> seeCosmetics.on());
        maxPlayers = add(new Setting.Num("Max players with shared cosmetics", 8, 1, 32, 1, ""));
        badge = add(new Setting.Bool("1227 badge above other 1227 players", true));
        add(new Setting.Action("Connection status", this::status));
    }

    private void status() {
        SocialLink l = SocialLink.INSTANCE;
        String s = l.linked ? "Linked" + (l.relayName.isEmpty() ? "" : " (" + l.relayName + ")") + ": " + l.remotes.size() + " other 1227 players"
                : Minecraft.getMinecraft().isSingleplayer() ? "Singleplayer: nothing to share" : "This server has no 1227 Relay plugin";
        Notifier.post(Notifier.Kind.SYSTEM, "1227 Link", s, l.linked ? Notifier.GOOD : Notifier.INFO, 3200);
    }

    public boolean shareEmotes() {
        return isEnabled() && shareEmotes.on();
    }

    public boolean shareCosmetics() {
        return isEnabled() && shareCosmetics.on();
    }

    private boolean category(int flag) {
        return flag == LinkProtocol.F_HALO ? seeHalo.on() : flag == LinkProtocol.F_HAT ? seeHat.on() : seeWings.on();
    }

    /** True when this frame 1227 Link draws that cosmetic on that player. */
    public boolean draws(EntityPlayer p, int flag) {
        if (!isEnabled() || !seeCosmetics.on() || !category(flag)) return false;
        SocialLink.Remote r = SocialLink.INSTANCE.remotes.get(p.getUniqueID());
        if (r == null || (r.state.flags & flag) == 0) return false;
        int id = p.getEntityId();
        for (int i = 0; i < drawnCount; i++) if (drawnIds[i] == id) return true;
        return false;
    }

    // ------------------------------------------------------------------ cosmetics + badge

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        drawnCount = 0;
        if (mc.theWorld == null || mc.thePlayer == null || !SocialLink.INSTANCE.linked) return;
        // nearest linked players first (insertion into a small fixed array)
        double[] d2s = this.d2s;
        int limit = Math.min(drawnIds.length, maxPlayers.i());
        for (Map.Entry<UUID, SocialLink.Remote> en : SocialLink.INSTANCE.remotes.entrySet()) {
            EntityPlayer p = mc.theWorld.getPlayerEntityByUUID(en.getKey());
            if (p == null || p == mc.thePlayer || p.isInvisible()) continue;
            double d2 = p.getDistanceSqToEntity(mc.thePlayer);
            if (d2 > 64 * 64) continue;
            int k = drawnCount < limit ? drawnCount++ : limit;
            if (k == limit) {
                if (d2 >= d2s[limit - 1]) continue;
                k = limit - 1;
            }
            while (k > 0 && d2s[k - 1] > d2) {
                d2s[k] = d2s[k - 1];
                drawnIds[k] = drawnIds[k - 1];
                k--;
            }
            d2s[k] = d2;
            drawnIds[k] = p.getEntityId();
        }
        float pt = e.partialTicks;
        for (int i = 0; i < drawnCount; i++) {
            net.minecraft.entity.Entity en = mc.theWorld.getEntityByID(drawnIds[i]);
            if (!(en instanceof EntityPlayer)) continue;
            EntityPlayer p = (EntityPlayer) en;
            SocialLink.Remote r = SocialLink.INSTANCE.remotes.get(p.getUniqueID());
            if (r == null) continue;
            int f = r.state.flags;
            int[] c = r.state.colors;
            try {
                if (seeCosmetics.on()) {
                    if ((f & LinkProtocol.F_HALO) != 0 && seeHalo.on() && HaloModule.INSTANCE != null) {
                        RemoteLook.set(c[0], c[1]);
                        HaloModule.INSTANCE.drawRemote(p, pt);
                    }
                    if ((f & LinkProtocol.F_HAT) != 0 && seeHat.on() && AuraHatModule.INSTANCE != null) {
                        RemoteLook.set(c[2], c[3]);
                        AuraHatModule.INSTANCE.drawRemote(p, pt);
                    }
                    if ((f & LinkProtocol.F_WINGS) != 0 && seeWings.on() && WingsModule.INSTANCE != null) {
                        RemoteLook.set(c[4], c[5]);
                        WingsModule.INSTANCE.drawRemote(p, pt);
                    }
                }
            } finally {
                RemoteLook.clear();
            }
            if (badge.on()) badge(mc, p, pt);
        }
    }

    /** A small "1227" tag above the name of another 1227 player. */
    private static void badge(Minecraft mc, EntityPlayer p, float pt) {
        RenderManager rm = mc.getRenderManager();
        double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt - rm.viewerPosX;
        double y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt - rm.viewerPosY + p.height + (p.isSneaking() ? 0.55 : 0.82);
        double z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt - rm.viewerPosZ;
        if (x * x + y * y + z * z > 32 * 32) return;
        FontRenderer fr = mc.fontRendererObj;
        String s = "1227";
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-rm.playerViewY, 0f, 1f, 0f);
            GlStateManager.rotate(rm.playerViewX, 1f, 0f, 0f);
            float k = 0.016f;
            GlStateManager.scale(-k, -k, k);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            int w = fr.getStringWidth(s);
            net.m1227.ui.Gfx.roundedRect(-w / 2f - 3, -1.5f, w + 6, 11, 3, 0x90101218);
            fr.drawString(s, -w / 2, 0, 0xFFB79CFF);
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableLighting();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }
    }

    // ------------------------------------------------------------------ emotes

    @SubscribeEvent
    public void onPlayer(RenderPlayerEvent.Pre e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = e.entityPlayer;
        if (p == mc.thePlayer || !seeEmotes.on() || !SocialLink.INSTANCE.linked) return;
        SocialLink.Remote r = SocialLink.INSTANCE.remotes.get(p.getUniqueID());
        if (r == null || r.emote < 0) return;
        float t = (System.nanoTime() - r.emoteStart) / 1e9f;
        // the same rules as your own emotes: moving ends it, one-shot emotes end after ~3 s
        if (!r.emoteAnchored) {
            r.emoteX = p.posX;
            r.emoteZ = p.posZ;
            r.emoteAnchored = true;
        }
        double dx = p.posX - r.emoteX, dz = p.posZ - r.emoteZ;
        if (dx * dx + dz * dz > 0.09 || (!EmotesModule.loops(r.emote) && t > 3.2f) || t > 600f || p.hurtTime > 0) {
            r.emote = -1;
            return;
        }
        e.setCanceled(true);
        EmotesModule.pose(r.emote, t, pose.reset());
        float yaw = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * e.partialRenderTick;
        Puppet.render(p, e.x, e.y, e.z, yaw, pose, e.partialRenderTick);
    }
}
