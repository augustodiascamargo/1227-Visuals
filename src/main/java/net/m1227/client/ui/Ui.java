package net.m1227.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.m1227.ui.Gfx;
import net.m1227.ui.TtfFont;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

/**
 * Immediate-mode component kit of the 1227 design system. A screen calls the component methods
 * every frame (they draw and register their click areas); input is routed back through
 * mouseClicked / mouseDragged / mouseReleased / wheel / keyTyped. All components share the
 * tokens in {@link DS}.
 */
public final class Ui {
    public interface Drag {
        void to(float mx, float my);
    }

    public interface FloatSink {
        void set(float v01);
    }

    public interface TextSink {
        void set(String v);
    }

    public static final int PRIMARY = 0, SECONDARY = 1, GHOST = 2, DANGER = 3;

    private static final class Hit {
        float x, y, w, h;
        float[] clip;
        Runnable left, right;
        Drag drag;
        Object focusKey;
    }

    private static final class Scroll {
        float offset, target, content, view;
    }

    private final List<Hit> hits = new ArrayList<Hit>();
    private final Map<Object, float[]> anims = new HashMap<Object, float[]>();
    private final Map<Object, Scroll> scrolls = new HashMap<Object, Scroll>();
    private final List<Object[]> scrollAreas = new ArrayList<Object[]>();
    private float[] clip;
    private Object scrollId;
    private float scrollX, scrollY, scrollW, scrollH;

    public int mx, my;
    public float dt = 0.016f;
    private Drag dragging;
    private String tip;

    // focused text field
    private Object focus;
    private String focusText = "";
    private int focusMax;
    private TextSink focusSink;

    public void begin(int mx, int my, float dt) {
        this.mx = mx;
        this.my = my;
        this.dt = dt;
        hits.clear();
        scrollAreas.clear();
        tip = null;
        clip = null;
    }

    /** Draws the tooltip; call last. */
    public void end(int screenW, int screenH) {
        if (tip == null) return;
        List<String> lines = Gfx.wrap(tip, 180);
        float w = 0;
        for (String s : lines) w = Math.max(w, Gfx.textWidth(s, DS.T_SMALL));
        float h = lines.size() * 9 + 8;
        float x = Math.min(mx + 10, screenW - w - 14), y = Math.min(my + 12, screenH - h - 4);
        Gfx.roundedRect(x, y, w + 12, h, DS.R_SM, 0xF0181B24);
        Gfx.roundedOutline(x, y, w + 12, h, DS.R_SM, 0.6f, DS.LINE_STRONG);
        for (int i = 0; i < lines.size(); i++) Gfx.textScaled(lines.get(i), x + 6, y + 5 + i * 9, DS.T_SMALL, DS.TEXT);
    }

    // ------------------------------------------------------------------ basics

    /** Smoothly approaches target (0..1) for the given key; returns the current value. */
    public float anim(Object key, float target) {
        float[] a = anims.get(key);
        if (a == null) {
            a = new float[]{target};
            anims.put(key, a);
        }
        a[0] += (target - a[0]) * (1f - (float) Math.exp(-DS.EASE_RATE * dt));
        if (Math.abs(target - a[0]) < 0.002f) a[0] = target;
        return a[0];
    }

