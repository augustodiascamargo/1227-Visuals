package net.m1227.client.settings;

import java.util.HashSet;
import java.util.Set;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.core.InputManager;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.ui.Gfx;

/**
 * Builds the rows of a settings page top to bottom (label on the left, control on the right),
 * with the shared look of the design system. Rows outside the visible area only advance the
 * cursor. A search filter hides rows whose label does not match.
 */
final class Rows {
    interface Sink {
        void set(float v01);
    }

    private final Ui ui;
    final float x, w;
    float y;
    private final float viewTop, viewBottom;
    private final String filter;
    /** Colour settings currently expanded (shared across frames). */
    static final Set<Setting.Col> OPEN_COLORS = new HashSet<Setting.Col>();
    /** Key setting waiting for a key, or null. */
    static Setting.Key capturing;

    static final float CONTROL_W = 132;

    Rows(Ui ui, float x, float y, float w, float viewTop, float viewBottom, String filter) {
        this.ui = ui;
        this.x = x;
        this.y = y;
        this.w = w;
        this.viewTop = viewTop;
        this.viewBottom = viewBottom;
        this.filter = filter == null ? "" : filter.trim().toLowerCase();
    }

    private boolean match(String label) {
        return filter.isEmpty() || label.toLowerCase().contains(filter);
    }

    private boolean visible(float h) {
        return y + h > viewTop && y < viewBottom;
    }

    float controlX() {
        return x + w - CONTROL_W - 8;
    }

    void space(float h) {
        y += h;
    }

    void section(String title) {
        if (!filter.isEmpty()) return;
        y += 6;
        if (visible(14)) Ui.caption(title, x + 2, y + 2);
        y += 14;
    }

    void note(String text) {
        if (!filter.isEmpty()) return;
        for (String l : Gfx.wrap(text, w - 12)) {
            if (visible(10)) Ui.text(l, x + 4, y, DS.T_SMALL, DS.FAINT);
            y += 10;
        }
        y += 4;
    }

    /** Row background and label (+ optional description); returns the control's y centre. */
    private float base(String label, String desc, float h) {
        boolean hv = ui.hover(x, y, w, h);
        float k = ui.anim("row" + label + desc, hv ? 1f : 0f);
        Gfx.roundedRect(x, y, w, h, DS.R_MD, Gfx.mix(DS.SURFACE, DS.SURFACE_2, k));
        float labelW = w - CONTROL_W - 24;
        if (desc != null && !desc.isEmpty()) {
            Ui.textMedium(Gfx.trim(label, labelW), x + 10, y + 6, DS.T_BODY, DS.TEXT);
            Ui.text(Gfx.trim(desc, labelW), x + 10, y + 17, DS.T_SMALL, DS.FAINT);
        } else {
            Ui.text(Gfx.trim(label, labelW), x + 10, y + h / 2f - 3.6f, DS.T_BODY, DS.TEXT);
        }
        return y + h / 2f;
    }

    private float height(String desc) {
        return desc != null && !desc.isEmpty() ? 30 : 24;
    }

