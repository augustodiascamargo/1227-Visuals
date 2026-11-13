package net.m1227.client.menu;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.m1227.client.Os;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiRenameWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.storage.ISaveFormat;
import net.minecraft.world.storage.SaveFormatComparator;
import org.lwjgl.input.Keyboard;

/**
 * The 1227 world list: your singleplayer worlds (newest first) with game mode, hardcore, cheats
 * and last played date, plus search, Play, Create, Rename, Delete and the saves folder.
 * Loading, creating and renaming go through Minecraft's and Forge's own code.
 */
public final class WorldScreen extends UiScreen {
    private List<SaveFormatComparator> worlds = new ArrayList<SaveFormatComparator>();
    private SaveFormatComparator selected;
    private String search = "";
    private long lastClick;
    private boolean loading, deleting;
    private String error;

    public WorldScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        reload();
    }

    private void reload() {
        try {
            worlds = new ArrayList<SaveFormatComparator>(mc.getSaveLoader().getSaveList());
            Collections.sort(worlds);
            error = null;
        } catch (Exception e) {
            worlds = new ArrayList<SaveFormatComparator>();
            error = "Could not read the saves folder: " + e.getMessage();
        }
        if (selected != null) {
            SaveFormatComparator keep = null;
            for (SaveFormatComparator s : worlds) if (s.getFileName().equals(selected.getFileName())) keep = s;
            selected = keep;
        }
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, false);
        float pw = Math.min(440, w - 20), px = (w - pw) / 2f, top = 14;
        Ui.textBold("Singleplayer", px, top, DS.T_TITLE, DS.TEXT);
        ui.textField("search", px + pw - 150, top - 2, 150, 18, search, "Search worlds", 40, v -> search = v);
        float ly = top + 26, lh = h - ly - 44;
        Ui.card(px, ly, pw, lh);
        float off = ui.beginScroll("worlds", px + 4, ly + 4, pw - 8, lh - 8);
        float y = ly + 4 - off, rh = 34;
        int shown = 0;
        String q = search.trim().toLowerCase();
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (final SaveFormatComparator s : worlds) {
            String name = s.getDisplayName() == null || s.getDisplayName().isEmpty() ? s.getFileName() : s.getDisplayName();
            if (!q.isEmpty() && !name.toLowerCase().contains(q) && !s.getFileName().toLowerCase().contains(q)) continue;
            shown++;
            if (y + rh > ly && y < ly + lh) {
                boolean sel = selected == s, hv = ui.hover(px + 4, y, pw - 8, rh);
                float k = ui.anim(s.getFileName(), sel ? 1f : hv ? 0.5f : 0f);
                Gfx.roundedRect(px + 4, y, pw - 8, rh, DS.R_MD, Gfx.mix(0, sel ? Gfx.alpha(DS.accent(), 0.22f) : DS.SURFACE_2, k));
                if (sel) Gfx.roundedOutline(px + 4, y, pw - 8, rh, DS.R_MD, 0.6f, Gfx.alpha(DS.accent(), 0.7f));
                Gfx.roundedRect(px + 9, y + 4, 26, 26, DS.R_SM, DS.SURFACE_3);
                Glyph.draw(Glyph.WORLD, px + 22, y + 17, s.isHardcoreModeEnabled() ? DS.BAD : DS.MUTED);
                Ui.textMedium(Gfx.trim(name, pw - 160), px + 42, y + 6, DS.T_BODY, DS.TEXT);
                String mode = s.isHardcoreModeEnabled() ? "Hardcore" : I18n.format("gameMode." + s.getEnumGameType().getName());
                String info = s.getFileName() + "  ·  " + mode + (s.getCheatsEnabled() ? "  ·  Cheats" : "") + (s.requiresConversion() ? "  ·  Needs conversion" : "");
                Ui.text(Gfx.trim(info, pw - 160), px + 42, y + 19, DS.T_SMALL, DS.FAINT);
                Ui.textRight(df.format(new Date(s.getLastTimePlayed())), px + pw - 14, y + 13, DS.T_SMALL, DS.MUTED);
                ui.click(px + 4, y, pw - 8, rh, () -> {
                    long now = System.currentTimeMillis();
                    if (selected == s && now - lastClick < 350) play();
                    selected = s;
                    lastClick = now;
                });
            }
            y += rh + 2;
        }
        if (shown == 0) Ui.textCenter(error != null ? error : worlds.isEmpty() ? "No worlds yet. Create one below." : "No world matches the search.",
                px + pw / 2f, ly + lh / 2f - 4, DS.T_BODY, error != null ? DS.BAD : DS.FAINT);
        ui.endScroll(shown * (rh + 2));

        float by = h - 36, bx = px, g = 4;
        boolean sel = selected != null;
        ui.button("play", "Play", bx, by, 80, 22, Ui.PRIMARY, sel && !loading, Glyph.PLAY, this::play);
        bx += 80 + g;
        ui.button("create", "Create", bx, by, 72, 22, Ui.SECONDARY, true, Glyph.PLUS, () -> mc.displayGuiScreen(new GuiCreateWorld(this)));
        bx += 72 + g;
        ui.button("rename", "Rename", bx, by, 72, 22, Ui.SECONDARY, sel, Glyph.PENCIL, () -> mc.displayGuiScreen(new GuiRenameWorld(this, selected.getFileName())));
        bx += 72 + g;
        ui.button("delete", "Delete", bx, by, 72, 22, Ui.DANGER, sel, Glyph.TRASH, this::delete);
        float rx = px + pw;
        ui.iconButton("back", rx - 22, by, 22, Glyph.BACK, "Back", true, this::back);
        ui.iconButton("classic", rx - 48, by, 22, Glyph.EYE, "Classic world list", true, () -> MenuSystem.openVanilla(new GuiSelectWorld(parent)));
        ui.iconButton("folder", rx - 74, by, 22, Glyph.FOLDER, "Open saves folder", true, () -> Os.open(new File(mc.mcDataDir, "saves")));
    }

    private void play() {
        if (selected == null || loading) return;
        ISaveFormat sf = mc.getSaveLoader();
        String file = selected.getFileName();
        String name = selected.getDisplayName() == null || selected.getDisplayName().isEmpty() ? file : selected.getDisplayName();
        if (!sf.canLoadWorld(file)) {
            error = "This world cannot be loaded.";
            return;
        }
        loading = true;
        GuiSelectWorld owner = new GuiSelectWorld(parent);
        mc.displayGuiScreen(null);
        net.minecraftforge.fml.client.FMLClientHandler.instance().tryLoadExistingWorld(owner, file, name);
    }

    private void delete() {
        if (selected == null) return;
        deleting = true;
        String name = selected.getDisplayName() == null ? selected.getFileName() : selected.getDisplayName();
        mc.displayGuiScreen(new GuiYesNo(this, I18n.format("selectWorld.deleteQuestion"),
                "'" + name + "' " + I18n.format("selectWorld.deleteWarning"), I18n.format("selectWorld.deleteButton"), I18n.format("gui.cancel"), 0));
    }

    @Override
    public void confirmClicked(boolean ok, int id) {
        if (deleting && ok && selected != null) {
            ISaveFormat sf = mc.getSaveLoader();
            sf.flushCache();
            sf.deleteWorldDirectory(selected.getFileName());
            selected = null;
        }
        deleting = false;
        mc.displayGuiScreen(this);
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (!ui.typing() && (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER)) {
            play();
            return;
        }
        super.keyTyped(c, key);
    }
}
