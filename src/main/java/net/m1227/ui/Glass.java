package net.m1227.ui;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Frosted-glass panels for HUD elements. The screen area under the panel is
 * copied into a texture (GPU-side copy, no readback) and redrawn several
 * times with small offsets on two rings; averaging the taps blurs it. A
 * tint and a thin highlight edge finish the glass look. Cost: one small
 * copy and ~15 quads per panel.
 */
public final class Glass {
    private static int tex = -1, texW, texH;
    /** Blur taps: centre + three rings (6, 9 and 12 taps), radius 1 = full blur width. */
    private static final float[] TAP = new float[56];

    static {
        int i = 0;
        TAP[i++] = 0f;
        TAP[i++] = 0f;
        int[] n = {6, 9, 12};
        float[] rr = {0.34f, 0.67f, 1f};
        for (int ring = 0; ring < 3; ring++) {
            for (int k = 0; k < n[ring]; k++) {
                double a = k * Math.PI * 2 / n[ring] + ring * 0.4;
                TAP[i++] = (float) Math.cos(a) * rr[ring];
                TAP[i++] = (float) Math.sin(a) * rr[ring];
            }
        }
    }

    private Glass() {}

    /**
     * Draws a glass rounded rectangle. Coordinates are in the current GUI
     * space; "outerScale" is any extra GlStateManager.scale applied by the
     * caller (so the copied screen area lines up).
     */
    public static void panel(float x, float y, float w, float h, float r, float outerScale, int tint, float tintAlpha, float blurPx) {
        panel(x, y, w, h, r, outerScale, tint, tintAlpha, blurPx, 0f);
    }

    private static float lensCx, lensCy, lensAmt;

    /**
     * lens > 0 gives the "liquid glass" look: what is behind the panel is drawn slightly
     * magnified towards the centre, like looking through a thick rounded slab of glass.
     */
    public static void panel(float x, float y, float w, float h, float r, float outerScale, int tint, float tintAlpha, float blurPx, float lens) {
        Minecraft mc = Minecraft.getMinecraft();
        lensCx = x + w * 0.5f;
        lensCy = y + h * 0.5f;
        lensAmt = lens;
        int sf = new ScaledResolution(mc).getScaleFactor();
        float k = outerScale * sf;
        int pad = (int) Math.ceil(blurPx) + 2;
        int px0 = (int) Math.floor(x * k) - pad;
        int py0 = mc.displayHeight - (int) Math.ceil((y + h) * k) - pad;
        int pw = (int) Math.ceil(w * k) + pad * 2, ph = (int) Math.ceil(h * k) + pad * 2;
        if (px0 < 0) {
            pw += px0;
            px0 = 0;
        }
        if (py0 < 0) {
            ph += py0;
            py0 = 0;
        }
        pw = Math.min(pw, mc.displayWidth - px0);
        ph = Math.min(ph, mc.displayHeight - py0);
        if (pw <= 4 || ph <= 4) {
            Gfx.roundedRect(x, y, w, h, r, Gfx.alpha(tint, Math.max(0.6f, tintAlpha)));
            return;
        }
        ensure(pw, ph);
        GlStateManager.bindTexture(tex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, px0, py0, pw, ph);

        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull(); // the fans wind clockwise; culling would drop the whole panel
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        int taps = TAP.length / 2;
        for (int i = 0; i < taps; i++) {
            GlStateManager.color(1f, 1f, 1f, 1f / (i + 1));
            fan(x, y, w, h, r, k, px0, py0, TAP[i * 2] * blurPx, TAP[i * 2 + 1] * blurPx, mc.displayHeight);
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
        GlStateManager.enableAlpha();
        // frosted look: a faint milky layer, then the tint, then a soft light falloff from the top edge
        Gfx.roundedRect(x, y, w, h, r, lens > 0f ? 0x06FFFFFF : 0x0AFFFFFF);
        Gfx.roundedRect(x, y, w, h, r, Gfx.alpha(tint, tintAlpha));
        Gfx.gradient(x + r * 0.6f, y + 0.5f, w - r * 1.2f, Math.min(h * 0.5f, 14f), lens > 0f ? 0x22FFFFFF : 0x14FFFFFF, 0x00FFFFFF);
        if (lens > 0f) {
            // specular rim: bright along the top, fading to a faint edge at the bottom
            Gfx.roundedOutlineGradient(x, y, w, h, r, 1f, 0x66FFFFFF, 0x14FFFFFF);
            Gfx.roundedOutlineGradient(x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0f, r - 1f), 1f, 0x18FFFFFF, 0x06FFFFFF);
        }
        lensAmt = 0f;
    }

    private static void fan(float x, float y, float w, float h, float r, float k, int px0, int py0, float ox, float oy, int dispH) {
        r = Math.max(0f, Math.min(r, Math.min(w, h) * 0.5f));
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        vertex(x + w * 0.5f, y + h * 0.5f, k, px0, py0, ox, oy, dispH);
        arc(x + w - r, y + r, r, -90, 0, k, px0, py0, ox, oy, dispH);
        arc(x + w - r, y + h - r, r, 0, 90, k, px0, py0, ox, oy, dispH);
        arc(x + r, y + h - r, r, 90, 180, k, px0, py0, ox, oy, dispH);
        arc(x + r, y + r, r, 180, 270, k, px0, py0, ox, oy, dispH);
        vertex(x + w - r, y, k, px0, py0, ox, oy, dispH);
        GL11.glEnd();
    }

    private static void arc(float cx, float cy, float r, int from, int to, float k, int px0, int py0, float ox, float oy, int dispH) {
        for (int a = from; a <= to; a += 15) {
            double rad = Math.toRadians(a);
            vertex(cx + (float) Math.cos(rad) * r, cy + (float) Math.sin(rad) * r, k, px0, py0, ox, oy, dispH);
        }
    }

    private static void vertex(float gx, float gy, float k, int px0, int py0, float ox, float oy, int dispH) {
        float sx = gx, sy = gy;
        if (lensAmt > 0f) {
            sx = lensCx + (gx - lensCx) * (1f - lensAmt);
            sy = lensCy + (gy - lensCy) * (1f - lensAmt);
        }
        float u = (sx * k - px0 + ox) / texW;
        float v = (dispH - sy * k - py0 + oy) / texH;
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(gx, gy);
    }

    private static void ensure(int w, int h) {
        if (tex != -1 && w <= texW && h <= texH) return;
        if (tex != -1) GL11.glDeleteTextures(tex);
        texW = Math.max(w, texW);
        texH = Math.max(h, texH);
        tex = GL11.glGenTextures();
        GlStateManager.bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, texW, texH, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }
}
