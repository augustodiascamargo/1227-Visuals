package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.Reflect;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.IChatComponent;

/**
 * The 1227 "disconnected" screen: the reason and the server's message (with its colours), Back to
 * the server list and Reconnect to the last server. Built from the Minecraft screen it replaces.
 */
public final class DisconnectedScreen extends UiScreen {
    private final String reason;
    private final IChatComponent message;
    private final ServerData last;
    private List<String> lines;

    private DisconnectedScreen(GuiScreen parent, String reason, IChatComponent message, ServerData last) {
        super(parent);
        this.reason = reason == null ? "Disconnected" : reason;
        this.message = message;
        this.last = last;
    }

    static GuiScreen from(GuiDisconnected g) {
        GuiScreen parent = Reflect.get(g, GuiDisconnected.class, GuiScreen.class, 0);
        String reason = Reflect.get(g, GuiDisconnected.class, String.class, 0);
        IChatComponent msg = Reflect.get(g, GuiDisconnected.class, IChatComponent.class, 0);
        if (msg == null) return g; // unknown layout: keep Minecraft's screen
        ServerData sd = ServerStore.lastServer();
        return new DisconnectedScreen(parent, reason, msg, sd);
    }

    @Override
    public void initGui() {
        super.initGui();
        lines = mc.fontRendererObj.listFormattedStringToWidth(message.getFormattedText(), Math.min(300, width - 60));
    }

    @Override
    protected void back() {
        mc.displayGuiScreen(parent != null ? parent : new GuiMultiplayer(new GuiMainMenu()));
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, false);
        float cw = Math.min(330, w - 30);
        float ch = 92 + lines.size() * 10;
        float cx = (w - cw) / 2f, cy = (h - ch) / 2f;
        Ui.glassCard(cx, cy, cw, ch);
        Gfx.circle(cx + 22, cy + 22, 8, Gfx.alpha(DS.BAD, 0.2f));
        Glyph.draw(Glyph.CLOSE, cx + 22, cy + 22, DS.BAD);
        Ui.textMedium(Gfx.trim(net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(reason), cw - 50), cx + 38, cy + 15, DS.T_LABEL, DS.TEXT);
        if (last != null) Ui.text(Gfx.trim(last.serverIP, cw - 50), cx + 38, cy + 27, DS.T_SMALL, DS.FAINT);
        float y = cy + 46;
        for (String s : lines) {
            mc.fontRendererObj.drawString(s, (int) (cx + cw / 2f - mc.fontRendererObj.getStringWidth(s) / 2f), (int) y, 0xFFC8CCD6);
            y += 10;
        }
        float bw = (cw - 36) / 2f, by = cy + ch - 32;
        ui.button("back", "Server list", cx + 12, by, bw, 22, Ui.SECONDARY, true, Glyph.BACK, this::back);
        ui.button("again", "Reconnect", cx + 24 + bw, by, bw, 22, Ui.PRIMARY, last != null, Glyph.REFRESH, () -> {
            GuiScreen list = parent != null ? parent : new GuiMultiplayer(new GuiMainMenu());
            net.minecraftforge.fml.client.FMLClientHandler.instance().connectToServer(list, last);
        });
    }
}
