package net.m1227.render;

import java.nio.FloatBuffer;
import net.m1227.module.ItemGlowModule;
import net.m1227.module.PerformanceModule;
import net.m1227.module.WeaponEffectsModule;
import net.m1227.vfx.ParticleSystem;
import net.m1227.vfx.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Effects attached to the item in your hand (first person).
 *
 * They are drawn inside the hand pass, in the item's own model space, so
 * they sit exactly on the blade whatever the swing, block or equip
 * animation is doing. The blade's base and tip are also converted to
 * camera space every frame: that history is the swing trail, and the
 * blade's particles live in camera space too (they stay with your view
 * like the hand does). Item glow draws extra tinted, slightly larger
 * copies of the item with additive "constant colour" blending.
 */
public final class HandFx {
    public static final HandFx INSTANCE = new HandFx();

    private static final int TN = 48, PN = 320;
    private final FloatBuffer mv = BufferUtils.createFloatBuffer(16);
    private final float[] uv = new float[4];

    // swing trail history: base xyz, tip xyz, time
    private final float[] tr = new float[TN * 7];
    private int head = -1, count;

    // camera-space particles
    private final float[] px = new float[PN], py = new float[PN], pz = new float[PN];
    private final float[] vx = new float[PN], vy = new float[PN], vz = new float[PN];
    private final float[] age = new float[PN], life = new float[PN], size = new float[PN], rot = new float[PN], rv = new float[PN];
    private final float[] grav = new float[PN];
    private final int[] col = new int[PN], spr = new int[PN];
    private int pn;
    private long lastNanos;
    private float time, acc, lastSwing;
    private boolean weaponThisFrame;

    private HandFx() {}

