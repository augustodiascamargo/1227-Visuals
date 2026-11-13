package net.m1227.client.menu;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import net.m1227.client.ui.DS;
import net.m1227.client.ui.Ui;
import net.m1227.ui.Gfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Opening animation of the 1227 main menu (original design). Styles:
 * - Press to enter (default): the Converge assembly on a smoky backdrop, then the title waits
 *   with "Press any key to enter"; on a key or click the symbol spins, bursts into sparks and flies
 *   to its place in the menu while the menu fades in;
 * - Converge: hundreds of sparks fly in on curved paths and assemble the 1227 symbol, a
 *   shockwave and a light sweep follow, the name types in letter by letter;
 * - Glitch: the symbol arrives through RGB-split, sliced and flickering frames, the name
 *   scrambles into place;
 * - Minimal: fade and scale with a light sweep.
 * At the end the symbol flies to its place in the menu while the menu fades in. Any click or key
 * skips to that last part.
 */
final class Intro {
    static final ResourceLocation LOGO = new ResourceLocation("m1227", "textures/logo.png");
    /** Seconds before the move into the menu starts, and the move's length. */
    private static final float MOVE = 0.65f;
    private static float[][] targets;

    private final String style;
    private final boolean enter;
    private final float hold;
    /** Press to enter: when the key was pressed (-1 = still waiting). */
    private float exitAt = -1f;
    private static final float EXIT = 0.95f;
    private long start = System.nanoTime();
    private float skipAt = -1f;
    private final List<float[]> sparks = new ArrayList<float[]>();
    private final Random rnd = new Random();

    Intro(String style) {
        this.enter = style.equals("Press to enter");
        this.style = enter ? "Converge" : style;
        this.hold = this.style.equals("Converge") ? 3.0f : this.style.equals("Glitch") ? 2.5f : 1.8f;
        float[][] t = targets();
        Random r = new Random(1227);
        for (float[] p : t) {
            double ang = r.nextDouble() * Math.PI * 2;
            float dist = 0.9f + r.nextFloat() * 0.8f;
            // start (unit circle around the centre), curve control, target, delay, size
            sparks.add(new float[]{(float) Math.cos(ang) * dist, (float) Math.sin(ang) * dist,
                    (float) Math.cos(ang + 1.6) * dist * 0.6f, (float) Math.sin(ang + 1.6) * dist * 0.6f,
                    p[0], p[1], r.nextFloat() * 0.45f, 0.6f + r.nextFloat() * 0.8f});
        }
    }

