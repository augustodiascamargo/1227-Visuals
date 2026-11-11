package net.m1227.module;

import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Setting;
import net.m1227.vfx.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.opengl.GL11;

/**
 * Original 3D sword models for the first-person view, built from code
 * (extruded faceted blades, guards and grips) with simple directional
 * shading and optional emissive parts. They replace only how YOUR sword
 * looks in first person: reach, timing and hitboxes are untouched, other
 * players still see the normal sword, and in third person Minecraft's
 * sword is drawn (1.8.9 has no hook to swap held-item models there).
 */
public final class WeaponModelsModule extends Module {
    public static WeaponModelsModule INSTANCE;
    public static final String[] MODELS = {"Katana", "Crystal Blade", "Energy Saber", "Rune Greatsword", "Twin Fang", "Obsidian Edge", "Storm Blade", "Butterfly Knife", "Cleaver", "Dragon Fang", "Holy Claymore", "Void Rapier"};

    private final Setting.Mode model;
    private final Setting.Col primary, secondary, glow;
    private final Setting.Bool emissive, matchWeaponFx;
    private final Setting.Num length, size, posX, posY, posZ;
    private final Setting.Bool lightning, flipOnEquip;
    private final Setting.Key inspectKey;
    private long animStart = -1L;
    private int animKind; // 0 none, 1 open (equip), 2 close+open (inspect)
    private net.minecraft.item.ItemStack lastHeld;
    private boolean inspectWasDown;

    // item-space frame: blade runs along the item's diagonal
    private static final float OX = 0.04f, OY = 0.04f, OZ = 0.5f;
    private static final float AX = 0.70710678f, AY = 0.70710678f; // along
    private static final float NX = -0.70710678f, NY = 0.70710678f; // across (in item plane)
    private float len = 1.30f, thick = 1f;
    private final float[] p = new float[3], q = new float[3], r = new float[3], s4 = new float[3];

    public WeaponModelsModule() {
        super("Weapon Models", "Original 3D sword models in first person.", Category.WEAPONS, false);
        INSTANCE = this;
        model = add(new Setting.Mode("Model", "Katana", MODELS));
        primary = add(new Setting.Col("Blade color", 0xD8E2F0));
        secondary = add(new Setting.Col("Hilt color", 0x2A2238));
        glow = add(new Setting.Col("Glow color", 0x8B5CFF));
        matchWeaponFx = add(new Setting.Bool("Glow follows Weapon Effects color", true));
        emissive = add(new Setting.Bool("Glowing parts", true));
        length = add(new Setting.Num("Length", 1.0, 0.8, 1.3, 0.02, "x"));
        size = add(new Setting.Num("Thickness", 1.35, 0.6, 2.5, 0.05, "x"));
        posX = add(new Setting.Num("Offset X", 0.0, -0.5, 0.5, 0.01, ""));
        posY = add(new Setting.Num("Offset Y", 0.0, -0.5, 0.5, 0.01, ""));
        posZ = add(new Setting.Num("Offset Z", 0.0, -0.5, 0.5, 0.01, ""));
        lightning = add(new Setting.Bool("Lightning arcs", false));
        flipOnEquip = add(new Setting.Bool("Butterfly: flip open when drawn", true)).showIf(() -> model.is("Butterfly Knife"));
        inspectKey = add(new Setting.Key("Butterfly: inspect (flip) key", org.lwjgl.input.Keyboard.KEY_V));
    }

    public boolean lightning() {
        return isEnabled() && lightning.on();
    }

    public int glowColor() {
        return glowRgb();
    }

    public boolean replaces(ItemStack st) {
        return isEnabled() && st != null && st.getItem() instanceof ItemSword;
    }

    private int glowRgb() {
        WeaponEffectsModule w = WeaponEffectsModule.INSTANCE;
        if (matchWeaponFx.on() && w != null && w.isEnabled()) return w.col0();
        return glow.rgb();
    }

