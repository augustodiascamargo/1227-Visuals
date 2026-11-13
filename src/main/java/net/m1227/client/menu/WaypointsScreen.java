package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.Notifier;
import net.m1227.module.WaypointsModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;

/**
 * Waypoint list for the world or server you are in: rename, recolour, change the icon, hide or
 * delete each one; add one where you stand; export / import them as JSON through the clipboard.
 */
public final class WaypointsScreen extends UiScreen {
    private static final int[] COLORS = {0x8B5CFF, 0x3DDC84, 0xFFC857, 0x2DD4FF, 0xFF5A6A, 0xFF6FD8, 0xFFFFFF, 0xFF9A3D};
    private Object confirm;

    public WaypointsScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        final WaypointsModule m = WaypointsModule.INSTANCE;
        float pw = Math.min(560, w - 20), px = (w - pw) / 2f, top = 14;
        Ui.textBold("Waypoints", px, top, DS.T_TITLE, DS.TEXT);
        Ui.textRight(WaypointsModule.worldKey().replace("server:", "").replace("world:", "World: ") + "  ·  dimension " + WaypointsModule.dimKey(),
                px + pw, top + 4, DS.T_SMALL, DS.FAINT);
        if (m == null) return;
        if (!m.isEnabled()) ui.button("on", "Turn on Waypoints", px + pw - 130, top + 16, 130, 18, Ui.PRIMARY, true, Glyph.CHECK, () -> m.setEnabled(true));
        List<WaypointsModule.Waypoint> list = m.here();
        float y = top + 38, lh = h - y - 44;
        Ui.card(px, y, pw, lh);
        float off = ui.beginScroll("wp", px + 4, y + 4, pw - 8, lh - 8);
        float ry = y + 6;
        if (list.isEmpty()) Ui.textCenter("No waypoints here yet. Press the add key in game, or \"Add here\".", px + pw / 2f, y + lh / 2f - 4, DS.T_BODY, DS.FAINT);
        for (final WaypointsModule.Waypoint p : list) {
            float yy = ry - off;
            Gfx.roundedRect(px + 6, yy, pw - 12, 24, DS.R_SM, DS.SURFACE_2);
            // colour swatch: click = next colour
            Gfx.roundedRect(px + 12, yy + 6, 12, 12, 3, 0xFF000000 | p.color);
            ui.click(px + 12, yy + 6, 12, 12, () -> {
                int i = 0;
                for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == p.color) i = k;
                p.color = COLORS[(i + 1) % COLORS.length];
                m.save();
            });
            WaypointsModule.icon(p.icon, px + 36, yy + 12, 0xFF000000 | p.color);
            ui.click(px + 29, yy + 4, 16, 16, () -> {
                int i = 0;
                for (int k = 0; k < WaypointsModule.ICONS.length; k++) if (WaypointsModule.ICONS[k].equals(p.icon)) i = k;
                p.icon = WaypointsModule.ICONS[(i + 1) % WaypointsModule.ICONS.length];
                m.save();
            });
            ui.textField(p, px + 50, yy + 3, pw * 0.36f, 18, p.name, "Name", 40, v -> {
                p.name = v.trim().isEmpty() ? "Waypoint" : v;
                m.save();
            });
            float cx = px + 56 + pw * 0.36f;
            Ui.text((int) Math.floor(p.x) + ", " + (int) p.y + ", " + (int) Math.floor(p.z), cx, yy + 8, DS.T_SMALL, DS.MUTED);
            if (mc.thePlayer != null) {
                double d = Math.sqrt(mc.thePlayer.getDistanceSq(p.x, p.y, p.z));
                Ui.textRight((int) d + " m", px + pw - 82, yy + 8, DS.T_SMALL, DS.FAINT);
            }
            ui.toggle(p.name + "#v" + System.identityHashCode(p), px + pw - 72, yy + 6, p.visible, () -> {
                p.visible = !p.visible;
                m.save();
            });
            final Object key = p;
            ui.iconButton("del" + System.identityHashCode(p), px + pw - 36, yy + 2, 20, confirm == key ? Glyph.CHECK : Glyph.TRASH,
                    confirm == key ? "Click again to delete" : "Delete", true, () -> {
                        if (confirm != key) {
                            confirm = key;
                            return;
                        }
                        confirm = null;
                        m.remove(p);
                    });
            ry += 28;
        }
        ui.endScroll(ry - y);
        float by = h - 34;
        ui.button("add", "Add here", px, by, 84, 22, Ui.PRIMARY, mc.thePlayer != null, Glyph.PLUS, () -> m.addHere("Waypoint " + (m.here().size() + 1)));
        ui.button("exp", "Export", px + 88, by, 70, 22, Ui.SECONDARY, !list.isEmpty(), Glyph.LINK, () -> {
            GuiScreen.setClipboardString(m.exportHere());
            Notifier.post(Notifier.Kind.SYSTEM, "Waypoints", "Copied to clipboard", Notifier.GOOD, 1600);
        });
        ui.button("imp", "Import", px + 162, by, 70, 22, Ui.SECONDARY, true, Glyph.PLUS, () -> {
            int n = m.importText(GuiScreen.getClipboardString());
            Notifier.post(Notifier.Kind.SYSTEM, "Waypoints", n > 0 ? n + " imported" : "Clipboard has no waypoints", n > 0 ? Notifier.GOOD : Notifier.BAD, 1800);
        });
        ui.iconButton("folder", px + 236, by, 22, Glyph.FOLDER, "Open the folder of waypoints.json", true, () -> Os.open(new java.io.File(ConfigManager.dir(), "1227")));
        ui.iconButton("back", px + pw - 22, by, 22, Glyph.BACK, "Back", true, this::back);
    }
}
