package net.m1227.client.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Textured quads and a small cache of images loaded from disk (backgrounds, screenshots, server icons). */
public final class Img {
    private Img() {}

    public static final class Loaded {
        public final ResourceLocation id;
        public final int w, h;
        public final long stamp;
        final DynamicTexture tex;

        Loaded(ResourceLocation id, DynamicTexture tex, int w, int h, long stamp) {
            this.id = id;
            this.tex = tex;
            this.w = w;
            this.h = h;
            this.stamp = stamp;
        }
    }

    private static final Map<String, Loaded> CACHE = new HashMap<String, Loaded>();
    private static int counter;

    /** Uploads an image as a texture (main thread only). */
    public static Loaded upload(String name, BufferedImage img, long stamp) {
        DynamicTexture t = new DynamicTexture(img);
        ResourceLocation id = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("m1227_" + name.replaceAll("[^a-z0-9_]", "_") + "_" + (counter++), t);
        return new Loaded(id, t, img.getWidth(), img.getHeight(), stamp);
    }

    /** Image file as a texture, downscaled to maxSide, reloaded when the file changes. Null if unreadable. */
    public static Loaded file(File f, int maxSide) {
        String key = f.getAbsolutePath() + "@" + maxSide;
        Loaded l = CACHE.get(key);
        long stamp = f.lastModified();
        if (l != null && l.stamp == stamp) return l;
        if (l != null) free(key);
        try {
            BufferedImage src = ImageIO.read(f);
            if (src == null) return null;
            BufferedImage img = scale(src, maxSide);
            Loaded n = upload(f.getName().toLowerCase(), img, stamp);
            CACHE.put(key, n);
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the image for this key ("path@size") is already a texture. */
    public static boolean cached(String key) {
        return CACHE.containsKey(key);
    }

    public static void free(String key) {
        Loaded l = CACHE.remove(key);
        if (l != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(l.id);
    }

    /** Frees every cached texture whose key starts with prefix (e.g. a folder path). */
    public static void freeAll(String prefix) {
        for (String k : new java.util.ArrayList<String>(CACHE.keySet())) if (k.startsWith(prefix)) free(k);
    }

    public static BufferedImage scale(BufferedImage src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        float k = Math.min(1f, maxSide / (float) Math.max(w, h));
        if (k >= 1f && src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        int nw = Math.max(1, Math.round(w * k)), nh = Math.max(1, Math.round(h * k));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /** Draws a texture into (x, y, w, h) using the sub-rectangle u0..u1, v0..v1. */
    public static void draw(ResourceLocation id, float x, float y, float w, float h, float u0, float v0, float u1, float v1, float alpha) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(id);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** Draws the image covering (x, y, w, h) without stretching (crops the overflow). */
    public static void cover(Loaded l, float x, float y, float w, float h, float alpha) {
        float ia = l.w / (float) l.h, ba = w / h;
        float u0 = 0, v0 = 0, u1 = 1, v1 = 1;
        if (ia > ba) {
            float k = ba / ia;
            u0 = (1 - k) / 2f;
            u1 = u0 + k;
        } else {
            float k = ia / ba;
            v0 = (1 - k) / 2f;
            v1 = v0 + k;
        }
        draw(l.id, x, y, w, h, u0, v0, u1, v1, alpha);
    }

    /** Draws the image fitted inside (x, y, w, h) keeping its aspect ratio, centred. */
    public static void contain(Loaded l, float x, float y, float w, float h, float alpha) {
        float k = Math.min(w / l.w, h / l.h);
        float dw = l.w * k, dh = l.h * k;
        draw(l.id, x + (w - dw) / 2f, y + (h - dh) / 2f, dw, dh, 0, 0, 1, 1, alpha);
    }
}