    /** Points on the 1227 symbol (0..1 square), sampled once from the logo texture. */
    private static float[][] targets() {
        if (targets != null) return targets;
        List<float[]> out = new ArrayList<float[]>();
        try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(LOGO).getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            int step = Math.max(2, img.getWidth() / 40);
            for (int y = 0; y < img.getHeight(); y += step)
                for (int x = 0; x < img.getWidth(); x += step)
                    if ((img.getRGB(x, y) >>> 24) > 140) out.add(new float[]{x / (float) img.getWidth(), y / (float) img.getHeight()});
        } catch (Exception e) {
            // no texture: a ring of points
            for (int i = 0; i < 160; i++) {
                double a = Math.PI * 2 * i / 160;
                out.add(new float[]{0.5f + 0.4f * (float) Math.cos(a), 0.5f + 0.4f * (float) Math.sin(a)});
            }
        }
        java.util.Collections.shuffle(out, new Random(7));
        while (out.size() > 480) out.remove(out.size() - 1);
        targets = out.toArray(new float[0][]);
        return targets;
    }

    private float time() {
        return (System.nanoTime() - start) / 1e9f;
    }

    void skip() {
        float t = time();
        if (enter) {
            if (exitAt < 0f) exitAt = t;
            return;
        }
        if (t < hold) start -= (long) ((hold - t) * 1e9);
    }

    boolean done() {
        return enter ? exitAt >= 0f && time() >= exitAt + EXIT : time() >= hold + MOVE;
    }

    /** 0 while the intro covers the screen, rising to 1 as the menu takes over. */
    float menuAlpha() {
        if (enter) return exitAt < 0f ? 0f : clamp((time() - exitAt - EXIT * 0.35f) / (EXIT * 0.65f));
        return clamp((time() - hold) / MOVE);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float easeOut(float t) {
        t = clamp(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float easeInOut(float t) {
        t = clamp(t);
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /**
     * Draws the intro over the menu. (tx, ty, ts) = where the symbol sits in the menu at the end.
     */
    void draw(int w, int h, float tx, float ty, float ts) {
        float t = time();
        int acc = DS.accent();
        float move = enter ? (exitAt < 0f ? 0f : easeInOut((t - exitAt) / EXIT)) : easeInOut((t - hold) / MOVE);
        // backdrop
        Gfx.rect(0, 0, w, h, Gfx.alpha(0xFF050608, 1f - move));
        if (enter) MenuBackground.smoke(w, h, System.nanoTime() / 1e9, 0f, 0.85f * (1f - move));
        float size = Math.min(w, h) * 0.30f;
        float cx = w / 2f, cy = h / 2f - size * 0.12f;
        // symbol position, moving into the menu at the end
        float idle = enter && exitAt < 0f && t > 2.3f ? (float) Math.sin((t - 2.3f) * 1.6f) * size * 0.015f : 0f;
        float lx = cx - size / 2f + (tx - (cx - size / 2f)) * move;
        float ly = cy - size / 2f + idle + (ty - (cy - size / 2f)) * move - (float) Math.sin(move * Math.PI) * size * 0.25f;
        float ls = size + (ts - size) * move;

        if (style.equals("Converge")) converge(t, w, h, cx, cy, size, acc);
        else if (style.equals("Glitch")) glitch(t, cx, cy, size, acc);

        // the symbol itself
        float appear = style.equals("Converge") ? clamp((t - 1.25f) / 0.45f) : style.equals("Glitch") ? clamp((t - 1.3f) / 0.25f) : easeOut(t / 0.8f);
        if (enter && exitAt >= 0f) appear = 1f; // pressed during the assembly: show it whole and leave
        if (appear > 0f) {
            float pop = style.equals("Minimal") ? 0.92f + 0.08f * appear : 1f + 0.06f * (1f - appear);
            float s = ls * (move > 0 ? 1f : pop), o = (ls - s) / 2f;
            if (enter && exitAt >= 0f) {
                // two Y turns while it flies: drawn with a horizontal squash (cos of the angle)
                float spin = (float) Math.cos(move * Math.PI * 4);
                logoSpin(lx + o, ly + o, s, Math.max(0.06f, Math.abs(spin)), Gfx.alpha(acc, appear), 0.8f * (1f - move), spin < 0);
            } else {
                Gfx.logo(lx + o, ly + o, s, Gfx.alpha(acc, appear), 0.8f * (1f - move));
            }
            float since = t - (style.equals("Converge") ? 1.9f : style.equals("Glitch") ? 1.7f : 0.7f);
            // waiting on the title: the light sweep comes back every 3.5 s
            if (enter && exitAt < 0f && since > 1f) since = (since - 1f) % 3.5f;
            if (move <= 0f) sweep(since, lx + o, ly + o, s);
        }
        if (enter && exitAt >= 0f) burst(t - exitAt, cx, cy, size, acc);
        // name and caption (they leave first when entering)
        float textA = enter && exitAt >= 0f ? 1f - clamp((t - exitAt) / 0.25f) : 1f - move;
        if (textA > 0f) name(t, cx, cy + size * 0.62f, textA);
        if (enter && exitAt < 0f && t > 2.4f) {
            float k = clamp((t - 2.4f) / 0.5f) * (0.55f + 0.45f * (float) Math.sin((t - 2.4f) * 3.2f));
            Ui.textCenter("Press any key to enter", cx, cy + size * 0.62f + 46, DS.T_BODY, Gfx.alpha(0xFFD8DBE2, k));
            dust(t, w, h);
        }
    }

    /** The symbol squashed horizontally around its centre (a turn seen from the front). */
    private static void logoSpin(float x, float y, float s, float sx, int argb, float glow, boolean back) {
        float cxl = x + s / 2f;
        GlStateManager.pushMatrix();
        GlStateManager.translate(cxl, 0, 0);
        GlStateManager.scale(sx * (back ? -1f : 1f), 1f, 1f);
        GlStateManager.translate(-cxl, 0, 0);
        Gfx.logo(x, y, s, back ? Gfx.mix(argb, 0xFF202028, 0.35f) : argb, glow);
        GlStateManager.popMatrix();
    }

    /** Sparks thrown off the symbol when it starts to spin away. */
    private void burst(float e, float cx, float cy, float size, int acc) {
        if (e > 0.7f) return;
        float k = easeOut(e / 0.7f);
        for (int i = 0; i < sparks.size(); i += 2) {
            float[] p = sparks.get(i);
            float px = cx - size / 2f + p[4] * size, py = cy - size / 2f + p[5] * size;
            float dx = px - cx, dy = py - cy;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 1f;
            float fx = px + dx / d * size * (0.4f + p[7] * 0.5f) * k, fy = py + dy / d * size * (0.4f + p[7] * 0.5f) * k + k * k * size * 0.2f;
            Gfx.rect(fx - 0.6f, fy - 0.6f, 1.2f * p[7], 1.2f * p[7], Gfx.alpha(Gfx.mix(acc, 0xFFFFFFFF, 0.5f), 1f - k));
        }
    }

    /** Dust drifting slowly upward while the title waits. */
    private void dust(float t, int w, int h) {
        for (int i = 0; i < 40; i++) {
            float[] p = sparks.get(i % sparks.size());
            float x = (p[4] * 0.9f + 0.05f + p[0] * 0.3f) * w;
            float y = h - ((t * (6f + p[7] * 8f) + p[5] * h) % (h + 10f));
            Gfx.rect(x % w, y, 0.9f, 0.9f, Gfx.alpha(0xFFFFFFFF, 0.25f + 0.2f * (float) Math.sin(t * 2 + i)));
        }
    }

    // ------------------------------------------------------------------ styles

    private void converge(float t, int w, int h, float cx, float cy, float size, int acc) {
        float diag = (float) Math.sqrt(w * w + h * h) * 0.55f;
        // ignition spark
        float ign = clamp(t / 0.3f) * (1f - clamp((t - 0.5f) / 0.4f));
        if (ign > 0) Gfx.glow(cx, cy, size * 0.5f * ign, Gfx.alpha(acc, 0.8f * ign));
        float fade = 1f - clamp((t - 1.45f) / 0.35f);
        if (fade > 0f) {
            for (float[] p : sparks) {
                float k = easeOut((t - 0.15f - p[6]) / 0.95f);
                if (k <= 0f) continue;
                float k0 = easeOut((t - 0.15f - p[6] - 0.06f) / 0.95f);
                float[] a = bez(p, k, cx, cy, size, diag), b = bez(p, k0, cx, cy, size, diag);
                int c = Gfx.mix(acc, 0xFFFFFFFF, k * 0.6f);
                Gfx.line(b[0], b[1], a[0], a[1], 0.7f * p[7], Gfx.alpha(c, 0.55f * fade));
                Gfx.rect(a[0] - 0.7f * p[7], a[1] - 0.7f * p[7], 1.4f * p[7], 1.4f * p[7], Gfx.alpha(c, fade));
            }
        }
        // shockwave when the symbol locks in
        float sw = clamp((t - 1.35f) / 0.7f);
        if (sw > 0f && sw < 1f) {
            float r = size * (0.35f + 1.4f * easeOut(sw));
            Gfx.annulus(cx, cy, r - 1.2f, r, 0, Math.PI * 2, Gfx.alpha(acc, 0.7f * (1f - sw)));
            Gfx.glow(cx, cy, size * 0.9f, Gfx.alpha(acc, 0.35f * (1f - sw)));
        }
    }

    /** Quadratic curve from the spark's start to its point on the symbol. */
    private static float[] bez(float[] p, float k, float cx, float cy, float size, float diag) {
        float sx = cx + p[0] * diag, sy = cy + p[1] * diag;
        float qx = cx + p[2] * diag, qy = cy + p[3] * diag;
        float ex = cx - size / 2f + p[4] * size, ey = cy - size / 2f + p[5] * size;
        float u = 1f - k;
        return new float[]{u * u * sx + 2 * u * k * qx + k * k * ex, u * u * sy + 2 * u * k * qy + k * k * ey};
    }

    private void glitch(float t, float cx, float cy, float size, int acc) {
        float on = clamp(t / 0.2f);
        float settle = clamp((t - 0.35f) / 1.0f);
        if (t > 1.55f || on <= 0f) return;
        float x = cx - size / 2f, y = cy - size / 2f;
        // scanline noise
        for (int i = 0; i < 14; i++) {
            if (rnd.nextFloat() > 0.6f * (1f - settle)) continue;
            float yy = y - size * 0.3f + rnd.nextFloat() * size * 1.6f;
            Gfx.rect(cx - size, yy, size * 2f, 0.6f + rnd.nextFloat() * 1.4f, Gfx.alpha(acc, 0.25f + rnd.nextFloat() * 0.4f));
        }
        if (t < 0.3f) return;
        float jitter = (1f - settle) * size * 0.08f;
        // RGB split: red and cyan copies drifting around the true position
        logoSlices(x + jitter * (rnd.nextFloat() - 0.2f), y, size, 0xFFFF3B5C, 0.55f * (1f - settle), jitter);
        logoSlices(x - jitter * (rnd.nextFloat() - 0.2f), y, size, 0xFF3DF5FF, 0.55f * (1f - settle), jitter);
        if (rnd.nextFloat() < 0.75f) logoSlices(x, y, size, acc, 0.9f, jitter * 0.5f);
    }

    /** The symbol cut in horizontal slices, each shifted sideways (glitch). */
    private void logoSlices(float x, float y, float s, int argb, float a, float shift) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        GL11.glColor4f(r, g, b, a);
        int n = 9;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < n; i++) {
            float v0 = i / (float) n, v1 = (i + 1) / (float) n;
            float dx = rnd.nextFloat() < 0.35f ? (rnd.nextFloat() * 2f - 1f) * shift : 0f;
            GL11.glTexCoord2f(0, v0);
            GL11.glVertex2f(x + dx, y + v0 * s);
            GL11.glTexCoord2f(0, v1);
            GL11.glVertex2f(x + dx, y + v1 * s);
            GL11.glTexCoord2f(1, v1);
            GL11.glVertex2f(x + dx + s, y + v1 * s);
            GL11.glTexCoord2f(1, v0);
            GL11.glVertex2f(x + dx + s, y + v0 * s);
        }
        GL11.glEnd();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** A bright band crossing the symbol once (the symbol redrawn additively inside a moving clip). */
    private static void sweep(float t, float x, float y, float s) {
        float k = t / 0.7f;
        if (k <= 0f || k >= 1f) return;
        float bx = x - s * 0.3f + (s * 1.6f) * k, bw = s * 0.18f;
        Gfx.scissorOn(bx, y, bw, s);
        Minecraft.getMinecraft().getTextureManager().bindTexture(LOGO);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glColor4f(1f, 1f, 1f, 0.75f * (float) Math.sin(Math.PI * k));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + s);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + s, y + s);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + s, y);
        GL11.glEnd();
        Gfx.scissorOff();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private static final String NAME = "1227", CAPTION = "C L I E N T";
    private static final String GLYPHS = "#%&@$*+=?/<>01279";

    private void name(float t, float cx, float y, float alpha) {
        float t0 = style.equals("Converge") ? 1.55f : style.equals("Glitch") ? 1.35f : 0.45f;
        float scale = 2.4f;
        float total = Ui.width(NAME, scale);
        float x = cx - total / 2f;
        for (int i = 0; i < NAME.length(); i++) {
            float k = easeOut((t - t0 - i * 0.09f) / 0.35f);
            if (k <= 0f) {
                x += Ui.width(String.valueOf(NAME.charAt(i)), scale);
                continue;
            }
            String ch = String.valueOf(NAME.charAt(i));
            if (style.equals("Glitch") && k < 0.85f) ch = String.valueOf(GLYPHS.charAt(rnd.nextInt(GLYPHS.length())));
            Ui.textBold(ch, x, y + (1f - k) * 8f, scale, Gfx.alpha(DS.TEXT, k * alpha));
            x += Ui.width(String.valueOf(NAME.charAt(i)), scale);
        }
        float ck = easeOut((t - t0 - 0.45f) / 0.5f);
        if (ck > 0f) {
            float spread = 1f + (1f - ck) * 0.6f;
            float cw = Ui.width(CAPTION, 0.8f) * spread;
            float px = cx - cw / 2f;
            for (int i = 0; i < CAPTION.length(); i++) {
                String c = String.valueOf(CAPTION.charAt(i));
                Ui.text(c, px, y + 28, 0.8f, Gfx.alpha(DS.accent(), ck * alpha));
                px += Ui.width(c, 0.8f) * spread;
            }
        }
    }
}
