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

    /** Called right after the held item is drawn, with the hand transform still applied. */
    void afterItem(Minecraft mc, EntityPlayerSP p, ItemStack stack) {
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        ItemGlowModule g = ItemGlowModule.INSTANCE;
        boolean weapon = w != null && w.firstPersonActive(p);
        boolean glow = g != null && g.isEnabled() && g.applies(stack);
        weaponThisFrame = weapon;
        if (!weapon && !glow) return;
        IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
        if (model == null) return;
        GlStateManager.pushMatrix();
        // same chain RenderItem applies: 2x for flat items, the model's first-person transform,
        // then 0.5x and a -0.5 offset into the model's 0..1 space
        if (!model.isGui3d()) GlStateManager.scale(2.0F, 2.0F, 2.0F);
        model.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.FIRST_PERSON);
        if (glow) glowCopies(mc, stack, model, g);
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        if (weapon) {
            float b0 = w.bladeStart(), b1 = w.bladeEnd();
            if (w.bladeGlow()) bladeAura(w, b0, b1);
            record(w, b0, b1, p.getSwingProgress(0f));
        }
        GlStateManager.popMatrix();
    }

    // ----------------------------------------------------------- glow

    private void glowCopies(Minecraft mc, ItemStack stack, IBakedModel model, ItemGlowModule g) {
        int layers = g.layers();
        float pulse = g.pulse(time);
        int c = g.color(time);
        float r = ((c >> 16) & 255) / 255f, gr = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
        GlStateManager.disableLighting();
        for (int k = 1; k <= layers; k++) {
            float s = 1f + g.spread() * k / layers;
            float a = g.intensity() * pulse * (1.15f - k / (float) (layers + 1));
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            GL14.glBlendColor(r * a, gr * a, b * a, 1f);
            GlStateManager.blendFunc(0x8001 /* GL_CONSTANT_COLOR */, GL11.GL_ONE);
            GlStateManager.pushMatrix();
            GlStateManager.scale(s, s, s);
            mc.getRenderItem().renderItem(stack, model);
            GlStateManager.popMatrix();
        }
        GL14.glBlendColor(0f, 0f, 0f, 0f);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
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
        float wd = 0.12f + 0.04f * (float) Math.sin(time * 3f);
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
            float al = pass == 0 ? a : Math.min(1f, a * 1.6f);
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
        if (w == null || (count < 2 && pn == 0)) return;

        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();

        if (count >= 2 && w.swingTrail()) trail(w);

        if (pn > 0) {
            ParticleSystem.INSTANCE.bindAtlas();
            GlStateManager.enableTexture2D();
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < pn; i++) {
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
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /** The swoosh: a strip between recent blade positions, bright at the tip, fading with age. */
    private void trail(WeaponEffectsModule w) {
        float maxAge = w.trailLength();
        int c0 = w.col0(), c1 = w.col1();
        float a0 = w.alpha() * w.boost();
        GlStateManager.disableTexture2D();
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int k = 0; k < count; k++) {
            int o = ((head - k + TN) % TN) * 7;
            float ageK = time - tr[o + 6];
            if (ageK > maxAge) break;
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
        for (int k = 0; k < count; k++) {
            int o = ((head - k + TN) % TN) * 7;
            float ageK = time - tr[o + 6];
            if (ageK > maxAge) break;
            float f = 1f - ageK / maxAge;
            GL11.glColor4f(1f, 1f, 1f, Math.min(1f, a0 * f * f));
            GL11.glVertex3f(tr[o + 3], tr[o + 4], tr[o + 5]);
        }
        GL11.glEnd();
        GL11.glLineWidth(1f);
        GlStateManager.enableTexture2D();
    }
}
