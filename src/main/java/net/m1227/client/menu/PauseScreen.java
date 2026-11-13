package net.m1227.client.menu;

import net.m1227.client.settings.SettingsScreen;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.module.InterfaceModule;
import net.m1227.ui.ClickGui;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.gui.achievement.GuiAchievements;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * The 1227 pause menu. Left: Back to game, Options, Open to LAN, Achievements, Statistics,
 * Mod Options and Save and Quit / Disconnect (same steps as Minecraft's own menu). Right: shortcuts
 * to the HUD editor, modules, cosmetics, visuals, performance, profiles, keybinds, client
 * settings, screenshots and music, so nothing needs leaving the match.
 */
public final class PauseScreen extends UiScreen {
    private boolean blur, quitting;

    public PauseScreen() {
        super(null);
    }

    @Override
    public void initGui() {
        super.initGui();
        InterfaceModule m = InterfaceModule.INSTANCE;
        if (m != null && m.pauseBlur.on() && !blur) {
            try {
                if (net.minecraft.client.renderer.OpenGlHelper.shadersSupported && !mc.entityRenderer.isShaderActive()) {
                    mc.entityRenderer.loadShader(new net.minecraft.util.ResourceLocation("shaders/post/blur.json"));
                    blur = mc.entityRenderer.isShaderActive();
                }
            } catch (Throwable t) {
                blur = false;
            }
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (blur && mc.entityRenderer.isShaderActive()) mc.entityRenderer.stopUseShader();
        blur = false;
    }

    @Override
    protected void back() {
        mc.displayGuiScreen(null);
        mc.setIngameFocus();
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, true);
        float a = appear();
        float x = Math.max(16, w * 0.06f), colW = 150;
        boolean sp = mc.isSingleplayer();
        boolean canLan = sp && mc.getIntegratedServer() != null && !mc.getIntegratedServer().getPublic();
        String quit = mc.isIntegratedServerRunning() ? "Save and quit" : "Disconnect";

        float y = Math.max(16, h / 2f - 118);
        Ui.textBold("Paused", x, y, DS.T_TITLE, Gfx.alpha(DS.TEXT, a));
        String where = sp ? "Singleplayer" : mc.getCurrentServerData() != null ? mc.getCurrentServerData().serverIP : "Multiplayer";
        Ui.text(Gfx.trim(where, colW), x, y + 16, DS.T_SMALL, DS.FAINT);
        y += 32;
        float bh = 21, g = 4;
        ui.button("resume", "Back to game", x, y, colW, bh, Ui.PRIMARY, true, Glyph.PLAY, this::back);
        y += bh + g;
        ui.button("opts", "Options", x, y, colW, bh, Ui.SECONDARY, true, Glyph.SLIDERS, () -> mc.displayGuiScreen(new SettingsScreen(this, null)));
        y += bh + g;
        if (sp) {
            ui.button("lan", "Open to LAN", x, y, colW, bh, Ui.SECONDARY, canLan, Glyph.LAN, () -> mc.displayGuiScreen(new GuiShareToLan(this)));
            y += bh + g;
        }
        float half = (colW - g) / 2f;
        ui.button("ach", "Achievements", x, y, half, bh, Ui.SECONDARY, mc.thePlayer != null, -1,
                () -> mc.displayGuiScreen(new GuiAchievements(this, mc.thePlayer.getStatFileWriter())));
        ui.button("stats", "Statistics", x + half + g, y, half, bh, Ui.SECONDARY, mc.thePlayer != null, -1,
                () -> mc.displayGuiScreen(new GuiStats(this, mc.thePlayer.getStatFileWriter())));
        y += bh + g;
        ui.button("modopts", "Mod options", x, y, colW, bh, Ui.SECONDARY, true, Glyph.MODS,
                () -> mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this))); // what Forge 1.8.9 "Mod Options" opens
        y += bh + g;
        ui.button("classic", "Classic pause menu", x, y, colW, bh, Ui.GHOST, true, Glyph.EYE, () -> MenuSystem.openVanilla(new GuiIngameMenu()));
        y += bh + g + 6;
        ui.button("quit", quit, x, y, colW, bh, Ui.DANGER, !quitting, Glyph.POWER, this::quit);

        // shortcuts
        float gx = x + colW + 28;
        float avail = w - gx - 16;
        if (avail < 150) return;
        String[] names = {"HUD editor", "Modules", "Cosmetics", "Weapons", "Skies", "Visuals", "Performance", "Profiles", "Keybinds", "Client", "Screenshots", "Music"};
        int[] gl = {Glyph.HUD, Glyph.GEAR, Glyph.CROWN, Glyph.SWORD, Glyph.WORLD, Glyph.EYE, Glyph.BOLT, Glyph.PERSON, Glyph.KEY, Glyph.PALETTE, Glyph.CAMERA, Glyph.SOUND};
        int cols = avail > 330 ? 5 : avail > 250 ? 4 : 2;
        float tw = Math.min(78, (avail - (cols - 1) * 6) / cols), th = 50;
        float ty = Math.max(16, h / 2f - 118) + 32;
        Ui.caption("Shortcuts", gx, ty - 12);
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            float tx = gx + (i % cols) * (tw + 6), yy = ty + (i / cols) * (th + 6);
            float d = Math.max(0f, Math.min(1f, a * 1.5f - i * 0.05f));
            tile(names[i], gl[i], tx, yy + (1 - d) * 6, tw, th, d, () -> shortcut(k));
        }
    }

    private void tile(String label, int glyph, float x, float y, float w, float h, float alpha, Runnable r) {
        boolean hv = ui.hover(x, y, w, h);
        float k = ui.anim("tile" + label, hv ? 1f : 0f);
        Gfx.roundedRect(x, y, w, h, DS.R_LG, Gfx.alpha(Gfx.mix(0xC0141720, 0xE81E2130, k), alpha));
        Gfx.roundedOutline(x, y, w, h, DS.R_LG, 0.6f, Gfx.alpha(Gfx.mix(DS.LINE, Gfx.alpha(DS.accent(), 0.6f), k), alpha));
        Glyph.draw(glyph, x + w / 2f, y + 18 - k, Gfx.alpha(Gfx.mix(DS.MUTED, DS.accent(), k), alpha));
        Ui.textCenter(Gfx.trim(label, w - 6), x + w / 2f, y + h - 15, DS.T_SMALL, Gfx.alpha(DS.TEXT, alpha));
        ui.click(x, y, w, h, r);
    }

    private void shortcut(int i) {
        switch (i) {
            case 0:
                mc.displayGuiScreen(new net.m1227.hud.HudEditorScreen());
                break;
            case 1:
                ClickGui.open(null, this);
                break;
            case 2:
                mc.displayGuiScreen(new net.m1227.client.settings.CosmeticsScreen(this));
                break;
            case 3:
                mc.displayGuiScreen(new net.m1227.client.settings.WeaponEditorScreen(this));
                break;
            case 4:
                mc.displayGuiScreen(new net.m1227.client.settings.SkyGalleryScreen(this));
                break;
            case 5:
                mc.displayGuiScreen(new SettingsScreen(this, "Visuals"));
                break;
            case 6:
                mc.displayGuiScreen(new SettingsScreen(this, "Performance"));
                break;
            case 7:
                mc.displayGuiScreen(new SettingsScreen(this, "Profiles"));
                break;
            case 8:
                mc.displayGuiScreen(new GuiControls(this, mc.gameSettings));
                break;
            case 9:
                mc.displayGuiScreen(new SettingsScreen(this, "Interface"));
                break;
            case 10:
                mc.displayGuiScreen(new ScreenshotScreen(this));
                break;
            default:
                mc.displayGuiScreen(new net.m1227.ui.MusicScreen());
        }
    }

    /** Same steps as Minecraft's pause menu "Disconnect" / "Save and Quit to Title". */
    private void quit() {
        quitting = true;
        boolean integrated = mc.isIntegratedServerRunning();
        if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld((WorldClient) null);
        if (integrated) mc.displayGuiScreen(new GuiMainMenu());
        else mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
    }
}
