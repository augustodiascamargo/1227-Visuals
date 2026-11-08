package net.m1227.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.m1227.Mod1227;
import net.m1227.core.InputManager;
import net.m1227.core.Notifier;
import net.m1227.module.ClientModule;
import net.m1227.core.Category;
import net.m1227.core.ConfigManager;
import net.m1227.core.Module;
import net.m1227.core.ModuleManager;
import net.m1227.core.Setting;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The settings menu. Immediate-mode: every frame it draws the layout and
 * records clickable regions, which the next mouse event is tested against.
 * Widgets are generated from each module's Setting list.
 *
 * Controls: Right Shift opens/closes. Click a module to expand it, click its
 * switch (or right-click it) to toggle, click [KEY] to bind a key, scroll to
 * move, type to search.
 */
public class ClickGui extends GuiScreen {
    private static final int W = 590, H = 404, SIDE = 124;
    private static final float HEADER = 28f;

    // Higher-contrast palette: near-black panels, pure white text, brighter secondary text.
    private static final int BG = 0xFF0C0D12, SIDEBG = 0xFF08090C, CARD = 0xFF14161E, CARD_HOVER = 0xFF1B1E29;
    private static int ACCENT = 0xFF8B5CFF; // follows Client > Accent color
    private static final int TEXT = 0xFFF5F6FA, DIM = 0xFFA4A8BE, TRACK = 0xFF2A2D3D, WARN = 0xFFFFC857;
    private static final int WHITE = 0xFFFFFFFF, FIELD = 0xFF1E2130, ON = 0xFF3DDC84;

    private interface Click { void on(float mx, int button); }

    private interface Drag { void to(float mx); }

    private static final class Hit {
        final float x, y, w, h;
        final boolean list;
        final Click click;
        final Drag drag;

        Hit(float x, float y, float w, float h, boolean list, Click click, Drag drag) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.list = list;
            this.click = click;
            this.drag = drag;
        }

        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static Category category = Category.SKY;
    private static final Set<Module> EXPANDED = new HashSet<Module>();

