package net.m1227.hud;

import net.m1227.core.Setting;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;

/** Server address, players in the tab list and your ping (from the tab list, as Minecraft knows it). */
public final class ServerHud extends HudModule {
    private final Setting.Bool address, players, ping;

    public ServerHud() {
        super("Server Info", "Server address, players online and ping.", 0.01, 0.30);
        address = add(new Setting.Bool("Address", true));
        players = add(new Setting.Bool("Players (tab list)", true));
        ping = add(new Setting.Bool("Ping", true));
    }

    private int rows() {
        return (address.on() ? 1 : 0) + (players.on() ? 1 : 0) + (ping.on() ? 1 : 0);
    }

    @Override
    public float width() {
        return 110;
    }

    @Override
    public float height() {
        return 6 + 10 * Math.max(1, rows());
    }

    @Override
    protected void draw(float x, float y, boolean editing) {
        Minecraft mc = Minecraft.getMinecraft();
        panel(x, y, width(), height());
        ServerData sd = mc.getCurrentServerData();
        boolean single = mc.isSingleplayer() || sd == null;
        float ry = y + 4;
        if (address.on()) {
            String a = editing && single ? "play.example.net" : single ? "Singleplayer" : sd.serverIP;
            row(x, ry, "Server", Gfx.trim(a, width() - 50));
            ry += 10;
        }
        if (players.on()) {
            int n = mc.getNetHandler() == null ? 0 : mc.getNetHandler().getPlayerInfoMap().size();
            row(x, ry, "Players", String.valueOf(editing && n == 0 ? 42 : n));
            ry += 10;
        }
        if (ping.on()) {
            int p = 0;
            if (mc.getNetHandler() != null && mc.thePlayer != null) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
                if (info != null) p = info.getResponseTime();
            }
            row(x, ry, "Ping", (single && !editing ? 0 : (editing && p == 0 ? 38 : p)) + " ms");
        }
    }

    private void row(float x, float y, String l, String v) {
        Gfx.text(l, x + 5, y, LABEL);
        Gfx.text(v, x + width() - 5 - Gfx.textWidth(v), y, value());
    }
}
