package net.m1227.ui;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.m1227.Mod1227;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.opengl.GL11;

/**
 * Smooth anti-aliased text for the menu. Glyphs of the bundled Poppins font
 * (SIL Open Font License, see assets/m1227/fonts/OFL.txt) are rasterised by
 * Java2D at the exact pixel size they will appear on screen, packed into a
 * texture atlas and drawn with linear filtering - no pixelated Minecraft font.
 * One atlas per (weight, pixel size), built once and cached.
 */
public final class TtfFont {
    public static final String REGULAR = "Regular", MEDIUM = "Medium", BOLD = "Bold", LIGHT = "Light";
    private static final Map<String, Font> BASE = new HashMap<String, Font>();
    private static final Map<String, TtfFont> CACHE = new HashMap<String, TtfFont>();
    private static final String EXTRA = "…★✓•→←❤";
    private static boolean broken;

    private final int px;
    private int tex = -1;
    private final float[] adv = new float[256 + EXTRA.length()];
    private final float[] u0 = new float[adv.length], v0 = new float[adv.length], u1 = new float[adv.length], v1 = new float[adv.length];
    private final float[] cw = new float[adv.length];
    private float cellH, pad, ascent;

    private TtfFont(int px) {
        this.px = px;
    }

    /** Null if the font could not be loaded (callers fall back to Minecraft's font). */
    public static TtfFont get(String weight, int px) {
        if (broken) return null;
        px = Math.max(6, Math.min(96, px));
        String key = weight + "@" + px;
        TtfFont f = CACHE.get(key);
        if (f != null) return f;
        Font base = base(weight);
        if (base == null) return null;
        f = new TtfFont(px);
        try {
            f.build(base.deriveFont(Font.PLAIN, (float) px));
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227: could not build font atlas, using Minecraft's font", t);
            broken = true;
            return null;
        }
        if (CACHE.size() > 24) {
            for (TtfFont old : CACHE.values()) if (old.tex != -1) GL11.glDeleteTextures(old.tex);
            CACHE.clear();
        }
        CACHE.put(key, f);
        return f;
    }

    private static Font base(String weight) {
        Font f = BASE.get(weight);
        if (f != null) return f;
        InputStream in = null;
        try {
            in = TtfFont.class.getResourceAsStream("/assets/m1227/fonts/Poppins-" + weight + ".ttf");
            if (in == null) throw new IllegalStateException("missing font " + weight);
            f = Font.createFont(Font.TRUETYPE_FONT, in);
            BASE.put(weight, f);
            return f;
        } catch (Throwable t) {
            Mod1227.LOGGER.warn("1227: font " + weight + " unavailable", t);
            broken = true;
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing
                }
            }
        }
    }

    private static int index(char c) {
        if (c < 256) return c;
        int i = EXTRA.indexOf(c);
        return i < 0 ? '?' : 256 + i;
    }

    private static char charAt(int i) {
        return i < 256 ? (char) i : EXTRA.charAt(i - 256);
    }

    private void build(Font font) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        hints(pg);
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        pad = Math.max(2f, px * 0.12f);
        ascent = fm.getAscent();
        cellH = fm.getAscent() + fm.getDescent() + pad * 2;
        int atlasW = px <= 24 ? 512 : px <= 48 ? 1024 : 2048;
        // layout pass
        int x = 0, y = 0;
        int[] cx = new int[adv.length], cy = new int[adv.length];
        for (int i = 0; i < adv.length; i++) {
            char c = charAt(i);
            if (i < 32 || (i >= 127 && i < 160)) continue;
            float a = (float) font.getStringBounds(String.valueOf(c), pg.getFontRenderContext()).getWidth();
            adv[i] = a;
            int w = (int) Math.ceil(a + pad * 2);
            if (x + w > atlasW) {
                x = 0;
                y += (int) Math.ceil(cellH);
            }
            cx[i] = x;
            cy[i] = y;
            cw[i] = w;
            x += w;
        }
        pg.dispose();
        int atlasH = 1;
        while (atlasH < y + Math.ceil(cellH)) atlasH <<= 1;
        BufferedImage img = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        hints(g);
        g.setFont(font);
        g.setColor(java.awt.Color.WHITE);
        for (int i = 0; i < adv.length; i++) {
            if (cw[i] <= 0) continue;
            g.drawString(String.valueOf(charAt(i)), cx[i] + pad, cy[i] + pad + ascent);
            u0[i] = cx[i] / (float) atlasW;
            v0[i] = cy[i] / (float) atlasH;
            u1[i] = (cx[i] + cw[i]) / (float) atlasW;
            v1[i] = (cy[i] + cellH) / (float) atlasH;
        }
        g.dispose();
        tex = GL11.glGenTextures();
        TextureUtil.uploadTextureImageAllocate(tex, img, true, true);
    }

    private static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /** Width in pixels of this font size. */
    public float widthPx(String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            w += adv[index(c)];
        }
        return w;
    }

    public float ascentPx() {
        return ascent;
    }

    /**
     * Draws with the top of the line at y. k converts font pixels to GUI units
     * (1 / pixels-per-unit). Section-sign colour codes are skipped.
     */
    public void draw(String s, float x, float y, float k, int argb) {
        if (tex == -1 || (argb >>> 24) < 4) return;
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();
        GlStateManager.bindTexture(tex);
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f, gg = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        GlStateManager.color(r, gg, b, a);
        GL11.glColor4f(r, gg, b, a);
        // snap the start to the pixel grid so glyphs stay crisp
        float pen = Math.round(x / k) * k;
        float top = Math.round(y / k) * k - pad * k;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            int g = index(c);
            if (cw[g] > 0 && c != ' ') {
                float qx = pen - pad * k, qw = cw[g] * k, qh = cellH * k;
                GL11.glTexCoord2f(u0[g], v0[g]);
                GL11.glVertex2f(qx, top);
                GL11.glTexCoord2f(u0[g], v1[g]);
                GL11.glVertex2f(qx, top + qh);
                GL11.glTexCoord2f(u1[g], v1[g]);
                GL11.glVertex2f(qx + qw, top + qh);
                GL11.glTexCoord2f(u1[g], v0[g]);
                GL11.glVertex2f(qx + qw, top);
            }
            pen += adv[g] * k;
        }
        GL11.glEnd();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
