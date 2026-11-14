package net.m1227.client.menu;

import net.m1227.client.ui.DS;
import net.m1227.client.ui.Glyph;
import net.m1227.client.ui.Ui;
import net.m1227.client.ui.UiScreen;
import net.m1227.core.InputManager;
import net.m1227.core.Setting;
import net.m1227.module.CrosshairModule;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/**
 * Crosshair Editor: a live preview of the crosshair, drawn by the same function the game uses
 * (CrosshairModule.render), so what you see is what you get. Every change applies at once.
 *
 * Preview: zoom 1x / 2x / 4x (1x is the real size on your screen, at your GUI scale), test
 * backgrounds made from your own game's textures (sky, grass, stone, sand, nether, snow, dark,
 * white) and "simulate" switches for aiming at a player, moving (spread) and the hit flash.
 * Controls: style tiles, sliders, colour swatches with hue and brightness, animation, presets and
 * "Restore default".
 */
public final class CrosshairEditorScreen extends UiScreen {
    private static final String[] BGS = {"Sky", "Grass", "Stone", "Sand", "Nether", "Snow", "Dark", "White"};
    private static final ResourceLocation[] TEX = {null, new ResourceLocation("textures/blocks/grass_side.png"),
            new ResourceLocation("textures/blocks/stone.png"), new ResourceLocation("textures/blocks/sand.png"),
            new ResourceLocation("textures/blocks/netherrack.png"), new ResourceLocation("textures/blocks/snow.png"), null, null};
    private static final int[] SWATCH = {0xFFFFFF, 0x00FF7F, 0x8FE6FF, 0x4DA3FF, 0xB06CFF, 0xFF5CA8, 0xFF4D5E, 0xFFC857, 0xFFFF55, 0x111111};
    private static int bg;
    private static int zoom = 2;
    private boolean simAim, simMove;
    private int colorTarget; // 0 main, 1 entity, 2 hit

    private CrosshairEditorScreen(net.minecraft.client.gui.GuiScreen parent) {
        super(parent);
    }

    public static void open() {
        InputManager.INSTANCE.requestScreen(new CrosshairEditorScreen(Minecraft.getMinecraft().currentScreen));
    }

    private void background(float x, float y, float w, float h) {
        if (bg == 0) {
            Gfx.gradient(x, y, w, h, 0xFF6FA8FF, 0xFFBFD9FF);
            return;
        }
        if (bg == 6) {
            Gfx.rect(x, y, w, h, 0xFF0B0C10);
            return;
        }
        if (bg == 7) {
            Gfx.rect(x, y, w, h, 0xFFF4F4F4);
            return;
        }
        // tiled block texture, 32 px tiles, partial tiles at the edges
        mc.getTextureManager().bindTexture(TEX[bg]);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        int tile = 32;
        for (int ty = 0; ty < h; ty += tile) {
            for (int tx = 0; tx < w; tx += tile) {
                int tw = (int) Math.min(tile, w - tx), th = (int) Math.min(tile, h - ty);
                Gui.drawModalRectWithCustomSizedTexture((int) x + tx, (int) y + ty, 0, 0, tw, th, tile, tile);
            }
        }
        GlStateManager.enableBlend();
    }