    public boolean hover(float x, float y, float w, float h) {
        if (clip != null && !(mx >= clip[0] && mx < clip[0] + clip[2] && my >= clip[1] && my < clip[1] + clip[3])) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public boolean dragging() {
        return dragging != null;
    }

    public void tip(String text) {
        tip = text;
    }

    private Hit hit(float x, float y, float w, float h) {
        Hit t = new Hit();
        t.x = x;
        t.y = y;
        t.w = w;
        t.h = h;
        t.clip = clip;
        hits.add(t);
        return t;
    }

    public void click(float x, float y, float w, float h, Runnable left) {
        hit(x, y, w, h).left = left;
    }

    public void click(float x, float y, float w, float h, Runnable left, Runnable right) {
        Hit t = hit(x, y, w, h);
        t.left = left;
        t.right = right;
    }

    public void drag(float x, float y, float w, float h, Drag d) {
        hit(x, y, w, h).drag = d;
    }

    // ------------------------------------------------------------------ input

    private static boolean in(Hit t, float px, float py) {
        if (t.clip != null && !(px >= t.clip[0] && px < t.clip[0] + t.clip[2] && py >= t.clip[1] && py < t.clip[1] + t.clip[3])) return false;
        return px >= t.x && px < t.x + t.w && py >= t.y && py < t.y + t.h;
    }

    /** Returns true when a component took the click. */
    public boolean mouseClicked(int px, int py, int button) {
        Object newFocus = null;
        boolean took = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit t = hits.get(i);
            if (!in(t, px, py)) continue;
            if (button == 0 && t.drag != null) {
                dragging = t.drag;
                t.drag.to(px, py);
                took = true;
            } else if (button == 0 && t.left != null) {
                t.left.run();
                took = true;
            } else if (button == 1 && t.right != null) {
                t.right.run();
                took = true;
            }
            if (t.focusKey != null) newFocus = t.focusKey;
            if (took) break;
        }
        if (newFocus == null && focus != null) commitFocus();
        return took;
    }

    public void mouseDragged(int px, int py) {
        if (dragging != null) dragging.to(px, py);
    }

    public void mouseReleased() {
        dragging = null;
    }

    /** Mouse wheel: scrolls the innermost scroll area under the cursor. */
    public boolean wheel(int px, int py, int delta) {
        for (int i = scrollAreas.size() - 1; i >= 0; i--) {
            Object[] a = scrollAreas.get(i);
            float x = (Float) a[1], y = (Float) a[2], w = (Float) a[3], h = (Float) a[4];
            if (px >= x && px < x + w && py >= y && py < y + h) {
                Scroll s = scrolls.get(a[0]);
                if (s == null) return false;
                s.target = clampScroll(s, s.target - Math.signum(delta) * 30f);
                return true;
            }
        }
        return false;
    }

