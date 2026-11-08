package net.m1227.hud;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.module.ClientModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

/**
 * HUD layout editor. Drag elements to move them (they snap to screen
 * edges, the centre lines and other elements), scroll over one to resize,
 * right-click to reset it. The chips at the top turn elements on and off.
 */
public final class HudEditorScreen extends GuiScreen {
    private static final float SNAP = 4f;
    private final List<HudModule> huds = new ArrayList<HudModule>();
    private HudModule dragging;
    private float offX, offY;
    private float guideX = -1, guideY = -1;
    private long opened;

    public HudEditorScreen() {
        for (Module m : ModuleManager.inCategory(Category.HUD)) {
            if (m instanceof HudModule) huds.add((HudModule) m);
        }
    }

    @Override
    public void initGui() {
        opened = System.currentTimeMillis();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int accent() {
        return 0xFF000000 | ClientModule.accent();
    }

    private ScaledResolution sr() {
        return new ScaledResolution(mc);
    }

    private HudModule at(int mx, int my) {
        ScaledResolution sr = sr();
        for (int i = huds.size() - 1; i >= 0; i--) {
            HudModule h = huds.get(i);
            if (!h.isEnabled()) continue;
            float x = h.screenX(sr), y = h.screenY(sr), w = h.width() * h.scale.f(), hh = h.height() * h.scale.f();
            if (mx >= x && mx <= x + w && my >= y && my <= y + hh) return h;
        }
        return null;
    }

    // ------------------------------------------------------------- chips

    private float chipsY() {
        return 6;
    }

    /** Returns chip x positions; lays chips out centred on one row. */
    private float[] chipX() {
        float total = 0;
        float[] w = new float[huds.size()];
        for (int i = 0; i < huds.size(); i++) {
            w[i] = Gfx.textWidth(huds.get(i).name) + 16;
            total += w[i] + 4;
        }
        float[] x = new float[huds.size() + 1];
        float cx = (width - total + 4) / 2f;
        for (int i = 0; i < huds.size(); i++) {
            x[i] = cx;
            cx += w[i] + 4;
        }
        x[huds.size()] = cx;
        return x;
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        ScaledResolution sr = sr();
        float in = Math.min(1f, (System.currentTimeMillis() - opened) / 200f);
        Gfx.rect(0, 0, width, height, Gfx.alpha(0xFF05060A, 0.35f * in));
        // dot grid + centre lines
        int dot = Gfx.alpha(0xFFFFFFFF, 0.06f * in);
        for (int gx = 10; gx < width; gx += 10) {
            for (int gy = 10; gy < height; gy += 10) Gfx.rect(gx, gy, 1, 1, dot);
        }
        Gfx.rect(width / 2f - 0.5f, 0, 1, height, Gfx.alpha(0xFFFFFFFF, 0.08f * in));
        Gfx.rect(0, height / 2f - 0.5f, width, 1, Gfx.alpha(0xFFFFFFFF, 0.08f * in));

        HudModule hover = dragging != null ? dragging : at(mx, my);
        for (HudModule h : huds) {
            if (!h.isEnabled()) continue;
            h.render(sr, true);
            float x = h.screenX(sr), y = h.screenY(sr), w = h.width() * h.scale.f(), hh = h.height() * h.scale.f();
            int c = h == hover ? accent() : Gfx.alpha(0xFFFFFFFF, 0.25f);
            Gfx.rect(x - 1, y - 1, w + 2, 1, c);
            Gfx.rect(x - 1, y + hh, w + 2, 1, c);
            Gfx.rect(x - 1, y, 1, hh, c);
            Gfx.rect(x + w, y, 1, hh, c);
            if (h == hover) {
                String lbl = h.name + "  " + Math.round(h.scale.f() * 100) + "%";
                float ly = y > 14 ? y - 11 : y + hh + 3;
                Gfx.roundedRect(x - 1, ly - 1, Gfx.textWidth(lbl) + 6, 10, 3, Gfx.alpha(0xFF0B0C11, 0.85f));
                Gfx.text(lbl, x + 2, ly, 0xFFFFFFFF);
            }
        }
        if (dragging != null) {
            if (guideX >= 0) Gfx.rect(guideX - 0.5f, 0, 1, height, Gfx.alpha(accent(), 0.8f));
            if (guideY >= 0) Gfx.rect(0, guideY - 0.5f, width, 1, Gfx.alpha(accent(), 0.8f));
        }

        // chips
        float[] cx = chipX();
        float cy = chipsY();
        for (int i = 0; i < huds.size(); i++) {
            HudModule h = huds.get(i);
            float w = cx[i + 1] - cx[i] - 4;
            boolean over = mx >= cx[i] && mx <= cx[i] + w && my >= cy && my <= cy + 14;
            int bg = h.isEnabled() ? Gfx.alpha(accent(), over ? 0.95f : 0.8f) : Gfx.alpha(0xFF15171F, over ? 0.95f : 0.85f);
            Gfx.roundedRect(cx[i], cy, w, 14, 7, bg);
            Gfx.text(h.name, cx[i] + 8, cy + 3.5f, h.isEnabled() ? 0xFFFFFFFF : 0xFFA9ADBA);
        }
        String help = "Drag to move  ·  Scroll to resize  ·  Right-click to reset  ·  Esc to save";
        float hw = Gfx.textWidth(help);
        Gfx.roundedRect((width - hw) / 2f - 8, height - 20, hw + 16, 14, 7, Gfx.alpha(0xFF0B0C11, 0.85f));
        Gfx.text(help, (width - hw) / 2f, height - 16.5f, 0xFFA9ADBA);
        super.drawScreen(mx, my, pt);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) throws IOException {
        float[] cx = chipX();
        float cy = chipsY();
        for (int i = 0; i < huds.size(); i++) {
            float w = cx[i + 1] - cx[i] - 4;
            if (mx >= cx[i] && mx <= cx[i] + w && my >= cy && my <= cy + 14) {
                huds.get(i).toggle();
                return;
            }
        }
        HudModule h = at(mx, my);
        if (h == null) return;
        if (button == 1) {
            h.resetPosition();
            ConfigManager.markDirty();
            return;
        }
        if (button == 0) {
            ScaledResolution sr = sr();
            dragging = h;
            offX = mx - h.screenX(sr);
            offY = my - h.screenY(sr);
            huds.remove(h);
            huds.add(h); // draw on top
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (dragging == null) return;
        float w = dragging.width() * dragging.scale.f(), h = dragging.height() * dragging.scale.f();
        float x = mx - offX, y = my - offY;
        guideX = guideY = -1;
        // snap candidates: edges, centre, other elements
        float[] xs = snapX(x, w);
        x = xs[0];
        guideX = xs[1];
        float[] ys = snapY(y, h);
        y = ys[0];
        guideY = ys[1];
        x = Math.max(0, Math.min(width - w, x));
        y = Math.max(0, Math.min(height - h, y));
        dragging.posX.set((double) (x / width));
        dragging.posY.set((double) (y / height));
    }

    private float[] snapX(float x, float w) {
        List<Float> lines = new ArrayList<Float>();
        lines.add(2f);
        lines.add(width - 2f);
        lines.add(width / 2f);
        ScaledResolution sr = sr();
        for (HudModule o : huds) {
            if (o == dragging || !o.isEnabled()) continue;
            float ox = o.screenX(sr);
            lines.add(ox);
            lines.add(ox + o.width() * o.scale.f());
        }
        for (float l : lines) {
            if (Math.abs(x - l) < SNAP) return new float[]{l, l};
            if (Math.abs(x + w - l) < SNAP) return new float[]{l - w, l};
            if (Math.abs(x + w / 2f - l) < SNAP && l == width / 2f) return new float[]{l - w / 2f, l};
        }
        return new float[]{x, -1};
    }

    private float[] snapY(float y, float h) {
        List<Float> lines = new ArrayList<Float>();
        lines.add(2f);
        lines.add(height - 2f);
        lines.add(height / 2f);
        ScaledResolution sr = sr();
        for (HudModule o : huds) {
            if (o == dragging || !o.isEnabled()) continue;
            float oy = o.screenY(sr);
            lines.add(oy);
            lines.add(oy + o.height() * o.scale.f());
        }
        for (float l : lines) {
            if (Math.abs(y - l) < SNAP) return new float[]{l, l};
            if (Math.abs(y + h - l) < SNAP) return new float[]{l - h, l};
            if (Math.abs(y + h / 2f - l) < SNAP && l == height / 2f) return new float[]{l - h / 2f, l};
        }
        return new float[]{y, -1};
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        if (dragging != null) ConfigManager.markDirty();
        dragging = null;
        guideX = guideY = -1;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        HudModule h = at(mx, my);
        if (h == null) return;
        h.scale.setClamped(h.scale.f() + (d > 0 ? 0.05 : -0.05));
        ConfigManager.markDirty();
    }

    @Override
    public void onGuiClosed() {
        ConfigManager.markDirty();
    }
}
