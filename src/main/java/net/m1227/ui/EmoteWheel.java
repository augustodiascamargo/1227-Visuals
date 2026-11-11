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
    /** 0 = emotes, 1 = dances; remembered between openings. */
    private static int page;
    private int tabHover = -1;

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
            Gfx.rect(0, 0, width, height, Gfx.alpha(0xFF05060A, 0.22f * o));
            int first = EmotesModule.pageStart(page), n = EmotesModule.pageSize(page);
            // page tabs above the wheel (scroll or right-click also switches)
            tabHover = -1;
            String[] tabs = EmotesModule.PAGES;
            float tw = 64f, tx0 = cx - tw * tabs.length / 2f, tyT = cy - r1 - 30f;
            for (int i = 0; i < tabs.length; i++) {
                float x = tx0 + i * tw + 2;
                boolean on = i == page, hv = mx >= x && mx < x + tw - 4 && my >= tyT && my < tyT + 16;
                if (hv) tabHover = i;
                Gfx.roundedRect(x, tyT, tw - 4, 16, 8, Gfx.alpha(on ? acc : 0xFF1A1D27, (on ? 0.9f : hv ? 0.85f : 0.7f) * o));
                Gfx.weight(on ? TtfFont.MEDIUM : TtfFont.REGULAR);
                Gfx.textScaled(tabs[i], x + (tw - 4) / 2f - Gfx.textWidth(tabs[i], 0.8f) / 2f, tyT + 5f, 0.8f, Gfx.alpha(on ? 0xFFFFFFFF : 0xFFC9CCD6, o));
                Gfx.weight(TtfFont.REGULAR);
            }
            double ang = Math.atan2(my - cy, mx - cx), dist = Math.hypot(mx - cx, my - cy);
            hover = -1;
            if (dist > r0 * 0.7 && dist < r1 + 26) {
                double a = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 4) % (Math.PI * 2);
                hover = (int) (a / (Math.PI * 2 / n)) % n;
            }
            // clean look: one glass disc, hairline dividers, and only the hovered slice filled
            float R = r1 + 6;
            if (o > 0.6f) Glass.panel(cx - R, cy - R, R * 2, R * 2, R, 1f, Theme.hudTint(), 0.30f, 6f * sf, 0.035f);
            else Gfx.circle(cx, cy, R, Gfx.alpha(0xFF0B0C11, 0.6f * o));
            Gfx.annulus(cx, cy, R - 1f, R, 0, Math.PI * 2, Gfx.alpha(0xFFFFFFFF, 0.22f * o));
            Gfx.annulus(cx, cy, r0, r0 + 0.8f, 0, Math.PI * 2, Gfx.alpha(0xFFFFFFFF, 0.14f * o));
            double step = Math.PI * 2 / n;
            for (int i = 0; i < n; i++) {
                double a0 = -Math.PI / 2 - step / 2 + i * step, a1 = a0 + step;
                boolean h = i == hover;
                if (h) {
                    Gfx.annulus(cx, cy, r0 + 2f, r1 + 2f, a0 + 0.012, a1 - 0.012, Gfx.alpha(acc, 0.42f * o));
                    Gfx.annulus(cx, cy, r1 + 1f, r1 + 3f, a0 + 0.012, a1 - 0.012, Gfx.alpha(acc, 0.95f * o));
                }
                float sx = (float) Math.cos(a0), sy = (float) Math.sin(a0);
                Gfx.line(cx + sx * (r0 + 6f), cy + sy * (r0 + 6f), cx + sx * (r1 - 2f), cy + sy * (r1 - 2f), 0.6f, Gfx.alpha(0xFFFFFFFF, 0.10f * o));
                double mid = (a0 + a1) / 2;
                float tx = cx + (float) Math.cos(mid) * (r0 + r1) / 2, ty = cy + (float) Math.sin(mid) * (r0 + r1) / 2;
                String s = EmotesModule.EMOTES[first + i];
                Gfx.weight(h ? TtfFont.MEDIUM : TtfFont.REGULAR);
                Gfx.textScaled(s, tx - Gfx.textWidth(s, 0.8f) / 2f, ty - 3f, 0.8f, Gfx.alpha(h ? 0xFFFFFFFF : 0xFFC9CCD6, o));
                Gfx.weight(TtfFont.REGULAR);
            }
            Gfx.circle(cx, cy, r0 - 3, Gfx.alpha(0xFF0B0C11, 0.55f * o));
            Gfx.logo(cx - 14, cy - 16, 28, Gfx.alpha(acc, o), 0.6f);
            String hint = mod.playing() ? "Click the centre to stop" : "Pick one  \u00b7  scroll to switch page";
            Gfx.textScaled(hint, cx - Gfx.textWidth(hint, 0.75f) / 2f, cy + r1 + 12, 0.75f, Gfx.alpha(0xFFA9ADBA, o));
        } finally {
            Gfx.vanillaFont();
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        if (tabHover >= 0) {
            page = tabHover;
            return;
        }
        if (button == 1) {
            page = (page + 1) % EmotesModule.PAGES.length;
            return;
        }
        if (hover >= 0) mod.play(EmotesModule.pageStart(page) + hover);
        else mod.stop();
        mc.displayGuiScreen(null);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = org.lwjgl.input.Mouse.getEventDWheel();
        if (d != 0) {
            int n = EmotesModule.PAGES.length;
            page = ((page + (d < 0 ? 1 : -1)) % n + n) % n;
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        super.handleKeyboardInput();
        // releasing the wheel key picks the emote under the mouse
        if (!Keyboard.getEventKeyState() && Keyboard.getEventKey() == key) {
            if (hover >= 0) mod.play(EmotesModule.pageStart(page) + hover);
            mc.displayGuiScreen(null);
        }
    }
}
