package net.m1227.client.menu;

import java.util.List;
import net.m1227.client.DisplayControl;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.InputManager;
import net.m1227.module.DisplayModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

/**
 * Display settings: window sizes (common sizes that fit your monitor, or your own), fullscreen
 * at the desktop resolution or any mode your monitor reports, and a 12 s "keep or revert" check
 * after every change: if you do not answer (for example the screen went black), the previous
 * mode comes back by itself. GUI scale is shown for reference but is a different setting.
 */
public final class DisplaySettingsScreen extends UiScreen {
    private String customW = "", customH = "";
    private DisplayControl.Snapshot pending;
    private long pendingUntil;
    private String pendingWhat;
    private Runnable onKeep;
    private String message = "";

    private DisplaySettingsScreen(net.minecraft.client.gui.GuiScreen parent) {
        super(parent);
        DisplayMode m = Display.getDisplayMode();
        customW = String.valueOf(m.getWidth());
        customH = String.valueOf(m.getHeight());
    }

    public static void open() {
        InputManager.INSTANCE.requestScreen(new DisplaySettingsScreen(Minecraft.getMinecraft().currentScreen));
    }

    private void change(String what, Runnable apply, Runnable keep) {
        DisplayControl.Snapshot s = DisplayControl.snapshot();
        apply.run();
        pending = s;
        pendingUntil = System.currentTimeMillis() + 12000L;
        pendingWhat = what;
        onKeep = keep;
    }

    private void keep() {
        if (onKeep != null) onKeep.run();
        ConfigManager.markDirty();
        pending = null;
        message = "Kept: " + pendingWhat;
    }

    private void revert() {
        DisplayControl.revert(pending);
        pending = null;
        message = "Reverted to the previous mode";
    }

    private void window(final int w, final int h) {
        final int[] c = DisplayControl.clampWindow(w, h);
        change("window " + c[0] + "x" + c[1], () -> DisplayControl.setWindow(c[0], c[1]), () -> {
            DisplayModule.INSTANCE.windowW.setClamped(c[0]);
            DisplayModule.INSTANCE.windowH.setClamped(c[1]);
        });
    }