    /** Keyboard for the focused text field; returns true when consumed. */
    public boolean keyTyped(char c, int key) {
        if (focus == null) return false;
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_TAB) {
            commitFocus();
            return true;
        }
        if (key == Keyboard.KEY_BACK) {
            if (!focusText.isEmpty()) focusText = focusText.substring(0, focusText.length() - 1);
        } else if (GuiScreen.isKeyComboCtrlV(key)) {
            focusText = (focusText + ChatAllowedCharacters.filterAllowedCharacters(GuiScreen.getClipboardString()));
        } else if (ChatAllowedCharacters.isAllowedCharacter(c)) {
            focusText += c;
        }
        if (focusText.length() > focusMax) focusText = focusText.substring(0, focusMax);
        if (focusSink != null) focusSink.set(focusText);
        return true;
    }

    public boolean typing() {
        return focus != null;
    }

    private void commitFocus() {
        if (focusSink != null) focusSink.set(focusText);
        focus = null;
        focusSink = null;
    }

    // ------------------------------------------------------------------ scroll areas

    private static float clampScroll(Scroll s, float v) {
        return Math.max(0f, Math.min(Math.max(0f, s.content - s.view), v));
    }

    /** Starts a clipped, scrollable area; returns the scroll offset to subtract from content y. */
    public float beginScroll(Object id, float x, float y, float w, float h) {
        Scroll s = scrolls.get(id);
        if (s == null) {
            s = new Scroll();
            scrolls.put(id, s);
        }
        s.view = h;
        s.target = clampScroll(s, s.target);
        s.offset += (s.target - s.offset) * (1f - (float) Math.exp(-18f * dt));
        scrollAreas.add(new Object[]{id, x, y, w, h});
        scrollId = id;
        scrollX = x;
        scrollY = y;
        scrollW = w;
        scrollH = h;
        clip = new float[]{x, y, w, h};
        Gfx.scissorOn(x, y, w, h);
        return s.offset;
    }

    /** Ends the scroll area; contentH = total height of what was drawn inside. */
    public void endScroll(float contentH) {
        Gfx.scissorOff();
        clip = null;
        Scroll s = scrolls.get(scrollId);
        if (s == null) return;
        s.content = contentH;
        if (contentH > s.view + 1f) {
            float th = Math.max(16f, s.view * s.view / contentH);
            float ty = scrollY + (s.view - th) * (s.offset / Math.max(1f, contentH - s.view));
            Gfx.roundedRect(scrollX + scrollW - 3f, ty, 2f, th, 1f, 0x40FFFFFF);
        }
    }

    public void resetScroll(Object id) {
        Scroll s = scrolls.get(id);
        if (s != null) {
            s.offset = 0f;
            s.target = 0f;
        }
    }

    // ------------------------------------------------------------------ text

    public static void text(String s, float x, float y, float scale, int color) {
        Gfx.textScaled(s, x, y, scale, color);
    }

    public static void textBold(String s, float x, float y, float scale, int color) {
        Gfx.weight(TtfFont.BOLD);
        Gfx.textScaled(s, x, y, scale, color);
        Gfx.weight(TtfFont.REGULAR);
    }

    public static void textMedium(String s, float x, float y, float scale, int color) {
        Gfx.weight(TtfFont.MEDIUM);
        Gfx.textScaled(s, x, y, scale, color);
        Gfx.weight(TtfFont.REGULAR);
    }

    public static void textCenter(String s, float cx, float y, float scale, int color) {
        Gfx.textScaled(s, cx - Gfx.textWidth(s, scale) / 2f, y, scale, color);
    }

    public static void textRight(String s, float rx, float y, float scale, int color) {
        Gfx.textScaled(s, rx - Gfx.textWidth(s, scale), y, scale, color);
    }

    public static float width(String s, float scale) {
        return Gfx.textWidth(s, scale);
    }

    // ------------------------------------------------------------------ surfaces

    public static void card(float x, float y, float w, float h) {
        Gfx.roundedRect(x, y, w, h, DS.R_LG, DS.SURFACE);
        Gfx.roundedOutline(x, y, w, h, DS.R_LG, 0.6f, DS.LINE);
    }

    /** Translucent card for screens drawn over the world or a background image. */
    public static void glassCard(float x, float y, float w, float h) {
        Gfx.roundedRect(x, y, w, h, DS.R_LG, 0xD90F1117);
        Gfx.roundedOutline(x, y, w, h, DS.R_LG, 0.6f, DS.LINE_STRONG);
    }

    public static void divider(float x, float y, float w) {
        Gfx.rect(x, y, w, 0.6f, DS.LINE);
    }

    /** Section caption: small, spaced, muted. */
    public static void caption(String s, float x, float y) {
        Gfx.weight(TtfFont.MEDIUM);
        Gfx.textScaled(s.toUpperCase(), x, y, 0.72f, DS.FAINT);
        Gfx.weight(TtfFont.REGULAR);
    }

    public static void spinner(float cx, float cy, float r, int color) {
        double t = System.nanoTime() / 1e9;
        double a0 = t * 5.0;
        Gfx.annulus(cx, cy, r - 1.2f, r, a0, a0 + Math.PI * 1.3, color);
    }

    // ------------------------------------------------------------------ components

    public void button(String label, float x, float y, float w, float h, int style, boolean enabled, Runnable r) {
        button(label, label, x, y, w, h, style, enabled, -1, r);
    }

    /** Button with optional glyph (Glyph.* or -1). style = PRIMARY, SECONDARY, GHOST or DANGER. */
    public void button(Object key, String label, float x, float y, float w, float h, int style, boolean enabled, int glyph, Runnable r) {
        boolean hv = enabled && hover(x, y, w, h);
        float a = anim(key, hv ? 1f : 0f);
        int acc = DS.accent();
        int fill, text = DS.TEXT, border = 0;
        switch (style) {
            case PRIMARY:
                fill = Gfx.mix(acc, Gfx.mix(acc, 0xFFFFFFFF, 0.18f), a);
                text = 0xFFFFFFFF;
                break;
            case GHOST:
                fill = Gfx.alpha(DS.SURFACE_3, a);
                text = Gfx.mix(DS.MUTED, DS.TEXT, a);
                break;
            case DANGER:
                fill = Gfx.mix(DS.SURFACE_2, Gfx.mix(DS.SURFACE_2, DS.BAD, 0.35f), a);
                text = Gfx.mix(DS.BAD, 0xFFFFFFFF, a);
                border = DS.LINE;
                break;
            default:
                fill = Gfx.mix(DS.SURFACE_2, DS.SURFACE_3, a);
                border = Gfx.mix(DS.LINE, DS.LINE_STRONG, a);
        }
        if (!enabled) {
            fill = Gfx.alpha(fill, 0.45f);
            text = Gfx.alpha(text, 0.4f);
        }
        Gfx.roundedRect(x, y, w, h, DS.R_MD, fill);
        if (border != 0) Gfx.roundedOutline(x, y, w, h, DS.R_MD, 0.6f, border);
        float tw = Gfx.textWidth(label, DS.T_BODY);
        float gw = glyph >= 0 ? 14f : 0f;
        float cx = x + (w - tw - gw) / 2f;
        if (glyph >= 0) Glyph.draw(glyph, cx + 5f, y + h / 2f, text);
        textMedium(label, cx + gw, y + h / 2f - 3.6f, DS.T_BODY, text);
        if (enabled && r != null) click(x, y, w, h, r);
    }

    /** Square icon button with a tooltip. */
    public void iconButton(Object key, float x, float y, float s, int glyph, String tooltip, boolean enabled, Runnable r) {
        boolean hv = enabled && hover(x, y, s, s);
        float a = anim(key, hv ? 1f : 0f);
        Gfx.roundedRect(x, y, s, s, DS.R_SM, Gfx.alpha(DS.SURFACE_3, 0.5f + 0.5f * a));
        Glyph.draw(glyph, x + s / 2f, y + s / 2f, enabled ? Gfx.mix(DS.MUTED, DS.TEXT, a) : Gfx.alpha(DS.MUTED, 0.35f));
        if (hv && tooltip != null) tip(tooltip);
        if (enabled && r != null) click(x, y, s, s, r);
    }

    /** On/off switch, 24 x 13. */
    public void toggle(Object key, float x, float y, boolean on, Runnable flip) {
        float a = anim(key, on ? 1f : 0f);
        boolean hv = hover(x - 2, y - 2, 28, 17);
        Gfx.roundedRect(x, y, 24, 13, 6.5f, Gfx.mix(hv ? 0xFF2A2E3B : DS.SURFACE_3, DS.accent(), a));
        Gfx.circle(x + 6.5f + a * 11f, y + 6.5f, 4.6f, 0xFFFFFFFF);
        if (flip != null) click(x - 2, y - 2, 28, 17, flip);
    }

    /** Horizontal slider; v01 = current position, sink receives the new position while dragging. */
    public void slider(Object key, float x, float y, float w, float v01, final FloatSink sink) {
        v01 = Math.max(0f, Math.min(1f, v01));
        boolean hv = hover(x - 4, y - 5, w + 8, 14);
        float a = anim(key, hv || dragging() && hv ? 1f : 0f);
        Gfx.roundedRect(x, y + 1.5f, w, 3f, 1.5f, DS.SURFACE_3);
        Gfx.roundedRect(x, y + 1.5f, w * v01, 3f, 1.5f, DS.accent());
        Gfx.circle(x + w * v01, y + 3f, 4f + a * 0.8f, 0xFFFFFFFF);
        final float fx = x, fw = w;
        drag(x - 4, y - 5, w + 8, 14, new Drag() {
            @Override
            public void to(float px, float py) {
                sink.set(Math.max(0f, Math.min(1f, (px - fx) / fw)));
            }
        });
    }

    /** Slider drawn over a custom track (colour bars): only the knob and the drag area. */
    public void knob(Object key, float x, float y, float w, float v01, final FloatSink sink) {
        v01 = Math.max(0f, Math.min(1f, v01));
        Gfx.circle(x + w * v01, y + 3f, 4.2f, 0xFFFFFFFF);
        Gfx.circle(x + w * v01, y + 3f, 2.6f, 0xFF20232E);
        final float fx = x, fw = w;
        drag(x - 4, y - 5, w + 8, 14, new Drag() {
            @Override
            public void to(float px, float py) {
                sink.set(Math.max(0f, Math.min(1f, (px - fx) / fw)));
            }
        });
    }

    /** Value picker: left click = next, right click = previous; chevrons on both sides. */
    public void cycle(Object key, float x, float y, float w, float h, String value, Runnable next, Runnable prev) {
        boolean hv = hover(x, y, w, h);
        float a = anim(key, hv ? 1f : 0f);
        Gfx.roundedRect(x, y, w, h, DS.R_SM, Gfx.mix(DS.SURFACE_2, DS.SURFACE_3, a));
        Gfx.roundedOutline(x, y, w, h, DS.R_SM, 0.6f, DS.LINE);
        net.m1227.ui.Gfx.chevron(x + 7, y + h / 2f, 180, DS.MUTED);
        net.m1227.ui.Gfx.chevron(x + w - 7, y + h / 2f, 0, DS.MUTED);
        String v = Gfx.trim(value, w - 26);
        textCenter(v, x + w / 2f, y + h / 2f - 3.6f, DS.T_BODY, DS.TEXT);
        click(x, y, w, h, next, prev);
    }

    /** Single-line text field. */
    public void textField(final Object key, float x, float y, float w, float h, String value, String placeholder, final int max, final TextSink sink) {
        boolean focused = key.equals(focus);
        boolean hv = hover(x, y, w, h);
        Gfx.roundedRect(x, y, w, h, DS.R_SM, focused ? DS.SURFACE_3 : DS.SURFACE_2);
        Gfx.roundedOutline(x, y, w, h, DS.R_SM, 0.7f, focused ? Gfx.alpha(DS.accent(), 0.9f) : hv ? DS.LINE_STRONG : DS.LINE);
        String shown = focused ? focusText : value;
        boolean empty = shown == null || shown.isEmpty();
        String draw = empty && !focused ? placeholder : shown;
        if (draw == null) draw = "";
        // keep the end of long text visible while typing
        while (Gfx.textWidth(draw, DS.T_BODY) > w - 14 && draw.length() > 1) draw = draw.substring(1);
        text(draw, x + 6, y + h / 2f - 3.6f, DS.T_BODY, empty && !focused ? DS.FAINT : DS.TEXT);
        if (focused && System.currentTimeMillis() / 500 % 2 == 0) {
            float cx = x + 6 + Gfx.textWidth(draw, DS.T_BODY) + 1;
            Gfx.rect(cx, y + 4, 0.8f, h - 8, DS.TEXT);
        }
        final String start = value == null ? "" : value;
        Hit t = hit(x, y, w, h);
        t.focusKey = key;
        t.left = new Runnable() {
            @Override
            public void run() {
                if (!key.equals(focus)) {
                    if (focus != null) commitFocus();
                    focus = key;
                    focusText = start;
                    focusMax = max;
                    focusSink = sink;
                }
            }
        };
    }

    /** Small pill label (status, tag). */
    public static void pill(String s, float x, float y, int color) {
        float w = Gfx.textWidth(s, 0.72f) + 8;
        Gfx.roundedRect(x, y, w, 10, 5, Gfx.alpha(color, 0.18f));
        Gfx.textScaled(s, x + 4, y + 2.4f, 0.72f, color);
    }

    /** Toast notification drawn by ClientUi on every screen. */
    public static void toast(String title, String detail, int color, float x, float y, float alpha) {
        float w = Math.max(Gfx.textWidth(title, DS.T_BODY), Gfx.textWidth(detail, DS.T_SMALL)) + 28;
        Gfx.roundedRect(x - w, y, w, 28, DS.R_MD, Gfx.alpha(0xF0151820, alpha));
        Gfx.roundedOutline(x - w, y, w, 28, DS.R_MD, 0.6f, Gfx.alpha(DS.LINE_STRONG, alpha));
        Gfx.roundedRect(x - w + 6, y + 8, 3, 12, 1.5f, Gfx.alpha(0xFF000000 | color, alpha));
        textMedium(title, x - w + 15, y + 5, DS.T_BODY, Gfx.alpha(DS.TEXT, alpha));
        text(detail, x - w + 15, y + 16, DS.T_SMALL, Gfx.alpha(DS.MUTED, alpha));
    }
}