    void toggle(String label, String desc, boolean on, Runnable flip) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            ui.toggle(label + "#t", x + w - 34, cy - 6.5f, on, flip);
        }
        y += h + 3;
    }

    void slider(String label, String desc, String value, float v01, final Sink sink) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            float cx = controlX();
            Ui.textRight(value, x + w - 10, cy - 3.6f, DS.T_SMALL, DS.MUTED);
            float sw = CONTROL_W - Math.max(34, Ui.width(value, DS.T_SMALL) + 8);
            ui.slider(label + "#s", cx, cy - 3, sw, v01, v -> sink.set(v));
        }
        y += h + 3;
    }

    void cycle(String label, String desc, String value, Runnable next, Runnable prev) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            ui.cycle(label + "#c", controlX(), cy - 8, CONTROL_W, 16, value, next, prev);
        }
        y += h + 3;
    }

    void button(String label, String desc, String text, int style, Runnable r) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            float bw = Math.min(CONTROL_W, Ui.width(text, DS.T_BODY) + 24);
            ui.button(label + "#b", text, x + w - bw - 8, cy - 8, bw, 16, style, true, -1, r);
        }
        y += h + 3;
    }

    void text(String label, String desc, String value, String placeholder, int max, Ui.TextSink sink) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            ui.textField(label + "#f", controlX(), cy - 8, CONTROL_W, 16, value, placeholder, max, sink);
        }
        y += h + 3;
    }

    /** Row with several buttons on the right (first = rightmost is last in the arrays). */
    void buttons(String label, String desc, String[] texts, int[] styles, Runnable[] actions) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            float rx = x + w - 8;
            for (int i = texts.length - 1; i >= 0; i--) {
                float bw = Ui.width(texts[i], DS.T_BODY) + 20;
                rx -= bw;
                ui.button(label + "#" + i, texts[i], rx, cy - 8, bw, 16, styles[i], actions[i] != null, -1, actions[i]);
                rx -= 4;
            }
        }
        y += h + 3;
    }

    /** Read-only value on the right. */
    void info(String label, String desc, String value) {
        if (!match(label)) return;
        float h = height(desc);
        if (visible(h)) {
            float cy = base(label, desc, h);
            Ui.textRight(value, x + w - 10, cy - 3.6f, DS.T_BODY, DS.MUTED);
        }
        y += h + 3;
    }

    /** A module: on/off switch (if it can be turned off) and a gear that opens its settings. */
    void module(final Module m, final Runnable openSettings) {
        if (!match(m.name) && !match(m.description)) return;
        float h = 30;
        if (visible(h)) {
            float cy = base(m.name, m.description, h);
            float rx = x + w - 8;
            boolean hasSettings = false;
            for (Setting<?> s : m.settings()) if (s.visible()) hasSettings = true;
            if (hasSettings) {
                ui.iconButton(m.name + "#g", rx - 18, cy - 9, 18, Glyph.GEAR, "Settings", true, openSettings);
                rx -= 24;
            }
            if (m.toggleable()) ui.toggle(m.name + "#on", rx - 26, cy - 6.5f, m.isEnabled(), m::toggle);
        }
        y += h + 3;
    }

    // ---------------------------------------------------------------- generic settings

    static String num(Setting.Num n) {
        double v = n.get();
        String s = n.step >= 1 ? String.valueOf(Math.round(v)) : n.step >= 0.1 ? String.format("%.1f", v) : String.format("%.2f", v);
        return s + (n.suffix == null || n.suffix.isEmpty() ? "" : n.suffix.equals("x") ? "x" : " " + n.suffix);
    }

    void setting(final Setting<?> s) {
        if (!s.visible()) return;
        if (s instanceof Setting.Bool) {
            final Setting.Bool b = (Setting.Bool) s;
            toggle(s.name, null, b.on(), b::toggle);
        } else if (s instanceof Setting.Num) {
            final Setting.Num n = (Setting.Num) s;
            float v01 = (float) ((n.get() - n.min) / Math.max(1e-9, n.max - n.min));
            slider(s.name, null, num(n), v01, v -> n.setClamped(n.min + v * (n.max - n.min)));
        } else if (s instanceof Setting.Mode) {
            final Setting.Mode md = (Setting.Mode) s;
            cycle(s.name, null, md.get(), () -> md.cycle(1), () -> md.cycle(-1));
        } else if (s instanceof Setting.Col) {
            color((Setting.Col) s);
        } else if (s instanceof Setting.Key) {
            final Setting.Key k = (Setting.Key) s;
            String v = capturing == k ? "Press a key (Esc = none)" : k.code() == 0 ? "None" : InputManager.keyName(k.code());
            button(s.name, null, v, capturing == k ? Ui.PRIMARY : Ui.SECONDARY, () -> capturing = capturing == k ? null : k);
        } else if (s instanceof Setting.Action) {
            final Setting.Action a = (Setting.Action) s;
            button(s.name, null, "Run", Ui.SECONDARY, a::run);
        } else if (s instanceof Setting.Text) {
            final Setting.Text t = (Setting.Text) s;
            text(s.name, null, t.get(), t.placeholder, t.maxLength, t::set);
        }
    }

    private void color(final Setting.Col c) {
        if (!match(c.name)) return;
        boolean open = OPEN_COLORS.contains(c);
        float h = 24;
        if (visible(h)) {
            float cy = base(c.name, null, h);
            float sx = x + w - 30;
            Gfx.roundedRect(sx, cy - 7, 22, 14, 4, 0xFF000000 | c.rgb());
            Gfx.roundedOutline(sx, cy - 7, 22, 14, 4, 0.6f, DS.LINE_STRONG);
            String hex = String.format("#%06X", c.rgb());
            Ui.textRight(hex, sx - 6, cy - 3.6f, DS.T_SMALL, DS.MUTED);
            ui.click(x, y, w, h, () -> {
                if (!OPEN_COLORS.remove(c)) OPEN_COLORS.add(c);
            });
        }
        y += h + 3;
        if (!open) return;
        // hue, saturation and brightness sliders
        String[] names = {"Hue", "Saturation", "Brightness"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            float v = i == 0 ? c.hue : i == 1 ? c.sat : c.bri;
            if (visible(18)) {
                Ui.text(names[i], x + 22, y + 5, DS.T_SMALL, DS.MUTED);
                float bx = x + 90, bw = w - 110;
                if (i == 0) Gfx.hueBar(bx, y + 6, bw, 4, 1f);
                else if (i == 1) Gfx.satBar(bx, y + 6, bw, 4, java.awt.Color.HSBtoRGB(c.hue, 1f, 1f) & 0xFFFFFF, 1f);
                else Gfx.gradientH(bx, y + 6, bw, 4, 0xFF000000, 0xFF000000 | (java.awt.Color.HSBtoRGB(c.hue, c.sat, 1f) & 0xFFFFFF));
                ui.knob(c.name + names[i], bx, y + 5, bw, v, nv -> {
                    if (k == 0) c.setHSB(nv, c.sat, c.bri);
                    else if (k == 1) c.setHSB(c.hue, nv, c.bri);
                    else c.setHSB(c.hue, c.sat, nv);
                });
            }
            y += 18;
        }
        y += 4;
    }
}
