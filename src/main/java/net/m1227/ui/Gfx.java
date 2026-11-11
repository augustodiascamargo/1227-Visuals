package net.m1227.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Small immediate-mode 2D drawing toolkit used by the menu and the HUD. */
public final class Gfx {
    private Gfx() {}

    // ---------------------------------------------------------- colours

    /** Multiplies the alpha channel of an ARGB colour. */
    public static int alpha(int argb, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int a = (int) (((argb >>> 24) & 255) * f);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (r << 16) | (g << 8) | bl;
    }

    private static void color(int c) {
        GlStateManager.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, ((c >>> 24) & 255) / 255f);
    }

    private static void begin() {
        // Rounded shapes are triangle fans whose winding is clockwise on screen. Minecraft keeps
        // back-face culling on in the GUI, which silently removed every rounded panel, circle,
        // switch and slider. Shapes are 2D, so culling is never wanted here.
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
    }

    private static void end() {
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    // ----------------------------------------------------------- shapes

    public static void rect(float x, float y, float w, float h, int argb) {
        if (w <= 0f || h <= 0f || (argb >>> 24) == 0) return;
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        end();
    }

    public static void roundedRect(float x, float y, float w, float h, float r, int argb) {
        if (w <= 0f || h <= 0f || (argb >>> 24) == 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w * 0.5f, y + h * 0.5f);
        arc(x + w - r, y + r, r, -90, 0);
        arc(x + w - r, y + h - r, r, 0, 90);
        arc(x + r, y + h - r, r, 90, 180);
        arc(x + r, y + r, r, 180, 270);
        GL11.glVertex2f(x + w - r, y); // close the fan at the first arc point
        GL11.glEnd();
        end();
    }

    private static final net.minecraft.util.ResourceLocation LOGO = new net.minecraft.util.ResourceLocation("m1227", "textures/logo.png");

    /**
     * The 1227 symbol (a white mask) tinted with any colour, with an optional soft glow.
     * Used in the menu corner and the watermark; it follows the accent colour.
     */
    public static void logo(float x, float y, float size, int argb, float glow) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(LOGO);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        float a = (argb >>> 24) / 255f, r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        if (glow > 0f) {
            // additive halo: the same mask drawn a little larger, twice
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            for (int i = 1; i <= 2; i++) {
                float gs = size * (1f + 0.08f * i), o = (gs - size) / 2f;
                quadTex(x - o, y - o, gs, r, g, b, a * glow * (0.35f / i));
            }
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        quadTex(x, y, size, r, g, b, a);
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private static void quadTex(float x, float y, float s, float r, float g, float b, float a) {
        GlStateManager.color(r, g, b, a);
        GL11.glColor4f(r, g, b, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0f, 0f);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0f, 1f);
        GL11.glVertex2f(x, y + s);
        GL11.glTexCoord2f(1f, 1f);
        GL11.glVertex2f(x + s, y + s);
        GL11.glTexCoord2f(1f, 0f);
        GL11.glVertex2f(x + s, y);
        GL11.glEnd();
    }

    /** Outline whose colour goes from top (at y) to bottom (at y + h). */
    public static void roundedOutlineGradient(float x, float y, float w, float h, float r, float t, int top, int bottom) {
        if (w <= 0f || h <= 0f) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        begin();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        float[][] c = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        float ri = Math.max(0f, r - t);
        for (int k = 0; k <= 4; k++) {
            float[] q = c[k % 4];
            int steps = k == 4 ? 0 : 6;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(q[2] + 90.0 * i / 6.0);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                float vy = q[1] + sn * r;
                color(mixArgb(top, bottom, (vy - y) / h));
                GL11.glVertex2f(q[0] + cs * r, vy);
                GL11.glVertex2f(q[0] + cs * ri, q[1] + sn * ri);
            }
        }
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        end();
    }

    private static int mixArgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) ((a >>> 24) + ((b >>> 24) - (a >>> 24)) * t);
        return (aa << 24) | (mix(a, b, t) & 0xFFFFFF);
    }

    /** Rounded-rectangle outline, thickness in GUI units. */
    public static void roundedOutline(float x, float y, float w, float h, float r, float t, int argb) {
        if (w <= 0f || h <= 0f || (argb >>> 24) == 0) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        float[][] c = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        float ri = Math.max(0f, r - t);
        for (int k = 0; k <= 4; k++) {
            float[] q = c[k % 4];
            int steps = k == 4 ? 0 : 6;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(q[2] + 90.0 * i / 6.0);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                GL11.glVertex2f(q[0] + cs * r, q[1] + sn * r);
                GL11.glVertex2f(q[0] + cs * ri, q[1] + sn * ri);
            }
        }
        GL11.glEnd();
        end();
    }

    private static void arc(float cx, float cy, float r, int from, int to) {
        for (int a = from; a <= to; a += 15) {
            double rad = Math.toRadians(a);
            GL11.glVertex2f(cx + (float) Math.cos(rad) * r, cy + (float) Math.sin(rad) * r);
        }
    }

    /** Thick line as a quad, thickness in GUI units. */
    public static void line(float x1, float y1, float x2, float y2, float w, int argb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0E-4f || (argb >>> 24) == 0) return;
        float nx = -dy / len * w * 0.5f, ny = dx / len * w * 0.5f;
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x1 + nx, y1 + ny);
        GL11.glVertex2f(x1 - nx, y1 - ny);
        GL11.glVertex2f(x2 - nx, y2 - ny);
        GL11.glVertex2f(x2 + nx, y2 + ny);
        GL11.glEnd();
        end();
    }

    /** Any convex quad (corners in order). */
    public static void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x2, y2);
        GL11.glVertex2f(x3, y3);
        GL11.glEnd();
        end();
    }

    /** Five-point star, filled (triangle fan) or outlined. */
    public static void star(float cx, float cy, float r, boolean filled, int argb) {
        if ((argb >>> 24) == 0) return;
        float[] px = new float[10], py = new float[10];
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rr = i % 2 == 0 ? r : r * 0.45f;
            px[i] = cx + (float) Math.cos(a) * rr;
            py[i] = cy + (float) Math.sin(a) * rr;
        }
        if (filled) {
            begin();
            color(argb);
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2f(cx, cy);
            for (int i = 0; i <= 10; i++) GL11.glVertex2f(px[i % 10], py[i % 10]);
            GL11.glEnd();
            end();
        } else {
            for (int i = 0; i < 10; i++) line(px[i], py[i], px[(i + 1) % 10], py[(i + 1) % 10], 1f, argb);
        }
    }

    public static void circle(float cx, float cy, float r, int argb) {
        roundedRect(cx - r, cy - r, r * 2f, r * 2f, r, argb);
    }

    /** Ring sector between radii r0..r1 and angles a0..a1 (radians), drawn in one strip (no overlapping seams). */
    public static void annulus(float cx, float cy, float r0, float r1, double a0, double a1, int argb) {
        if ((argb >>> 24) == 0 || r1 <= r0) return;
        begin();
        color(argb);
        int n = Math.max(6, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 48)));
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n;
            float c = (float) Math.cos(a), sn = (float) Math.sin(a);
            GL11.glVertex2f(cx + c * r1, cy + sn * r1);
            GL11.glVertex2f(cx + c * r0, cy + sn * r0);
        }
        GL11.glEnd();
        end();
    }

    /** Soft radial glow: argb at the centre fading to transparent at radius r. */
    public static void glow(float cx, float cy, float r, int argb) {
        if (r <= 0f || (argb >>> 24) == 0) return;
        begin();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(argb);
        GL11.glVertex2f(cx, cy);
        color(argb & 0x00FFFFFF);
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI * 2 * i / 32;
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        end();
    }

    /** Horizontal gradient (left colour to right colour). */
    public static void gradientH(float x, float y, float w, float h, int left, int right) {
        if (w <= 0f || h <= 0f) return;
        begin();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        color(left);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        color(right);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        end();
    }

    /**
     * Rounded outline whose colour sweeps around the shape between c0 and c1; phase (radians)
     * turns the sweep, so animating it makes the light run along the border.
     */
    public static void roundedOutlineSweep(float x, float y, float w, float h, float r, float t, int c0, int c1, float phase) {
        if (w <= 0f || h <= 0f) return;
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        float mx = x + w / 2f, my = y + h / 2f;
        begin();
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        float[][] c = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        float ri = Math.max(0f, r - t);
        for (int k = 0; k <= 4; k++) {
            float[] q = c[k % 4];
            int steps = k == 4 ? 0 : 6;
            for (int i = 0; i <= steps; i++) {
                double a = Math.toRadians(q[2] + 90.0 * i / 6.0);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                float vx = q[0] + cs * r, vy = q[1] + sn * r;
                double ang = Math.atan2((vy - my) / h, (vx - mx) / w);
                color(mixArgb(c0, c1, 0.5f + 0.5f * (float) Math.sin(ang + phase)));
                GL11.glVertex2f(vx, vy);
                GL11.glVertex2f(q[0] + cs * ri, q[1] + sn * ri);
            }
        }
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        end();
    }

    /** Vertical gradient (top colour to bottom colour). */
    public static void gradient(float x, float y, float w, float h, int top, int bottom) {
        begin();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(((top >> 16) & 255) / 255f, ((top >> 8) & 255) / 255f, (top & 255) / 255f, ((top >>> 24) & 255) / 255f);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x, y);
        GL11.glColor4f(((bottom >> 16) & 255) / 255f, ((bottom >> 8) & 255) / 255f, (bottom & 255) / 255f, ((bottom >>> 24) & 255) / 255f);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glEnd();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        end();
    }

    /** Text with a soft dark shadow, for the in-game HUD over bright scenes. */
    public static void textShadow(String s, float x, float y, int argb) {
        text(s, x + 0.6f, y + 0.6f, alpha(0xFF000000, ((argb >>> 24) & 255) / 255f * 0.55f));
        text(s, x, y, argb);
    }

    public static void hueBar(float x, float y, float w, float h, float alpha) {
        begin();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= 24; i++) {
            float t = i / 24f;
            int c = java.awt.Color.HSBtoRGB(t, 1f, 1f);
            GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, alpha);
            GL11.glVertex2f(x + t * w, y);
            GL11.glVertex2f(x + t * w, y + h);
        }
        GL11.glEnd();
        end();
    }

    /** Gradient from white (left) to the given colour (right). */
    public static void satBar(float x, float y, float w, float h, int rgb, float alpha) {
        begin();
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(1f, 1f, 1f, alpha);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glColor4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, alpha);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        end();
    }

    /** Small triangle, pointing right at 0 degrees and down at 90. */
    public static void chevron(float cx, float cy, float deg, int argb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0f);
        GlStateManager.rotate(deg, 0f, 0f, 1f);
        begin();
        color(argb);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex2f(-2f, -3f);
        GL11.glVertex2f(-2f, 3f);
        GL11.glVertex2f(2.5f, 0f);
        GL11.glEnd();
        end();
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------- text

    // Smooth font mode: while active (the menu turns it on), text goes through TtfFont.
    private static float smoothPxPerUnit;
    private static String weight = TtfFont.REGULAR;
    /** Size of normal text in GUI units when the smooth font is on (Minecraft's font is ~8 tall). */
    private static final float EM = 8.6f;

    /** Turn the smooth font on; pxPerUnit = screen pixels per GUI unit at the size text is drawn. */
    public static void smoothFont(float pxPerUnit) {
        smoothPxPerUnit = pxPerUnit;
    }

    public static void vanillaFont() {
        smoothPxPerUnit = 0f;
        weight = TtfFont.REGULAR;
    }

    public static boolean smooth() {
        return smoothPxPerUnit > 0f;
    }

    /** Regular / Medium / Bold / Light for the following text calls (smooth font only). */
    public static void weight(String w) {
        weight = w;
    }

    private static TtfFont ttf(float scale) {
        if (smoothPxPerUnit <= 0f) return null;
        return TtfFont.get(weight, Math.round(EM * scale * smoothPxPerUnit));
    }

    public static void text(String s, float x, float y, int argb) {
        text(s, x, y, 1f, argb);
    }

    private static void text(String s, float x, float y, float scale, int argb) {
        if ((argb >>> 24) < 8) return;
        TtfFont f = ttf(scale);
        if (f != null) {
            float k = (EM * scale) / Math.max(1f, Math.round(EM * scale * smoothPxPerUnit));
            // centre the glyphs' cap height in the 8-unit line Minecraft text used, so layouts keep working
            f.draw(s, x, y - 2.6f * scale, k, argb);
            return;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        Minecraft.getMinecraft().fontRendererObj.drawString(s, 0f, 0f, argb, false);
        GlStateManager.popMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    public static void textScaled(String s, float x, float y, float scale, int argb) {
        text(s, x, y, scale, argb);
    }

    public static float textWidth(String s) {
        TtfFont f = ttf(1f);
        if (f != null) return f.widthPx(s) * EM / Math.max(1f, Math.round(EM * smoothPxPerUnit));
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
    }

    public static float textWidth(String s, float scale) {
        TtfFont f = ttf(scale);
        if (f != null) return f.widthPx(s) * EM * scale / Math.max(1f, Math.round(EM * scale * smoothPxPerUnit));
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s) * scale;
    }

    /** Cuts the string to fit maxW (adds an ellipsis). */
    public static String trim(String s, float maxW) {
        if (textWidth(s) <= maxW) return s;
        String e = smooth() ? "\u2026" : "..";
        while (s.length() > 1 && textWidth(s + e) > maxW) s = s.substring(0, s.length() - 1);
        return s + e;
    }

    /** Word-wraps to maxW. */
    public static java.util.List<String> wrap(String s, float maxW) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (textWidth(t) > maxW && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(t);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    // ---------------------------------------------------------- scissor

    public static void scissorOn(float x, float y, float w, float h) {
        scissorOn(x, y, w, h, new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());
    }

    // optional transform (centre + scale) applied to scissor rectangles, set by the menu when it is scaled
    private static float tCx, tCy, tS = 1f;

    public static void setTransform(float cx, float cy, float s) {
        tCx = cx;
        tCy = cy;
        tS = s;
    }

    public static void clearTransform() {
        tS = 1f;
    }

    public static void scissorOn(float x, float y, float w, float h, int scaleFactor) {
        if (tS != 1f) {
            x = (x - tCx) * tS + tCx;
            y = (y - tCy) * tS + tCy;
            w *= tS;
            h *= tS;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int f = scaleFactor;
        int sx = Math.max(0, (int) (x * f));
        int sy = Math.max(0, (int) (mc.displayHeight - (y + h) * f));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, Math.max(0, (int) (w * f)), Math.max(0, (int) (h * f)));
    }

    public static void scissorOff() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
