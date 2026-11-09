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

    public static void text(String s, float x, float y, int argb) {
        // FontRenderer treats alpha <= 3 as opaque, so skip near-invisible text.
        if ((argb >>> 24) < 8) return;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Minecraft.getMinecraft().fontRendererObj.drawString(s, x, y, argb, false);
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    public static void textScaled(String s, float x, float y, float scale, int argb) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0f);
        GlStateManager.scale(scale, scale, 1f);
        text(s, 0f, 0f, argb);
        GlStateManager.popMatrix();
    }

    public static int textWidth(String s) {
        return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
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
