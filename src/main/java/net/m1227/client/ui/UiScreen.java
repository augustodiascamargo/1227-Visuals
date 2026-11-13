package net.m1227.client.ui;

import java.io.IOException;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Base of every 1227 client screen: frame timing, the smooth font, the fade-in, input routing to
 * {@link Ui}, and Esc going back to the screen that opened it.
 */
public abstract class UiScreen extends GuiScreen {
    protected final Ui ui = new Ui();
    protected final GuiScreen parent;
    private long last;
    /** 0..1 fade-in progress. */
    protected float open;

    protected UiScreen(GuiScreen parent) {
        this.parent = parent;
    }

    /** Draws the screen content with the design-system kit (GUI units, smooth font on). */
    protected abstract void render(float pt);

    /** Esc / Back. Default: return to the parent screen (or the game). */
    protected void back() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        last = 0L;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        long now = System.nanoTime();
        float dt = last == 0L ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        open = Math.min(1f, open + dt / DS.OPEN_TIME);
        ui.begin(mouseX, mouseY, dt);
        try {
            Gfx.smoothFont(new ScaledResolution(mc).getScaleFactor());
            render(pt);
            ClientToasts.draw(width, height);
            ui.end(width, height);
        } finally {
            Gfx.vanillaFont();
            Gfx.scissorOff();
        }
    }

    /** Fade-in curve for content (ease out). */
    protected float appear() {
        float t = open;
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        ui.mouseClicked(mx, my, button);
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        ui.mouseDragged(mx, my);
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        ui.mouseReleased();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            ui.wheel(mx, my, d);
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (ui.keyTyped(c, key)) return;
        if (key == Keyboard.KEY_ESCAPE) back();
    }
}
