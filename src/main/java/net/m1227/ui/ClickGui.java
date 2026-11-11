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
    private static final int W = 680, H = 470, SIDE = 164;
    private static final float HEADER = 34f;

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
    private static boolean favView;
    private Module tipModule, tipShown;
    private long tipSince;
    private static final Set<Module> EXPANDED = new HashSet<Module>();

    private final Map<Object, float[]> states = new HashMap<Object, float[]>();
    private final List<Hit> hits = new ArrayList<Hit>();
    private final float[] openAnim = {0f};
    private String search = "";
    private boolean searchFocus;
    private Module binding;
    private Setting.Key bindingKey;
    private float curScale = 1f, pxPerUnit = 2f;
    private Drag drag;
    private float scroll, scrollTarget, contentH, dt, ease;
    private float listX, listY, listW, listH;
    private long lastNanos;
    private int lastMx, lastMy;
    private boolean closing;
    private boolean blurStarted;
    private float panelA = 1f, cardA = 1f;
    private Category prevCategory;
    private float catSwitch = 1f;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        lastNanos = 0L;
        openAnim[0] = 0f;
        closing = false;
        startBlur();
    }

    /** Frosted background: Minecraft's own blur post-shader behind the menu (if the GPU supports shaders). */
    private void startBlur() {
        boolean glass = ClientModule.glass() && Theme.blur();
        panelA = 1f;
        cardA = 1f;
        if (!glass || blurStarted) return;
        try {
            if (net.minecraft.client.renderer.OpenGlHelper.shadersSupported && !mc.entityRenderer.isShaderActive()) {
                mc.entityRenderer.loadShader(new net.minecraft.util.ResourceLocation("shaders/post/blur.json"));
                blurStarted = mc.entityRenderer.isShaderActive();
            }
        } catch (Throwable t) {
            blurStarted = false;
        }
    }

    private void stopBlur() {
        if (blurStarted && mc.entityRenderer.isShaderActive()) mc.entityRenderer.stopUseShader();
        blurStarted = false;
    }

    /** Plays the close animation, then leaves the screen. */
    private void close() {
        closing = true;
    }

    @Override
    public void onGuiClosed() {
        net.m1227.vfx.PreviewMode.stop();
        disposeStages();
        stopBlur();
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
        try {
            drawScreen0(rawMx, rawMy, partialTicks);
        } finally {
            Gfx.vanillaFont();
        }
    }

    private void drawScreen0(int rawMx, int rawMy, float partialTicks) {
        long now = System.nanoTime();
        dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0E9f);
        dt *= ClientModule.animSpeed();
        lastNanos = now;
        ease = 1f - (float) Math.exp(-16f * dt);
        ACCENT = 0xFF000000 | ClientModule.accent();
        hits.clear();
        tipModule = null;

        float closeEase = 1f - (float) Math.exp(-22f * dt);
        net.m1227.vfx.PreviewMode.update();
        boolean previewing = net.m1227.vfx.PreviewMode.active();
        if (previewing && blurStarted) stopBlur();
        openAnim[0] += ((closing || previewing ? 0f : 1f) - openAnim[0]) * (closing || previewing ? closeEase : ease);
        if (previewing) {
            drawPreviewPill();
            if (openAnim[0] < 0.03f) return;
        }
        if (closing && openAnim[0] < 0.03f) {
            mc.displayGuiScreen(null);
            return;
        }
        float o = openAnim[0];
        // fit the window on small screens / GUI scales, then apply the user's menu scale and the open "pop"
        float fit = Math.min(Math.min((width - 12f) / W, (height - 12f) / H), ClientModule.menuScale());
        float sc = fit * (0.94f + 0.06f * o);
        curScale = sc;
        int sf = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();
        // smooth font rasterised for the final size of the window (not the opening pop), so it is built once
        Gfx.smoothFont(sf * fit);
        pxPerUnit = sf * sc;
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

        // live style switch: start/stop the background blur when Menu Style changes
        boolean wantBlur = ClientModule.glass() && Theme.blur();
        if (wantBlur && !blurStarted) startBlur();
        else if (!wantBlur && blurStarted) stopBlur();
        float dim = (blurStarted ? 0.45f : 1f) * Theme.dim();
        Gfx.gradient(0, 0, width, height, Gfx.alpha(0xFF000000, 0.30f * o * dim), Gfx.alpha(0xFF000000, 0.55f * o * dim));
        if (Theme.blur()) {
            // frosted copy of what is behind the window, in real screen coordinates (outside the menu transform)
            float cx = width / 2f, cy = height / 2f;
            float gx = cx + ((width - W) / 2f - cx) * sc, gy = cy + ((height - H) / 2f + (1f - o) * 10f - cy) * sc;
            if (o > 0.6f) {
                Glass.panel(gx, gy, W * sc, H * sc, Theme.radius() * sc, 1f, Theme.tint(), 0f,
                        (Theme.liquid() ? 7f : 10f) * Theme.blurAmount() * sf, Theme.liquid() ? 0.035f : 0f);
            }
        }
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(width / 2f, height / 2f, 0f);
        net.minecraft.client.renderer.GlStateManager.scale(sc, sc, 1f);
        net.minecraft.client.renderer.GlStateManager.translate(-width / 2f, -height / 2f, 0f);
        float rad = Theme.radius();
        // soft drop shadow (not for the see-through styles), window fill, border and sheen
        if (!Theme.is("Minimal") && !Theme.is("Transparent")) {
            Gfx.roundedRect(wx - 6, wy - 4, W + 12, H + 12, rad + 6, Gfx.alpha(0xFF000000, (Theme.blur() ? 0.18f : 0.35f) * o));
        }
        if (Theme.is("Neon")) {
            for (int i = 3; i >= 1; i--) Gfx.roundedRect(wx - i * 2, wy - i * 2, W + i * 4, H + i * 4, rad + i * 2, Gfx.alpha(ACCENT, 0.06f * o));
        }
        Gfx.roundedRect(wx, wy, W, H, rad, Gfx.alpha(Theme.window(), o));
        if (Theme.liquid()) Gfx.roundedOutlineGradient(wx, wy, W, H, rad, 1f, Gfx.alpha(0x8CFFFFFF, o), Gfx.alpha(0x1EFFFFFF, o));
        else Gfx.roundedOutline(wx, wy, W, H, rad, 1f, Gfx.alpha(Theme.windowBorder(ACCENT), o));
        if (Theme.sheen()) Gfx.gradient(wx + rad, wy + 0.5f, W - rad * 2, 40, Gfx.alpha(Theme.blur() ? 0x1CFFFFFF : 0x10FFFFFF, o), 0x00FFFFFF);

        drawSidebar(wx, wy, mx, my, o);
        drawHeader(wx, wy, o);
        drawList(wx, wy, mx, my, o * (0.35f + 0.65f * catSwitch));
        drawFooter(wx, wy, o);
        if (settingsFor != null) drawSettingsWindow(wx, wy, mx, my, o);
        else drawTooltip(mx, my, o);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        Gfx.clearTransform();

        if (drag != null && !Mouse.isButtonDown(0)) drag = null;
    }

    private void drawSidebar(float wx, float wy, int mx, int my, float o) {
        float rad = Theme.radius();
        Gfx.roundedRect(wx, wy, SIDE, H, rad - 1, Gfx.alpha(Theme.sidebar(), o));
        Gfx.rect(wx + SIDE - rad, wy, rad, H, Gfx.alpha(Theme.sidebar(), o));
        Gfx.rect(wx + SIDE, wy + 10, 1, H - 20, Gfx.alpha(Theme.cardBorder(ACCENT) != 0 ? Theme.cardBorder(ACCENT) : 0x14FFFFFF, o));
        // brand: the 1227 symbol in the accent colour (follows the menu colour) + wordmark
        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 700.0);
        Gfx.logo(wx + 9, wy + 9, 34, Gfx.alpha(Gfx.mix(ACCENT, WHITE, 0.12f), o), pulse);
        Gfx.weight(TtfFont.BOLD);
        Gfx.textScaled("1227", wx + 44, wy + 14.5f, 1.35f, Gfx.alpha(TEXT, o));
        Gfx.weight(TtfFont.MEDIUM);
        Gfx.textScaled("V I S U A L S", wx + 44.5f, wy + 29f, 0.62f, Gfx.alpha(DIM, o));
        Gfx.weight(TtfFont.REGULAR);

        // search
        {
            float sx = wx + 12, sy = wy + 48, sw = SIDE - 24, sh = 20;
            if (searchFocus) Gfx.roundedRect(sx - 1, sy - 1, sw + 2, sh + 2, 8, Gfx.alpha(ACCENT, 0.55f * o));
            Gfx.roundedRect(sx, sy, sw, sh, 7, Gfx.alpha(Theme.card(0.6f) | 0x10000000, o));
            Icons.search(sx + 10, sy + sh / 2f, Gfx.alpha(DIM, o));
            boolean placeholder = search.isEmpty() && !searchFocus;
            String shown = placeholder ? "Search" : search + (searchFocus && (System.currentTimeMillis() / 500L) % 2L == 0L ? "|" : "");
            while (shown.length() > 1 && Gfx.textWidth(shown) > sw - 26) shown = shown.substring(1);
            Gfx.text(shown, sx + 19, sy + 6.5f, Gfx.alpha(placeholder ? DIM : TEXT, o));
            hits.add(new Hit(sx, sy, sw, sh, false, new Click() {
                @Override
                public void on(float x, int b) {
                    searchFocus = true;
                }
            }, null));
        }

        float cy = wy + 76;
        cy = sideItem(wx, cy, mx, my, o, "fav", -1, "Favorites", favorites().size(), search.trim().isEmpty() && favView, new Click() {
            @Override
            public void on(float x, int b) {
                favView = true;
                search = "";
                scroll = 0f;
                scrollTarget = 0f;
            }
        });
        Category[] cats = Category.values();
        String lastGroup = "";
        for (int i = 0; i < cats.length; i++) {
            final Category c = cats[i];
            if (!c.group.equals(lastGroup)) {
                lastGroup = c.group;
                Gfx.weight(TtfFont.MEDIUM);
                Gfx.textScaled(c.group, wx + 18, cy + 4, 0.7f, Gfx.alpha(DIM, 0.75f * o));
                Gfx.weight(TtfFont.REGULAR);
                cy += 15;
            }
            int on = 0;
            for (Module m : ModuleManager.inCategory(c)) {
                if (m.toggleable() && m.isEnabled()) on++;
            }
            cy = sideItem(wx, cy, mx, my, o, c, c.icon, c.label, on, search.trim().isEmpty() && !favView && c == category, new Click() {
                @Override
                public void on(float x, int b) {
                    category = c;
                    favView = false;
                    search = "";
                    scroll = 0f;
                    scrollTarget = 0f;
                }
            });
        }

        // you + performance, at the bottom of the sidebar
        float uy = wy + H - 34;
        Gfx.rect(wx + 14, uy - 6, SIDE - 28, 1, Gfx.alpha(0x14FFFFFF, o));
        if (mc.thePlayer != null) {
            try {
                mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
                net.minecraft.client.renderer.GlStateManager.enableBlend();
                net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, o);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect((int) wx + 14, (int) uy + 2, 8, 8, 8, 8, 20, 20, 64, 64);
                net.minecraft.client.gui.Gui.drawScaledCustomSizeModalRect((int) wx + 14, (int) uy + 2, 40, 8, 8, 8, 20, 20, 64, 64);
                net.minecraft.client.renderer.GlStateManager.color(1f, 1f, 1f, 1f);
            } catch (Throwable ignored) {
                // skin not loaded yet
            }
            Gfx.weight(TtfFont.MEDIUM);
            Gfx.text(Gfx.trim(mc.thePlayer.getName(), SIDE - 60), wx + 40, uy + 3, Gfx.alpha(TEXT, o));
            Gfx.weight(TtfFont.REGULAR);
        }
        int fps = net.minecraft.client.Minecraft.getDebugFPS();
        int pc = fps >= 100 ? ON : fps >= 50 ? WARN : 0xFFFF5A6A;
        Gfx.circle(wx + 43, uy + 17, 1.8f, Gfx.alpha(pc, o));
        Gfx.textScaled(fps + " fps  \u00b7  " + net.m1227.vfx.ParticleSystem.INSTANCE.alive() + " fx", wx + 47, uy + 14, 0.78f, Gfx.alpha(DIM, o));
    }

    /** One sidebar row; icon < 0 draws a star. Returns the next y. */
    private float sideItem(float wx, float cy, int mx, int my, float o, Object key, int icon, String label, int count, boolean sel, Click click) {
        float bx = wx + 10, by = cy, bw = SIDE - 20, bh = 20;
        boolean hov = in(mx, my, bx, by, bw, bh);
        float[] a = state(key);
        a[0] += ((sel ? 1f : 0f) - a[0]) * ease;
        a[1] += ((hov ? 1f : 0f) - a[1]) * ease;
        if (a[1] > 0.01f) Gfx.roundedRect(bx, by, bw, bh, 8, Gfx.alpha(WHITE, 0.05f * a[1] * o));
        if (a[0] > 0.01f) {
            // selected: accent pill with a soft glow and a lighter top
            Gfx.roundedRect(bx - 1, by - 1, bw + 2, bh + 2, 9, Gfx.alpha(ACCENT, 0.18f * a[0] * o));
            Gfx.roundedRect(bx, by, bw, bh, 8, Gfx.alpha(ACCENT, 0.88f * a[0] * o));
            Gfx.gradient(bx + 4, by, bw - 8, bh / 2f, Gfx.alpha(0x2EFFFFFF, a[0] * o), 0x00FFFFFF);
        }
        int ic = Gfx.alpha(Gfx.mix(DIM, WHITE, Math.max(a[0], a[1] * 0.5f)), o);
        if (icon < 0) Gfx.star(bx + 13, by + bh / 2f, 4.5f, true, Gfx.alpha(Gfx.mix(WARN, WHITE, a[0]), o));
        else Icons.draw(icon, bx + 13, by + bh / 2f, ic);
        Gfx.weight(sel ? TtfFont.MEDIUM : TtfFont.REGULAR);
        Gfx.text(label, bx + 26, by + 6, Gfx.alpha(Gfx.mix(DIM, WHITE, Math.max(a[0], a[1] * 0.7f)), o));
        Gfx.weight(TtfFont.REGULAR);
        if (count > 0) {
            String n = String.valueOf(count);
            float nw = Gfx.textWidth(n, 0.75f) + 8;
            Gfx.roundedRect(bx + bw - nw - 5, by + 5, nw, 10, 5, Gfx.alpha(a[0] > 0.5f ? 0x33FFFFFF : 0x14FFFFFF, o));
            Gfx.textScaled(n, bx + bw - nw - 1, by + 6.6f, 0.75f, Gfx.alpha(a[0] > 0.5f ? WHITE : DIM, o));
        }
        hits.add(new Hit(bx, by, bw, bh, false, click, null));
        return cy + 21;
    }

    private void drawHeader(float wx, float wy, float o) {
        float titleX = wx + SIDE + 20;
        String title = search.trim().isEmpty() ? (favView ? "Favorites" : category.title) : "Search";
        Gfx.weight(TtfFont.BOLD);
        Gfx.textScaled(title, titleX, wy + 15, 1.6f, Gfx.alpha(TEXT, o));
        Gfx.weight(TtfFont.REGULAR);
        List<Module> here = currentModules();
        int on = 0, tog = 0;
        for (Module m : here) {
            if (m.toggleable()) {
                tog++;
                if (m.isEnabled()) on++;
            }
        }
        String sub = here.size() + (here.size() == 1 ? " module" : " modules");
        Gfx.text(sub, titleX, wy + 34, Gfx.alpha(DIM, o));
        // "Menu style" shortcut (palette)
        {
            String t = "Menu style";
            float bw = Gfx.textWidth(t, 0.85f) + 26, bx = wx + W - bw - 20, by = wy + 19;
            boolean hov = in(lastMx, lastMy, bx, by, bw, 16);
            Gfx.roundedRect(bx, by, bw, 16, 8, Gfx.alpha(hov ? 0x33FFFFFF : 0x1CFFFFFF, o));
            for (int i = 0; i < 3; i++) Gfx.circle(bx + 8 + i * 3.2f, by + 8, 2f, Gfx.alpha(new int[]{ACCENT, 0xFF2DD4FF, 0xFFFF4FD8}[i], o));
            Gfx.textScaled(t, bx + 19, by + 5, 0.85f, Gfx.alpha(TEXT, o));
            hits.add(new Hit(bx, by, bw, 16, false, new Click() {
                @Override
                public void on(float c, int b) {
                    category = Category.SETTINGS;
                    favView = false;
                    search = "";
                    scroll = 0f;
                    scrollTarget = 0f;
                    if (net.m1227.module.MenuStyleModule.INSTANCE != null) openSettings(net.m1227.module.MenuStyleModule.INSTANCE);
                }
            }, null));
            wx -= bw + 6; // the "active" pill sits to its left
        }
        if (tog > 0) {
            String act = on + " active";
            float aw = Gfx.textWidth(act, 0.85f) + 22, ax = wx + W - aw - 20, ay = wy + 19;
            Gfx.roundedRect(ax, ay, aw, 16, 8, Gfx.alpha(on > 0 ? Gfx.mix(ACCENT, 0xFF000000, 0.55f) : 0xFF20232E, 0.8f * o));
            Gfx.roundedOutline(ax, ay, aw, 16, 8, 0.7f, Gfx.alpha(on > 0 ? ACCENT : 0x30FFFFFF, 0.6f * o));
            Gfx.circle(ax + 9, ay + 8, 2.2f, Gfx.alpha(on > 0 ? ON : DIM, o));
            Gfx.textScaled(act, ax + 15, ay + 5, 0.85f, Gfx.alpha(TEXT, o));
        }
    }

    private void drawList(float wx, float wy, int mx, int my, float o) {
        listX = wx + SIDE + 14;
        listY = wy + 54;
        listW = W - SIDE - 26;
        listH = H - 78;

        List<Module> mods = currentModules();

        float maxScroll = Math.max(0f, contentH - listH);
        scrollTarget = Math.max(0f, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-18f * dt));

        Gfx.scissorOn(listX - 2, listY, listW + 4, listH);
        float y = listY - scroll;
        float cw = listW - 8;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float[] a = modState(m);
            a[0] = 0f; // settings open in their own window (gear), cards stay compact
            a[1] += (((m.isEnabled() || !m.toggleable()) ? 1f : 0f) - a[1]) * ease;
            float full = settingsHeight(m);
            float cardH = HEADER + (full > 0f ? (full + 8f) * a[0] : 0f);
            if (y + cardH >= listY && y <= listY + listH) drawCard(m, a, listX, y, cw, cardH, mx, my, o);
            y += cardH + 6f;
        }
        contentH = y + scroll - listY;
        if (mods.isEmpty()) {
            String empty = favView && search.trim().isEmpty() ? "No favorites yet - click the star on any module" : "No modules found";
            Gfx.text(empty, listX + 6, listY + 8, Gfx.alpha(DIM, o));
        }
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

        Gfx.roundedRect(x, y, w, h, 10, Gfx.alpha(Theme.card(a[2]), o));
        int cb = Theme.cardBorder(ACCENT);
        if (Theme.liquid()) Gfx.roundedOutlineGradient(x, y, w, h, 10, 0.8f, Gfx.alpha(0x40FFFFFF, o), Gfx.alpha(0x0AFFFFFF, o));
        else if (cb != 0) Gfx.roundedOutline(x, y, w, h, 10, 1f, Gfx.alpha(cb, o * (0.7f + 0.3f * a[2])));
        if (m.toggleable() && a[1] > 0.01f) {
            Gfx.roundedRect(x, y, w, Math.min(h, HEADER), 10, Gfx.alpha(ACCENT, 0.08f * a[1] * o));
        }
        // status dot instead of a bar, chevron on the right of the name
        Gfx.circle(x + 15, y + HEADER / 2f, 3.2f, Gfx.alpha(m.toggleable() ? Gfx.mix(0xFF3A3E4D, ACCENT, a[1]) : DIM, o));
        if (a[1] > 0.05f && m.toggleable()) Gfx.circle(x + 15, y + HEADER / 2f, 6f, Gfx.alpha(ACCENT, 0.18f * a[1] * o));
        Gfx.weight(TtfFont.MEDIUM);
        Gfx.text(m.name, x + 28, y + 7.5f, Gfx.alpha(TEXT, o));
        Gfx.weight(TtfFont.REGULAR);

        // left-click toggles, right-click (or the gear) opens the settings window
        hits.add(new Hit(x, y, w, HEADER, true, new Click() {
            @Override
            public void on(float cx, int b) {
                if (b == 0 && m.toggleable()) m.toggle();
                else if (b == 1 || (b == 0 && !m.toggleable())) openSettings(m);
            }
        }, null));

        float rightEdge = x + w - 12;
        if (m.toggleable()) {
            float swW = 30, swH = 16, swX = x + w - swW - 12, swY = y + (HEADER - swH) / 2f;
            Gfx.roundedRect(swX, swY, swW, swH, 8, Gfx.alpha(Gfx.mix(TRACK, ACCENT, a[1]), o));
            if (a[1] > 0.05f) Gfx.gradient(swX + 3, swY, swW - 6, swH / 2f, Gfx.alpha(0x30FFFFFF, a[1] * o), 0x00FFFFFF);
            Gfx.circle(swX + 8 + a[1] * 14, swY + 8, 6.4f, Gfx.alpha(0x40000000, o));
            Gfx.circle(swX + 8 + a[1] * 14, swY + 8, 6f, Gfx.alpha(WHITE, o));
            hits.add(new Hit(swX - 2, swY - 2, swW + 4, swH + 4, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    if (b == 0) m.toggle();
                }
            }, null));

            String ks = binding == m ? "Press a key" : (m.key == 0 ? "Bind" : keyName(m.key));
            float kw = Gfx.textWidth(ks, 0.78f) + 12, kx = swX - 10 - kw;
            boolean clash = m.key != 0 && InputManager.conflicts(m.key, m).size() > 0;
            Gfx.roundedRect(kx, y + (HEADER - 14) / 2f, kw, 14, 7, Gfx.alpha(binding == m ? Gfx.mix(0xFF20232E, ACCENT, 0.5f) : 0x22FFFFFF, o));
            Gfx.textScaled(ks, kx + 6, y + HEADER / 2f - 3.2f, 0.78f, Gfx.alpha(binding == m ? WHITE : (clash ? WARN : DIM), o));
            hits.add(new Hit(kx - 3, y + 6, kw + 6, HEADER - 12, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    if (b == 0) binding = m;
                }
            }, null));
            rightEdge = kx - 8;
        }
        if (!m.settings().isEmpty()) {
            float gx = rightEdge - 8;
            boolean gh = in(lastMx, lastMy, gx - 8, y + 5, 16, HEADER - 10) && inList(lastMx, lastMy);
            if (gh) Gfx.circle(gx, y + HEADER / 2f, 9f, Gfx.alpha(0x22FFFFFF, o));
            Icons.gear(gx, y + HEADER / 2f, Gfx.alpha(gh ? WHITE : TEXT, o));
            hits.add(new Hit(gx - 8, y + 5, 16, HEADER - 10, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    openSettings(m);
                }
            }, null));
            rightEdge = gx - 14;
        }
        {
            float sx = rightEdge - 6;
            boolean sh = in(lastMx, lastMy, sx - 6, y + 6, 12, HEADER - 12) && inList(lastMx, lastMy);
            if (m.favorite) Gfx.star(sx, y + HEADER / 2f, 4.4f, true, Gfx.alpha(WARN, o));
            else Gfx.star(sx, y + HEADER / 2f, 4.4f, false, Gfx.alpha(sh ? TEXT : Gfx.mix(DIM, CARD, 0.35f), o));
            hits.add(new Hit(sx - 6, y + 6, 12, HEADER - 12, true, new Click() {
                @Override
                public void on(float cx, int b) {
                    if (b != 0) return;
                    m.favorite = !m.favorite;
                    ConfigManager.markDirty();
                }
            }, null));
            rightEdge = sx - 10;
        }
        if (hover) {
            if (tipShown != m) {
                tipShown = m;
                tipSince = System.currentTimeMillis();
            }
            tipModule = m;
        }
        String desc = Gfx.trim(m.description, Math.max(10f, rightEdge - (x + 28)) / 0.82f);
        Gfx.textScaled(desc, x + 28, y + 20f, 0.82f, Gfx.alpha(DIM, o));

        if (a[0] > 0.03f) {
            float al = Math.min(1f, a[0] * 1.3f) * o;
            float ry = y + HEADER + 2, rx = x + 14, rw = w - 28, limit = y + h - 2;
            for (Setting<?> s : m.settings()) {
                if (!s.visible()) continue;
                float rh = rowHeight(m, s);
                if (ry + rh > limit) break;
                drawSetting(m, s, rx, ry, rw, al);
                ry += rh;
            }
            if (ry + RESET_ROW <= limit + 0.5f) drawReset(m, rx, ry, rw, al);
        }
    }

    private static final float RESET_ROW = 20f;

    // ------------------------------------------------------------ settings window

    private Module settingsFor;
    private float winAnim, winScroll, winScrollTarget, winContentH;
    private static float rowWidthOverride = -1f;

    private void openSettings(Module m) {
        settingsFor = m;
        winAnim = 0f;
        winScroll = 0f;
        winScrollTarget = 0f;
    }

    private void closeSettings() {
        settingsFor = null;
        rowWidthOverride = -1f;
    }

    /** The settings of one module in a floating window over the list (opened with the gear). */
    private void drawSettingsWindow(float wx, float wy, int mx, int my, float o) {
        final Module m = settingsFor;
        winAnim += (1f - winAnim) * ease;
        float a = winAnim * o;
        float ww = Math.min(W - SIDE - 40, 470), wh = H - 70;
        float x = wx + SIDE + (W - SIDE - ww) / 2f, y = wy + 36 + (1f - winAnim) * 14f;
        // click-outside blocker (closes), drawn first so the window's own hits win
        hits.add(new Hit(wx, wy, W, H, false, new Click() {
            @Override
            public void on(float cx, int b) {
                closeSettings();
            }
        }, null));
        // clicks on empty parts of the window must not fall through to the blocker
        hits.add(new Hit(x, y, ww, wh, false, new Click() {
            @Override
            public void on(float cx, int b) {
            }
        }, null));
        Gfx.roundedRect(wx + SIDE, wy, W - SIDE, H, Theme.radius(), Gfx.alpha(0xFF05060A, 0.45f * a));
        Gfx.roundedRect(x - 8, y - 6, ww + 16, wh + 16, 18, Gfx.alpha(0xFF000000, 0.35f * a));
        Gfx.roundedRect(x, y, ww, wh, 14, Gfx.alpha(Gfx.mix(0xFF101219, Theme.tint(), 0.4f), 0.97f * a));
        Gfx.roundedOutlineGradient(x, y, ww, wh, 14, 1f, Gfx.alpha(0x50FFFFFF, a), Gfx.alpha(0x10FFFFFF, a));
        // header
        Gfx.circle(x + 20, y + 21, 4f, Gfx.alpha(m.isEnabled() || !m.toggleable() ? ACCENT : 0xFF3A3E4D, a));
        Gfx.weight(TtfFont.BOLD);
        Gfx.textScaled(m.name, x + 32, y + 13, 1.25f, Gfx.alpha(TEXT, a));
        Gfx.weight(TtfFont.REGULAR);
        Gfx.textScaled(Gfx.trim(m.description, (ww - 120) / 0.82f), x + 32, y + 28, 0.82f, Gfx.alpha(DIM, a));
        // close button
        float cx0 = x + ww - 26, cy0 = y + 12;
        boolean ch = in(mx, my, cx0, cy0, 16, 16);
        Gfx.circle(cx0 + 8, cy0 + 8, 8, Gfx.alpha(ch ? 0x40FFFFFF : 0x1CFFFFFF, a));
        Gfx.line(cx0 + 5, cy0 + 5, cx0 + 11, cy0 + 11, 1.3f, Gfx.alpha(WHITE, a));
        Gfx.line(cx0 + 11, cy0 + 5, cx0 + 5, cy0 + 11, 1.3f, Gfx.alpha(WHITE, a));
        hits.add(new Hit(cx0, cy0, 16, 16, false, new Click() {
            @Override
            public void on(float c, int b) {
                closeSettings();
            }
        }, null));
        // on/off switch in the header
        if (m.toggleable()) {
            float[] st = modState(m);
            float swW = 30, swH = 16, swX = cx0 - swW - 12, swY = y + 13;
            Gfx.roundedRect(swX, swY, swW, swH, 8, Gfx.alpha(Gfx.mix(TRACK, ACCENT, st[1]), a));
            Gfx.circle(swX + 8 + st[1] * 14, swY + 8, 6f, Gfx.alpha(WHITE, a));
            hits.add(new Hit(swX, swY, swW, swH, false, new Click() {
                @Override
                public void on(float c, int b) {
                    if (b == 0) m.toggle();
                }
            }, null));
        }
        // window body: same controls as before, with its own scroll
        Gfx.rect(x + 14, y + 42, ww - 28, 1, Gfx.alpha(0x14FFFFFF, a));
        float bx = x + 18, by = y + 50, bw = ww - 36, bh = wh - 58;
        listX = bx;
        listY = by;
        listW = bw;
        listH = bh;
        rowWidthOverride = bw;
        float maxScroll = Math.max(0f, winContentH - bh);
        winScrollTarget = Math.max(0f, Math.min(maxScroll, winScrollTarget));
        winScroll += (winScrollTarget - winScroll) * (1f - (float) Math.exp(-18f * dt));
        Gfx.scissorOn(bx - 4, by, bw + 8, bh);
        float ry = by - winScroll;
        for (Setting<?> st : m.settings()) {
            if (!st.visible()) continue;
            float rh = rowHeight(m, st);
            if (ry + rh >= by - 4 && ry <= by + bh) drawSetting(m, st, bx, ry, bw, a);
            ry += rh;
        }
        if (!m.settings().isEmpty()) {
            drawReset(m, bx, ry, bw, a);
            ry += RESET_ROW;
        }
        winContentH = ry + winScroll - by;
        Gfx.scissorOff();
        if (winContentH > bh && maxScroll > 0f) {
            float sbh = Math.max(20f, bh * bh / winContentH);
            float sby = by + (bh - sbh) * (winScroll / maxScroll);
            Gfx.roundedRect(x + ww - 7, sby, 3, sbh, 1.5f, Gfx.alpha(WHITE, 0.2f * a));
        }
    }

    private static List<Module> favorites() {
        List<Module> out = new ArrayList<Module>();
        for (Module m : ModuleManager.all()) {
            if (m.favorite) out.add(m);
        }
        return out;
    }

    private List<Module> currentModules() {
        if (!search.trim().isEmpty()) return ModuleManager.search(search);
        return favView ? favorites() : ModuleManager.inCategory(category);
    }

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

    private void drawSetting(final Module m, final Setting<?> s, final float x, float y, final float w, float al) {
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
        } else if (s instanceof Setting.Mode && galleryKind(m, (Setting.Mode) s) != 0) {
            drawGallery(m, (Setting.Mode) s, x, y, w, al);
        } else if (s instanceof Setting.Mode && ((Setting.Mode) s).options.length <= 4 && segWidth((Setting.Mode) s) <= w * 0.62f) {
            // short lists: segmented control
            final Setting.Mode md = (Setting.Mode) s;
            Gfx.text(s.name, x, y + 4, Gfx.alpha(TEXT, al));
            float tw = segWidth(md), px = x + w - tw;
            Gfx.roundedRect(px, y, tw, 16, 8, Gfx.alpha(0x22FFFFFF, al));
            float cx = px + 2;
            for (final String opt : md.options) {
                float ow = Gfx.textWidth(opt, 0.8f) + 14;
                boolean selOpt = md.is(opt);
                if (selOpt) Gfx.roundedRect(cx, y + 2, ow, 12, 6, Gfx.alpha(ACCENT, al));
                Gfx.textScaled(opt, cx + 7, y + 4.6f, 0.8f, Gfx.alpha(selOpt ? WHITE : DIM, al));
                final float ox = cx, ow2 = ow;
                hits.add(new Hit(ox, y, ow2, 16, true, new Click() {
                    @Override
                    public void on(float c, int bt) {
                        if (bt == 0) md.set(opt);
                    }
                }, null));
                cx += ow;
            }
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
                Gfx.text(Gfx.trim(clash, w), x, y + 18, Gfx.alpha(WARN, al));
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
                    if (bt != 0) return;
                    if (ac.name.startsWith("Preview") && !(m instanceof net.m1227.module.HitSoundsModule)) {
                        net.m1227.vfx.PreviewMode.begin(m.name + (ac.name.length() > 7 ? " - " + ac.name.substring(7).trim() : ""), ac::run);
                    } else {
                        ac.run();
                    }
                }
            }, null));
        } else if (s instanceof Setting.Col) {
            drawColor((Setting.Col) s, x, y, w, al);
        }
    }

    private static final java.util.Set<Setting<?>> OPEN_COLORS = new java.util.HashSet<Setting<?>>();
    private static final int[] SWATCHES = {0xFFFFFF, 0x8B5CFF, 0x6C7BFF, 0x2DD4FF, 0x3DDC84, 0xFFC857,
            0xFF8A3D, 0xFF4D5E, 0xFF4FD8, 0xB57BFF, 0x9AA3B5, 0x15161C};
    private float dragY;

    /** Colour row: swatch + hex; click to open a picker with a saturation/brightness square, hue bar and swatches. */
    private void drawColor(final Setting.Col c, final float x, final float y, final float w, float al) {
        Gfx.text(c.name, x, y + 4, Gfx.alpha(TEXT, al));
        String hex = String.format("#%06X", c.rgb());
        float sw = 26, sx = x + w - sw;
        Gfx.textScaled(hex, sx - 8 - Gfx.textWidth(hex, 0.8f), y + 4.6f, 0.8f, Gfx.alpha(DIM, al));
        Gfx.roundedRect(sx, y + 2, sw, 12, 6, Gfx.alpha(0xFF000000 | c.rgb(), al));
        Gfx.roundedOutline(sx, y + 2, sw, 12, 6, 0.8f, Gfx.alpha(0x40FFFFFF, al));
        final boolean open = OPEN_COLORS.contains(c);
        hits.add(new Hit(x, y, w, 16, true, new Click() {
            @Override
            public void on(float cx, int bt) {
                if (bt != 0) return;
                if (!OPEN_COLORS.remove(c)) OPEN_COLORS.add(c);
            }
        }, null));
        if (!open) return;
        final float qx = x, qy = y + 20, qw = Math.min(150f, w * 0.5f), qh = 62;
        int pure = java.awt.Color.HSBtoRGB(c.hue, 1f, 1f) & 0xFFFFFF;
        Gfx.satBar(qx, qy, qw, qh, pure, al);
        Gfx.gradient(qx, qy, qw, qh, 0x00000000, Gfx.alpha(0xFF000000, al));
        Gfx.roundedOutline(qx, qy, qw, qh, 3, 0.8f, Gfx.alpha(0x30FFFFFF, al));
        float kx = qx + c.sat * qw, ky = qy + (1f - c.bri) * qh;
        Gfx.circle(kx, ky, 4f, Gfx.alpha(0xFF000000, al * 0.6f));
        Gfx.circle(kx, ky, 3.2f, Gfx.alpha(WHITE, al));
        Gfx.circle(kx, ky, 2.2f, Gfx.alpha(0xFF000000 | c.rgb(), al));
        hits.add(new Hit(qx, qy, qw, qh, true, null, new Drag() {
            @Override
            public void to(float cx) {
                c.setHSB(c.hue, (cx - qx) / qw, 1f - (dragY - qy) / qh);
            }
        }));
        final float hy = qy + qh + 8;
        Gfx.hueBar(x, hy, w, 7, al);
        Gfx.circle(x + c.hue * w, hy + 3.5f, 4.5f, Gfx.alpha(WHITE, al));
        Gfx.circle(x + c.hue * w, hy + 3.5f, 3.2f, Gfx.alpha(0xFF000000 | pure, al));
        hits.add(new Hit(x, hy - 3, w, 13, true, null, new Drag() {
            @Override
            public void to(float cx) {
                c.setHSB((cx - x) / w, c.sat, c.bri);
            }
        }));
        // swatches + accent
        float gx = qx + qw + 10, gw = x + w - gx;
        int per = 6;
        float cell = Math.min(18f, (gw - (per - 1) * 5f) / per);
        for (int i = 0; i < SWATCHES.length; i++) {
            final int rgb = SWATCHES[i];
            float cx0 = gx + (i % per) * (cell + 5), cy0 = qy + (i / per) * (cell + 5);
            Gfx.roundedRect(cx0, cy0, cell, cell, cell / 2f, Gfx.alpha(0xFF000000 | rgb, al));
            if ((c.rgb() & 0xFFFFFF) == rgb) Gfx.roundedOutline(cx0 - 1.5f, cy0 - 1.5f, cell + 3, cell + 3, cell / 2f + 1.5f, 1f, Gfx.alpha(WHITE, al));
            hits.add(new Hit(cx0, cy0, cell, cell, true, new Click() {
                @Override
                public void on(float mx, int bt) {
                    if (bt == 0) c.setRgb(rgb);
                }
            }, null));
        }
        float by = qy + 2 * (cell + 5) + 4;
        final int acc = ACCENT & 0xFFFFFF;
        Gfx.roundedRect(gx, by, gw, 16, 8, Gfx.alpha(0x1CFFFFFF, al));
        Gfx.circle(gx + 9, by + 8, 3.5f, Gfx.alpha(ACCENT, al));
        Gfx.textScaled("Use accent", gx + 17, by + 4.6f, 0.8f, Gfx.alpha(TEXT, al));
        hits.add(new Hit(gx, by, gw, 16, true, new Click() {
            @Override
            public void on(float mx, int bt) {
                if (bt == 0) c.setRgb(acc);
            }
        }, null));
        Gfx.textScaled("Reset", gx + 4, by + 22, 0.78f, Gfx.alpha(DIM, al));
        hits.add(new Hit(gx, by + 18, 40, 12, true, new Click() {
            @Override
            public void on(float mx, int bt) {
                if (bt == 0) c.reset();
            }
        }, null));
    }

    // =================================================================
    // Layout helpers
    // =================================================================

    private static float rowHeight(Module m, Setting<?> s) {
        if (s instanceof Setting.Mode && galleryKind(m, (Setting.Mode) s) != 0) return galleryHeight((Setting.Mode) s, rowWidth());
        if (s instanceof Setting.Action) return 24f;
        if (s instanceof Setting.Key) return 30f;
        if (s instanceof Setting.Num) return 26f;
        if (s instanceof Setting.Col) return OPEN_COLORS.contains(s) ? 106f : 20f;
        return 18f;
    }

    private static float segWidth(Setting.Mode md) {
        float t = 4;
        for (String o : md.options) t += Gfx.textWidth(o, 0.8f) + 14;
        return t;
    }

    // ------------------------------------------------------------ effect gallery

    private static final int GAL_COLS = 3;
    private final Map<String, net.m1227.vfx.PreviewStage> stages = new HashMap<String, net.m1227.vfx.PreviewStage>();
    private final Map<String, net.m1227.vfx.AuraLibrary.State> auraStates = new HashMap<String, net.m1227.vfx.AuraLibrary.State>();
    private net.minecraft.client.entity.EntityOtherPlayerMP standIn;

    /** 0 = no gallery, 1 = replayed effect, 2 = continuous aura. */
    private static int galleryKind(Module m, Setting.Mode md) {
        if (md.options.length < 2) return 0;
        if ((m instanceof net.m1227.module.TargetAuraModule || m instanceof net.m1227.module.PlayerAuraModule) && md.name.equals("Style")) return 2;
        if (m instanceof net.m1227.module.TrailsModule && md.name.equals("Style")) return 3;
        return previewAction(m, md) != null ? 1 : 0;
    }

    private static Setting.Action previewAction(Module m, Setting.Mode md) {
        Setting.Action plain = null;
        for (Setting<?> s : m.settings()) {
            if (!(s instanceof Setting.Action)) continue;
            String n = s.name.toLowerCase(Locale.ROOT);
            if (n.equals(("preview " + md.name).toLowerCase(Locale.ROOT))) return (Setting.Action) s;
            if (n.equals("preview")) plain = (Setting.Action) s;
        }
        return md.name.equals("Style") ? plain : null;
    }

    private static float tileW(float w) {
        return (w - (GAL_COLS - 1) * 6f) / GAL_COLS;
    }

    private static float tileH(float w) {
        return tileW(w) * 0.62f + 18f;
    }

    private static float galleryHeight(Setting.Mode md, float w) {
        int rows = (md.options.length + GAL_COLS - 1) / GAL_COLS;
        return 16f + rows * (tileH(w) + 6f) + 4f;
    }

    /** Settings row width inside an expanded card (same math as drawList / drawCard). */
    private static float rowWidth() {
        if (rowWidthOverride > 0f) return rowWidthOverride;
        return (W - SIDE - 26) - 8 - 28;
    }

    private void drawGallery(final Module m, final Setting.Mode md, float x, float y, float w, float al) {
        Gfx.weight(TtfFont.MEDIUM);
        Gfx.text(md.name, x, y + 3, Gfx.alpha(TEXT, al));
        Gfx.weight(TtfFont.REGULAR);
        String hint = md.options.length + " effects  \u00b7  live preview";
        Gfx.textScaled(hint, x + w - Gfx.textWidth(hint, 0.75f), y + 4.5f, 0.75f, Gfx.alpha(DIM, al));
        final int kind = galleryKind(m, md);
        final Setting.Action act = kind == 1 ? previewAction(m, md) : null;
        float tw = tileW(w), th = tileH(w), ph = th - 18f;
        float top = y + 16f;
        for (int i = 0; i < md.options.length; i++) {
            final String opt = md.options[i];
            float tx = x + (i % GAL_COLS) * (tw + 6f), ty = top + (i / GAL_COLS) * (th + 6f);
            boolean sel = md.is(opt);
            boolean hov = in(lastMx, lastMy, tx, ty, tw, th) && inList(lastMx, lastMy);
            Gfx.roundedRect(tx, ty, tw, th, 9, Gfx.alpha(sel ? Gfx.mix(0xFF141621, ACCENT, 0.18f) : 0xFF101219, al * 0.92f));
            // only tiles that are on screen get a live preview
            boolean visible = ty + th >= listY && ty <= listY + listH;
            if (visible && al > 0.5f) {
                final String key = m.name + "/" + md.name + "/" + opt;
                net.m1227.vfx.PreviewStage st = stages.get(key);
                if (st == null) {
                    float loop = m instanceof net.m1227.module.KillEffectModule ? 3.4f : 2.2f;
                    st = new net.m1227.vfx.PreviewStage(kind == 1 ? loop : 1f, kind != 3, kind != 1);
                    stages.put(key, st);
                }
                Runnable trig = kind == 3 ? trailTrigger((net.m1227.module.TrailsModule) m, opt, key)
                        : kind == 2 ? auraTrigger(m, opt, key) : new Runnable() {
                    @Override
                    public void run() {
                        String old = md.get();
                        md.setSilently(opt);
                        try {
                            act.run();
                        } finally {
                            md.setSilently(old);
                        }
                    }
                };
                if (trig != null) {
                    Gfx.scissorOn(Math.max(tx + 1, listX - 2), Math.max(ty + 1, listY), tw - 2, Math.min(ph, listY + listH - ty));
                    st.draw(tx + 1, ty + 1, tw - 2, ph, Math.round((tw - 2) * pxPerUnit), Math.round(ph * pxPerUnit), trig, ACCENT);
                    Gfx.scissorOn(listX - 2, listY, listW + 4, listH);
                }
            }
            // label strip
            Gfx.weight(sel ? TtfFont.MEDIUM : TtfFont.REGULAR);
            Gfx.textScaled(Gfx.trim(opt, (tw - 20) / 0.85f), tx + 7, ty + th - 13, 0.85f, Gfx.alpha(sel ? WHITE : TEXT, al));
            Gfx.weight(TtfFont.REGULAR);
            if (sel) {
                Gfx.roundedOutline(tx, ty, tw, th, 9, 1.2f, Gfx.alpha(ACCENT, al));
                Gfx.circle(tx + tw - 10, ty + th - 9, 4.5f, Gfx.alpha(ACCENT, al));
                Gfx.line(tx + tw - 12.2f, ty + th - 9, tx + tw - 10.5f, ty + th - 7.4f, 1.1f, Gfx.alpha(WHITE, al));
                Gfx.line(tx + tw - 10.5f, ty + th - 7.4f, tx + tw - 7.6f, ty + th - 10.8f, 1.1f, Gfx.alpha(WHITE, al));
            } else if (hov) {
                Gfx.roundedOutline(tx, ty, tw, th, 9, 1f, Gfx.alpha(0x50FFFFFF, al));
            } else {
                Gfx.roundedOutline(tx, ty, tw, th, 9, 0.8f, Gfx.alpha(0x16FFFFFF, al));
            }
            hits.add(new Hit(tx, ty, tw, th, true, new Click() {
                @Override
                public void on(float c, int bt) {
                    if (bt == 0) md.set(opt);
                }
            }, null));
        }
    }

    private Runnable auraTrigger(final Module m, final String opt, final String key) {
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        if (standIn == null || standIn.worldObj != mc.theWorld) {
            standIn = new net.minecraft.client.entity.EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        }
        final net.minecraft.client.entity.EntityOtherPlayerMP e = standIn;
        net.m1227.vfx.AuraLibrary.State s0 = auraStates.get(key);
        if (s0 == null) {
            s0 = new net.m1227.vfx.AuraLibrary.State();
            auraStates.put(key, s0);
        }
        final net.m1227.vfx.AuraLibrary.State st = s0;
        return new Runnable() {
            @Override
            public void run() {
                e.setPosition(0, 0, 0);
                e.prevPosX = e.lastTickPosX = 0;
                e.prevPosY = e.lastTickPosY = 0;
                e.prevPosZ = e.lastTickPosZ = 0;
                e.ticksExisted = (int) (System.currentTimeMillis() / 50L);
                if (m instanceof net.m1227.module.TargetAuraModule) ((net.m1227.module.TargetAuraModule) m).drawPreview(opt, e, st);
                else ((net.m1227.module.PlayerAuraModule) m).drawPreview(opt, e, st);
            }
        };
    }

    private final Map<String, net.m1227.module.TrailsModule.PreviewState> trailStates = new HashMap<String, net.m1227.module.TrailsModule.PreviewState>();

    private Runnable trailTrigger(final net.m1227.module.TrailsModule m, final String opt, String key) {
        net.m1227.module.TrailsModule.PreviewState s0 = trailStates.get(key);
        if (s0 == null) {
            s0 = new net.m1227.module.TrailsModule.PreviewState();
            trailStates.put(key, s0);
        }
        final net.m1227.module.TrailsModule.PreviewState st = s0;
        return new Runnable() {
            @Override
            public void run() {
                m.previewFrame(opt, st);
            }
        };
    }

    private void disposeStages() {
        trailStates.clear();
        for (net.m1227.vfx.PreviewStage st : stages.values()) st.dispose();
        stages.clear();
        auraStates.clear();
    }

    private static float settingsHeight(Module m) {
        float h = 0f;
        for (Setting<?> s : m.settings()) {
            if (s.visible()) h += rowHeight(m, s);
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
        if (net.m1227.vfx.PreviewMode.active()) {
            net.m1227.vfx.PreviewMode.stop();
            return;
        }
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
                dragY = my;
                drag = h.drag;
                drag.to(mx);
            }
            return;
        }
    }

    @Override
    protected void mouseClickMove(int mx, int my, int button, long time) {
        dragY = localY(my);
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
        if (d != 0) {
            if (settingsFor != null) winScrollTarget -= Math.signum((float) d) * 36f;
            else scrollTarget -= Math.signum((float) d) * 36f;
        }
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
        if (settingsFor != null && key == Keyboard.KEY_ESCAPE) {
            closeSettings();
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

    /** Small pill shown while the menu steps aside for a live preview. */
    private void drawPreviewPill() {
        String t = "Previewing  " + net.m1227.vfx.PreviewMode.label() + "   \u00b7   click to return";
        float w = Gfx.textWidth(t) + 24, x = (width - w) / 2f, y = height - 46;
        Gfx.roundedRect(x, y, w, 18, 9, 0xD00B0C11);
        Gfx.roundedOutline(x, y, w, 18, 9, 0.8f, 0x30FFFFFF);
        Gfx.roundedRect(x + 4, y + 14.5f, (w - 8) * net.m1227.vfx.PreviewMode.progress(), 1.5f, 0.75f, ACCENT);
        Gfx.circle(x + 11, y + 9, 2.5f, ACCENT);
        Gfx.text(t, x + 18, y + 5, 0xFFE6E8EF);
    }

    /** Full description + hints after hovering a module header for half a second. */
    private void drawTooltip(int mx, int my, float o) {
        if (tipModule == null) {
            tipShown = null;
            return;
        }
        long held = System.currentTimeMillis() - tipSince;
        if (held < 550L) return;
        float a = Math.min(1f, (held - 550L) / 150f) * o;
        Module m = tipModule;
        List<String> lines = new ArrayList<String>(Gfx.wrap(m.description, 200));
        String hint = (m.toggleable() ? "Click: on/off  \u00b7  " : "") + "Gear: settings  \u00b7  " + m.category.label;
        float w = Gfx.textWidth(hint);
        for (String l : lines) w = Math.max(w, Gfx.textWidth(l));
        float h = lines.size() * 10 + 16;
        float tx = mx + 10, ty = my + 12;
        Gfx.roundedRect(tx - 1, ty - 1, w + 14, h + 2, 6, Gfx.alpha(ACCENT, 0.35f * a));
        Gfx.roundedRect(tx, ty, w + 12, h, 5, Gfx.alpha(0xFF0A0B10, 0.96f * a));
        float ly = ty + 5;
        for (String l : lines) {
            Gfx.text(l, tx + 6, ly, Gfx.alpha(TEXT, a));
            ly += 10;
        }
        Gfx.text(hint, tx + 6, ly + 1, Gfx.alpha(DIM, a));
    }

    private void drawFooter(float wx, float wy, float o) {
        String l = InputManager.keyName(ClientModule.menuKey()) + " / Esc  \u00b7  click: on/off  \u00b7  gear or right-click: settings  \u00b7  /1227  \u00b7  v" + Mod1227.VERSION;
        Gfx.textScaled(l, wx + SIDE + 20, wy + H - 14, 0.75f, Gfx.alpha(DIM, 0.6f * o));
    }

    private float localX(float sx) {
        return (sx - width / 2f) / curScale + width / 2f;
    }

    private float localY(float sy) {
        return (sy - height / 2f) / curScale + height / 2f;
    }
}