    private void fullscreen(final DisplayMode m) {
        change("fullscreen " + DisplayControl.describe(m), () -> DisplayControl.setFullscreen(m),
                () -> DisplayModule.INSTANCE.fullscreenMode.set(m == null ? "" : DisplayControl.describe(m)));
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        if (pending != null && System.currentTimeMillis() > pendingUntil) revert();
        float pw = Math.min(560, w - 16), px = (w - pw) / 2f, top = 10;
        Ui.textBold("Display", px, top, DS.T_TITLE, DS.TEXT);
        ui.iconButton("back", px + pw - 20, top, 20, Glyph.BACK, "Back", pending == null, this::back);
        DisplayMode cur = Display.getDisplayMode();
        Ui.text("Now: " + (Display.isFullscreen() ? "fullscreen " : "window ") + cur.getWidth() + "x" + cur.getHeight()
                + (Display.isFullscreen() ? " @ " + cur.getFrequency() + " Hz" : "") + "   ·   GUI scale is separate (Video Settings)",
                px, top + 18, DS.T_SMALL, DS.MUTED);
        float cy = top + 32, ch = h - cy - 10;
        Ui.card(px, cy, pw, ch);
        if (pending != null) {
            long left = Math.max(0, (pendingUntil - System.currentTimeMillis()) / 1000L + 1);
            Ui.textCenter("Keep " + pendingWhat + "?", px + pw / 2f, cy + ch / 2f - 30, DS.T_LABEL, DS.TEXT);
            Ui.textCenter("Reverting in " + left + " s if you do nothing.", px + pw / 2f, cy + ch / 2f - 14, DS.T_SMALL, DS.MUTED);
            ui.button("keep", "Keep", px + pw / 2f - 104, cy + ch / 2f + 4, 100, 20, Ui.PRIMARY, true, Glyph.CHECK, this::keep);
            ui.button("rev", "Revert", px + pw / 2f + 4, cy + ch / 2f + 4, 100, 20, Ui.SECONDARY, true, Glyph.REFRESH, this::revert);
            return;
        }
        float off = ui.beginScroll("disp", px + 4, cy + 4, pw - 8, ch - 8);
        float x = px + 12, y = cy + 10 - off, iw = pw - 24;
        Ui.caption("WINDOW SIZE", x, y);
        y += 13;
        List<int[]> ws = DisplayControl.windowPresets();
        float bw = (iw - 3 * 4) / 4f;
        for (int i = 0; i < ws.size(); i++) {
            final int[] s = ws.get(i);
            boolean now = !Display.isFullscreen() && cur.getWidth() == s[0] && cur.getHeight() == s[1];
            ui.button("w" + i, s[0] + " x " + s[1], x + (i % 4) * (bw + 4), y + (i / 4) * 20, bw, 16, now ? Ui.PRIMARY : Ui.SECONDARY, true, -1,
                    () -> window(s[0], s[1]));
        }
        y += ((ws.size() + 3) / 4) * 20 + 6;
        Ui.text("Custom", x, y + 4, DS.T_SMALL, DS.MUTED);
        ui.textField("cw", x + 50, y, 60, 16, customW, "width", 5, v -> customW = v.replaceAll("[^0-9]", ""));
        Ui.text("x", x + 116, y + 4, DS.T_SMALL, DS.MUTED);
        ui.textField("ch", x + 126, y, 60, 16, customH, "height", 5, v -> customH = v.replaceAll("[^0-9]", ""));
        ui.button("capply", "Apply", x + 194, y, 70, 16, Ui.PRIMARY, !customW.isEmpty() && !customH.isEmpty(), Glyph.CHECK, () -> {
            try {
                window(Integer.parseInt(customW), Integer.parseInt(customH));
            } catch (NumberFormatException ignored) {
                message = "Width and height must be numbers";
            }
        });
        DisplayMode d = DisplayControl.desktop();
        Ui.text("Between " + DisplayControl.MIN_W + "x" + DisplayControl.MIN_H + " and your desktop (" + d.getWidth() + "x" + d.getHeight() + ").",
                x + 272, y + 4, 0.7f, DS.FAINT);
        y += 26;
        Ui.caption("FULLSCREEN", x, y);
        y += 13;
        ui.button("fsd", "Desktop resolution (" + d.getWidth() + "x" + d.getHeight() + ")", x, y, 200, 16, Ui.SECONDARY, true, Glyph.CHECK,
                () -> fullscreen(null));
        if (Display.isFullscreen()) {
            ui.button("fsoff", "Back to a window", x + 206, y, 120, 16, Ui.GHOST, true, -1, () -> {
                DisplayModule m = DisplayModule.INSTANCE;
                window(m.windowW.i() >= DisplayControl.MIN_W ? m.windowW.i() : 1280, m.windowH.i() >= DisplayControl.MIN_H ? m.windowH.i() : 720);
            });
        }
        y += 22;
        List<DisplayMode> modes = DisplayControl.fullscreenModes();
        float mw = (iw - 3 * 4) / 4f;
        for (int i = 0; i < modes.size(); i++) {
            final DisplayMode m = modes.get(i);
            boolean now = Display.isFullscreen() && DisplayControl.sameMode(m, cur);
            ui.button("fm" + i, m.getWidth() + "x" + m.getHeight() + " " + m.getFrequency() + "Hz", x + (i % 4) * (mw + 4), y + (i / 4) * 20, mw, 16,
                    now ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> fullscreen(m));
        }
        y += ((modes.size() + 3) / 4) * 20 + 8;
        DisplayModule dm = DisplayModule.INSTANCE;
        ui.toggle("f11", x, y, dm.useWithF11.on(), () -> dm.useWithF11.set(!dm.useWithF11.on()));
        Ui.text("F11 uses the chosen fullscreen mode (" + (dm.fullscreenMode.get().isEmpty() ? "desktop" : dm.fullscreenMode.get()) + ")",
                x + 32, y + 2, DS.T_SMALL, DS.MUTED);
        y += 18;
        ui.toggle("start", x, y, dm.applyAtStart.on(), () -> dm.applyAtStart.set(!dm.applyAtStart.on()));
        Ui.text("Open the game at the saved window size" + (dm.windowW.i() > 0 ? " (" + dm.windowW.i() + "x" + dm.windowH.i() + ")" : ""),
                x + 32, y + 2, DS.T_SMALL, DS.MUTED);
        y += 22;
        if (!message.isEmpty()) {
            Ui.text(message, x, y, DS.T_SMALL, DS.GOOD);
            y += 14;
        }
        for (String l : Gfx.wrap("After each change you have 12 seconds to keep it; otherwise the previous mode comes back, so a mode your monitor "
                + "cannot show never leaves you stuck. Menus and HUDs are laid out again for the new size (HUD positions are kept as a share "
                + "of the screen).", iw / DS.T_SMALL)) {
            Ui.text(l, x, y, DS.T_SMALL, DS.FAINT);
            y += 10;
        }
        ui.endScroll(y + off - cy + 10);
    }
}