    public static boolean wantsHand() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        ItemGlowModule g = ItemGlowModule.INSTANCE;
        ItemStack held = mc.thePlayer.getHeldItem();
        return (w != null && w.firstPersonActive(mc.thePlayer)) || (g != null && g.isEnabled() && held != null && g.applies(held));
    }

    /**
     * True when an effect is drawn ON the held item (glow, outline, blade aura): it must use the
     * very same transform as the item, so the 1227 hand pass draws the item too. If Minecraft drew
     * the item in its own pass, anything that changes that pass (another mod, OptiFine, a
     * different FOV) would leave the glow as a second "sword of light" beside the real one.
     */
    public static boolean needsItemAlignment() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return false;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return false;
        ItemGlowModule g = ItemGlowModule.INSTANCE;
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        return (g != null && g.isEnabled() && g.applies(held)) || (w != null && w.firstPersonActive(mc.thePlayer) && w.bladeGlow());
    }

    /**
     * Called right before the held item is drawn, with the hand transform applied. Glow, outline
     * and blade aura go here: they write no depth, so the real item drawn afterwards covers them
     * and they only show around its edges, never on top of the blade.
     */
    void beforeItem(Minecraft mc, EntityPlayerSP p, ItemStack stack, boolean full) {
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        ItemGlowModule g = ItemGlowModule.INSTANCE;
        boolean weapon = w != null && w.firstPersonActive(p) && w.bladeGlow();
        net.m1227.module.WeaponModelsModule wm = net.m1227.module.WeaponModelsModule.INSTANCE;
        boolean custom = wm != null && wm.replaces(stack);
        boolean glow = g != null && g.isEnabled() && g.applies(stack);
        if (custom) {
            // 1.22.1: with a Weapon Models 3D sword the copies are that 3D model (flat colour), in its
            // own pose and animation, so the glow follows the real silhouette instead of the vanilla one
            if (glow) modelCopies(mc, stack, wm, g);
            return;
        }
        // 1.28.0: effects drawn ON the item need the item drawn by this same pass. In the
        // effects-only pass (hand Compatibility mode) Minecraft draws the item afterwards with its
        // own pose, so a copy drawn here would float beside it: the "second sword of light".
        if (!full) return;
        if (glow) itemCopies(mc, p, stack, g);
        if (!weapon) return;
        IBakedModel model = heldModel(mc, p, stack);
        if (model == null) return;
        GlStateManager.pushMatrix();
        // the chain RenderItem applies to every held item: ItemRenderer.renderItem doubles block
        // models, RenderItem.preTransform doubles flat ones, so the item is always 2x; then the
        // first-person transform (through Forge), 0.5x and the -0.5 offset into 0..1 model space.
        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        cameraTransform(model);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        bladeAura(w, w.bladeStart(), w.bladeEnd());
        GlStateManager.popMatrix();
    }

    /** Called right after the held item is drawn, with the hand transform still applied. */
    void afterItem(Minecraft mc, EntityPlayerSP p, ItemStack stack, boolean full) {
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        boolean weapon = w != null && w.firstPersonActive(p);
        net.m1227.module.WeaponModelsModule wm = net.m1227.module.WeaponModelsModule.INSTANCE;
        weaponThisFrame = weapon;
        boolean arcs = (weapon && w.lightningArcs()) || (wm != null && wm.lightning() && wm.replaces(stack));
        if (!full) arcs = false; // the item is drawn later by Minecraft, with its own pose
        if (!weapon && !arcs) return;
        IBakedModel model = heldModel(mc, p, stack);
        if (model == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.scale(2.0F, 2.0F, 2.0F); // ItemRenderer (blocks) or RenderItem.preTransform (flat items)
        model = cameraTransform(model);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        if (arcs) {
            float b0 = weapon ? w.bladeStart() : 0.3f, b1 = weapon ? w.bladeEnd() : 0.97f;
            mv.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
            float[] b = tf(bx(b0), bx(b0), 0.5f);
            arcB[0] = b[0]; arcB[1] = b[1]; arcB[2] = b[2];
            float[] t = tf(bx(b1), bx(b1), 0.5f);
            arcT[0] = t[0]; arcT[1] = t[1]; arcT[2] = t[2];
            arcColor = weapon ? w.col0() : wm.glowColor();
            arcsNow = true;
        }
        if (weapon) record(w, w.bladeStart(), w.bladeEnd(), p.getSwingProgress(0f));
        GlStateManager.popMatrix();
    }

    /**
     * The model the game draws for this held stack: the mesher's model, or the pulled-bow /
     * cast-rod / Forge per-item model, chosen the way RenderItem.renderItemModelForEntity does.
     */
    static IBakedModel heldModel(Minecraft mc, EntityPlayerSP p, ItemStack stack) {
        net.minecraft.client.renderer.ItemModelMesher mesher = mc.getRenderItem().getItemModelMesher();
        IBakedModel model = mesher.getItemModel(stack);
        try {
            net.minecraft.item.Item item = stack.getItem();
            net.minecraft.client.resources.model.ModelResourceLocation loc = null;
            if (item == net.minecraft.init.Items.fishing_rod && p.fishEntity != null) {
                loc = new net.minecraft.client.resources.model.ModelResourceLocation("fishing_rod_cast", "inventory");
            } else if (item == net.minecraft.init.Items.bow && p.getItemInUse() != null) {
                int i = stack.getMaxItemUseDuration() - p.getItemInUseCount();
                if (i >= 18) loc = new net.minecraft.client.resources.model.ModelResourceLocation("bow_pulling_2", "inventory");
                else if (i > 13) loc = new net.minecraft.client.resources.model.ModelResourceLocation("bow_pulling_1", "inventory");
                else if (i > 0) loc = new net.minecraft.client.resources.model.ModelResourceLocation("bow_pulling_0", "inventory");
            } else {
                loc = item.getModel(stack, p, p.getItemInUseCount());
            }
            if (loc != null) {
                IBakedModel m = mesher.getModelManager().getModel(loc);
                if (m != null) model = m;
            }
        } catch (Throwable t) {
            // a mod's item threw: keep the mesher's model
        }
        return model;
    }

    /** The first-person transform exactly as RenderItem applies it (through Forge); returns the model to draw. */
    static IBakedModel cameraTransform(IBakedModel model) {
        IBakedModel m = net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(model, ItemCameraTransforms.TransformType.FIRST_PERSON);
        return m != null ? m : model;
    }

    // ----------------------------------------------------------- glow

    /**
     * Glow and outline around a Weapon Models 3D sword: the model drawn as a flat silhouette,
     * shifted by a few screen pixels in 8 (or 16) directions, before the real model, with no depth
     * writes. The real model then covers the middle and only the rim stays visible.
     */
    private void modelCopies(Minecraft mc, ItemStack stack, net.m1227.module.WeaponModelsModule wm, ItemGlowModule g) {
        float pulse = g.pulse(time);
        int c = g.color(time);
        ScreenOffset.begin();
        try {
            if (g.glowOn()) {
                int layers = g.layers();
                float reach = (1.5f + g.spread() * 40f) * pxScale(mc);
                for (int k = layers; k >= 1; k--) {
                    float rad = reach * k / layers;
                    float a = Math.min(1f, g.intensity() * pulse * (1.1f - k / (float) (layers + 1)) * 0.35f);
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * 2 * (i + (k & 1) * 0.5) / 8;
                        ScreenOffset.shift((float) Math.cos(ang) * rad, (float) Math.sin(ang) * rad, 0f);
                        wm.renderSilhouette(mc, stack, c, a);
                    }
                }
            }
            if (g.outlineOn()) {
                float a = Math.min(1f, 0.4f + g.intensity()) * pulse;
                float w = g.outlineWidth() / 0.03f * pxScale(mc);
                int n = w > 2.5f ? 16 : 8;
                for (int i = 0; i < n; i++) {
                    double ang = Math.PI * 2 * i / n;
                    ScreenOffset.shift((float) Math.cos(ang) * w, (float) Math.sin(ang) * w, 0f);
                    wm.renderSilhouette(mc, stack, c, a);
                }
            }
        } finally {
            ScreenOffset.end();
            GlStateManager.depthMask(true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        }
    }

    /** Screen pixels per "1x": 2 px at 1080p, more on bigger screens, never under 1. */
    private static float pxScale(Minecraft mc) {
        return Math.max(1f, mc.displayHeight / 540f);
    }

    /**
     * Glow, aura and outline around a normal (non-3D) held item.
     *
     * Every copy is drawn by the very call that draws the real item next,
     * ItemRenderer.renderItem(player, stack, FIRST_PERSON), so it gets exactly the same model,
     * size and pose, whatever another mod or OptiFine does to that call. Up to 1.27.1 the copies
     * rebuilt that transform by hand, and any difference showed as a second sword.
     *
     * Each copy is a flat-colour silhouette (Silhouette: colour from the texture environment,
     * alpha from the item texture, so the pixel cut-outs stay) moved a few SCREEN pixels by
     * ScreenOffset, with no depth writes. The real item drawn afterwards covers the middle: only
     * the rim shows, the same thickness all round.
     *
     * RenderItem sets blending to (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) and the alpha test to 0.1 on
     * every call through GlStateManager, which skips GL calls whose value it already holds. So the
     * cache is set to those exact values and the real GL state is then changed directly
     * (additive blending, a lower alpha test): RenderItem's calls are then skipped and the copies
     * keep the glow's state. Both are put back afterwards, in GL and in the cache.
     */
    private void itemCopies(Minecraft mc, EntityPlayerSP p, ItemStack stack, ItemGlowModule g) {
        // without the enchantment shine: its pass turns depth writes on and resets blending
        ItemStack plain = stack;
        if (stack.hasEffect() && stack.hasTagCompound()) {
            plain = stack.copy();
            plain.getTagCompound().removeTag("ench");
        }
        net.minecraft.client.renderer.ItemRenderer ir = mc.getItemRenderer();
        float pulse = g.pulse(time);
        float px = pxScale(mc);
        mc.entityRenderer.disableLightmap(); // full-bright colour, also in the dark
        ScreenOffset.begin();
        try {
            if (g.glowOn()) {
                boolean aura = g.auraOn();
                int layers = g.layers();
                float reach = (1.5f + g.spread() * 40f) * px; // default spread 0.12 ~ 6 px at 1080p
                int n = aura ? 10 : 8;
                for (int k = layers; k >= 1; k--) {
                    float f = k / (float) layers; // 1 = outermost layer
                    float rad = reach * f;
                    float a = Math.min(1f, g.intensity() * pulse * (1.15f - f * 0.75f) * (aura ? 0.42f : 0.36f));
                    int c = g.color(time + f * 0.9f);
                    for (int i = 0; i < n; i++) {
                        double ang = Math.PI * 2 * (i + (k & 1) * 0.5) / n;
                        float dx = (float) Math.cos(ang) * rad, dy = (float) Math.sin(ang) * rad;
                        if (aura) {
                            // energy: each tongue flickers on its own rhythm and leans upwards
                            float ph = time * 5.0f + i * 2.399f + k * 1.3f;
                            float flick = 0.55f + 0.45f * (float) Math.sin(ph);
                            dx = dx * (0.75f + 0.25f * flick) + (float) Math.sin(ph * 0.7f) * rad * 0.18f;
                            dy = dy * (0.75f + 0.25f * flick) + rad * (0.35f + 0.45f * flick);
                            copy(mc, ir, p, plain, true, c, a * (0.6f + 0.4f * flick), dx, dy);
                        } else {
                            copy(mc, ir, p, plain, true, c, a, dx, dy);
                        }
                    }
                }
            }
            if (g.outlineOn()) {
                int c = g.color(time);
                float a = Math.min(1f, 0.45f + g.intensity()) * pulse;
                float w = g.outlineWidth() / 0.03f * px; // "1.0x" = 2 px at 1080p
                int n = w > 2.5f ? 16 : 8;
                for (int i = 0; i < n; i++) {
                    double ang = Math.PI * 2 * i / n;
                    copy(mc, ir, p, plain, false, c, a, (float) Math.cos(ang) * w, (float) Math.sin(ang) * w);
                }
                if (w > 1.5f) { // fill the inside of thick lines
                    for (int i = 0; i < 8; i++) {
                        double ang = Math.PI * 2 * (i + 0.5) / 8;
                        copy(mc, ir, p, plain, false, c, a, (float) Math.cos(ang) * w * 0.55f, (float) Math.sin(ang) * w * 0.55f);
                    }
                }
            }
        } finally {
            ScreenOffset.end();
            Silhouette.end();
            // back to what RenderItem and the rest of the hand pass expect, in GL and in the cache
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
            GL14.glBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.depthMask(true);
            GlStateManager.color(1f, 1f, 1f, 1f);
            mc.entityRenderer.enableLightmap();
        }
    }

    /** One silhouette copy of the held item, shifted (dx, dy) screen pixels. */
    private static void copy(Minecraft mc, net.minecraft.client.renderer.ItemRenderer ir, EntityPlayerSP p, ItemStack stack,
                             boolean additive, int rgb, float alpha, float dx, float dy) {
        if (alpha <= 0.004f) return;
        ScreenOffset.shift(dx, dy, 0f);
        // the cache holds RenderItem's values, so its own calls change nothing (see itemCopies)
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        if (additive) GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 0, 1);
        else GL14.glBlendFuncSeparate(770, 771, 0, 1);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.003f);
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        Silhouette.begin(rgb, Math.min(1f, alpha));
        ir.renderItem(p, stack, ItemCameraTransforms.TransformType.FIRST_PERSON);
    }

    // ------------------------------------------------- blade (model space)

    private static float bx(float u) {
        return 0.06f + 0.9f * u;
    }

    /** Soft glowing capsule along the blade: one quad in the item plane, one across it. */
    private void bladeAura(WeaponEffectsModule w, float b0, float b1) {
        float x0 = bx(b0), x1 = bx(b1);
        float pulse = 0.75f + 0.25f * (float) Math.sin(time * 4f);
        float a = w.bladeGlowAlpha() * pulse;
        float wd = 0.14f + 0.03f * (float) Math.sin(time * 3f);
        int c0 = w.col0(), c1 = w.col1();
        ParticleSystem.INSTANCE.bindAtlas();
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        ParticleSystem.uv(Sprite.GLOW, uv);
        // blade runs along the item's diagonal; n = perpendicular inside the item plane
        float dx = x1 - x0, dy = x1 - x0, l = (float) Math.sqrt(dx * dx + dy * dy);
        float nx = -dy / l, ny = dx / l;
        float ex = dx / l * 0.08f, ey = dy / l * 0.08f; // extend a little past both ends
        GL11.glBegin(GL11.GL_QUADS);
        for (int pass = 0; pass < 2; pass++) {
            float w0 = pass == 0 ? wd : wd * 0.35f;
            float al = pass == 0 ? a : Math.min(1f, a * 0.8f);
            int cb = pass == 0 ? c0 : 0xFFFFFF, ct = pass == 0 ? c1 : ParticleSystem.mix(c1, 0xFFFFFF, 0.5f);
            // in-plane quad
            vtx(cb, al, uv[0], uv[3], x0 - ex + nx * w0, x0 - ey + ny * w0, 0.5f);
            vtx(cb, al, uv[2], uv[3], x0 - ex - nx * w0, x0 - ey - ny * w0, 0.5f);
            vtx(ct, al, uv[2], uv[1], x1 + ex - nx * w0, x1 + ey - ny * w0, 0.5f);
            vtx(ct, al, uv[0], uv[1], x1 + ex + nx * w0, x1 + ey + ny * w0, 0.5f);
            // across the plane (visible when the blade is edge-on)
            vtx(cb, al * 0.7f, uv[0], uv[3], x0 - ex, x0 - ey, 0.5f + w0);
            vtx(cb, al * 0.7f, uv[2], uv[3], x0 - ex, x0 - ey, 0.5f - w0);
            vtx(ct, al * 0.7f, uv[2], uv[1], x1 + ex, x1 + ey, 0.5f - w0);
            vtx(ct, al * 0.7f, uv[0], uv[1], x1 + ex, x1 + ey, 0.5f + w0);
        }
        GL11.glEnd();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    private static void vtx(int c, float a, float u, float v, float x, float y, float z) {
        GL11.glColor4f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, a);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3f(x, y, z);
    }

    /** Converts the blade ends to camera space; feeds the trail and emits blade particles. */
    private void record(WeaponEffectsModule w, float b0, float b1, float swing) {
        mv.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, mv);
        float x0 = bx(b0), x1 = bx(b1);
        float[] base = tf(x0, x0, 0.5f), tip = tf(x1, x1, 0.5f);
        head = (head + 1) % TN;
        int o = head * 7;
        tr[o] = base[0];
        tr[o + 1] = base[1];
        tr[o + 2] = base[2];
        tr[o + 3] = tip[0];
        tr[o + 4] = tip[1];
        tr[o + 5] = tip[2];
        tr[o + 6] = time;
        if (count < TN) count++;

        float rate = w.amount() * 7f * w.boost() * PerformanceModule.qualityScale();
        boolean newSwing = swing > 0f && (lastSwing == 0f || swing < lastSwing);
        lastSwing = swing;
        int n = 0;
        acc += Math.min(0.1f, dtFrame) * rate;
        n += (int) acc;
        acc -= (int) acc;
        if (newSwing) n += Math.round(8 * w.boost());
        n = Math.min(n, 24);
        for (int i = 0; i < n; i++) {
            float u = (float) Math.random();
            float x = base[0] + (tip[0] - base[0]) * u, y = base[1] + (tip[1] - base[1]) * u, z = base[2] + (tip[2] - base[2]) * u;
            emit(w, x, y, z);
        }
    }

    private float dtFrame = 0.016f;

    private float[] scratch0 = new float[3], scratch1 = new float[3];
    private boolean flip;

    private float[] tf(float x, float y, float z) {
        float[] o = (flip = !flip) ? scratch0 : scratch1;
        o[0] = mv.get(0) * x + mv.get(4) * y + mv.get(8) * z + mv.get(12);
        o[1] = mv.get(1) * x + mv.get(5) * y + mv.get(9) * z + mv.get(13);
        o[2] = mv.get(2) * x + mv.get(6) * y + mv.get(10) * z + mv.get(14);
        return o;
    }

    private void emit(WeaponEffectsModule w, float x, float y, float z) {
        if (pn >= PN) return;
        int i = pn++;
        int t = w.typeIndex();
        float r = (float) Math.random();
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        vx[i] = (float) (Math.random() - 0.5) * 0.06f;
        vy[i] = (float) (Math.random() - 0.5) * 0.06f;
        vz[i] = (float) (Math.random() - 0.5) * 0.06f;
        age[i] = 0f;
        life[i] = w.life() * (0.6f + 0.6f * r);
        size[i] = w.handSize() * (0.6f + 0.8f * (float) Math.random());
        rot[i] = (float) (Math.random() * 6.28);
        rv[i] = (float) (Math.random() - 0.5) * 6f;
        grav[i] = 0f;
        col[i] = ParticleSystem.mix(w.col0(), w.col1(), (float) Math.random());
        spr[i] = Sprite.GLOW;
        switch (t) {
            case 2: // sparks fall
                grav[i] = 0.9f;
                vy[i] += 0.12f;
                size[i] *= 0.6f;
                break;
            case 3: // flame rises
                spr[i] = Sprite.FLAME;
                vy[i] += 0.14f;
                size[i] *= 1.6f;
                break;
            case 4: // frost drifts down
                spr[i] = r < 0.4f ? Sprite.SHARD : Sprite.STAR;
                vy[i] -= 0.05f;
                break;
            case 6: // void wisps
                spr[i] = Sprite.SMOKE;
                size[i] *= 2f;
                vy[i] += 0.03f;
                break;
            case 7: // starlight
                spr[i] = Sprite.STAR;
                break;
            case 8: // petals
                spr[i] = Sprite.PETAL;
                vy[i] -= 0.06f;
                break;
            case 9: // runes
                spr[i] = r < 0.3f ? Sprite.RUNE : Sprite.GLOW;
                break;
            default: // aura, trail, lightning: soft motes rising
                vy[i] += 0.06f;
        }
    }

    // --------------------------------------------- camera space (trail + particles)

    void renderCameraSpace(Minecraft mc) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0E9f);
        lastNanos = now;
        dtFrame = dt;
        time += dt;
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;

        // particles
        for (int i = 0; i < pn; ) {
            age[i] += dt;
            if (age[i] >= life[i]) {
                int l = --pn;
                px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l];
                vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
                age[i] = age[l]; life[i] = life[l]; size[i] = size[l];
                rot[i] = rot[l]; rv[i] = rv[l]; grav[i] = grav[l];
                col[i] = col[l]; spr[i] = spr[l];
                continue;
            }
            vy[i] -= grav[i] * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            rot[i] += rv[i] * dt;
            i++;
        }
        if (!weaponThisFrame) {
            count = 0;
        }
        weaponThisFrame = false;
        boolean arcsFrame = arcsNow;
        arcsNow = false;
        if (w == null || (count < 2 && pn == 0 && !arcsFrame)) return;

        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        GlStateManager.disableFog();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();

        if (count >= 2 && w.swingTrail()) trail(w);
        if (arcsFrame) lightning(dt);

        if (pn > 0) {
            ParticleSystem.INSTANCE.bindAtlas();
            GlStateManager.enableTexture2D();
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < pn; i++) {
                if (pz[i] > -0.1f) continue; // at/behind the near plane: would cover the screen
                float t = age[i] / life[i];
                float a = w.alpha() * Math.min(1f, t * 6f) * (1f - t) * (spr[i] == Sprite.SMOKE ? 0.35f : 1f);
                float s = size[i] * (1f - 0.5f * t);
                ParticleSystem.uv(spr[i], uv);
                float c = (float) Math.cos(rot[i]) * s, sn = (float) Math.sin(rot[i]) * s;
                int k = col[i];
                GL11.glColor4f(((k >> 16) & 255) / 255f, ((k >> 8) & 255) / 255f, (k & 255) / 255f, a);
                GL11.glTexCoord2f(uv[0], uv[3]);
                GL11.glVertex3f(px[i] - c + sn, py[i] - sn - c, pz[i]);
                GL11.glTexCoord2f(uv[2], uv[3]);
                GL11.glVertex3f(px[i] + c + sn, py[i] + sn - c, pz[i]);
                GL11.glTexCoord2f(uv[2], uv[1]);
                GL11.glVertex3f(px[i] + c - sn, py[i] + sn + c, pz[i]);
                GL11.glTexCoord2f(uv[0], uv[1]);
                GL11.glVertex3f(px[i] - c - sn, py[i] - sn + c, pz[i]);
            }
            GL11.glEnd();
        }

        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        if (fog) GlStateManager.enableFog();
        GlStateManager.color(1f, 1f, 1f, 1f);
        org.lwjgl.opengl.GL11.glColor4f(1f, 1f, 1f, 1f); // raw glColor calls bypass GlStateManager's cache
    }

    // ------------------------------------------------------ lightning arcs

    private static final int NA = 4, NP = 12;
    private final float[] arcB = new float[3], arcT = new float[3];
    private final float[][] arcT01 = new float[NA][NP], arcO1 = new float[NA][NP], arcO2 = new float[NA][NP];
    private final float[] arcLife = new float[NA];
    private int arcColor = 0x9AD0FF;
    private boolean arcsNow;
    private float arcTimer;

    /**
     * Jagged electric arcs that jump between points of the blade, re-rolled about 20 times a
     * second, with the odd branch leaping off the edge. Stored as blade-relative coordinates so
     * they stay glued to the sword while it swings.
     */
    private void lightning(float dt) {
        if (arcB[2] > -0.1f || arcT[2] > -0.1f) return; // behind the near plane
        arcTimer -= dt;
        if (arcTimer <= 0f) {
            arcTimer = 0.04f + (float) Math.random() * 0.03f;
            for (int a = 0; a < NA; a++) {
                if (Math.random() < 0.35) {
                    arcLife[a] = 0f; // a gap now and then makes it flicker like real sparks
                    continue;
                }
                arcLife[a] = 1f;
                float t0 = (float) Math.random() * 0.75f, t1 = Math.min(1f, t0 + 0.12f + (float) Math.random() * 0.3f);
                boolean branch = Math.random() < 0.3;
                float dir = Math.random() < 0.5 ? -1f : 1f;
                for (int k = 0; k < NP; k++) {
                    float u = k / (float) (NP - 1);
                    float env = (float) Math.sin(Math.PI * u);
                    arcT01[a][k] = t0 + (t1 - t0) * u;
                    arcO1[a][k] = ((float) Math.random() - 0.5f) * 0.05f * env + (branch ? dir * 0.12f * u * u : 0f);
                    arcO2[a][k] = ((float) Math.random() - 0.5f) * 0.05f * env;
                }
            }
        }
        float dx = arcT[0] - arcB[0], dy = arcT[1] - arcB[1], dz = arcT[2] - arcB[2];
        // two directions perpendicular to the blade
        float ax = dy * 1f - dz * 0f, ay = dz * 0f - dx * 1f, az = 0f; // d x (0,0,1)
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-5f) return;
        ax /= al; ay /= al; az /= al;
        float bxv = dy * az - dz * ay, byv = dz * ax - dx * az, bzv = dx * ay - dy * ax;
        float bl = (float) Math.sqrt(bxv * bxv + byv * byv + bzv * bzv);
        if (bl < 1e-5f) return;
        bxv /= bl; byv /= bl; bzv /= bl;
        float r = ((arcColor >> 16) & 255) / 255f, g = ((arcColor >> 8) & 255) / 255f, b = (arcColor & 255) / 255f;
        GlStateManager.disableTexture2D();
        for (int pass = 0; pass < 2; pass++) {
            GL11.glLineWidth(pass == 0 ? 4.5f : 1.6f);
            for (int a = 0; a < NA; a++) {
                if (arcLife[a] <= 0f) continue;
                GL11.glBegin(GL11.GL_LINE_STRIP);
                if (pass == 0) GL11.glColor4f(r, g, b, 0.35f);
                else GL11.glColor4f(0.85f + r * 0.15f, 0.85f + g * 0.15f, 0.9f + b * 0.1f, 0.95f);
                for (int k = 0; k < NP; k++) {
                    float t = arcT01[a][k], o1 = arcO1[a][k], o2 = arcO2[a][k];
                    GL11.glVertex3f(arcB[0] + dx * t + ax * o1 + bxv * o2, arcB[1] + dy * t + ay * o1 + byv * o2,
                            arcB[2] + dz * t + az * o1 + bzv * o2);
                }
                GL11.glEnd();
            }
        }
        GL11.glLineWidth(1f);
        GlStateManager.enableTexture2D();
    }

    /**
     * How many trail points can be drawn: stops at the first point that is
     * too old, at or behind the camera's near plane, or that jumped far from
     * the previous one (item switch, teleport of the hand). Without this a
     * single bad point turns the strip into a huge screen-covering quad.
     */
    private int usableTrail(float maxAge) {
        int n = 0;
        float lx = 0, ly = 0, lz = 0;
        for (int k = 0; k < count; k++) {
            int o = ((head - k + TN) % TN) * 7;
            if (time - tr[o + 6] > maxAge) break;
            if (tr[o + 2] > -0.1f || tr[o + 5] > -0.1f) break;
            if (k > 0) {
                float dx = tr[o + 3] - lx, dy = tr[o + 4] - ly, dz = tr[o + 5] - lz;
                if (dx * dx + dy * dy + dz * dz > 0.6f * 0.6f) break;
            }
            lx = tr[o + 3];
            ly = tr[o + 4];
            lz = tr[o + 5];
            n++;
        }
        return n;
    }

    /** The swoosh: a strip between recent blade positions, bright at the tip, fading with age. */
    private void trail(WeaponEffectsModule w) {
        float maxAge = w.trailLength();
        int c0 = w.col0(), c1 = w.col1();
        float a0 = w.alpha() * w.boost();
        GlStateManager.disableTexture2D();
        int usable = usableTrail(maxAge);
        if (usable < 2) {
            GlStateManager.enableTexture2D();
            return;
        }
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int k = 0; k < usable; k++) {
            int o = ((head - k + TN) % TN) * 7;
            float ageK = time - tr[o + 6];
            float f = 1f - ageK / maxAge;
            f = f * f;
            int c = ParticleSystem.mix(c1, c0, 1f - f);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            GL11.glColor4f(r, g, b, Math.min(1f, a0 * f * 0.15f));
            GL11.glVertex3f(tr[o], tr[o + 1], tr[o + 2]);
            GL11.glColor4f(Math.min(1f, r + 0.25f * f), Math.min(1f, g + 0.25f * f), Math.min(1f, b + 0.25f * f), Math.min(1f, a0 * f * 0.85f));
            GL11.glVertex3f(tr[o + 3], tr[o + 4], tr[o + 5]);
        }
        GL11.glEnd();
        GL11.glLineWidth(2f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int k = 0; k < usable; k++) {
            int o = ((head - k + TN) % TN) * 7;
            float ageK = time - tr[o + 6];
            float f = 1f - ageK / maxAge;
            GL11.glColor4f(1f, 1f, 1f, Math.min(1f, a0 * f * f));
            GL11.glVertex3f(tr[o + 3], tr[o + 4], tr[o + 5]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.enableTexture2D();
    }
}
