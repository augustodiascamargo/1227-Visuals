package net.m1227.weapons;

import java.nio.FloatBuffer;
import net.m1227.render.FlatColor;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/**
 * GPU side of an imported model: one texture per image (nearest filtering, pixel art stays
 * sharp) and one display list per part, compiled once. Drawing multiplies each part by its
 * bone matrix, so animated bones cost nothing extra; animated texture strips move through the
 * texture matrix. Honors {@link FlatColor} for glow / outline silhouettes (texture alpha kept,
 * so cut-out pixels stay cut out). Main thread only.
 */
final class WeaponGpu {
    private int[] tex = new int[0];
    private int listBase = -1, listCount, white;
    private boolean failed;
    private static final FloatBuffer BUF = BufferUtils.createFloatBuffer(16);

    boolean ready() {
        return listBase >= 0;
    }

    boolean failed() {
        return failed;
    }

    void upload(WModel m) {
        if (listBase >= 0 || failed) return;
        try {
            tex = new int[m.textures.size()];
            for (int i = 0; i < tex.length; i++) {
                WModel.Tex t = m.textures.get(i);
                int id = TextureUtil.glGenTextures();
                TextureUtil.allocateTexture(id, t.w, t.h);
                TextureUtil.uploadTexture(id, t.argb, t.w, t.h);
                GlStateManager.bindTexture(id);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                tex[i] = id;
            }
            // untextured parts use a white texture, so silhouettes (glow, outlines) still apply to them
            white = TextureUtil.glGenTextures();
            TextureUtil.allocateTexture(white, 1, 1);
            TextureUtil.uploadTexture(white, new int[]{0xFFFFFFFF}, 1, 1);
            listCount = m.parts.size();
            listBase = GL11.glGenLists(Math.max(1, listCount));
            for (int p = 0; p < listCount; p++) {
                WModel.Part part = m.parts.get(p);
                GL11.glNewList(listBase + p, GL11.GL_COMPILE);
                GL11.glBegin(GL11.GL_TRIANGLES);
                float[] v = part.v;
                for (int i = 0; i < part.verts; i++) {
                    int o = i * 8;
                    GL11.glNormal3f(v[o + 5], v[o + 6], v[o + 7]);
                    GL11.glTexCoord2f(v[o + 3], v[o + 4]);
                    GL11.glVertex3f(v[o], v[o + 1], v[o + 2]);
                }
                GL11.glEnd();
                GL11.glEndList();
            }
        } catch (Throwable t) {
            failed = true;
            free();
            throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
        }
    }

    void free() {
        for (int id : tex) if (id > 0) TextureUtil.deleteTexture(id);
        tex = new int[0];
        if (white > 0) TextureUtil.deleteTexture(white);
        white = 0;
        if (listBase >= 0) GL11.glDeleteLists(listBase, Math.max(1, listCount));
        listBase = -1;
    }

    private static void mult(float[] m) {
        BUF.clear();
        BUF.put(m);
        BUF.flip();
        GL11.glMultMatrix(BUF);
    }

    /**
     * Draws the model: W maps model space to the current space, bones are the bone matrices
     * (rest pose when the model has no animation running), ticks drives animated textures.
     */
    void draw(WModel m, float[][] bones, float[] W, boolean glowParts, long ticks, float alpha) {
        if (!ready()) return;
        boolean flat = FlatColor.active();
        boolean lit = GL11.glIsEnabled(GL11.GL_LIGHTING), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        mult(W);
        GL11.glEnable(GL11.GL_NORMALIZE);
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        if (flat) {
            GlStateManager.disableLighting();
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL13.GL_COMBINE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_RGB, GL11.GL_REPLACE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_RGB, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_RGB, GL11.GL_SRC_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_COMBINE_ALPHA, GL11.GL_MODULATE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE0_ALPHA, GL11.GL_TEXTURE);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND0_ALPHA, GL11.GL_SRC_ALPHA);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_SOURCE1_ALPHA, GL13.GL_PRIMARY_COLOR);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL13.GL_OPERAND1_ALPHA, GL11.GL_SRC_ALPHA);
        } else {
            GlStateManager.enableLighting();
        }
        // both sides lit the same (thin planes, and meshes whose faces were wound either way)
        GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, GL11.GL_TRUE);
        try {
            for (int pass = 0; pass < 2; pass++) {
                for (int p = 0; p < m.parts.size() && p < listCount; p++) {
                    WModel.Part part = m.parts.get(p);
                    boolean additive = part.tex >= 0 && part.tex < m.textures.size() && m.textures.get(part.tex).additive;
                    boolean second = part.translucent || additive;
                    if (second != (pass == 1)) continue;
                    boolean em = part.emissive && glowParts;
                    WModel.Tex t = part.tex >= 0 && part.tex < tex.length ? m.textures.get(part.tex) : null;
                    GlStateManager.enableTexture2D();
                    GlStateManager.bindTexture(t != null ? tex[part.tex] : white);
                    if (additive) GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                    GlStateManager.depthMask(!flat && pass == 0);
                    if (em && !flat) {
                        GlStateManager.disableLighting();
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
                    }
                    int c = part.color;
                    float a = ((c >>> 24) & 255) / 255f * alpha;
                    if (flat) FlatColor.color(1f, 1f, 1f, a);
                    else GlStateManager.color(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
                    boolean anim = t != null && t.frames > 1;
                    if (anim) {
                        GlStateManager.matrixMode(GL11.GL_TEXTURE);
                        GlStateManager.pushMatrix();
                        GlStateManager.loadIdentity();
                        GlStateManager.translate(0f, t.frameAt(ticks) / (float) t.frames, 0f);
                        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                    }
                    GlStateManager.pushMatrix();
                    mult(bones[Math.max(0, Math.min(bones.length - 1, part.bone))]);
                    GL11.glCallList(listBase + p);
                    GlStateManager.popMatrix();
                    if (anim) {
                        GlStateManager.matrixMode(GL11.GL_TEXTURE);
                        GlStateManager.popMatrix();
                        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                    }
                    if (em && !flat) {
                        if (lit) GlStateManager.enableLighting();
                        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
                    }
                    if (additive) GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                }
            }
        } finally {
            GL11.glLightModeli(GL11.GL_LIGHT_MODEL_TWO_SIDE, GL11.GL_FALSE);
            if (flat) GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(true);
            GL11.glDisable(GL11.GL_NORMALIZE);
            if (lit) GlStateManager.enableLighting();
            else GlStateManager.disableLighting();
            if (cull) GlStateManager.enableCull();
            GlStateManager.color(1f, 1f, 1f, 1f);
            FlatColor.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
        }
    }
}
