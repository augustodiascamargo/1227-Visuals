package net.m1227.client.menu;

import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;

/**
 * The 1227 death screen (not used in hardcore, which keeps Minecraft's own). Respawn and Title
 * screen do exactly what Minecraft's buttons do; Title screen asks for a second click instead of
 * a separate confirmation screen. Buttons wake up after one second, like vanilla.
 */
public final class DeathScreen extends UiScreen {
    private int ticks;
    private boolean confirmQuit;

    public DeathScreen() {
        super(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void back() {
        // Esc cannot skip the death screen (as in vanilla)
    }

    @Override
    public void updateScreen() {
        ticks++;
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        float a = appear();
        Gfx.gradient(0, 0, w, h, Gfx.alpha(0xFF3A0A12, 0.55f * a), Gfx.alpha(0xFF0B0C11, 0.85f * a));
        float cy = h / 2f - 60;
        Ui.textCenter("You died", w / 2f, cy, DS.T_DISPLAY, Gfx.alpha(0xFFFF8A8A, a));
        int score = mc.thePlayer == null ? 0 : mc.thePlayer.getScore();
        Ui.textCenter("Score  " + score, w / 2f, cy + 30, DS.T_BODY, Gfx.alpha(DS.MUTED, a));
        boolean ready = ticks >= 20;
        float bw = 160, bx = w / 2f - bw / 2f, by = cy + 54;
        ui.button("respawn", "Respawn", bx, by, bw, 22, Ui.PRIMARY, ready, Glyph.REFRESH, this::respawn);
        String q = confirmQuit ? "Click again to leave" : "Title screen";
        ui.button("title", q, bx, by + 27, bw, 22, confirmQuit ? Ui.DANGER : Ui.SECONDARY, ready && mc.getSession() != null, Glyph.BACK, () -> {
            if (!confirmQuit) {
                confirmQuit = true;
                return;
            }
            if (mc.theWorld != null) mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld((WorldClient) null);
            mc.displayGuiScreen(new GuiMainMenu());
        });
    }

    private void respawn() {
        if (mc.thePlayer != null) mc.thePlayer.respawnPlayer();
        mc.displayGuiScreen(null);
    }
}