    private final Map<Object, float[]> states = new HashMap<Object, float[]>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final float[] openAnim = {0f};
    private String search = "";
    private boolean searchFocus;
    private Module binding;
    private Setting.Key bindingKey;
    private float curScale = 1f;
    private Drag drag;
    private float scroll, scrollTarget, contentH, dt, ease;
    private float listX, listY, listW, listH;
    private long lastNanos;
    private int lastMx, lastMy;
    private boolean closing;
    private Category prevCategory;
    private float catSwitch = 1f;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        lastNanos = 0L;
        openAnim[0] = 0f;
        closing = false;
    }

    /** Plays the close animation, then leaves the screen. */
    private void close() {
        closing = true;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ConfigManager.save();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // keep the world running so effects can be previewed live
    }

    // =================================================================
    // Drawing
    // =================================================================

    @Override
    public void drawScreen(int rawMx, int rawMy, float partialTicks) {
        long now = System.nanoTime();
        dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0E9f);
        dt *= ClientModule.animSpeed();
        lastNanos = now;
        ease = 1f - (float) Math.exp(-16f * dt);
        ACCENT = 0xFF000000 | ClientModule.accent();
        hits.clear();

        float closeEase = 1f - (float) Math.exp(-22f * dt);
        openAnim[0] += ((closing ? 0f : 1f) - openAnim[0]) * (closing ? closeEase : ease);
        if (closing && openAnim[0] < 0.03f) {
            mc.displayGuiScreen(null);
            return;
        }
        float o = openAnim[0];
        // fit the window on small screens / GUI scales, then apply the user's menu scale and the open "pop"
        float fit = Math.min(Math.min((width - 12f) / W, (height - 12f) / H), ClientModule.menuScale());
        float sc = fit * (0.94f + 0.06f * o);
        curScale = sc;
        int mx = (int) localX(rawMx), my = (int) localY(rawMy);
        lastMx = mx;
        lastMy = my;
        Gfx.setTransform(width / 2f, height / 2f, sc);
        float wx = (width - W) / 2f;
        float wy = (height - H) / 2f + (1f - o) * 10f;
        if (prevCategory != category) {
            prevCategory = category;
            catSwitch = 0f;
        }
        catSwitch += (1f - catSwitch) * ease;

        Gfx.gradient(0, 0, width, height, Gfx.alpha(0xFF000000, 0.35f * o), Gfx.alpha(0xFF000000, 0.65f * o));
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(width / 2f, height / 2f, 0f);
        net.minecraft.client.renderer.GlStateManager.scale(sc, sc, 1f);
        net.minecraft.client.renderer.GlStateManager.translate(-width / 2f, -height / 2f, 0f);
        // soft drop shadow + accent rim
        Gfx.roundedRect(wx - 6, wy - 4, W + 12, H + 12, 16, Gfx.alpha(0xFF000000, 0.35f * o));
        Gfx.roundedRect(wx - 1, wy - 1, W + 2, H + 2, 11, Gfx.alpha(ACCENT, 0.45f * o));
        Gfx.roundedRect(wx, wy, W, H, 10, Gfx.alpha(BG, o));

        drawSidebar(wx, wy, mx, my, o);
        drawHeader(wx, wy, o);
        drawList(wx, wy, mx, my, o * (0.35f + 0.65f * catSwitch));
        drawFooter(wx, wy, o);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        Gfx.clearTransform();

        if (drag != null && !Mouse.isButtonDown(0)) drag = null;
    }

    private void drawSidebar(float wx, float wy, int mx, int my, float o) {
        Gfx.roundedRect(wx, wy, SIDE, H, 9, Gfx.alpha(SIDEBG, o));
        Gfx.rect(wx + SIDE - 10, wy, 10, H, Gfx.alpha(SIDEBG, o));
        // 1227 wordmark: quiet, with a small accent mark
        Gfx.textScaled("1227", wx + 16, wy + 16, 1.7f, Gfx.alpha(TEXT, o));
        Gfx.roundedRect(wx + 16, wy + 33, 14, 2, 1, Gfx.alpha(ACCENT, o));

        Category[] cats = Category.values();
        float cy = wy + 56;
        String lastGroup = "";
        for (int i = 0; i < cats.length; i++) {
            final Category c = cats[i];
            if (!c.group.equals(lastGroup)) {
                lastGroup = c.group;
                Gfx.text(c.group, wx + 14, cy + 2, Gfx.alpha(DIM, 0.7f * o));
                cy += 14;
            }
            float bx = wx + 8, by = cy, bw = SIDE - 16, bh = 20;
            cy += 21;
            boolean sel = search.trim().isEmpty() && c == category;
            boolean hov = in(mx, my, bx, by, bw, bh);
            float[] a = state(c);
            a[0] += ((sel ? 1f : 0f) - a[0]) * ease;
            a[1] += ((hov ? 1f : 0f) - a[1]) * ease;

            if (a[1] > 0.01f) Gfx.roundedRect(bx, by, bw, bh, 6, Gfx.alpha(WHITE, 0.05f * a[1] * o));
            if (a[0] > 0.01f) Gfx.roundedRect(bx, by, bw, bh, 6, Gfx.alpha(ACCENT, 0.16f * a[0] * o));
            Gfx.roundedRect(bx + 1, by + 4, 2.5f, bh - 8, 1.2f, Gfx.alpha(ACCENT, a[0] * o));
            int ic = Gfx.alpha(Gfx.mix(DIM, ACCENT, a[0]), o);
            Icons.draw(c.icon, bx + 13, by + bh / 2f, ic);
            Gfx.text(c.label, bx + 24, by + 6, Gfx.alpha(Gfx.mix(DIM, TEXT, Math.max(a[0], a[1] * 0.6f)), o));
            int on = 0;
            for (Module m : ModuleManager.inCategory(c)) {
                if (m.toggleable() && m.isEnabled()) on++;
            }
            if (on > 0) {
                String n = String.valueOf(on);
                Gfx.text(n, bx + bw - 6 - Gfx.textWidth(n), by + 6, Gfx.alpha(Gfx.mix(DIM, ON, 0.7f), o * 0.9f));
            }

            hits.add(new Hit(bx, by, bw, bh, false, new Click() {
                @Override
                public void on(float x, int b) {
                    category = c;
                    search = "";
                    scroll = 0f;
                    scrollTarget = 0f;
                }
            }, null));
        }
    }

    private void drawHeader(float wx, float wy, float o) {
        float titleX = wx + SIDE + 14;
        String title = search.trim().isEmpty() ? category.title : "Search";
        Gfx.textScaled(title, titleX, wy + 11, 1.3f, Gfx.alpha(TEXT, o));
        List<Module> here = search.trim().isEmpty() ? ModuleManager.inCategory(category) : ModuleManager.search(search);
        int on = 0, tog = 0;
        for (Module m : here) {
            if (m.toggleable()) {
                tog++;
                if (m.isEnabled()) on++;
            }
        }
        String sub = here.size() + (here.size() == 1 ? " module" : " modules") + (tog > 0 ? "  \u00b7  " + on + " on" : "");
        Gfx.text(sub, titleX, wy + 25, Gfx.alpha(DIM, o));

        float sw = 170, sh = 18, sx = wx + W - sw - 14, sy = wy + 11;
        if (searchFocus) Gfx.roundedRect(sx - 1, sy - 1, sw + 2, sh + 2, 7, Gfx.alpha(ACCENT, 0.55f * o));
        Gfx.roundedRect(sx, sy, sw, sh, 6, Gfx.alpha(FIELD, o));
        boolean placeholder = search.isEmpty() && !searchFocus;
        String shown = placeholder ? "Search modules..." : search + (searchFocus && (System.currentTimeMillis() / 500L) % 2L == 0L ? "_" : "");
        while (shown.length() > 1 && Gfx.textWidth(shown) > sw - 14) shown = shown.substring(1);
        Gfx.text(shown, sx + 7, sy + 5, Gfx.alpha(placeholder ? DIM : TEXT, o));
        hits.add(new Hit(sx, sy, sw, sh, false, new Click() {
            @Override
            public void on(float x, int b) {
                searchFocus = true;
            }
        }, null));
    }

    private void drawList(float wx, float wy, int mx, int my, float o) {
        listX = wx + SIDE + 10;
        listY = wy + 44;
        listW = W - SIDE - 20;
        listH = H - 68;

        boolean searching = !search.trim().isEmpty();
        List<Module> mods = searching ? ModuleManager.search(search) : ModuleManager.inCategory(category);

        float maxScroll = Math.max(0f, contentH - listH);
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-18f * dt));

        Gfx.scissorOn(listX - 2, listY, listW + 4, listH);
        float y = listY - scroll;
        float cw = listW - 8;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float[] a = modState(m);
            a[0] += ((EXPANDED.contains(m) ? 1f : 0f) - a[0]) * ease;
            a[1] += (((m.isEnabled() || !m.toggleable()) ? 1f : 0f) - a[1]) * ease;
            float full = settingsHeight(m);
            float cardH = HEADER + (full > 0f ? (full + 8f) * a[0] : 0f);
            if (y + cardH >= listY && y <= listY + listH) drawCard(m, a, listX, y, cw, cardH, mx, my, o);
            y += cardH + 6f;
        }
        contentH = y + scroll - listY;
        if (mods.isEmpty()) Gfx.text("No modules found", listX + 6, listY + 8, Gfx.alpha(DIM, o));
        Gfx.scissorOff();

        if (contentH > listH && maxScroll > 0f) {
            float bh = Math.max(20f, listH * listH / contentH);
            float by = listY + (listH - bh) * (scroll / maxScroll);
            Gfx.roundedRect(listX + listW - 4, by, 3, bh, 1.5f, Gfx.alpha(WHITE, 0.18f * o));
        }
    }

    private void drawCard(final Module m, float[] a, float x, float y, float w, float h, int mx, int my, float o) {
        boolean hover = in(mx, my, x, y, w, HEADER) && inList(mx, my);
        a[2] += ((hover ? 1f : 0f) - a[2]) * ease;

        Gfx.roundedRect(x, y, w, h, 7, Gfx.alpha(Gfx.mix(CARD, CARD_HOVER, a[2]), o));
        if (m.toggleable() && a[1] > 0.01f) {
            Gfx.roundedRect(x, y, w, Math.min(h, HEADER), 7, Gfx.alpha(ACCENT, 0.07f * a[1] * o));
        }
        if (m.toggleable()) Gfx.roundedRect(x, y + 7, 3, HEADER - 14, 1.5f, Gfx.alpha(ACCENT, a[1] * o));
        Gfx.chevron(x + 14, y + HEADER / 2f, a[0] * 90f, Gfx.alpha(DIM, o));
        Gfx.text(m.name, x + 26, y + 6, Gfx.alpha(TEXT, o));

        hits.add(new Hit(x, y, w, HEADER, true, new Click() {
            @Override
            public void on(float cx, int b) {
                if (b == 1 && m.toggleable()) m.toggle();
                else if (b == 0 && !EXPANDED.remove(m)) EXPANDED.add(m);
            }
        }, null));

        float rightEdge = x + w - 12;
        if (m.toggleable()) {
            float swW = 26, swH = 12, swX = x + w - swW - 12, swY = y + (HEADER - swH) / 2f;
            Gfx.roundedRect(swX, swY, swW, swH, 6, Gfx.alpha(Gfx.mix(TRACK, ACCENT, a[1]), o));
            Gfx.roundedRect(swX + 2 + a[1] * 14, swY + 2, 8, 8, 4, Gfx.alpha(WHITE, o));
            hits.add(new Hit(swX - 2, swY - 2, swW + 4, swH + 4, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    if (b == 0) m.toggle();
                }
            }, null));

            String ks = binding == m ? "..." : "[" + keyName(m.key) + "]";
            float kw = Gfx.textWidth(ks), kx = swX - 10 - kw;
            boolean clash = m.key != 0 && InputManager.conflicts(m.key, m).size() > 0;
            Gfx.text(ks, kx, y + 10, Gfx.alpha(binding == m ? ACCENT : (clash ? WARN : DIM), o));
            hits.add(new Hit(kx - 3, y + 6, kw + 6, 16, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    if (b == 0) binding = m;
                }
            }, null));
            rightEdge = kx - 8;
        }
        String desc = mc.fontRendererObj.trimStringToWidth(m.description, (int) Math.max(10f, rightEdge - (x + 26)));
        Gfx.text(desc, x + 26, y + 16, Gfx.alpha(DIM, o));

        if (a[0] > 0.03f) {
            float al = Math.min(1f, a[0] * 1.3f) * o;
            float ry = y + HEADER + 2, rx = x + 14, rw = w - 28, limit = y + h - 2;
            for (Setting<?> s : m.settings()) {
                if (!s.visible()) continue;
                float rh = rowHeight(s);
                if (ry + rh > limit) break;
                drawSetting(s, rx, ry, rw, al);
                ry += rh;
            }
            if (ry + RESET_ROW <= limit + 0.5f) drawReset(m, rx, ry, rw, al);
        }
    }

    private static final float RESET_ROW = 20f;

    private void drawReset(final Module m, float x, float y, float w, float al) {
        String l = "Reset to defaults";
        float tw = Gfx.textWidth(l) + 14;
        boolean hv = in(lastMx, lastMy, x + w - tw, y + 3, tw, 14) && inList(lastMx, lastMy);
        Gfx.roundedRect(x + w - tw, y + 3, tw, 14, 7, Gfx.alpha(hv ? 0x33FFFFFF : 0x14FFFFFF, al));
        Gfx.text(l, x + w - tw + 7, y + 6, Gfx.alpha(hv ? TEXT : DIM, al));
        hits.add(new Hit(x + w - tw, y + 3, tw, 14, true, new Click() {
            @Override
            public void on(float cx, int bt) {
                if (bt != 0) return;
                m.reset();
                Notifier.post(Notifier.Kind.SYSTEM, m.name, "Defaults restored", Notifier.INFO, 1400);
            }
        }, null));
    }

    private void drawSetting(final Setting<?> s, final float x, float y, final float w, float al) {
        float[] st = state(s);
        if (s instanceof Setting.Bool) {
            final Setting.Bool b = (Setting.Bool) s;
            if (st[3] == 0f) {
                st[0] = b.on() ? 1f : 0f;
                st[3] = 1f;
            }
            st[0] += ((b.on() ? 1f : 0f) - st[0]) * ease;
            Gfx.text(s.name, x, y + 4, Gfx.alpha(TEXT, al));
            float sw = 22, sh = 10, sx = x + w - sw;
            Gfx.roundedRect(sx, y + 3, sw, sh, 5, Gfx.alpha(Gfx.mix(TRACK, ACCENT, st[0]), al));
            Gfx.roundedRect(sx + 1.5f + st[0] * 11, y + 4.5f, 7, 7, 3.5f, Gfx.alpha(WHITE, al));
            hits.add(new Hit(x, y, w, 16, true, new Click() {
                @Override
                public void on(float cx, int bt) {
                    if (bt == 0) b.toggle();
                }
            }, null));
        } else if (s instanceof Setting.Num) {
            final Setting.Num n = (Setting.Num) s;
            float frac = (float) ((n.get() - n.min) / (n.max - n.min));
            frac = Math.max(0f, Math.min(1f, frac));
            String val = fmt(n);
            Gfx.text(s.name, x, y + 2, Gfx.alpha(TEXT, al));
            Gfx.text(val, x + w - Gfx.textWidth(val), y + 2, Gfx.alpha(DIM, al));
            float ty = y + 15;
            Gfx.roundedRect(x, ty, w, 4, 2, Gfx.alpha(TRACK, al));
            Gfx.roundedRect(x, ty, Math.max(4f, frac * w), 4, 2, Gfx.alpha(ACCENT, al));
            float kx = Math.max(x, Math.min(x + w - 8, x + frac * w - 4));
            Gfx.roundedRect(kx, ty - 2, 8, 8, 4, Gfx.alpha(WHITE, al));
            hits.add(new Hit(x, y + 10, w, 14, true, null, new Drag() {
                @Override
                public void to(float cx) {
                    float t = Math.max(0f, Math.min(1f, (cx - x) / w));
                    n.setClamped(n.min + t * (n.max - n.min));
                }
            }));
        } else if (s instanceof Setting.Mode) {
            final Setting.Mode md = (Setting.Mode) s;
            Gfx.text(s.name, x, y + 4, Gfx.alpha(TEXT, al));
            String v = md.get();
            final float vw = Gfx.textWidth(v) + 24;
            final float px = x + w - vw;
            Gfx.roundedRect(px, y + 1, vw, 14, 5, Gfx.alpha(FIELD, al));
            Gfx.text("<", px + 5, y + 4, Gfx.alpha(DIM, al));
            Gfx.text(">", px + vw - 10, y + 4, Gfx.alpha(DIM, al));
            Gfx.text(v, px + (vw - Gfx.textWidth(v)) / 2f, y + 4, Gfx.alpha(TEXT, al));
            hits.add(new Hit(px, y + 1, vw, 14, true, new Click() {
                @Override
                public void on(float cx, int bt) {
                    if (bt == 0) md.cycle(cx < px + vw / 2f ? -1 : 1);
                }
            }, null));
        } else if (s instanceof Setting.Key) {
            final Setting.Key k = (Setting.Key) s;
            Gfx.text(s.name, x, y + 4, Gfx.alpha(TEXT, al));
            boolean waiting = bindingKey == k;
            String v = waiting ? "Press a key (Esc = none)" : InputManager.keyName(k.code());
            float vw = Gfx.textWidth(v) + 16, px = x + w - vw;
            Gfx.roundedRect(px, y + 1, vw, 14, 7, Gfx.alpha(waiting ? Gfx.mix(FIELD, ACCENT, 0.35f) : FIELD, al));
            Gfx.text(v, px + 8, y + 4, Gfx.alpha(waiting ? WHITE : TEXT, al));
            String clash = ClientModule.conflictText(k);
            if (clash != null) {
                Gfx.text(mc.fontRendererObj.trimStringToWidth(clash, (int) w), x, y + 18, Gfx.alpha(WARN, al));
            } else if (k.code() != 0) {
                Gfx.text("No conflicts", x, y + 18, Gfx.alpha(Gfx.mix(DIM, 0xFF3DDC84, 0.6f), al * 0.8f));
            }
            hits.add(new Hit(px, y + 1, vw, 14, true, new Click() {
                @Override
                public void on(float cx, int bt) {
                    if (bt == 0) bindingKey = k;
                    else if (bt == 1) k.set(0);
                }
            }, null));
        } else if (s instanceof Setting.Action) {
            final Setting.Action ac = (Setting.Action) s;
            boolean hv = in(lastMx, lastMy, x, y + 1, w, 20) && inList(lastMx, lastMy);
            Gfx.roundedRect(x, y + 1, w, 20, 6, Gfx.alpha(hv ? 0xFF2C3045 : FIELD, al));
            Gfx.roundedRect(x, y + 5, 3, 12, 1.5f, Gfx.alpha(ACCENT, al));
            Gfx.text(s.name, x + (w - Gfx.textWidth(s.name)) / 2f, y + 7, Gfx.alpha(TEXT, al));
            hits.add(new Hit(x, y + 1, w, 20, true, new Click() {
                @Override
                public void on(float cx, int bt) {
                    if (bt == 0) ac.run();
                }
            }, null));
        } else if (s instanceof Setting.Col) {
            final Setting.Col c = (Setting.Col) s;
            Gfx.text(s.name, x, y + 1, Gfx.alpha(TEXT, al));
            Gfx.roundedRect(x + w - 20, y, 20, 9, 3, Gfx.alpha(0xFF000000 | c.rgb(), al));
            float b1 = y + 13, b2 = y + 24, bh = 7;
            Gfx.hueBar(x, b1, w, bh, al);
            Gfx.rect(x + c.hue * w - 1, b1 - 1, 2, bh + 2, Gfx.alpha(WHITE, al));
            Gfx.satBar(x, b2, w, bh, java.awt.Color.HSBtoRGB(c.hue, 1f, 1f) & 0xFFFFFF, al);
            Gfx.rect(x + c.sat * w - 1, b2 - 1, 2, bh + 2, Gfx.alpha(WHITE, al));
            hits.add(new Hit(x, b1 - 2, w, bh + 4, true, null, new Drag() {
                @Override
                public void to(float cx) {
                    c.setHS((cx - x) / w, c.sat);
                }
            }));
            hits.add(new Hit(x, b2 - 2, w, bh + 4, true, null, new Drag() {
                @Override
                public void to(float cx) {
                    c.setHS(c.hue, (cx - x) / w);
                }
            }));
        }
    }

    // =================================================================
    // Layout helpers
    // =================================================================

    private static float rowHeight(Setting<?> s) {
        if (s instanceof Setting.Action) return 24f;
        if (s instanceof Setting.Key) return 30f;
        if (s instanceof Setting.Num) return 26f;
        if (s instanceof Setting.Col) return 36f;
        return 18f;
    }

    private static float settingsHeight(Module m) {
        float h = 0f;
        for (Setting<?> s : m.settings()) {
            if (s.visible()) h += rowHeight(s);
        }
        return m.settings().isEmpty() ? h : h + RESET_ROW;
    }

    private float[] state(Object key) {
        float[] a = states.get(key);
        if (a == null) {
            a = new float[4];
            states.put(key, a);
        }
        return a;
    }

    private float[] modState(Module m) {
        float[] a = states.get(m);
        if (a == null) {
            a = new float[]{EXPANDED.contains(m) ? 1f : 0f, (m.isEnabled() || !m.toggleable()) ? 1f : 0f, 0f, 0f};
            states.put(m, a);
        }
        return a;
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private boolean inList(float mx, float my) {
        return in(mx, my, listX, listY, listW, listH);
    }

    private static String fmt(Setting.Num n) {
        String v = n.step >= 1.0 ? String.valueOf(n.i()) : String.format(Locale.ROOT, "%.2f", n.get());
        return n.suffix.isEmpty() ? v : v + n.suffix;
    }

    private static String keyName(int key) {
        if (key == 0) return "NONE";
        String n = Keyboard.getKeyName(key);
        return n == null ? "?" : n;
    }

    // =================================================================
    // Input
    // =================================================================

    @Override
    protected void mouseClicked(int rawMx, int rawMy, int button) throws IOException {
        int mx = (int) localX(rawMx), my = (int) localY(rawMy);
        searchFocus = false;
        binding = null;
        bindingKey = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.contains(mx, my)) continue;
            if (h.list && !inList(mx, my)) continue;
            if (h.click != null) h.click.on(mx, button);
            if (h.drag != null && button == 0) {
                drag = h.drag;
                drag.to(mx);
            }
            return;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        if (drag != null) drag.to(localX(mx));
    }

    @Override
    protected void mouseReleased(int mx, int my, int state) {
        drag = null;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scrollTarget -= Math.signum((float) d) * 36f;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (bindingKey != null) {
            int code = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK ? 0 : key;
            bindingKey.set(code);
            java.util.List<String> clashes = InputManager.conflicts(code, bindingKey);
            if (!clashes.isEmpty()) Notifier.post(Notifier.Kind.SYSTEM, "Key conflict", clashes.get(0), Notifier.WARN, 2200);
            bindingKey = null;
            return;
        }
        if (binding != null) {
            binding.key = key == Keyboard.KEY_ESCAPE ? 0 : key;
            ConfigManager.markDirty();
            binding = null;
            return;
        }
        if (searchFocus) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN) {
                searchFocus = false;
            } else if (key == Keyboard.KEY_BACK) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                scrollTarget = 0f;
            } else if (ChatAllowedCharacters.isAllowedCharacter(c) && search.length() < 32) {
                search += c;
                scrollTarget = 0f;
            }
            return;
        }
        if (key == Keyboard.KEY_ESCAPE || (key != 0 && key == ClientModule.menuKey())) {
            close();
        }
    }

    private void drawFooter(float wx, float wy, float o) {
        String l = InputManager.keyName(ClientModule.menuKey()) + " / Esc to close   \u00b7   right-click a module to toggle   \u00b7   /1227   \u00b7   v" + Mod1227.VERSION;
        Gfx.rect(wx + SIDE, wy + H - 18, W - SIDE, 1, Gfx.alpha(0x14FFFFFF, o));
        Gfx.text(l, wx + SIDE + 12, wy + H - 12, Gfx.alpha(DIM, 0.7f * o));
    }

    private float localX(float sx) {
        return (sx - width / 2f) / curScale + width / 2f;
    }

    private float localY(float sy) {
        return (sy - height / 2f) / curScale + height / 2f;
    }
}
