package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.Reflect;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Ui;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.NetworkManager;

/**
 * Draws kept vanilla screens in the 1227 style: Connecting, Loading terrain and the Yes/No
 * confirmations (including "open this link?"). Minecraft still owns the screen and its buttons
 * (clicks, timers, cancelling), 1227 only draws it. Buttons are drawn exactly where Minecraft
 * placed them. If a text cannot be read the screen is left as it is.
 */
final class Restyle {
    private Restyle() {}

    static boolean handles(GuiScreen g) {
        if (g == null) return false;
        Class<?> c = g.getClass();
        return c == GuiConnecting.class || c == GuiDownloadTerrain.class || c == GuiYesNo.class || c == GuiConfirmOpenLink.class;
    }

    /** Returns true when it drew the screen (the caller then skips Minecraft's drawing). */
    static boolean draw(GuiScreen g, List<GuiButton> buttons, int mx, int my) {
        Minecraft mc = Minecraft.getMinecraft();
        String title, line1 = null, line2 = null;
        boolean spinner = false;
        if (g instanceof GuiConnecting) {
            NetworkManager nm = Reflect.get(g, GuiConnecting.class, NetworkManager.class, 0);
            title = nm == null ? "Connecting" : "Logging in";
            ServerData sd = mc.getCurrentServerData();
            line1 = sd == null ? null : sd.serverName;
            line2 = sd == null ? null : sd.serverIP;
            spinner = true;
        } else if (g instanceof GuiDownloadTerrain) {
            title = "Loading terrain";
            line1 = "Getting the world ready";
            spinner = true;
        } else {
            String a = Reflect.get(g, GuiYesNo.class, String.class, 0);
            String b = Reflect.get(g, GuiYesNo.class, String.class, 1);
            if (a == null) return false;
            title = a;
            line1 = b;
        }
        int w = g.width, h = g.height;
        try {
            Gfx.smoothFont(new ScaledResolution(mc).getScaleFactor());
            MenuBackground.draw(w, h, mx, my, mc.theWorld != null);
            float cw = Math.min(320, w - 24);
            List<String> l1 = line1 == null ? null : mc.fontRendererObj.listFormattedStringToWidth(line1, (int) cw - 24);
            float textH = 36 + (spinner ? 20 : 0) + (l1 == null ? 0 : l1.size() * 11) + (line2 != null ? 10 : 0);
            // the card wraps the text and the buttons where Minecraft placed them
            float bTop = Float.MAX_VALUE, bBottom = 0;
            for (GuiButton b : buttons) {
                if (!b.visible) continue;
                bTop = Math.min(bTop, b.yPosition);
                bBottom = Math.max(bBottom, b.yPosition + 20);
            }
            float cx = (w - cw) / 2f, cy, ch;
            if (bBottom > 0) {
                cy = Math.max(6, bTop - textH - 4);
                ch = bBottom + 12 - cy;
            } else {
                ch = textH;
                cy = Math.max(10, h / 2f - ch / 2f);
            }
            float minX = cx;
            for (GuiButton b : buttons) if (b.visible) minX = Math.min(minX, b.xPosition - 12);
            if (minX < cx) {
                cw += (cx - minX) * 2;
                cx = minX;
            }
            Ui.glassCard(cx, cy, cw, ch);
            float y = cy + 16;
            if (spinner) {
                Ui.spinner(cx + cw / 2f, y + 6, 7, DS.accent());
                y += 20;
            }
            Ui.textCenter(Gfx.trim(title, cw - 20), cx + cw / 2f, y, DS.T_LABEL, DS.TEXT);
            y += 16;
            if (l1 != null) {
                // vanilla font here: these lines can carry Minecraft colour codes
                for (String s : l1) {
                    mc.fontRendererObj.drawString(s, (int) (cx + cw / 2f - mc.fontRendererObj.getStringWidth(s) / 2f), (int) y, 0xFFB8BCC8);
                    y += 11;
                }
            }
            if (line2 != null) Ui.textCenter(line2, cx + cw / 2f, y, DS.T_SMALL, DS.FAINT);
            for (GuiButton b : buttons) {
                if (!b.visible) continue;
                float bw = b.getButtonWidth(), bh = 20;
                boolean hv = b.enabled && mx >= b.xPosition && my >= b.yPosition && mx < b.xPosition + bw && my < b.yPosition + bh;
                int fill = hv ? DS.SURFACE_3 : DS.SURFACE_2;
                if (b.id == 0 && !(g instanceof GuiConnecting)) fill = hv ? Gfx.mix(DS.accent(), 0xFFFFFFFF, 0.15f) : DS.accent();
                if (!b.enabled) fill = Gfx.alpha(fill, 0.45f);
                Gfx.roundedRect(b.xPosition, b.yPosition, bw, bh, DS.R_MD, fill);
                Gfx.roundedOutline(b.xPosition, b.yPosition, bw, bh, DS.R_MD, 0.6f, DS.LINE);
                String label = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(b.displayString);
                Ui.textCenter(label == null ? "" : label, b.xPosition + bw / 2f, b.yPosition + 6.4f, DS.T_BODY,
                        b.enabled ? DS.TEXT : Gfx.alpha(DS.TEXT, 0.4f));
            }
        } finally {
            Gfx.vanillaFont();
        }
        return true;
    }
}