    /** Draws the model with the item's own first-person transform (call where the item would be drawn). */
    public void renderFirstPerson(Minecraft mc, ItemStack stack) {
        IBakedModel m = mc.getRenderItem().getItemModelMesher().getItemModel(stack);
        GlStateManager.pushMatrix();
        GlStateManager.translate(posX.f(), posY.f(), posZ.f());
        thick = size.f();
        if (m != null) {
            if (!m.isGui3d()) GlStateManager.scale(2.0F, 2.0F, 2.0F);
            m.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.FIRST_PERSON);
        }
        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        GlStateManager.translate(-0.5F, -0.5F, -0.5F);
        len = 1.30f * length.f();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        String md = model.get();
        int b = primary.rgb(), h = secondary.rgb(), g = glowRgb();
        if (md.equals("Butterfly Knife")) {
            butterfly(mc, stack, b, h, g);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            return;
        }
        if (md.equals("Crystal Blade")) crystal(b, h, g);
        else if (md.equals("Energy Saber")) saber(h, g);
        else if (md.equals("Rune Greatsword")) rune(b, h, g);
        else if (md.equals("Twin Fang")) twin(b, h, g);
        else if (md.equals("Obsidian Edge")) obsidian(h, g);
        else if (md.equals("Storm Blade")) storm(b, h, g);
        else if (md.equals("Cleaver")) cleaver(b, h, g);
        else if (md.equals("Dragon Fang")) fang(b, h, g);
        else if (md.equals("Holy Claymore")) claymore(b, h, g);
        else if (md.equals("Void Rapier")) rapier(b, h, g);
        else katana(b, h, g);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------- models

