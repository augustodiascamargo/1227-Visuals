package net.m1227.client.menu;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.imageio.ImageIO;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Img;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.Notifier;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/**
 * Screenshot manager: every screenshot in the screenshots folder as a thumbnail grid (newest
 * first). Click one to view it large; Open, Copy image, Delete (asks twice), previous / next with
 * the arrow keys, and Open folder. Thumbnails load a few per frame and are freed on close.
 */
public final class ScreenshotScreen extends UiScreen {
    private final File dir;
    private List<File> files = new ArrayList<File>();
    private int viewing = -1;
    private boolean confirmDelete;

    public ScreenshotScreen(GuiScreen parent) {
        super(parent);
        dir = new File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir, "screenshots");
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
        File[] all = dir.listFiles((d, n) -> n.toLowerCase().endsWith(".png"));
        files = new ArrayList<File>();
        if (all != null) {
            Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            files.addAll(Arrays.asList(all));
        }
        if (viewing >= files.size()) viewing = files.size() - 1;
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Img.freeAll(dir.getAbsolutePath());
    }

    @Override
    protected void back() {
        if (viewing >= 0) {
            viewing = -1;
            confirmDelete = false;
            return;
        }
        super.back();
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        if (viewing >= 0 && viewing < files.size()) {
            viewer(w, h);
            return;
        }
        float pw = Math.min(520, w - 20), px = (w - pw) / 2f, top = 14;
        Ui.textBold("Screenshots", px, top, DS.T_TITLE, DS.TEXT);
        Ui.textRight(files.size() + " screenshots  ·  F2 in game takes one", px + pw, top + 4, DS.T_SMALL, DS.FAINT);
        float ly = top + 26, lh = h - ly - 44;
        Ui.card(px, ly, pw, lh);
        int cols = Math.max(2, (int) ((pw - 8) / 120));
        float cw = (pw - 8 - (cols - 1) * 6) / cols, ch = cw * 9f / 16f + 14;
        float off = ui.beginScroll("shots", px + 4, ly + 4, pw - 8, lh - 8);
        int loadsThisFrame = 0;
        for (int i = 0; i < files.size(); i++) {
            final int k = i;
            float x = px + 4 + (i % cols) * (cw + 6), y = ly + 4 + (i / cols) * (ch + 6) - off;
            if (y + ch < ly || y > ly + lh) continue;
            boolean hv = ui.hover(x, y, cw, ch);
            float a = ui.anim(files.get(i), hv ? 1f : 0f);
            Gfx.roundedRect(x, y, cw, ch, DS.R_MD, Gfx.mix(DS.SURFACE_2, DS.SURFACE_3, a));
            String key = files.get(i).getAbsolutePath() + "@320";
            Img.Loaded l = null;
            if (loadsThisFrame < 2 || Img.cached(key)) {
                if (!Img.cached(key)) loadsThisFrame++;
                l = Img.file(files.get(i), 320);
            }
            if (l != null) Img.cover(l, x + 2, y + 2, cw - 4, cw * 9f / 16f - 4, 1f);
            else Ui.spinner(x + cw / 2f, y + cw * 9f / 32f, 5, DS.FAINT);
            Ui.text(Gfx.trim(files.get(i).getName().replace(".png", ""), cw - 6), x + 4, y + ch - 11, 0.72f, DS.MUTED);
            ui.click(x, y, cw, ch, () -> viewing = k);
        }
        if (files.isEmpty()) Ui.textCenter("No screenshots yet. Press F2 in game.", px + pw / 2f, ly + lh / 2f - 4, DS.T_BODY, DS.FAINT);
        ui.endScroll(((files.size() + cols - 1) / cols) * (ch + 6));
        float by = h - 36;
        ui.button("folder", "Open folder", px, by, 100, 22, Ui.SECONDARY, true, Glyph.FOLDER, () -> Os.open(dir));
        ui.button("refresh", "Refresh", px + 104, by, 80, 22, Ui.SECONDARY, true, Glyph.REFRESH, this::reload);
        ui.iconButton("back", px + pw - 22, by, 22, Glyph.BACK, "Back", true, this::back);
    }

    private void viewer(int w, int h) {
        final File f = files.get(viewing);
        Gfx.rect(0, 0, w, h, 0xE0050608);
        Img.Loaded l = Img.file(f, 1600);
        float bx = 16, by = 30, bw = w - 32, bh = h - 76;
        if (l != null) Img.contain(l, bx, by, bw, bh, 1f);
        Ui.textMedium(f.getName(), 16, 10, DS.T_BODY, DS.TEXT);
        Ui.textRight(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(f.lastModified())) + "   " + (viewing + 1) + " / " + files.size(),
                w - 16, 10, DS.T_SMALL, DS.MUTED);
        float y = h - 36, x = w / 2f - 210;
        ui.button("prev", "Previous", x, y, 76, 22, Ui.SECONDARY, viewing > 0, Glyph.BACK, () -> step(-1));
        ui.button("open", "Open", x + 80, y, 64, 22, Ui.SECONDARY, true, Glyph.EYE, () -> Os.open(f));
        ui.button("copy", "Copy image", x + 148, y, 84, 22, Ui.SECONDARY, true, Glyph.CHECK, () -> copy(f));
        ui.button("del", confirmDelete ? "Click again" : "Delete", x + 236, y, 80, 22, Ui.DANGER, true, Glyph.TRASH, () -> {
            if (!confirmDelete) {
                confirmDelete = true;
                return;
            }
            confirmDelete = false;
            Img.freeAll(f.getAbsolutePath());
            boolean ok = f.delete();
            Notifier.post(Notifier.Kind.SYSTEM, "Screenshot", ok ? "Deleted" : "Could not delete", ok ? Notifier.GOOD : Notifier.BAD, 1500);
            reload();
            if (files.isEmpty()) viewing = -1;
        });
        ui.button("next", "Next", x + 320, y, 64, 22, Ui.SECONDARY, viewing < files.size() - 1, Glyph.ARROW_RIGHT, () -> step(1));
        ui.iconButton("close", w - 30, y, 22, Glyph.CLOSE, "Back to the grid", true, this::back);
    }

    private void step(int d) {
        int n = viewing + d;
        if (n >= 0 && n < files.size()) {
            viewing = n;
            confirmDelete = false;
        }
    }

    private static void copy(File f) {
        try {
            final BufferedImage img = ImageIO.read(f);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{DataFlavor.imageFlavor};
                }

                @Override
                public boolean isDataFlavorSupported(DataFlavor fl) {
                    return DataFlavor.imageFlavor.equals(fl);
                }

                @Override
                public Object getTransferData(DataFlavor fl) {
                    return img;
                }
            }, null);
            Notifier.post(Notifier.Kind.SYSTEM, "Screenshot", "Image copied", Notifier.GOOD, 1500);
        } catch (Throwable t) {
            Notifier.post(Notifier.Kind.SYSTEM, "Screenshot", "Could not copy", Notifier.BAD, 1500);
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (viewing >= 0 && key == Keyboard.KEY_LEFT) {
            step(-1);
            return;
        }
        if (viewing >= 0 && key == Keyboard.KEY_RIGHT) {
            step(1);
            return;
        }
        super.keyTyped(c, key);
    }
}
