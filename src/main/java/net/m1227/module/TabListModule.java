package net.m1227.module;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A cleaner tab list: rounded panel, player heads, ping in milliseconds (coloured) instead of
 * bars, your own name highlighted. It lists exactly the players the server sends, in the same
 * order as vanilla (team, then name), with the server's header and footer.
 */
public final class TabListModule extends Module {
    private final Setting.Bool heads, pingNumbers, highlightSelf, headerFooter;
    private final Setting.Num opacity, maxRows;

    private static Field sortField, footerField, headerField;
    private static boolean reflected;

    public TabListModule() {
        super("Tab List", "Cleaner tab list with heads and ping in ms.", Category.HUD, false);
        heads = add(new Setting.Bool("Player heads", true));
        pingNumbers = add(new Setting.Bool("Ping as numbers", true));
        highlightSelf = add(new Setting.Bool("Highlight yourself", true));
        headerFooter = add(new Setting.Bool("Server header and footer", true));
        opacity = add(new Setting.Num("Background opacity", 0.7, 0.2, 1.0, 0.05, ""));
        maxRows = add(new Setting.Num("Rows per column", 20, 10, 30, 1, ""));
    }

    /** Vanilla's sort order and the header/footer are private fields; found once by type. */
    private static void reflect() {
        if (reflected) return;
        reflected = true;
        try {
            List<Field> chat = new ArrayList<Field>();
            for (Field f : GuiPlayerTabOverlay.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && com.google.common.collect.Ordering.class.isAssignableFrom(f.getType())) sortField = f;
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == IChatComponent.class) chat.add(f);
            }
            if (sortField != null) sortField.setAccessible(true);
            if (chat.size() == 2) {
                footerField = chat.get(0); // declared order in 1.8.9: footer, header
                headerField = chat.get(1);
                footerField.setAccessible(true);
                headerField.setAccessible(true);
            }
        } catch (Throwable ignored) {
            sortField = footerField = headerField = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<NetworkPlayerInfo> sorted(Collection<NetworkPlayerInfo> c) {
        reflect();
        try {
            if (sortField != null) return ((com.google.common.collect.Ordering<NetworkPlayerInfo>) sortField.get(null)).sortedCopy(c);
        } catch (Throwable ignored) {
            // fall through
        }
        List<NetworkPlayerInfo> l = new ArrayList<NetworkPlayerInfo>(c);
        java.util.Collections.sort(l, (a, b) -> a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName()));
        return l;
    }

    private static String chat(Field f, Object o) {
        try {
            Object v = f == null ? null : f.get(o);
            return v == null ? null : ((IChatComponent) v).getFormattedText();
        } catch (Throwable t) {
            return null;
        }
    }

    @SubscribeEvent
    public void onTab(RenderGameOverlayEvent.Pre e) {
        if (e.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) return;
        e.setCanceled(true);
        draw(mc, e.resolution.getScaledWidth());
    }

    private static int pingColor(int ms) {
        return ms < 0 ? 0xFF8A8F9C : ms < 80 ? 0xFF3DDC97 : ms < 150 ? 0xFFE7D24A : ms < 300 ? 0xFFFF9A3D : 0xFFFF5A5A;
    }

    private void draw(Minecraft mc, int sw) {
        FontRenderer fr = mc.fontRendererObj;
        GuiPlayerTabOverlay overlay = mc.ingameGUI.getTabList();
        List<NetworkPlayerInfo> list = sorted(mc.getNetHandler().getPlayerInfoMap());
        if (list.size() > 80) list = list.subList(0, 80);
        int rows = Math.max(1, Math.min(maxRows.i(), list.size()));
        int cols = (list.size() + rows - 1) / rows;
        int nameW = 0, pingW = pingNumbers.on() ? fr.getStringWidth("999ms") : 11;
        for (NetworkPlayerInfo p : list) nameW = Math.max(nameW, fr.getStringWidth(overlay.getPlayerName(p)));
        int cellW = (heads.on() ? 11 : 0) + nameW + 6 + pingW;
        cellW = Math.min(cellW, (sw - 20) / Math.max(1, cols) - 4);
        int totalW = cols * (cellW + 4) - 4;

        List<String> head = new ArrayList<String>(), foot = new ArrayList<String>();
        if (headerFooter.on()) {
            String h = chat(headerField, overlay), f = chat(footerField, overlay);
            if (h != null && !h.isEmpty()) head = fr.listFormattedStringToWidth(h, sw - 50);
            if (f != null && !f.isEmpty()) foot = fr.listFormattedStringToWidth(f, sw - 50);
        }
        for (String s : head) totalW = Math.max(totalW, fr.getStringWidth(s));
        for (String s : foot) totalW = Math.max(totalW, fr.getStringWidth(s));

        int pad = 6;
        int h = pad * 2 + head.size() * 9 + (head.isEmpty() ? 0 : 4) + rows * 10 + (foot.isEmpty() ? 0 : 4) + foot.size() * 9;
        float x0 = sw / 2f - totalW / 2f - pad, y0 = 10;
        Gfx.roundedRect(x0, y0, totalW + pad * 2, h, 6f, Gfx.alpha(0xFF0B0C11, opacity.f()));
        Gfx.roundedOutline(x0, y0, totalW + pad * 2, h, 6f, 0.6f, Gfx.alpha(0xFFFFFFFF, 0.08f));
        float y = y0 + pad;
        for (String s : head) {
            fr.drawStringWithShadow(s, sw / 2f - fr.getStringWidth(s) / 2f, y, 0xFFFFFFFF);
            y += 9;
        }
        if (!head.isEmpty()) y += 4;
        float gx = sw / 2f - totalW / 2f;
        for (int i = 0; i < list.size(); i++) {
            NetworkPlayerInfo p = list.get(i);
            int c = i / rows, r = i % rows;
            float cx = gx + c * (cellW + 4), cy = y + r * 10;
            boolean self = mc.thePlayer != null && p.getGameProfile().getId() != null && p.getGameProfile().getId().equals(mc.thePlayer.getUniqueID());
            Gfx.roundedRect(cx - 1, cy - 1, cellW + 2, 10, 2f, self && highlightSelf.on()
                    ? Gfx.alpha(0xFF000000 | ClientModule.accent(), 0.35f) : 0x22FFFFFF);
            float tx = cx + 1;
            if (heads.on()) {
                mc.getTextureManager().bindTexture(p.getLocationSkin());
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.color(1f, 1f, 1f, 1f);
                Gui.drawScaledCustomSizeModalRect((int) cx, (int) cy, 8, 8, 8, 8, 8, 8, 64f, 64f);
                Gui.drawScaledCustomSizeModalRect((int) cx, (int) cy, 40, 8, 8, 8, 8, 8, 64f, 64f);
                tx = cx + 10;
            }
            String name = overlay.getPlayerName(p);
            fr.drawStringWithShadow(fr.trimStringToWidth(name, (int) (cellW - (tx - cx) - pingW - 4)), tx, cy, p.getGameType() == net.minecraft.world.WorldSettings.GameType.SPECTATOR ? 0xFF8A8F9C : 0xFFFFFFFF);
            int ms = p.getResponseTime();
            if (pingNumbers.on()) {
                String s = ms + "ms";
                fr.drawStringWithShadow(s, cx + cellW - fr.getStringWidth(s), cy, pingColor(ms));
            } else {
                int bars = ms < 0 ? 0 : ms < 150 ? 5 : ms < 300 ? 4 : ms < 600 ? 3 : ms < 1000 ? 2 : 1;
                for (int k = 0; k < 5; k++) {
                    Gfx.rect(cx + cellW - 10 + k * 2, cy + 7 - k * 1.5f, 1.4f, 1 + k * 1.5f, k < bars ? pingColor(ms) : 0x40FFFFFF);
                }
            }
        }
        y += rows * 10;
        if (!foot.isEmpty()) y += 4;
        for (String s : foot) {
            fr.drawStringWithShadow(s, sw / 2f - fr.getStringWidth(s) / 2f, y, 0xFFFFFFFF);
            y += 9;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