    @Override
    protected void render(float pt) {
        final CrosshairModule m = CrosshairModule.INSTANCE;
        int w = width, h = height;
        MenuBackground.draw(w, h, ui.mx, ui.my, mc.theWorld != null);
        if (m == null) return;
        float pw = Math.min(640, w - 16), px = (w - pw) / 2f, top = 10;
        Ui.textBold("Crosshair", px, top, DS.T_TITLE, DS.TEXT);
        Ui.text("Every change shows at once. The preview uses the same drawing as the game.", px, top + 18, DS.T_SMALL, DS.MUTED);
        ui.iconButton("back", px + pw - 20, top, 20, Glyph.BACK, "Back", true, this::back);
        ui.toggle("enabled", px + pw - 56, top + 3, m.isEnabled(), () -> m.setEnabled(!m.isEnabled()));
        Ui.textRight(m.isEnabled() ? "On" : "Off", px + pw - 62, top + 5, DS.T_SMALL, DS.MUTED);
        float cy = top + 34, ch = h - cy - 10;
        float lw = Math.min(280, pw * 0.46f);
        // ---------------------------------------------------------- preview
        Ui.card(px, cy, lw, ch);
        float bx = px + 10, by = cy + 10, bw = lw - 20, bh = Math.min(bw, ch - 92);
        background(bx, by, bw, bh);
        Gfx.roundedOutline(bx, by, bw, bh, 4, 1f, DS.LINE_STRONG);
        float spread = simMove ? 2f + 2f * (float) Math.sin(System.currentTimeMillis() / 300.0) : 0f;
        float ccx = bx + bw / 2f, ccy = by + bh / 2f;
        Gfx.scissorOn(bx, by, bw, bh);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(ccx, ccy, 0);
            GlStateManager.scale(zoom, zoom, 1);
            GlStateManager.translate(-ccx, -ccy, 0);
            m.render(ccx, ccy, simAim, spread);
        } finally {
            GlStateManager.popMatrix();
            Gfx.scissorOff();
        }
        Ui.text(zoom + "x" + (zoom == 1 ? " (real size)" : ""), bx + 4, by + bh - 10, 0.7f, bg == 7 || bg == 5 ? 0xFF303030 : 0xFFF0F0F0);
        float ry = by + bh + 8;
        for (int i = 0; i < BGS.length; i++) {
            final int k = i;
            float sw = (bw - 7 * 3) / 8f;
            ui.button("bg" + i, BGS[i], bx + i * (sw + 3), ry, sw, 14, bg == i ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> bg = k);
        }
        ry += 20;
        for (int z = 0; z < 3; z++) {
            final int zz = z == 0 ? 1 : z == 1 ? 2 : 4;
            ui.button("z" + z, zz + "x", bx + z * 40, ry, 36, 14, zoom == zz ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> zoom = zz);
        }
        ui.button("hit", "Test hit flash", bx + bw - 96, ry, 96, 14, Ui.SECONDARY, true, -1, m::flash);
        ry += 22;
        ui.toggle("simAim", bx, ry, simAim, () -> simAim = !simAim);
        Ui.text("Aiming at a player", bx + 32, ry + 2, DS.T_SMALL, DS.MUTED);
        ui.toggle("simMove", bx + bw / 2f, ry, simMove, () -> simMove = !simMove);
        Ui.text("Moving (spread)", bx + bw / 2f + 32, ry + 2, DS.T_SMALL, DS.MUTED);
        // ---------------------------------------------------------- controls
        float cx = px + lw + 10, cw = pw - lw - 10;
        Ui.card(cx, cy, cw, ch);
        float off = ui.beginScroll("xh", cx + 4, cy + 4, cw - 8, ch - 8);
        float x = cx + 12, y = cy + 10 - off, iw = cw - 24;
        Ui.caption("STYLE", x, y);
        y += 12;
        int cols = Math.max(4, (int) (iw / 38));
        float tw = (iw - (cols - 1) * 4) / cols;
        for (int i = 0; i < CrosshairModule.STYLES.length; i++) {
            final String s = CrosshairModule.STYLES[i];
            float tx = x + (i % cols) * (tw + 4), ty = y + (i / cols) * (tw * 0.8f + 4);
            boolean sel = m.style.is(s);
            boolean hv = ui.hover(tx, ty, tw, tw * 0.8f);
            Gfx.roundedRect(tx, ty, tw, tw * 0.8f, DS.R_SM, sel ? Gfx.alpha(DS.accent(), 0.28f) : hv ? DS.SURFACE_3 : DS.SURFACE_2);
            if (sel) Gfx.roundedOutline(tx, ty, tw, tw * 0.8f, DS.R_SM, 1f, DS.accent());
            CrosshairModule.shape(s, tx + tw / 2f, ty + tw * 0.4f - 2, 3.5f, 2f, 1f, 0xFFFFFFFF, 0f);
            if (s.equals("Dot") || s.equals("Circle Dot")) Gfx.circle(tx + tw / 2f, ty + tw * 0.4f - 2, 1f, 0xFFFFFFFF);
            Ui.textCenter(s, tx + tw / 2f, ty + tw * 0.8f - 8, 0.55f, sel ? DS.TEXT : DS.FAINT);
            ui.click(tx, ty, tw, tw * 0.8f, () -> m.style.set(s));
        }
        y += ((CrosshairModule.STYLES.length + cols - 1) / cols) * (tw * 0.8f + 4) + 8;
        y = slider("Size", m.size, x, y, iw);
        y = slider("Gap", m.gap, x, y, iw);
        y = slider("Thickness", m.thickness, x, y, iw);
        y = slider("Opacity", m.opacity, x, y, iw);
        y = toggle("Outline", m.outline, x, y, iw);
        if (m.outline.on()) y = slider("Outline width", m.outlineWidth, x, y, iw);
        y = toggle("Center dot", m.dot, x, y, iw);
        y = toggle("Dynamic spread (moving / jumping)", m.dynamic, x, y, iw);
        y = toggle("Colour when aiming at a player", m.onEntity, x, y, iw);
        y = toggle("Flash on hit", m.hitFlash, x, y, iw);
        y += 4;
        Ui.caption("COLOUR", x, y);
        String[] tgt = {"Main", "Player", "Hit flash"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            ui.button("ct" + i, tgt[i], x + iw - 3 * 62 + i * 62, y - 3, 58, 12, colorTarget == i ? Ui.PRIMARY : Ui.GHOST, true, -1, () -> colorTarget = k);
        }
        y += 14;
        final Setting.Col col = colorTarget == 0 ? m.color : colorTarget == 1 ? m.targetColor : m.hitColor;
        float sw = Math.min(18, (iw - 9 * 4) / 10f);
        for (int i = 0; i < SWATCH.length; i++) {
            final int c = SWATCH[i];
            float sx = x + i * (sw + 4);
            Gfx.roundedRect(sx, y, sw, sw, 3, 0xFF000000 | c);
            if ((col.rgb() & 0xFFFFFF) == c) Gfx.roundedOutline(sx - 1.5f, y - 1.5f, sw + 3, sw + 3, 4, 1f, 0xFFFFFFFF);
            ui.click(sx, y, sw, sw, () -> col.setRgb(c));
        }
        y += sw + 10;
        // hue bar
        for (int i = 0; i < 24; i++) {
            Gfx.gradientH(x + iw * i / 24f, y, iw / 24f + 0.5f, 6, 0xFF000000 | java.awt.Color.HSBtoRGB(i / 24f, 1f, 1f),
                    0xFF000000 | java.awt.Color.HSBtoRGB((i + 1) / 24f, 1f, 1f));
        }
        ui.knob("hue", x, y, iw, col.hue, v -> col.setHSB(v, Math.max(0.35f, col.sat), Math.max(0.35f, col.bri)));
        y += 14;
        Gfx.gradientH(x, y, iw, 6, 0xFF000000, 0xFF000000 | java.awt.Color.HSBtoRGB(col.hue, col.sat, 1f));
        ui.knob("bri", x, y, iw, col.bri, v -> col.setHSB(col.hue, col.sat, v));
        y += 14;
        Gfx.gradientH(x, y, iw, 6, 0xFFFFFFFF, 0xFF000000 | java.awt.Color.HSBtoRGB(col.hue, 1f, col.bri));
        ui.knob("sat", x, y, iw, col.sat, v -> col.setHSB(col.hue, v, col.bri));
        y += 16;
        Ui.caption("ANIMATION", x, y);
        ui.cycle("anim", x + iw - 120, y - 4, 120, 14, m.animation.get(), () -> m.animation.cycle(1), () -> m.animation.cycle(-1));
        y += 16;
        if (!m.animation.is("None")) y = slider("Animation speed", m.animSpeed, x, y, iw);
        y += 4;
        Ui.caption("PRESETS", x, y);
        y += 12;
        float pbw = (iw - 3 * 4) / 4f;
        for (int i = 0; i < CrosshairModule.PRESETS.length; i++) {
            final String n = CrosshairModule.PRESETS[i];
            ui.button("p" + i, n, x + (i % 4) * (pbw + 4), y + (i / 4) * 18, pbw, 15, Ui.SECONDARY, true, -1, () -> m.applyPreset(n));
        }
        y += ((CrosshairModule.PRESETS.length + 3) / 4) * 18 + 6;
        ui.button("reset", "Restore default", x, y, 110, 16, Ui.GHOST, true, Glyph.REFRESH, m::restoreDefaults);
        y += 24;
        ui.endScroll(y + off - cy);
    }

    private float slider(String label, final Setting.Num n, float x, float y, float w) {
        Ui.text(label, x, y, DS.T_SMALL, DS.MUTED);
        String v = (n.get() == Math.rint(n.get()) ? String.valueOf((long) n.get().doubleValue()) : String.format("%.2f", n.get())) + n.suffix;
        Ui.textRight(v, x + w, y, DS.T_SMALL, DS.TEXT);
        ui.slider("s" + label, x, y + 10, w, (float) ((n.get() - n.min) / (n.max - n.min)),
                f -> n.setClamped(Math.round((n.min + f * (n.max - n.min)) / n.step) * n.step));
        return y + 22;
    }

    private float toggle(String label, final Setting.Bool b, float x, float y, float w) {
        Ui.text(label, x, y + 2, DS.T_SMALL, DS.MUTED);
        ui.toggle("t" + label, x + w - 26, y, b.on(), () -> b.set(!b.on()));
        return y + 17;
    }
}
