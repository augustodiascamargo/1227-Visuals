package net.m1227.client.settings;

import net.m1227.client.menu.MenuBackground;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Stage3D;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.ConfigManager;
import net.m1227.core.Setting;
import net.m1227.module.WeaponModelsModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.gui.GuiScreen;

/**
 * Weapon editor: the sword model you hold, in 3D, turning (drag to turn it yourself), next to a
 * grid of all models and every option of Weapon Models (finish, colours, glow, length, thickness,
 * width, size, hold position, hands). Every change shows at once; the same settings are used in
 * game.
 */
public final class WeaponEditorScreen extends UiScreen {
    private float yaw = 30f, spinVel;
    private boolean auto = true;
    private long last, dragUntil;
    private float lastDragX = -1;

    public WeaponEditorScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        ConfigManager.save();
    }

    @Override
    protected void render(float pt) {
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        WeaponModelsModule wm = WeaponModelsModule.INSTANCE;
        long n = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (n - last) / 1e9f);
        last = n;

        float ww = Math.min(680, w - 16), wh = Math.min(420, h - 16);
        float wx = (w - ww) / 2f, wy = (h - wh) / 2f;
        Gfx.roundedRect(wx, wy, ww, wh, DS.R_LG, 0xF00E1016);
        Gfx.roundedOutline(wx, wy, ww, wh, DS.R_LG, 0.6f, DS.LINE_STRONG);
        ui.iconButton("back", wx + 10, wy + 10, 18, Glyph.BACK, "Back", true, this::back);
        Ui.textBold("Weapon editor", wx + 34, wy + 13, DS.T_LABEL, DS.TEXT);
        Ui.text("Applies to swords you hold. Drag the weapon to turn it", wx + 34, wy + 26, DS.T_SMALL, DS.FAINT);
        if (wm == null) return;

        // ---- preview
        float sw = ww * 0.46f, sx = wx + 10, sy = wy + 42, sh = wh - 52;
        Gfx.roundedRect(sx, sy, sw, sh, DS.R_LG, 0xFF0A0B10);
        Gfx.glow(sx + sw / 2f, sy + sh / 2f, sh * 0.55f, Gfx.alpha(DS.accent(), 0.14f));
        if (ui.hover(sx, sy, sw, sh) || lastDragX >= 0) {
            ui.drag(sx, sy, sw, sh, (px, py) -> {
                if (lastDragX >= 0) {
                    float d = px - lastDragX;
                    yaw += d * 1.2f;
                    spinVel = d * 1.2f / Math.max(0.008f, dt);
                }
                lastDragX = px;
                dragUntil = System.currentTimeMillis() + 150;
            });
        }
        if (!ui.dragging()) lastDragX = -1;
        if (System.currentTimeMillis() > dragUntil) {
            spinVel *= (float) Math.exp(-4f * dt);
            yaw += spinVel * dt + (auto ? 22f * dt : 0f);
        }
        float scale = Math.min(sw, sh) * 0.62f;
        Stage3D.begin(sx + sw / 2f, sy + sh / 2f, scale, yaw, 12f);
        // stand the blade up: the model runs along the item's diagonal
        net.minecraft.client.renderer.GlStateManager.rotate(45f, 0f, 0f, 1f);
        wm.renderPreview();
        Stage3D.end();
        ui.iconButton("spin", sx + sw - 24, sy + 6, 18, auto ? Glyph.PAUSE : Glyph.PLAY, auto ? "Stop turning" : "Turn by itself", true, () -> auto = !auto);
        if (!wm.isEnabled()) {
            Ui.textCenter("Weapon Models is off: your normal sword shows in game", sx + sw / 2f, sy + sh - 14, DS.T_SMALL, DS.WARN);
        }

        // ---- options
        float lx = sx + sw + 10, lw = wx + ww - 10 - lx, ly = wy + 42, lh = wh - 52;
        float off = ui.beginScroll("weap", lx, ly, lw, lh);
        Rows r = new Rows(ui, lx + 2, ly - off, lw - 8, ly, ly + lh, "");
        float start = r.y;
        r.toggle("Use custom weapon models", null, wm.isEnabled(), wm::toggle);
        r.section("Model");
        Setting.Mode model = null;
        for (Setting<?> s : wm.settings()) if (s.name.equals("Model") && s instanceof Setting.Mode) model = (Setting.Mode) s;
        if (model != null) r.y = chips(model, lx + 2, r.y, lw - 8, ly, ly + lh);
        r.section("Look");
        for (Setting<?> s : wm.settings()) if (!s.name.equals("Model")) r.setting(s);
        r.space(6);
        r.button("Reset weapon", "Every option back to its default", "Reset", Ui.DANGER, wm::reset);
        ui.endScroll(r.y - start + 6);
    }

    /** Grid of model names; the selected one is highlighted. Returns the y after the grid. */
    private float chips(final Setting.Mode model, float x, float y, float w, float top, float bottom) {
        float cx = x, ch = 18;
        for (final String m : WeaponModelsModule.MODELS) {
            float cw = Ui.width(m, DS.T_SMALL) + 14;
            if (cx + cw > x + w) {
                cx = x;
                y += ch + 4;
            }
            boolean on = model.is(m);
            if (y + ch > top && y < bottom) {
                boolean hv = ui.hover(cx, y, cw, ch);
                Gfx.roundedRect(cx, y, cw, ch, 9, on ? DS.accent() : hv ? DS.SURFACE_3 : DS.SURFACE_2);
                Ui.text(m, cx + 7, y + 5, DS.T_SMALL, on ? 0xFFFFFFFF : DS.MUTED);
                ui.click(cx, y, cw, ch, () -> model.set(m));
            }
            cx += cw + 4;
        }
        return y + ch + 8;
    }
}
