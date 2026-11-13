package net.m1227.client.menu;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javax.imageio.ImageIO;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Img;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiScreenServerList;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.network.LanServerDetector;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import org.lwjgl.input.Keyboard;

/**
 * The 1227 server list. Tabs: Servers (Minecraft's own list from servers.dat), Favorites,
 * History (servers you joined) and LAN. Each server shows its icon, MOTD, real ping, player
 * count and version, all from Minecraft's own status ping; nothing is shown before the server
 * answers. Join, direct connect, add, edit, delete, reorder (Shift + arrows), refresh and the
 * classic list use Minecraft's own screens and connection (through Forge).
 */
public final class ServerScreen extends UiScreen {
    private static final String[] TABS = {"Servers", "Favorites", "History", "LAN"};
    private static final ExecutorService PING = Executors.newFixedThreadPool(5, new ThreadFactory() {
        private int n;

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "1227-server-ping-" + (n++));
            t.setDaemon(true);
            return t;
        }
    });

    private static int tab;
    private ServerList list;
    private final OldServerPinger pinger = new OldServerPinger();
    private final Set<ServerData> pinged = new HashSet<ServerData>();
    private final Map<String, ServerData> extra = new HashMap<String, ServerData>();
    private final Set<ServerData> lanSet = new HashSet<ServerData>();
    private final Map<String, Img.Loaded> icons = new HashMap<String, Img.Loaded>();
    private final Map<String, String> iconData = new HashMap<String, String>();
    private LanServerDetector.LanServerList lan;
    private LanServerDetector.ThreadLanServerFind lanThread;
    private List<LanServerDetector.LanServer> lanServers = new ArrayList<LanServerDetector.LanServer>();
    private ServerData selected;
    private long lastClick;
    private ServerData lastClicked;

    private static final int ADD = 1, EDIT = 2, DELETE = 3, DIRECT = 4;
    private int pending;
    private ServerData editing, editTarget;

    public ServerScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (list == null) {
            list = new ServerList(mc);
            list.loadServerList();
        }
        if (lan == null) lan = new LanServerDetector.LanServerList();
        if (lanThread == null || !lanThread.isAlive()) {
            try {
                lanThread = new LanServerDetector.ThreadLanServerFind(lan);
                lanThread.start();
            } catch (Exception e) {
                lanThread = null;
            }
        }
        // pings cut off when this screen was left are started again
        for (ServerData sd : new ArrayList<ServerData>(pinged)) if (sd.pingToServer == -2L) pinged.remove(sd);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        pinger.clearPendingNetworks();
        if (lanThread != null) {
            lanThread.interrupt();
            lanThread = null;
        }
    }

    @Override
    public void updateScreen() {
        pinger.pingPendingNetworks();
        if (lan != null && lan.getWasUpdated()) {
            lanServers = lan.getLanServers();
            lan.setWasNotUpdated();
        }
    }

    private void ping(final ServerData sd) {
        if (sd == null || pinged.contains(sd)) return;
        pinged.add(sd);
        sd.pingToServer = -2L;
        sd.serverMOTD = "";
        sd.populationInfo = "";
        PING.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    pinger.ping(sd);
                } catch (java.net.UnknownHostException e) {
                    sd.pingToServer = -1L;
                    sd.serverMOTD = EnumChatFormatting.DARK_RED + "Can't resolve hostname";
                } catch (Exception e) {
                    sd.pingToServer = -1L;
                    sd.serverMOTD = EnumChatFormatting.DARK_RED + "Can't connect to server.";
                }
            }
        });
    }

    private ServerData find(String ip) {
        for (int i = 0; i < list.countServers(); i++) if (list.getServerData(i).serverIP.equalsIgnoreCase(ip)) return list.getServerData(i);
        ServerData sd = extra.get(ip.toLowerCase());
        if (sd == null) {
            sd = new ServerData(ip, ip, false);
            extra.put(ip.toLowerCase(), sd);
        }
        return sd;
    }

    private int indexInList(ServerData sd) {
        for (int i = 0; i < list.countServers(); i++) if (list.getServerData(i) == sd) return i;
        return -1;
    }

    private List<ServerData> rows() {
        List<ServerData> out = new ArrayList<ServerData>();
        if (tab == 0) {
            for (int i = 0; i < list.countServers(); i++) out.add(list.getServerData(i));
        } else if (tab == 1) {
            for (String ip : ServerStore.INSTANCE.favorites()) out.add(find(ip));
        } else if (tab == 2) {
            for (ServerStore.Visit v : ServerStore.INSTANCE.history()) {
                ServerData sd = find(v.ip);
                if (sd.serverName.equals(sd.serverIP)) sd.serverName = v.name;
                out.add(sd);
            }
        } else {
            for (LanServerDetector.LanServer l : lanServers) {
                ServerData sd = extra.get("lan:" + l.getServerIpPort());
                if (sd == null) {
                    sd = new ServerData(l.getServerMotd(), l.getServerIpPort(), true);
                    extra.put("lan:" + l.getServerIpPort(), sd);
                    lanSet.add(sd);
                }
                out.add(sd);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        float pw = Math.min(460, w - 20), px = (w - pw) / 2f, top = 14;
        Ui.textBold("Multiplayer", px, top, DS.T_TITLE, DS.TEXT);
        // tabs
        float tx = px + pw;
        for (int i = TABS.length - 1; i >= 0; i--) {
            final int k = i;
            float tw = Ui.width(TABS[i], DS.T_SMALL) + 16;
            tx -= tw + 4;
            boolean on = tab == i, hv = ui.hover(tx, top, tw, 16);
            Gfx.roundedRect(tx, top, tw, 16, 8, on ? DS.accent() : hv ? DS.SURFACE_3 : DS.SURFACE_2);
            Ui.text(TABS[i], tx + 8, top + 4.4f, DS.T_SMALL, on ? 0xFFFFFFFF : DS.MUTED);
            ui.click(tx, top, tw, 16, () -> {
                tab = k;
                ui.resetScroll("servers");
            });
        }
        float ly = top + 26, lh = h - ly - 44;
        Ui.card(px, ly, pw, lh);
        List<ServerData> rows = rows();
        float off = ui.beginScroll("servers", px + 4, ly + 4, pw - 8, lh - 8);
        float y = ly + 4 - off;
        float rh = 38;
        for (ServerData sd : rows) {
            if (y + rh > ly && y < ly + lh) row(sd, px + 4, y, pw - 8, rh);
            y += rh + 2;
        }
        if (rows.isEmpty()) {
            String msg = tab == 0 ? "No servers yet. Add one below." : tab == 1 ? "Star a server to keep it here."
                    : tab == 2 ? "Servers you join appear here." : "Looking for games on your network...";
            Ui.textCenter(msg, px + pw / 2f, ly + lh / 2f - 4, DS.T_BODY, DS.FAINT);
        }
        ui.endScroll(rows.size() * (rh + 2));

        // actions
        float by = h - 36, bw = 64, g = 4;
        boolean inList = selected != null && indexInList(selected) >= 0;
        float bx = px;
        ui.button("join", "Join", bx, by, 76, 22, Ui.PRIMARY, selected != null, Glyph.PLAY, () -> connect(selected));
        bx += 76 + g;
        ui.button("direct", "Direct", bx, by, bw, 22, Ui.SECONDARY, true, Glyph.LINK, this::direct);
        bx += bw + g;
        ui.button("add", "Add", bx, by, bw - 10, 22, Ui.SECONDARY, true, Glyph.PLUS, this::add);
        bx += bw - 10 + g;
        ui.button("edit", "Edit", bx, by, bw - 10, 22, Ui.SECONDARY, inList, Glyph.PENCIL, this::edit);
        bx += bw - 10 + g;
        ui.button("del", "Delete", bx, by, bw, 22, Ui.DANGER, inList, Glyph.TRASH, this::delete);
        float rx = px + pw;
        ui.iconButton("back", rx - 22, by, 22, Glyph.BACK, "Back", true, this::back);
        ui.iconButton("classic", rx - 48, by, 22, Glyph.EYE, "Classic server list", true, () -> MenuSystem.openVanilla(new GuiMultiplayer(parent)));
        ui.iconButton("refresh", rx - 74, by, 22, Glyph.REFRESH, "Refresh", true, () -> {
            pinger.clearPendingNetworks();
            pinged.clear();
        });
        if (tab == 2) ui.iconButton("clearh", rx - 100, by, 22, Glyph.TRASH, "Clear history", true, () -> ServerStore.INSTANCE.clearHistory());
    }

    private void row(final ServerData sd, float x, float y, float w, float h) {
        if (!lanSet.contains(sd)) ping(sd);
        boolean sel = sd == selected, hv = ui.hover(x, y, w, h);
        float k = ui.anim(sd, sel ? 1f : hv ? 0.5f : 0f);
        Gfx.roundedRect(x, y, w, h, DS.R_MD, Gfx.mix(0x00000000, Gfx.mix(DS.SURFACE_2, Gfx.alpha(DS.accent(), 0.22f), sel ? 1f : 0f), k));
        if (sel) Gfx.roundedOutline(x, y, w, h, DS.R_MD, 0.6f, Gfx.alpha(DS.accent(), 0.7f));
        // icon
        Img.Loaded icon = icon(sd);
        if (icon != null) Img.draw(icon.id, x + 4, y + 3, 32, 32, 0, 0, 1, 1, 1f);
        else {
            Gfx.roundedRect(x + 4, y + 3, 32, 32, DS.R_SM, DS.SURFACE_3);
            Glyph.draw(lanSet.contains(sd) ? Glyph.LAN : Glyph.SERVERS, x + 20, y + 19, DS.FAINT);
        }
        float tx = x + 42;
        float right = x + w - 6;
        // favourite star
        final String ip = sd.serverIP;
        boolean fav = ServerStore.INSTANCE.favorite(ip);
        float sx = right - 10;
        boolean sh = ui.hover(sx - 6, y + 3, 14, 14);
        Gfx.star(sx, y + 10, 4.6f, fav, fav ? DS.WARN : sh ? DS.TEXT : 0x50FFFFFF);
        ui.click(sx - 6, y + 3, 14, 14, () -> ServerStore.INSTANCE.toggleFavorite(ip));
        if (sh) ui.tip(fav ? "Remove from favorites" : "Add to favorites");
        right -= 20;
        // ping and players
        long p = sd.pingToServer;
        if (lanSet.contains(sd)) {
            Ui.pill("LAN", right - 24, y + 5, DS.GOOD);
        } else if (p == -2L || !pinged.contains(sd)) {
            Ui.spinner(right - 6, y + 10, 4, DS.MUTED);
        } else if (p < 0) {
            Ui.textRight("offline", right, y + 6, DS.T_SMALL, DS.BAD);
        } else {
            int bars = p < 150 ? 5 : p < 300 ? 4 : p < 600 ? 3 : p < 1000 ? 2 : 1;
            int col = bars >= 4 ? DS.GOOD : bars == 3 ? DS.WARN : DS.BAD;
            for (int i = 0; i < 5; i++) Gfx.rect(right - 14 + i * 3, y + 13 - (i + 1) * 1.6f, 2, (i + 1) * 1.6f, i < bars ? col : 0x30FFFFFF);
            String ms = p + " ms";
            Ui.textRight(ms, right - 18, y + 6, DS.T_SMALL, DS.MUTED);
            String pop = EnumChatFormatting.getTextWithoutFormattingCodes(sd.populationInfo);
            if (pop != null && !pop.isEmpty()) {
                Ui.textRight(pop, right, y + 19, DS.T_SMALL, DS.TEXT);
                float pw = Ui.width(pop, DS.T_SMALL);
                if (ui.hover(right - pw, y + 17, pw, 10) && sd.playerList != null && !sd.playerList.isEmpty())
                    ui.tip(EnumChatFormatting.getTextWithoutFormattingCodes(sd.playerList));
            }
            if (sd.version != 47 && sd.gameVersion != null) {
                String v = EnumChatFormatting.getTextWithoutFormattingCodes(sd.gameVersion);
                Ui.pill(Gfx.trim(v, 70), right - Ui.width(Gfx.trim(v, 70), 0.72f) - 8, y + 27, DS.WARN);
            }
        }
        // name and MOTD
        float textW = right - tx - 60;
        Ui.textMedium(Gfx.trim(sd.serverName == null ? sd.serverIP : sd.serverName, textW), tx, y + 4, DS.T_BODY, DS.TEXT);
        String motd = lanSet.contains(sd) ? sd.serverIP : sd.serverMOTD;
        if (motd != null && !motd.isEmpty()) {
            List<String> lines = mc.fontRendererObj.listFormattedStringToWidth(motd, (int) Math.max(40, textW));
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(tx, y + 15 + i * 9, 0);
                net.minecraft.client.renderer.GlStateManager.scale(0.85f, 0.85f, 1f);
                mc.fontRendererObj.drawString(lines.get(i), 0, 0, 0xFF9AA0AE);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }
        }
        ui.click(x, y, w, h, () -> {
            long now = System.currentTimeMillis();
            if (lastClicked == sd && now - lastClick < 350) connect(sd);
            selected = sd;
            lastClicked = sd;
            lastClick = now;
        });
    }

    private Img.Loaded icon(ServerData sd) {
        String data = sd.getBase64EncodedIconData();
        String key = sd.serverIP.toLowerCase();
        if (data == null) return null;
        Img.Loaded l = icons.get(key);
        if (l != null && data.equals(iconData.get(key))) return l;
        try {
            byte[] png = java.util.Base64.getDecoder().decode(data);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null || img.getWidth() != 64 || img.getHeight() != 64) return null;
            if (l != null) mc.getTextureManager().deleteTexture(l.id);
            l = Img.upload("server_" + key, img, 0L);
            icons.put(key, l);
            iconData.put(key, data);
            return l;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ actions

    private void connect(ServerData sd) {
        if (sd == null) return;
        net.minecraftforge.fml.client.FMLClientHandler.instance().connectToServer(this, sd);
    }

    private void add() {
        pending = ADD;
        editing = new ServerData(I18n.format("selectServer.defaultName"), "", false);
        mc.displayGuiScreen(new GuiScreenAddServer(this, editing));
    }

    private void edit() {
        if (selected == null || indexInList(selected) < 0) return;
        pending = EDIT;
        editTarget = selected;
        editing = new ServerData(selected.serverName, selected.serverIP, false);
        editing.copyFrom(selected);
        mc.displayGuiScreen(new GuiScreenAddServer(this, editing));
    }

    private void delete() {
        if (selected == null || indexInList(selected) < 0) return;
        pending = DELETE;
        String q = I18n.format("selectServer.deleteQuestion");
        String warn = "'" + selected.serverName + "' " + I18n.format("selectServer.deleteWarning");
        mc.displayGuiScreen(new GuiYesNo(this, q, warn, I18n.format("selectServer.deleteButton"), I18n.format("gui.cancel"), 0));
    }

    private void direct() {
        pending = DIRECT;
        editing = new ServerData(I18n.format("selectServer.defaultName"), mc.gameSettings.lastServer == null ? "" : mc.gameSettings.lastServer, false);
        mc.displayGuiScreen(new GuiScreenServerList(this, editing));
    }

    @Override
    public void confirmClicked(boolean ok, int id) {
        int p = pending;
        pending = 0;
        if (ok && p == ADD) {
            list.addServerData(editing);
            list.saveServerList();
            selected = editing;
        } else if (ok && p == EDIT && editTarget != null) {
            editTarget.serverName = editing.serverName;
            editTarget.serverIP = editing.serverIP;
            editTarget.copyFrom(editing);
            list.saveServerList();
            pinged.remove(editTarget);
        } else if (ok && p == DELETE && selected != null) {
            int i = indexInList(selected);
            if (i >= 0) {
                list.removeServerData(i);
                list.saveServerList();
            }
            selected = null;
        } else if (ok && p == DIRECT) {
            mc.gameSettings.lastServer = editing.serverIP;
            mc.gameSettings.saveOptions();
            connect(editing);
            return;
        }
        mc.displayGuiScreen(this);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (!ui.typing() && tab == 0 && selected != null && isShiftKeyDown() && (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN)) {
            int i = indexInList(selected), j = i + (key == Keyboard.KEY_UP ? -1 : 1);
            if (i >= 0 && j >= 0 && j < list.countServers()) {
                list.swapServers(i, j);
                list.saveServerList();
            }
            return;
        }
        if (!ui.typing() && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) && selected != null) {
            connect(selected);
            return;
        }
        if (!ui.typing() && key == Keyboard.KEY_F5) {
            pinger.clearPendingNetworks();
            pinged.clear();
            return;
        }
        super.keyTyped(c, key);
    }
}