    private void katana(int blade, int hilt, int g) {
        grip(0.0f, 0.27f, 0.026f, hilt, true);
        box(0.0f, 0.025f, 0.034f, 0.03f, 0xB89B5E, 0f);               // kashira
        disc(0.27f, 0.295f, 0.075f, 0.022f, 0x2B2B2B);                  // tsuba
        box(0.295f, 0.33f, 0.03f, 0.018f, 0xC9A65A, 0f);              // habaki
        int n = 14;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.33f + 0.67f * u;
            hw[i] = u < 0.9f ? 0.042f - 0.01f * u : 0.042f * (1f - u) / 0.1f * 0.9f;
            th[i] = 0.011f * (1f - 0.5f * u);
            cv[i] = 0.06f * u * u;                                     // sori (curve)
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 0.25f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.75f, 0.5f);
    }

    private void crystal(int blade, int hilt, int g) {
        grip(0.0f, 0.25f, 0.028f, hilt, false);
        octa(0.02f, 0.05f, g);                                          // pommel gem
        box(0.25f, 0.29f, 0.12f, 0.035f, ParticleSystem.mix(hilt, g, 0.3f), 0f);
        octa(0.27f, 0.045f, g);
        float[] ss = {0.29f, 0.36f, 0.55f, 0.78f, 0.92f, 1.0f};
        float[] hw = {0.05f, 0.095f, 0.085f, 0.07f, 0.04f, 0f};
        float[] th = {0.03f, 0.05f, 0.045f, 0.035f, 0.02f, 0f};
        float[] cv = new float[ss.length];
        int c = ParticleSystem.mix(blade, g, 0.45f);
        extrude(ss, hw, th, cv, c, ParticleSystem.mix(c, 0xFFFFFF, 0.55f), 0.82f, false);
        if (emissive.on()) emissiveShell(ss, hw, th, cv, g, 0.22f, 1.25f);
    }

    private void saber(int hilt, int g) {
        grip(0.0f, 0.30f, 0.032f, 0x8C93A3, false);
        box(0.08f, 0.2f, 0.036f, 0.036f, hilt, 0f);
        box(0.29f, 0.32f, 0.045f, 0.045f, 0x5A606E, 0f);
        float[] ss = new float[10], hw = new float[10], th = new float[10], cv = new float[10];
        for (int i = 0; i < 10; i++) {
            float u = i / 9f;
            ss[i] = 0.32f + 0.68f * u;
            float tip = u > 0.92f ? (float) Math.sqrt(Math.max(0f, 1f - (u - 0.92f) / 0.08f)) : 1f;
            hw[i] = 0.022f * tip;
            th[i] = 0.022f * tip;
        }
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        extrude(ss, hw, th, cv, 0xFFFFFF, 0xFFFFFF, 1f, true);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        float pulse = 0.85f + 0.15f * (float) Math.sin(System.nanoTime() / 1e9 * 9.0);
        for (int k = 1; k <= 3; k++) {
            float[] hw2 = new float[10], th2 = new float[10];
            for (int i = 0; i < 10; i++) {
                hw2[i] = hw[i] * (1f + k * 0.9f) + (i == 9 ? 0f : 0.004f * k);
                th2[i] = th[i] * (1f + k * 0.9f);
            }
            extrude(ss, hw2, th2, cv, g, g, 0.32f / k * pulse, true);
        }
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
    }

    private void rune(int blade, int hilt, int g) {
        grip(0.0f, 0.26f, 0.03f, hilt, true);
        octa(0.015f, 0.045f, g);
        box(0.26f, 0.30f, 0.15f, 0.04f, 0x3A3346, 0.04f);
        float[] ss = {0.30f, 0.40f, 0.80f, 0.90f, 1.0f};
        float[] hw = {0.085f, 0.09f, 0.075f, 0.06f, 0f};
        float[] th = {0.022f, 0.022f, 0.018f, 0.014f, 0f};
        float[] cv = new float[ss.length];
        int dark = ParticleSystem.mix(blade, 0x1A1A22, 0.75f);
        extrude(ss, hw, th, cv, dark, ParticleSystem.mix(dark, 0xFFFFFF, 0.35f), 1f, false);
        if (emissive.on()) {
            // glowing fuller with pulsing rune segments
            float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            double t = System.nanoTime() / 1e9;
            for (int i = 0; i < 9; i++) {
                float s0 = 0.34f + i * 0.06f, s1 = s0 + 0.035f;
                float a = 0.55f + 0.45f * (float) Math.sin(t * 3.0 - i * 0.8);
                for (int side = -1; side <= 1; side += 2) {
                    quad(s0, -0.012f, side * 0.0235f, s1, 0.012f, side * 0.0235f, g, a);
                }
            }
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        }
    }

    private void twin(int blade, int hilt, int g) {
        grip(0.0f, 0.26f, 0.026f, hilt, true);
        box(0.26f, 0.30f, 0.1f, 0.03f, ParticleSystem.mix(hilt, blade, 0.4f), 0.06f);
        // two narrow blades that split and rejoin at the tip
        for (int side = -1; side <= 1; side += 2) {
            float[] ss = {0.30f, 0.45f, 0.75f, 0.92f, 1.0f};
            float[] hw = {0.03f, 0.028f, 0.026f, 0.02f, 0f};
            float[] th = {0.014f, 0.013f, 0.012f, 0.01f, 0f};
            float[] cv = {side * 0.045f, side * 0.06f, side * 0.055f, side * 0.025f, 0f};
            extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        }
        if (emissive.on()) {
            float[] ss = {0.32f, 0.92f};
            float[] hw = {0.012f, 0.004f};
            float[] th = {0.004f, 0.002f};
            float[] cv = {0f, 0f};
            float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            extrude(ss, hw, th, cv, g, 0xFFFFFF, 0.9f, true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        }
    }

    private void obsidian(int hilt, int g) {
        grip(0.0f, 0.27f, 0.03f, hilt, true);
        box(0.27f, 0.31f, 0.13f, 0.035f, 0x1B1622, 0.05f);
        float[] ss = {0.31f, 0.42f, 0.55f, 0.7f, 0.85f, 1.0f};
        float[] hw = {0.07f, 0.09f, 0.065f, 0.085f, 0.055f, 0f};   // jagged silhouette
        float[] th = {0.03f, 0.03f, 0.026f, 0.026f, 0.02f, 0f};
        float[] cv = {0f, 0.01f, -0.005f, 0.01f, 0f, 0f};
        extrude(ss, hw, th, cv, 0x120E18, ParticleSystem.mix(0x120E18, g, 0.35f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.9f, 1f);
    }

    /**
     * The butterfly knife from your .blend file: two handles swing around their pins, driven by
     * the model's own animation (closed <-> open). Flips open when drawn and on the inspect key.
     */
    private void butterfly(Minecraft mc, net.minecraft.item.ItemStack stack, int blade, int hilt, int glow) {
        net.m1227.vfx.MeshAssets.Rigid k = net.m1227.vfx.MeshAssets.knife();
        if (k == null) {
            katana(blade, hilt, glow);
            return;
        }
        long now = System.currentTimeMillis();
        if (lastHeld == null || stack.getItem() != lastHeld.getItem() || mc.thePlayer.inventory.currentItem != lastSlot) {
            lastSlot = mc.thePlayer.inventory.currentItem;
            if (flipOnEquip.on()) {
                animStart = now;
                animKind = 1;
            }
        }
        lastHeld = stack;
        int key = inspectKey.code();
        boolean down = key > 0 && mc.currentScreen == null && org.lwjgl.input.Keyboard.isKeyDown(key);
        if (down && !inspectWasDown && (animKind == 0 || now - animStart > 1100L)) {
            animStart = now;
            animKind = 2;
        }
        inspectWasDown = down;
        // frame 0 = open (in hand), frame 39 = closed
        float ft = 0f, t = (now - animStart) / 1000f;
        if (animKind == 1) {
            ft = t < 0.7f ? 39f * (1f - t / 0.7f) : 0f;
            if (t >= 0.7f) animKind = 0;
        } else if (animKind == 2) {
            if (t < 0.5f) ft = 39f * (t / 0.5f);
            else if (t < 1.05f) ft = 39f * (1f - (t - 0.5f) / 0.55f);
            else animKind = 0;
        }
        final float kk = len / 2.84f;
        final float th = thick;
        net.m1227.vfx.MeshAssets.Mapper map = new net.m1227.vfx.MeshAssets.Mapper() {
            @Override
            public void map(float x, float y, float z, float[] o) {
                float a = (1.46f - x) * kk;      // along the sword diagonal, handles at the bottom
                float n = (z + 0.18f) * kk;      // swing plane = the item plane
                o[0] = OX + AX * a + NX * n;
                o[1] = OY + AY * a + NY * n;
                o[2] = OZ + y * kk * 1.6f * th;
            }
        };
        int[][] colors = {{ParticleSystem.mix(blade, 0xFFFFFF, 0.15f), glow}, {hilt}, {ParticleSystem.mix(hilt, 0xFFFFFF, 0.12f)}, {glow}};
        boolean[] em = {false, false, false, emissive.on()};
        k.draw(ft, map, colors, em, 1f);
    }

    private int lastSlot = -1;

    /** Wide, squared-off chopping blade with a spine. */
    private void cleaver(int blade, int hilt, int g) {
        grip(0.0f, 0.3f, 0.03f, hilt, true);
        box(0.3f, 0.33f, 0.07f, 0.03f, 0x3A3F4A, 0f);
        float[] ss = {0.33f, 0.4f, 0.85f, 0.97f, 1.0f};
        float[] hw = {0.08f, 0.12f, 0.12f, 0.11f, 0.06f};
        float[] th = {0.02f, 0.018f, 0.016f, 0.014f, 0.01f};
        float[] cv = {0.03f, 0.05f, 0.05f, 0.045f, 0.03f};
        extrude(ss, hw, th, cv, ParticleSystem.mix(blade, 0x5A606E, 0.35f), ParticleSystem.mix(blade, 0xFFFFFF, 0.5f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.7f, 0.5f);
    }

    /** Curved, serrated fang with a bone-like grip. */
    private void fang(int blade, int hilt, int g) {
        grip(0.0f, 0.28f, 0.03f, ParticleSystem.mix(hilt, 0xD8C9A8, 0.5f), true);
        octa(0.29f, 0.05f, g);
        int n = 16;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.31f + 0.69f * u;
            float teeth = (i % 2 == 0) ? 1f : 0.7f; // serrated edge
            hw[i] = (0.075f * (1f - u * 0.85f)) * teeth;
            th[i] = 0.022f * (1f - u * 0.7f);
            cv[i] = -0.09f * u * u;
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, ParticleSystem.mix(blade, 0xE8E2D0, 0.4f), ParticleSystem.mix(g, 0xFFFFFF, 0.4f), 1f, false);
        if (emissive.on()) emissiveShell(ss, hw, th, cv, g, 0.18f, 1.3f);
    }

    /** Long two-handed blade, wide cross-guard with a glowing gem. */
    private void claymore(int blade, int hilt, int g) {
        grip(0.0f, 0.3f, 0.026f, hilt, true);
        octa(0.02f, 0.045f, 0xFFE9A8);
        box(0.3f, 0.335f, 0.2f, 0.03f, 0xC9A65A, 0.03f);
        octa(0.318f, 0.04f, g);
        float[] ss = {0.335f, 0.4f, 0.9f, 1.0f};
        float[] hw = {0.06f, 0.065f, 0.05f, 0f};
        float[] th = {0.02f, 0.02f, 0.016f, 0f};
        float[] cv = new float[ss.length];
        extrude(ss, hw, th, cv, ParticleSystem.mix(blade, 0xFFFFFF, 0.25f), 0xFFF6DA, 1f, false);
        if (emissive.on()) emissiveShell(ss, hw, th, cv, 0xFFE08A, 0.14f, 1.25f);
    }

    /** Needle-thin blade with a round cup guard and a void-glow core. */
    private void rapier(int blade, int hilt, int g) {
        grip(0.0f, 0.24f, 0.024f, hilt, true);
        disc(0.24f, 0.27f, 0.09f, 0.03f, 0x1B1622);
        float[] ss = {0.27f, 0.5f, 0.95f, 1.0f};
        float[] hw = {0.022f, 0.018f, 0.01f, 0f};
        float[] th = {0.014f, 0.012f, 0.008f, 0f};
        float[] cv = new float[ss.length];
        extrude(ss, hw, th, cv, 0x1A1424, ParticleSystem.mix(0x1A1424, g, 0.5f), 1f, false);
        if (emissive.on()) emissiveShell(ss, hw, th, cv, g, 0.3f, 1.8f);
    }

    /** Steel blade with a bright energy channel; pairs with the lightning arcs. */
    private void storm(int blade, int hilt, int g) {
        grip(0.0f, 0.27f, 0.028f, hilt, true);
        octa(0.02f, 0.04f, g);
        box(0.27f, 0.30f, 0.12f, 0.03f, 0x4A5060, -0.05f);
        float[] ss = {0.30f, 0.45f, 0.75f, 0.9f, 1.0f};
        float[] hw = {0.06f, 0.07f, 0.06f, 0.04f, 0f};
        float[] th = {0.018f, 0.018f, 0.015f, 0.012f, 0f};
        float[] cv = new float[ss.length];
        extrude(ss, hw, th, cv, ParticleSystem.mix(blade, 0x3A4255, 0.5f), ParticleSystem.mix(blade, 0xFFFFFF, 0.4f), 1f, false);
        if (emissive.on()) {
            float[] cs = {0.31f, 0.92f};
            float[] chw = {0.014f, 0.006f};
            float[] cth = {0.0195f, 0.013f};
            float[] ccv = {0f, 0f};
            float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            float flick = 0.7f + 0.3f * (float) Math.random();
            extrude(cs, chw, cth, ccv, g, 0xFFFFFF, flick, true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        }
    }

    // ------------------------------------------------------- geometry kit

    private void pt(float s, float n, float z, float curve, float[] out) {
        float a = s * len;
        n *= thick;
        z *= thick;
        out[0] = OX + AX * a + NX * (n + curve);
        out[1] = OY + AY * a + NY * (n + curve);
        out[2] = OZ + z;
    }

    /** Diamond cross-section blade through stations (s, half width, half thickness, curve). */
    private void extrude(float[] ss, float[] hw, float[] th, float[] cv, int c0, int c1, float alpha, boolean flat) {
        float[][] sec = {{1f, 0f}, {0f, 1f}, {-1f, 0f}, {0f, -1f}};
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i + 1 < ss.length; i++) {
            for (int k = 0; k < 4; k++) {
                float[] a = sec[k], b = sec[(k + 1) % 4];
                pt(ss[i], a[0] * hw[i], a[1] * th[i], cv[i], p);
                pt(ss[i], b[0] * hw[i], b[1] * th[i], cv[i], q);
                pt(ss[i + 1], b[0] * hw[i + 1], b[1] * th[i + 1], cv[i + 1], r);
                pt(ss[i + 1], a[0] * hw[i + 1], a[1] * th[i + 1], cv[i + 1], s4);
                float u = (ss[i] - ss[0]) / Math.max(0.001f, ss[ss.length - 1] - ss[0]);
                int col = ParticleSystem.mix(c0, c1, u * 0.6f + (k % 2 == 0 ? 0.25f : 0f));
                face(p, q, r, s4, col, alpha, flat);
            }
        }
        GL11.glEnd();
    }

    /** Additive translucent shell around a blade. */
    private void emissiveShell(float[] ss, float[] hw, float[] th, float[] cv, int g, float alpha, float grow) {
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        float[] hw2 = new float[hw.length], th2 = new float[th.length];
        for (int i = 0; i < hw.length; i++) {
            hw2[i] = hw[i] * grow + 0.006f;
            th2[i] = th[i] * grow + 0.006f;
        }
        hw2[hw.length - 1] = 0f;
        th2[th.length - 1] = 0f;
        extrude(ss, hw2, th2, cv, g, 0xFFFFFF, alpha, true);
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
    }

    /** Thin glowing strip along both cutting edges. */
    private void edgeLine(float[] ss, float[] hw, float[] cv, int g, float alpha, float sides) {
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GL11.glBegin(GL11.GL_QUADS);
        for (int side = 1; side >= (sides >= 1f ? -1 : 1); side -= 2) {
            for (int i = 0; i + 1 < ss.length; i++) {
                float e0 = side * hw[i], e1 = side * hw[i + 1];
                pt(ss[i], e0, -0.004f, cv[i], p);
                pt(ss[i], e0, 0.004f, cv[i], q);
                pt(ss[i + 1], e1, 0.004f, cv[i + 1], r);
                pt(ss[i + 1], e1, -0.004f, cv[i + 1], s4);
                face(p, q, r, s4, g, alpha, true);
                pt(ss[i], e0 * 1.25f, 0f, cv[i], p);
                pt(ss[i], e0 * 0.8f, 0f, cv[i], q);
                pt(ss[i + 1], e1 * 0.8f, 0f, cv[i + 1], r);
                pt(ss[i + 1], e1 * 1.25f, 0f, cv[i + 1], s4);
                face(p, q, r, s4, g, alpha * 0.5f, true);
            }
        }
        GL11.glEnd();
        GlStateManager.depthMask(true);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
    }

    /** Wrapped grip: box with alternating darker bands. */
    private void grip(float s0, float s1, float hw, int c, boolean wrapped) {
        if (!wrapped) {
            box(s0, s1, hw, hw, c, 0f);
            return;
        }
        int n = 7;
        for (int i = 0; i < n; i++) {
            float a = s0 + (s1 - s0) * i / n, b = s0 + (s1 - s0) * (i + 1) / n;
            box(a, b, hw * (i % 2 == 0 ? 1f : 0.88f), hw * (i % 2 == 0 ? 1f : 0.88f),
                    i % 2 == 0 ? c : ParticleSystem.mix(c, 0x000000, 0.35f), 0f);
        }
    }

    private void disc(float s0, float s1, float rad, float th, int c) {
        box(s0, s1, rad, th * 0.5f + 0.02f, c, 0f);
        box(s0, s1, rad * 0.7f, rad * 0.7f, ParticleSystem.mix(c, 0xFFFFFF, 0.08f), 0f);
    }

    /** Rectangular box from s0 to s1; "flare" widens the far end (guards). */
    private void box(float s0, float s1, float hw, float th, int c, float flare) {
        float[][] sec = {{1f, 1f}, {-1f, 1f}, {-1f, -1f}, {1f, -1f}};
        GL11.glBegin(GL11.GL_QUADS);
        for (int k = 0; k < 4; k++) {
            float[] a = sec[k], b = sec[(k + 1) % 4];
            pt(s0, a[0] * hw, a[1] * th, 0f, p);
            pt(s0, b[0] * hw, b[1] * th, 0f, q);
            pt(s1, b[0] * (hw + flare), b[1] * th, 0f, r);
            pt(s1, a[0] * (hw + flare), a[1] * th, 0f, s4);
            face(p, q, r, s4, c, 1f, false);
        }
        for (int end = 0; end < 2; end++) {
            float s = end == 0 ? s0 : s1, w = end == 0 ? hw : hw + flare;
            pt(s, w, th, 0f, p);
            pt(s, -w, th, 0f, q);
            pt(s, -w, -th, 0f, r);
            pt(s, w, -th, 0f, s4);
            face(p, q, r, s4, c, 1f, false);
        }
        GL11.glEnd();
    }

    /** Small glowing gem (octahedron) centred at station s. */
    private void octa(float s, float rad, int c) {
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        float d = rad / len;
        float[] ss = {s - d, s, s + d};
        float[] hw = {0f, rad, 0f};
        float[] th = {0f, rad, 0f};
        float[] cv = {0f, 0f, 0f};
        extrude(ss, hw, th, cv, c, 0xFFFFFF, 1f, false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
    }

    /** Flat glowing quad lying on the blade surface (z = zf). */
    private void quad(float s0, float n0, float zf, float s1, float n1, float unused, int c, float a) {
        GL11.glBegin(GL11.GL_QUADS);
        pt(s0, n0, zf, 0f, p);
        pt(s0, n1, zf, 0f, q);
        pt(s1, n1, zf, 0f, r);
        pt(s1, n0, zf, 0f, s4);
        face(p, q, r, s4, c, a, true);
        GL11.glEnd();
    }

    private static final float LX = 0.35f, LY = 0.75f, LZ = 0.56f;

    /** Emits one shaded quad (inside glBegin(GL_QUADS)). flat = unlit (emissive). */
    private static void face(float[] a, float[] b, float[] c, float[] d, int col, float alpha, boolean flat) {
        float shade = 1f;
        if (!flat) {
            float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
            float vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 1e-6f) {
                float dot = Math.abs((nx * LX + ny * LY + nz * LZ) / l); // two-sided
                shade = 0.42f + 0.58f * dot;
            }
        }
        float r = ((col >> 16) & 255) / 255f * shade, g = ((col >> 8) & 255) / 255f * shade, bl = (col & 255) / 255f * shade;
        GL11.glColor4f(r, g, bl, alpha);
        GL11.glVertex3f(a[0], a[1], a[2]);
        GL11.glVertex3f(b[0], b[1], b[2]);
        GL11.glVertex3f(c[0], c[1], c[2]);
        GL11.glVertex3f(d[0], d[1], d[2]);
    }
}
