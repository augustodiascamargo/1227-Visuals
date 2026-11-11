package net.m1227.ui;

import java.io.IOException;
import net.m1227.module.ClientModule;
import net.m1227.module.EmotesModule;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Radial emote picker: hover a slice and click, or release the wheel key on it. */
public final class EmoteWheel extends GuiScreen {
    private final EmotesModule mod;
    private final int key;
    private long opened;
    private int hover = -1;

    public EmoteWheel(EmotesModule mod, int key) {
        this.mod = mod;
        this.key = key;
    }

    @Override
    public void initGui() {
        opened = System.currentTimeMillis();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        float o = Math.min(1f, (System.currentTimeMillis() - opened) / 160f);
        int sf = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        Gfx.smoothFont(sf);
        try {
            float cx = width / 2f, cy = height / 2f, r0 = 34f, r1 = 96f * (0.9f + 0.1f * o);
            int acc = 0xFF000000 | ClientModule.accent();
            Gfx.rect(0, 0, width, height, Gfx.alpha(0xFF05060A, 0.35f * o));
            int n = EmotesModule.EMOTES.length;
            double ang = Math.atan2(my - cy, mx - cx), dist = Math.hypot(mx - cx, my - cy);
            hover = -1;
            if (dist > r0 * 0.7) {
                double a = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 4) % (Math.PI * 2);
                hover = (int) (a / (Math.PI * 2 / n)) % n;
            }
            Gfx.circle(cx, cy, r1 + 4, Gfx.alpha(0xFF0B0C11, 0.75f * o));
            for (int i = 0; i < n; i++) {
                double a0 = -Math.PI / 2 - Math.PI / n + i * Math.PI * 2 / n, a1 = a0 + Math.PI * 2 / n;
                boolean h = i == hover;
                // slice as a fan of thin quads
                for (int k = 0; k < 8; k++) {
                    double b0 = a0 + (a1 - a0) * k / 8 + 0.01, b1 = a0 + (a1 - a0) * (k + 1) / 8 - (k == 7 ? 0.01 : 0);
                    float x0 = cx + (float) Math.cos(b0) * r0, y0 = cy + (float) Math.sin(b0) * r0;
                    float x1 = cx + (float) Math.cos(b0) * r1, y1 = cy + (float) Math.sin(b0) * r1;
                    float x2 = cx + (float) Math.cos(b1) * r1, y2 = cy + (float) Math.sin(b1) * r1;
                    float x3 = cx + (float) Math.cos(b1) * r0, y3 = cy + (float) Math.sin(b1) * r0;
                    Gfx.quad(x0, y0, x1, y1, x2, y2, x3, y3, Gfx.alpha(h ? acc : 0xFF1A1D27, (h ? 0.85f : 0.7f) * o));
                }
                double mid = (a0 + a1) / 2;
                float tx = cx + (float) Math.cos(mid) * (r0 + r1) / 2, ty = cy + (float) Math.sin(mid) * (r0 + r1) / 2;
                String s = EmotesModule.EMOTES[i];
                Gfx.weight(h ? TtfFont.MEDIUM : TtfFont.REGULAR);
                Gfx.textScaled(s, tx - Gfx.textWidth(s, 0.8f) / 2f, ty - 3f, 0.8f, Gfx.alpha(h ? 0xFFFFFFFF : 0xFFC9CCD6, o));
                Gfx.weight(TtfFont.REGULAR);
            }
            Gfx.circle(cx, cy, r0 - 4, Gfx.alpha(0xFF0B0C11, 0.9f * o));
            Gfx.logo(cx - 14, cy - 16, 28, Gfx.alpha(acc, o), 0.6f);
            String hint = mod.playing() ? "Click the centre to stop" : "Pick an emote";
            Gfx.textScaled(hint, cx - Gfx.textWidth(hint, 0.75f) / 2f, cy + r1 + 12, 0.75f, Gfx.alpha(0xFFA9ADBA, o));
        } finally {
            Gfx.vanillaFont();
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (hover >= 0) mod.play(hover);
        else mod.stop();
        mc.displayGuiScreen(null);
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        // releasing the wheel key picks the emote under the mouse
        if (!Keyboard.getEventKeyState() && Keyboard.getEventKey() == key) {
            if (hover >= 0) mod.play(hover);
            mc.displayGuiScreen(null);
        }
    }
}
