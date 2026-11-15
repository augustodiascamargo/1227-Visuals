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
 * 3D sword models: 18 originals built from code (extruded faceted blades, guards and grips)
 * plus your own models from config/1227/weapons (.bbmodel, .gltf/.glb, .fbx, .obj), with their
 * textures and their own animations. They change only how swords LOOK on your screen, in first
 * person and (optionally) in third person: reach, timing and hitboxes are untouched, and other
 * players see whatever their own game shows. One model for every sword, or one per sword type.
 */
public final class WeaponModelsModule extends Module {
    public static WeaponModelsModule INSTANCE;
    public static final String[] MODELS = {"Katana", "Crystal Blade", "Energy Saber", "Rune Greatsword", "Twin Fang", "Obsidian Edge", "Storm Blade", "Butterfly Knife", "Cleaver", "Dragon Fang", "Holy Claymore", "Void Rapier",
            "Karambit", "Bayonet", "Flip Knife", "Huntsman", "Skeleton Knife", "Push Dagger"};
    /** Blade finishes: name, blade, tip (fade), hilt, glow. "Custom" uses the colour settings. */
    public static final String[] FINISHES = {"Custom", "Emerald", "Pearl", "Sapphire", "Crimson", "Rose Steel", "Ember Fade",
            "Ocean Fade", "Midnight", "Gold", "Toxic"};
    private static final int[][] FINISH_COLORS = {
            {0, 0, 0, 0},
            {0x0E9F6E, 0x2DFFB0, 0x111317, 0x2DFF9A},
            {0xE8ECF2, 0xFFFFFF, 0xC9CED8, 0xFFFFFF},
            {0x1B2A8F, 0x6A5CFF, 0x0E0F18, 0x6A5CFF},
            {0x8F0E1E, 0xFF4058, 0x1A0E10, 0xFF3048},
            {0xB8A0A8, 0xFF6F9A, 0x2E2A30, 0xFF6F9A},
            {0xFFB000, 0xFF2D7A, 0x2A1A10, 0xFF6A00},
            {0x14D2FF, 0x7A3CFF, 0x10141E, 0x2DA8FF},
            {0x1A1D26, 0x4A5268, 0x0B0C10, 0x8B5CFF},
            {0xD4A63A, 0xFFF1A8, 0x3A2A10, 0xFFD36B},
            {0x5CFF2D, 0xE8FF3D, 0x10180A, 0x8BFF2D}};

    private final Setting.Mode model;
    private final Setting.Col primary, secondary, glow;
    private final Setting.Bool emissive, matchWeaponFx;
    private final Setting.Num length, size, posX, posY, posZ, rotX, rotY, rotZ, scaleAll, bladeWidth;
    private final Setting.Mode holdMode, finish;
    /** Tip colour of the current finish (blade fades into it), or -1 for the model's own highlight. */
    private int tipColor = -1;
    private long inspectStart = -1L;
    private final Setting.Bool showHands;
    private final Setting.Num handAngle, secondAngle, handSize;
    /** How much "Blade width" applies to the current model (wide blades like the cleaver stay as built). */
    private float widthBoost = 1f;
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
        super("Weapon Models", "18 original 3D swords and knives in first person, with finishes.", Category.WEAPONS, false);
        INSTANCE = this;
        model = add(new Setting.Mode("Model", "Katana", MODELS));
        finish = add(new Setting.Mode("Finish", "Custom", FINISHES));
        primary = add(new Setting.Col("Blade color", 0xD8E2F0));
        secondary = add(new Setting.Col("Hilt color", 0x2A2238));
        glow = add(new Setting.Col("Glow color", 0x8B5CFF));
        matchWeaponFx = add(new Setting.Bool("Glow follows Weapon Effects color", true));
        emissive = add(new Setting.Bool("Glowing parts", true));
        length = add(new Setting.Num("Length", 1.0, 0.8, 1.3, 0.02, "x"));
        size = add(new Setting.Num("Thickness", 1.3, 0.6, 2.5, 0.05, "x"));
        posX = add(new Setting.Num("Offset X", 0.0, -0.5, 0.5, 0.01, ""));
        posY = add(new Setting.Num("Offset Y", 0.0, -0.5, 0.5, 0.01, ""));
        posZ = add(new Setting.Num("Offset Z", 0.0, -0.5, 0.5, 0.01, ""));
        rotX = add(new Setting.Num("Rotation X", 0, -180, 180, 1, "\u00b0"));
        rotY = add(new Setting.Num("Rotation Y", 0, -180, 180, 1, "\u00b0"));
        rotZ = add(new Setting.Num("Rotation Z", 0, -180, 180, 1, "\u00b0"));
        scaleAll = add(new Setting.Num("Model size", 1.15, 0.6, 1.8, 0.05, "x"));
        bladeWidth = add(new Setting.Num("Blade width", 1.6, 0.8, 3.0, 0.05, "x"));
        holdMode = add(new Setting.Mode("Hold & swing", "Per model", "Per model", "Classic"));
        showHands = add(new Setting.Bool("Show hands on the grip", true));
        handAngle = add(new Setting.Num("Hand angle", 35, -90, 90, 1, "\u00b0")).showIf(() -> showHands.on());
        secondAngle = add(new Setting.Num("Second hand angle (two-handed)", -70, -150, 150, 1, "\u00b0")).showIf(() -> showHands.on());
        handSize = add(new Setting.Num("Hand size", 1.0, 0.6, 1.6, 0.05, "x")).showIf(() -> showHands.on());
        lightning = add(new Setting.Bool("Lightning arcs", false));
        flipOnEquip = add(new Setting.Bool("Butterfly: flip open when drawn", true)).showIf(() -> model.is("Butterfly Knife"));
        inspectKey = add(new Setting.Key("Inspect key", org.lwjgl.input.Keyboard.KEY_V));
        inspectStyle = add(new Setting.Mode("Inspect animation", "Twirl", "Twirl", "Turn over", "Toss", "Show off", "Flip"));
        drawAnim = add(new Setting.Mode("Draw animation", "Spin", "Spin", "Flip", "Rise", "Quick", "Smooth", "Off"));
        altSwings = add(new Setting.Bool("Alternate swing direction", true));
        idleMotion = add(new Setting.Bool("Idle motion", true));
        sprintPose = add(new Setting.Bool("Sprint pose", true));
        animSpeed = add(new Setting.Num("Animation speed", 1.0, 0.5, 2.0, 0.05, "x"));
        swingStyle = add(new Setting.Mode("Swing style", "Per model", net.m1227.weapons.WeaponMotion.SWINGS));
        swingSpeed = add(new Setting.Num("Swing speed", 1.0, 0.6, 1.6, 0.05, "x"));
        comboSwings = add(new Setting.Bool("Combo swings (diagonal, horizontal, vertical)", false));
        critSwing = add(new Setting.Bool("Critical swing when falling", true));
        intensity = add(new Setting.Num("Animation intensity", 1.0, 0.0, 1.5, 0.05, "x"));
        handSway = add(new Setting.Num("Hand sway (camera inertia)", 1.0, 0.0, 2.0, 0.05, "x"));
        moveMotion = add(new Setting.Num("Walking & sprint motion", 1.0, 0.0, 2.0, 0.05, "x"));
        jumpMotion = add(new Setting.Num("Jump & landing motion", 1.0, 0.0, 2.0, 0.05, "x"));
        blockAnim = add(new Setting.Mode("Sword block animation", "Smooth", "Smooth", "Instant"));
        inspectLength = add(new Setting.Num("Inspect duration", 1.0, 0.5, 2.0, 0.05, "x"));
        for (final String pr : new String[]{"Classic PvP", "Smooth PvP", "Modern Combat", "Cinematic", "Minimal"}) {
            add(new Setting.Action("Motion preset: " + pr, () -> preset(pr)));
        }
        thirdPerson = add(new Setting.Bool("Show in third person", true));
        othersToo = add(new Setting.Bool("Also on other players' swords", false)).showIf(() -> thirdPerson.on());
        modelStore = add(new Setting.Text("Model file", "", 120, "")).showIf(() -> false);
        for (int i = 0; i < TIERS.length; i++) {
            tierModes[i] = add(new Setting.Mode(TIERS[i] + " model", SAME, withSame(MODELS)));
            tierStores[i] = add(new Setting.Text(TIERS[i] + " model file", "", 120, "")).showIf(() -> false);
        }
    }

    // ------------------------------------------------------------- model choice (built-in or imported)

    public static final String SAME = "Same as Model";
    public static final String[] TIERS = {"Wooden sword", "Stone sword", "Iron sword", "Golden sword", "Diamond sword"};
    private static final String[] TIER_MATERIALS = {"WOOD", "STONE", "IRON", "GOLD", "EMERALD"};
    private final Setting.Bool thirdPerson, othersToo;
    /** The Mode settings only keep values from their list, and imported names are listed only
     *  after the folder is read, so the chosen names are also kept as plain text. */
    private final Setting.Text modelStore;
    private final Setting.Mode[] tierModes = new Setting.Mode[TIERS.length];
    private final Setting.Text[] tierStores = new Setting.Text[TIERS.length];
    private boolean listReady;
    /** Model being drawn right now (null: the one for the held item). */
    private String active;
    /** Editor: model shown in the preview instead of the chosen one. */
    private String previewModel;

    public static boolean isBuiltIn(String n) {
        for (String m : MODELS) if (m.equalsIgnoreCase(n)) return true;
        return false;
    }

    private static String[] withSame(String[] list) {
        String[] r = new String[list.length + 1];
        r[0] = SAME;
        System.arraycopy(list, 0, r, 1, list.length);
        return r;
    }

    /** Every model name: built-in first, then the imported ones. */
    public String[] allModels() {
        java.util.List<String> l = new java.util.ArrayList<>(java.util.Arrays.asList(MODELS));
        for (net.m1227.weapons.WeaponLibrary.Entry e : net.m1227.weapons.WeaponLibrary.entries()) l.add(e.name);
        return l.toArray(new String[0]);
    }

    /** Called (main thread) when the weapons folder has been read: lists the imported models. */
    public void refreshModelList() {
        String[] all = allModels();
        String want = !modelStore.get().isEmpty() ? modelStore.get() : model.get();
        model.setOptions(all);
        if (java.util.Arrays.asList(all).contains(want)) model.setSilently(want);
        String[] tiers = withSame(all);
        for (int i = 0; i < TIERS.length; i++) {
            String w = !tierStores[i].get().isEmpty() ? tierStores[i].get() : tierModes[i].get();
            tierModes[i].setOptions(tiers);
            if (java.util.Arrays.asList(tiers).contains(w)) tierModes[i].setSilently(w);
        }
        listReady = true;
    }

    private void syncChoices() {
        if (!listReady) return;
        if (!model.get().equals(modelStore.get())) modelStore.set(model.get());
        for (int i = 0; i < TIERS.length; i++) if (!tierModes[i].get().equals(tierStores[i].get())) tierStores[i].set(tierModes[i].get());
    }

    public Setting.Mode modelSetting() {
        return model;
    }

    public Setting.Mode tierSetting(int i) {
        return tierModes[i];
    }

    /** The model drawn for this item (per sword type when set, else the main choice). */
    public String modelFor(ItemStack st) {
        syncChoices();
        if (st != null && st.getItem() instanceof ItemSword) {
            String mat = ((ItemSword) st.getItem()).getToolMaterialName();
            for (int i = 0; i < TIERS.length; i++) {
                if (TIER_MATERIALS[i].equals(mat) && !tierModes[i].is(SAME)) return tierModes[i].get();
            }
        }
        return model.get();
    }

    private String cur() {
        if (active != null) return active;
        Minecraft mc = Minecraft.getMinecraft();
        return modelFor(mc.thePlayer != null ? mc.thePlayer.getHeldItem() : null);
    }

    /** The imported model behind a name, or null for built-in ones. */
    public static net.m1227.weapons.WeaponLibrary.Entry imported(String name) {
        return isBuiltIn(name) ? null : net.m1227.weapons.WeaponLibrary.byName(name);
    }

    public void setPreviewModel(String m) {
        previewModel = m;
    }

    public String previewModel() {
        return previewModel != null ? previewModel : model.get();
    }

    // ------------------------------------------------------------- imported models: placement and animation

    /** Hold point in item space: the built-in grip station (hands are drawn there). */
    private static final float GRIP_STATION = 0.17f * 1.30f;
    private static final float[] ITEM_FRAME = {AX, AY, 0f, 0f, NX, NY, 0f, 0f, 0f, 0f, 1f, 0f,
            OX + AX * GRIP_STATION, OY + AY * GRIP_STATION, OZ, 1f};
    private float lastSwingFp;

    private static float seconds() {
        return (System.nanoTime() % 1_000_000_000_000L) / 1e9f;
    }

    private static long ticks() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null ? mc.theWorld.getTotalWorldTime() : System.currentTimeMillis() / 50L;
    }

    /** Draws an imported model in item space with the given animator (null = rest pose). */
    private boolean drawImported(net.m1227.weapons.WeaponLibrary.Entry e, net.m1227.weapons.WeaponAnimator anim) {
        if (e == null || !e.ready()) return false;
        float[] W = ITEM_FRAME.clone();
        net.m1227.weapons.M4.mul(W, e.matrix, W);
        net.m1227.weapons.WModel.Pose pose = null;
        if (anim != null) {
            anim.setSpeed(animSpeed.f());
            pose = anim.pose(seconds());
        }
        e.draw(pose != null ? e.model.boneMatrices(pose) : e.restBones, W, ticks(), 1f);
        return true;
    }

    // ------------------------------------------------------------- third person

    private final java.util.Map<String, net.m1227.weapons.WeaponAnimator> idleAnims = new java.util.HashMap<>();

    /** True when this entity's held sword is drawn with a weapon model in third person. */
    public boolean thirdPersonFor(net.minecraft.entity.EntityLivingBase e, ItemStack st) {
        if (!isEnabled() || !thirdPerson.on() || st == null || !(st.getItem() instanceof ItemSword)) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (e != mc.thePlayer && !othersToo.on()) return false;
        String md = modelFor(st);
        net.m1227.weapons.WeaponLibrary.Entry im = imported(md);
        if (im != null) return im.ready() && im.profile.thirdPerson;
        return true;
    }

    /** Called by the held-item layer, already in the hand's space (as Minecraft's own item). */
    public void renderThirdPerson(net.minecraft.entity.EntityLivingBase e, ItemStack st) {
        Minecraft mc = Minecraft.getMinecraft();
        String md = modelFor(st);
        IBakedModel m = mc.getRenderItem().getItemModelMesher().getItemModel(st);
        active = md;
        GlStateManager.pushMatrix();
        try {
            if (m != null) {
                if (!m.isGui3d()) GlStateManager.scale(2.0F, 2.0F, 2.0F);
                m.getItemCameraTransforms().applyTransform(ItemCameraTransforms.TransformType.THIRD_PERSON);
            }
            GlStateManager.scale(0.5F, 0.5F, 0.5F);
            GlStateManager.translate(-0.5F, -0.5F, -0.5F);
            float gx = OX + AX * 0.2f, gy = OY + AY * 0.2f;
            GlStateManager.translate(gx, gy, OZ);
            float sa = scaleAll.f();
            GlStateManager.scale(sa, sa, sa);
            GlStateManager.translate(-gx, -gy, -OZ);
            net.m1227.weapons.WeaponLibrary.Entry im = imported(md);
            if (im != null) {
                net.m1227.weapons.WeaponAnimator a = e == mc.thePlayer ? im.anim : idleAnim(im);
                drawImported(im, a);
            } else {
                drawBuiltIn(md, null, false);
            }
        } finally {
            GlStateManager.popMatrix();
            active = null;
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.color(1f, 1f, 1f, 1f);
            net.m1227.render.FlatColor.color(1f, 1f, 1f, 1f);
        }
    }

    private net.m1227.weapons.WeaponAnimator idleAnim(net.m1227.weapons.WeaponLibrary.Entry e) {
        net.m1227.weapons.WeaponAnimator a = idleAnims.get(e.name);
        if (a == null) {
            a = new net.m1227.weapons.WeaponAnimator();
            idleAnims.put(e.name, a);
        }
        a.bind(e.model, e.profile);
        return a;
    }

    /** Built-in model in item space (no hands); used for third person. */
    private void drawBuiltIn(String md, ItemStack stack, boolean fp) {
        thick = size.f();
        len = 1.30f * length.f() * (knife() ? 0.8f : 1f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        int b = primary.rgb(), h = secondary.rgb(), g = glowRgb();
        tipColor = -1;
        int fi = java.util.Arrays.asList(FINISHES).indexOf(finish.get());
        if (fi > 0) {
            int[] fc = FINISH_COLORS[fi];
            b = fc[0];
            tipColor = fc[1];
            h = fc[2];
            if (!matchWeaponFx.on()) g = fc[3];
        }
        widthBoost = md.equals("Cleaver") ? 0f : (md.equals("Rune Greatsword") || md.equals("Holy Claymore") || md.equals("Obsidian Edge")) ? 0.4f : 1f;
        if (md.equals("Butterfly Knife")) {
            net.m1227.vfx.MeshAssets.Rigid k = net.m1227.vfx.MeshAssets.knife();
            if (k != null) butterflyMesh(k, 0f, b, h, g);
            else katana(b, h, g);
        } else {
            models(md, b, h, g);
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
    }

    private final Setting.Mode inspectStyle, drawAnim;
    private final Setting.Bool altSwings, idleMotion, sprintPose;
    private final Setting.Num animSpeed;
    private final Setting.Mode swingStyle, blockAnim;
    private final Setting.Num swingSpeed, intensity, handSway, moveMotion, jumpMotion, inspectLength;
    private final Setting.Bool comboSwings, critSwing;
    private long inspectCancel = -1L;
    /** Animator of the imported model being drawn in first person (null for built-in models). */
    private net.m1227.weapons.WeaponAnimator modelAnim;
    /** Draw (equip) animation progress, 0 just drawn .. 1 done. */
    private float drawT = 1f;
    private long drawStart = -1L, lastRenderNanos;
    private int drawSlot = -1;
    private int swingCount;

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
        active = modelFor(stack);
        try {
            renderFirstPerson0(mc, stack);
        } finally {
            active = null;
        }
    }

    private void renderFirstPerson0(Minecraft mc, ItemStack stack) {
        net.m1227.weapons.WeaponLibrary.tick();
        net.m1227.weapons.WeaponLibrary.Entry im = imported(cur());
        net.m1227.weapons.WeaponAnimator anim = im != null && im.ready() ? im.anim : null;
        long ds = drawStart;
        updateDraw(mc);
        float now = seconds();
        if (anim != null) {
            if (drawStart != ds && anim.has(net.m1227.weapons.WeaponAnimator.DRAW)) anim.trigger(net.m1227.weapons.WeaponAnimator.DRAW, now);
            float sp = mc.thePlayer != null ? mc.thePlayer.getSwingProgress(1f) : 0f;
            if (sp > 0f && (lastSwingFp == 0f || sp < lastSwingFp - 0.2f)) anim.trigger(net.m1227.weapons.WeaponAnimator.SWING, now);
            lastSwingFp = sp;
        }
        modelAnim = anim;
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
        // user rotation / size, around the grip (lower part of the diagonal)
        float gx = OX + AX * 0.2f, gy = OY + AY * 0.2f;
        GlStateManager.translate(gx, gy, OZ);
        if (rotX.f() != 0f) GlStateManager.rotate(rotX.f(), 1f, 0f, 0f);
        if (rotY.f() != 0f) GlStateManager.rotate(rotY.f(), 0f, 1f, 0f);
        if (rotZ.f() != 0f) GlStateManager.rotate(rotZ.f(), 0f, 0f, 1f);
        float sa = scaleAll.f();
        GlStateManager.scale(sa, sa, sa);
        // the draw / inspect animation turns only the weapon: the arms keep holding the grip
        GlStateManager.pushMatrix();
        if (!cur().equals("Butterfly Knife")) {
            // the animations move in the normal hand frame: the Rotation settings only turn the
            // weapon at rest (undo them, animate, redo them), so a turned model still twirls,
            // tosses and draws straight instead of around tilted axes
            float rx = rotX.f(), ry = rotY.f(), rz = rotZ.f();
            GlStateManager.scale(1f / sa, 1f / sa, 1f / sa);
            if (rz != 0f) GlStateManager.rotate(-rz, 0f, 0f, 1f);
            if (ry != 0f) GlStateManager.rotate(-ry, 0f, 1f, 0f);
            if (rx != 0f) GlStateManager.rotate(-rx, 1f, 0f, 0f);
            if (anim == null || !anim.has(net.m1227.weapons.WeaponAnimator.DRAW)) drawSpin();
            inspect(mc);
            if (rx != 0f) GlStateManager.rotate(rx, 1f, 0f, 0f);
            if (ry != 0f) GlStateManager.rotate(ry, 0f, 1f, 0f);
            if (rz != 0f) GlStateManager.rotate(rz, 0f, 0f, 1f);
            GlStateManager.scale(sa, sa, sa);
        }
        GlStateManager.translate(-gx, -gy, -OZ);
        len = im != null ? 1.30f : 1.30f * length.f() * (knife() ? 0.8f : 1f);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        // other hand effects can leave depth writes off; solid blades need them or they look see-through
        GlStateManager.enableDepth();
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        String md = cur();
        int b = primary.rgb(), h = secondary.rgb(), g = glowRgb();
        tipColor = -1;
        int fi = java.util.Arrays.asList(FINISHES).indexOf(finish.get());
        if (fi > 0) {
            int[] fc = FINISH_COLORS[fi];
            b = fc[0];
            tipColor = fc[1];
            h = fc[2];
            if (!matchWeaponFx.on()) g = fc[3];
        }
        widthBoost = md.equals("Cleaver") ? 0f : (md.equals("Rune Greatsword") || md.equals("Holy Claymore") || md.equals("Obsidian Edge")) ? 0.4f : 1f;
        if (im != null && drawImported(im, anim)) {
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.popMatrix();
            GlStateManager.translate(-gx, -gy, -OZ);
            hands(mc);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            net.m1227.render.FlatColor.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            return;
        }
        if (md.equals("Butterfly Knife")) {
            butterfly(mc, stack, b, h, g);
            GlStateManager.popMatrix();
            GlStateManager.translate(-gx, -gy, -OZ);
            hands(mc);
            GlStateManager.shadeModel(GL11.GL_FLAT);
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.color(1f, 1f, 1f, 1f);
            net.m1227.render.FlatColor.color(1f, 1f, 1f, 1f);
            GlStateManager.popMatrix();
            return;
        }
        models(md, b, h, g);
        GlStateManager.popMatrix();
        GlStateManager.translate(-gx, -gy, -OZ);
        hands(mc);
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        net.m1227.render.FlatColor.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /**
     * The held 3D model as a flat-colour silhouette, in the same place and pose as the real one
     * (for Item Glow copies). Writes no depth, draws no hands. The caller sets the blending.
     */
    public void renderSilhouette(Minecraft mc, ItemStack stack, int rgb, float alpha) {
        net.m1227.render.FlatColor.begin(rgb, alpha);
        try {
            renderFirstPerson(mc, stack);
        } finally {
            net.m1227.render.FlatColor.end();
            GlStateManager.depthMask(true);
            GlStateManager.color(1f, 1f, 1f, 1f);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    /**
     * Draws the chosen model alone, centred on the middle of the weapon, with the current colours,
     * finish, size and width: the preview of the Weapon editor. No hands, no animation except the
     * butterfly knife opening and closing.
     */
    public void renderPreview() {
        active = previewModel();
        try {
            renderPreview0();
        } finally {
            active = null;
        }
    }

    private void renderPreview0() {
        thick = size.f();
        len = 1.30f * length.f() * (knife() ? 0.8f : 1f);
        String md = cur();
        net.m1227.weapons.WeaponLibrary.Entry im = imported(md);
        if (im != null) {
            if (!im.ready()) return;
            GlStateManager.pushMatrix();
            float sa = scaleAll.f();
            GlStateManager.scale(sa, sa, sa);
            // centre the preview on the middle of the weapon
            float lw = im.fit.lengthWith(im.profile), mid = (0.5f - im.fit.gripAt) * lw + im.profile.along;
            GlStateManager.translate(-(OX + AX * (GRIP_STATION + mid)), -(OY + AY * (GRIP_STATION + mid)), -OZ);
            drawImported(im, im.anim); // the editor sets up the lights (fixed to the screen)
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableCull();
            GlStateManager.color(1f, 1f, 1f, 1f);
            return;
        }
        int b = primary.rgb(), h = secondary.rgb(), g = glowRgb();
        tipColor = -1;
        int fi = java.util.Arrays.asList(FINISHES).indexOf(finish.get());
        if (fi > 0) {
            int[] fc = FINISH_COLORS[fi];
            b = fc[0];
            tipColor = fc[1];
            h = fc[2];
            if (!matchWeaponFx.on()) g = fc[3];
        }
        widthBoost = md.equals("Cleaver") ? 0f : (md.equals("Rune Greatsword") || md.equals("Holy Claymore") || md.equals("Obsidian Edge")) ? 0.4f : 1f;
        GlStateManager.pushMatrix();
        float sa = scaleAll.f();
        GlStateManager.scale(sa, sa, sa);
        GlStateManager.translate(-(OX + AX * len * 0.5f), -(OY + AY * len * 0.5f), -OZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        if (md.equals("Butterfly Knife")) {
            net.m1227.vfx.MeshAssets.Rigid k = net.m1227.vfx.MeshAssets.knife();
            float t = (System.currentTimeMillis() % 3000) / 3000f;
            float ft = 39f * (0.5f - 0.5f * (float) Math.cos(t * Math.PI * 2));
            if (k != null) butterflyMesh(k, ft, b, h, g);
            else katana(b, h, g);
        } else {
            models(md, b, h, g);
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
        GlStateManager.color(1f, 1f, 1f, 1f);
        net.m1227.render.FlatColor.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /** Model dispatch shared by the first-person view and the editor preview. */
    private void models(String md, int b, int h, int g) {
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
        else if (md.equals("Karambit")) karambit(b, h, g);
        else if (md.equals("Bayonet")) bayonet(b, h, g);
        else if (md.equals("Flip Knife")) flip(b, h, g);
        else if (md.equals("Huntsman")) huntsman(b, h, g);
        else if (md.equals("Skeleton Knife")) skeleton(b, h, g);
        else if (md.equals("Push Dagger")) push(b, h, g);
        else katana(b, h, g);
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
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
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
        float pulse = 0.9f + 0.1f * (float) Math.sin(System.nanoTime() / 1e9 * 2.5);
        for (int k = 1; k <= 3; k++) {
            float[] hw2 = new float[10], th2 = new float[10];
            for (int i = 0; i < 10; i++) {
                hw2[i] = hw[i] * (1f + k * 0.9f) + (i == 9 ? 0f : 0.004f * k);
                th2[i] = th[i] * (1f + k * 0.9f);
            }
            extrude(ss, hw2, th2, cv, g, g, 0.32f / k * pulse, true);
        }
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
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
        boolean down = key != 0 && mc.currentScreen == null && net.m1227.core.InputManager.isDown(key);
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
        butterflyMesh(k, ft, blade, hilt, glow);
    }

    /** The butterfly knife at animation frame ft (0 = open, 39 = closed). */
    private void butterflyMesh(net.m1227.vfx.MeshAssets.Rigid k, float ft, int blade, int hilt, int glow) {
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
        float[] th = {0.012f, 0.011f, 0.01f, 0.009f, 0.006f};
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
            float flick = 0.8f + 0.2f * (float) Math.sin(System.nanoTime() / 1e9 * 6.0);
            extrude(cs, chw, cth, ccv, g, 0xFFFFFF, flick, true);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        }
    }

    // ------------------------------------------------------- geometry kit

    private void pt(float s, float n, float z, float curve, float[] out) {
        float a = s * len;
        z *= thick;
        out[0] = OX + AX * a + NX * (n + curve);
        out[1] = OY + AY * a + NY * (n + curve);
        out[2] = OZ + z;
    }

    /**
     * Blade through stations (s, half width, half thickness, curve) with a real blade section:
     * a flat face on each side and a ground bevel down to each cutting edge (a hexagon). The
     * bevels are drawn a little brighter, like freshly sharpened steel. Emissive shells (flat)
     * use the same shape.
     */
    private static final float BEV = 0.42f;
    private static final float[][] SEC = {{1f, 0f}, {BEV, 1f}, {-BEV, 1f}, {-1f, 0f}, {-BEV, -1f}, {BEV, -1f}};

    private void extrude(float[] ss, float[] hw0, float[] th, float[] cv, int c0, int c1, float alpha, boolean flat) {
        float bw = 1f + (bladeWidth.f() - 1f) * widthBoost;
        float[] hw = new float[hw0.length];
        for (int i = 0; i < hw.length; i++) hw[i] = hw0[i] * bw;
        int ns = SEC.length;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i + 1 < ss.length; i++) {
            float u = (ss[i] - ss[0]) / Math.max(0.001f, ss[ss.length - 1] - ss[0]);
            for (int k = 0; k < ns; k++) {
                float[] a = SEC[k], b = SEC[(k + 1) % ns];
                pt(ss[i], a[0] * hw[i], a[1] * th[i], cv[i], p);
                pt(ss[i], b[0] * hw[i], b[1] * th[i], cv[i], q);
                pt(ss[i + 1], b[0] * hw[i + 1], b[1] * th[i + 1], cv[i + 1], r);
                pt(ss[i + 1], a[0] * hw[i + 1], a[1] * th[i + 1], cv[i + 1], s4);
                boolean bevel = k != 1 && k != 4;
                int col = tipColor >= 0 && !flat
                        ? ParticleSystem.mix(ParticleSystem.mix(c0, tipColor, u * u), 0xFFFFFF, bevel ? 0.2f : 0f)
                        : ParticleSystem.mix(c0, c1, u * 0.45f + (bevel ? 0.3f : 0f));
                face(p, q, r, s4, col, alpha, flat);
            }
        }
        GL11.glEnd();
        if (!flat && alpha >= 0.99f && ss.length >= 3) ridge(ss, hw, th, cv, c1);
    }

    /** Detail pass: a bright ridge line down both faces of the blade and a glint along each edge. */
    private void ridge(float[] ss, float[] hw, float[] th, float[] cv, int light) {
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i + 1 < ss.length; i++) {
            if (hw[i] < 0.004f) continue;
            float w0 = 0.0035f / Math.max(0.3f, thick), w1 = w0;
            for (int side = -1; side <= 1; side += 2) {
                // centre ridge (sits just above the diamond's peak)
                pt(ss[i], -w0, side * th[i] * 1.03f, cv[i], p);
                pt(ss[i], w0, side * th[i] * 1.03f, cv[i], q);
                pt(ss[i + 1], w1, side * th[i + 1] * 1.03f, cv[i + 1], r);
                pt(ss[i + 1], -w1, side * th[i + 1] * 1.03f, cv[i + 1], s4);
                face(p, q, r, s4, ParticleSystem.mix(light, 0x000000, 0.35f), 0.55f, true);
                // edge glint
                pt(ss[i], side * hw[i] * 0.97f, -0.002f, cv[i], p);
                pt(ss[i], side * hw[i] * 0.97f, 0.002f, cv[i], q);
                pt(ss[i + 1], side * hw[i + 1] * 0.97f, 0.002f, cv[i + 1], r);
                pt(ss[i + 1], side * hw[i + 1] * 0.97f, -0.002f, cv[i + 1], s4);
                face(p, q, r, s4, 0xFFFFFF, 0.55f, true);
            }
        }
        GL11.glEnd();
    }

    // ------------------------------------------------------------- knives

    private boolean knife() {
        String m = cur();
        return m.equals("Karambit") || m.equals("Bayonet") || m.equals("Flip Knife") || m.equals("Huntsman")
                || m.equals("Skeleton Knife") || m.equals("Push Dagger");
    }

    /**
     * Draw animation: starts when you switch to the sword (or it appears in your hand again after
     * a moment without it), and when you open the world. Progress lives in drawT.
     */
    private void updateDraw(Minecraft mc) {
        long now = System.nanoTime();
        int slot = mc.thePlayer != null ? mc.thePlayer.inventory.currentItem : -1;
        if (now - lastRenderNanos > 300_000_000L || slot != drawSlot) drawStart = now;
        lastRenderNanos = now;
        drawSlot = slot;
        if (drawAnim.is("Off") || drawStart < 0L) {
            drawT = 1f;
            return;
        }
        float dur = drawAnim.is("Quick") ? 0.28f : drawAnim.is("Smooth") ? 0.7f : 0.55f;
        drawT = Math.min(1f, (now - drawStart) / 1e9f / (dur / animSpeed.f()));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    /** The turning part of the draw animation, in item space around the grip. */
    private void drawSpin() {
        if (drawT >= 1f) return;
        float rest = 1f - (drawAnim.is("Smooth") ? smooth(drawT) : easeOutBack(drawT));
        if (drawAnim.is("Spin")) GlStateManager.rotate(360f * rest, AX, AY, 0f);        // around the blade
        else if (drawAnim.is("Flip")) GlStateManager.rotate(-360f * rest, 0f, 0f, 1f);  // cartwheel in the hand
    }

    /**
     * Inspect (all models except the butterfly, which flips), around the grip:
     * Twirl spins the sword in the hand like a baton, Turn over shows both sides, Toss throws it a
     * little up with a flip and catches it, Show off brings the blade close and turns it slowly.
     */
    private void inspect(Minecraft mc) {
        int key = inspectKey.code();
        long now = System.currentTimeMillis();
        String st = inspectStyle.get();
        long dur = (long) ((st.equals("Show off") ? 2600L : st.equals("Toss") ? 1300L : st.equals("Flip") ? 1100L : 1600L)
                * inspectLength.f() / animSpeed.f());
        boolean down = key != 0 && mc.currentScreen == null && net.m1227.core.InputManager.isDown(key);
        net.m1227.weapons.WeaponAnimator ma = modelAnim;
        if (ma != null && ma.has(net.m1227.weapons.WeaponAnimator.INSPECT)) {
            // the model has its own inspect animation: play it instead of the built-in one
            if (down && !inspectWasDown && !ma.busy(seconds())) ma.trigger(net.m1227.weapons.WeaponAnimator.INSPECT, seconds());
            inspectWasDown = down;
            return;
        }
        if (down && !inspectWasDown && (inspectStart < 0L || now - inspectStart > dur + 100L)) {
            inspectStart = now;
            inspectCancel = -1L;
        }
        inspectWasDown = down;
        if (inspectStart < 0L) return;
        float t = (now - inspectStart) / (float) dur;
        // cancelled by a swing: the pose fades back to the hand over 0.15 s (turns taken the short way)
        float w = 1f;
        if (inspectCancel >= 0L) {
            w = 1f - smooth((now - inspectCancel) / 150f);
            t = Math.min(t, (inspectCancel - inspectStart) / (float) dur);
        }
        if (t >= 1f || w <= 0f) {
            inspectStart = -1L;
            inspectCancel = -1L;
            return;
        }
        float e = t * t * (3f - 2f * t);
        float lift = (float) Math.sin(t * Math.PI);
        if (st.equals("Twirl")) {
            GlStateManager.rotate(-20f * lift * w, 1f, 0f, 0f);
            GlStateManager.rotate(shortWay(-720f * e) * w, 0f, 0f, 1f);
        } else if (st.equals("Toss")) {
            float up = 4f * t * (1f - t);                             // parabola: up and back down
            GlStateManager.translate(AX * 0.55f * up * w, AY * 0.55f * up * w, 0.1f * up * w);
            GlStateManager.rotate(shortWay(-360f * e) * w, 1f, -1f, 0f); // end over end
        } else if (st.equals("Show off")) {
            float in = Math.min(1f, t / 0.22f), out = Math.max(0f, (t - 0.78f) / 0.22f);
            float k = smooth(in) * (1f - smooth(out)) * w;
            float turn = Math.max(0f, Math.min(1f, (t - 0.18f) / 0.64f));
            GlStateManager.rotate(-45f * k, 1f, 0f, 0f);
            GlStateManager.rotate(30f * k, 0f, 0f, 1f);
            GlStateManager.rotate(200f * smooth(turn) * w, AX, AY, 0f);
        } else if (st.equals("Flip")) {
            // a quick double flip in the hand, cartwheeling around the grip
            float f = 1f - (1f - t) * (1f - t) * (1f - t);
            GlStateManager.rotate(-14f * lift * w, 1f, 0f, 0f);
            GlStateManager.rotate(shortWay(720f * f) * w, 0f, 0f, 1f);
        } else {
            GlStateManager.rotate(-30f * lift * w, 1f, 0f, 0f);       // bring it towards the camera
            GlStateManager.rotate(25f * lift * w, 0f, 0f, 1f);
            GlStateManager.rotate(shortWay(360f * e) * w, AX, AY, 0f);  // spin around the blade's axis
        }
    }

    /** An angle reduced to -180..180 (same orientation), so fading it out never spins backwards. */
    private static float shortWay(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static int metal(int c) {
        return ParticleSystem.mix(c, 0xB8BEC8, 0.65f);
    }

    /** Box like {@link #box} but shifted across the blade by nc. */
    private void slab(float s0, float s1, float nc, float hw, float th, int c) {
        float[][] sec = {{1f, 1f}, {-1f, 1f}, {-1f, -1f}, {1f, -1f}};
        GL11.glBegin(GL11.GL_QUADS);
        for (int k = 0; k < 4; k++) {
            float[] a = sec[k], b = sec[(k + 1) % 4];
            pt(s0, a[0] * hw, a[1] * th, nc, p);
            pt(s0, b[0] * hw, b[1] * th, nc, q);
            pt(s1, b[0] * hw, b[1] * th, nc, r);
            pt(s1, a[0] * hw, a[1] * th, nc, s4);
            face(p, q, r, s4, c, 1f, false);
        }
        for (int end = 0; end < 2; end++) {
            float s = end == 0 ? s0 : s1;
            pt(s, hw, th, nc, p);
            pt(s, -hw, th, nc, q);
            pt(s, -hw, -th, nc, r);
            pt(s, hw, -th, nc, s4);
            face(p, q, r, s4, c, 1f, false);
        }
        GL11.glEnd();
    }

    /** Flat ring (finger loop) centred at station sc, across offset nc, in the blade plane. */
    private void ring(float sc, float nc, float ro, float ri, float th, int c) {
        int n = 22;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            for (int side = -1; side <= 1; side += 2) {
                pt(sc + ro * c0 / len, ro * s0, side * th, nc, p);
                pt(sc + ro * c1 / len, ro * s1, side * th, nc, q);
                pt(sc + ri * c1 / len, ri * s1, side * th, nc, r);
                pt(sc + ri * c0 / len, ri * s0, side * th, nc, s4);
                face(p, q, r, s4, c, 1f, false);
            }
            for (int w = 0; w < 2; w++) {
                float rr = w == 0 ? ro : ri;
                pt(sc + rr * c0 / len, rr * s0, -th, nc, p);
                pt(sc + rr * c1 / len, rr * s1, -th, nc, q);
                pt(sc + rr * c1 / len, rr * s1, th, nc, r);
                pt(sc + rr * c0 / len, rr * s0, th, nc, s4);
                face(p, q, r, s4, ParticleSystem.mix(c, 0x000000, w == 0 ? 0.1f : 0.3f), 1f, false);
            }
        }
        GL11.glEnd();
    }

    private void karambit(int blade, int hilt, int g) {
        ring(-0.07f, 0f, 0.075f, 0.045f, 0.02f, metal(hilt));
        grip(0.0f, 0.3f, 0.032f, hilt, true);
        box(0.3f, 0.335f, 0.036f, 0.026f, metal(blade), 0f);
        int n = 14;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.335f + 0.5f * u;
            hw[i] = 0.05f * (1f - u * u) + 0.004f;
            th[i] = 0.011f * (1f - 0.6f * u);
            cv[i] = -0.32f * u * u;                                  // the claw hook
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.7f, 0.5f);
    }

    private void bayonet(int blade, int hilt, int g) {
        box(-0.035f, 0.0f, 0.036f, 0.03f, metal(hilt), 0f);         // pommel
        grip(0.0f, 0.32f, 0.033f, hilt, true);
        box(0.32f, 0.35f, 0.095f, 0.028f, metal(hilt), 0f);         // cross guard
        int n = 10;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.35f + 0.65f * u;
            hw[i] = u < 0.72f ? 0.05f : 0.05f * (1f - (u - 0.72f) / 0.28f);
            th[i] = 0.012f * (1f - 0.5f * u);
            cv[i] = u < 0.72f ? 0f : -0.02f * (u - 0.72f) / 0.28f;  // clip point
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.6f, 0.5f);
    }

    private void flip(int blade, int hilt, int g) {
        int n0 = 6;
        float[] hs = new float[n0], hh = new float[n0], ht = new float[n0], hc = new float[n0];
        for (int i = 0; i < n0; i++) {
            float u = i / (float) (n0 - 1);
            hs[i] = 0.33f * u;
            hh[i] = 0.026f + 0.01f * (float) Math.sin(u * Math.PI);
            ht[i] = 0.02f;
            hc[i] = 0.01f * (float) Math.sin(u * Math.PI);
        }
        int keep = tipColor;
        tipColor = -1;
        extrude(hs, hh, ht, hc, hilt, ParticleSystem.mix(hilt, 0xFFFFFF, 0.15f), 1f, false);
        tipColor = keep;
        box(0.31f, 0.36f, 0.04f, 0.024f, metal(hilt), 0f);         // pivot
        int n = 12;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.36f + 0.58f * u;
            hw[i] = 0.055f * (float) Math.sin(Math.PI * (0.18f + 0.82f * u)) + 0.006f;
            th[i] = 0.01f * (1f - 0.5f * u);
            cv[i] = 0.025f * u;
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.6f, 0.5f);
    }

    private void huntsman(int blade, int hilt, int g) {
        box(-0.03f, 0.0f, 0.038f, 0.03f, metal(hilt), 0f);
        grip(0.0f, 0.3f, 0.036f, hilt, true);
        box(0.3f, 0.33f, 0.085f, 0.03f, metal(hilt), 0f);
        int n = 12;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.33f + 0.67f * u;
            hw[i] = u < 0.65f ? 0.06f + 0.008f * u : 0.065f * (1f - (u - 0.65f) / 0.35f);
            th[i] = 0.013f * (1f - 0.5f * u);
            cv[i] = u < 0.65f ? 0.008f * u : -0.03f * (u - 0.65f) / 0.35f;
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        // sawback teeth on the spine
        for (int i = 0; i < 6; i++) {
            float s0 = 0.4f + i * 0.035f;
            slab(s0, s0 + 0.018f, 0.068f, 0.008f, 0.009f, metal(blade));
        }
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.6f, 0.5f);
    }

    private void skeleton(int blade, int hilt, int g) {
        ring(-0.06f, 0f, 0.06f, 0.038f, 0.018f, metal(hilt));
        slab(0.0f, 0.3f, 0.026f, 0.009f, 0.016f, metal(hilt));      // open frame handle
        slab(0.0f, 0.3f, -0.026f, 0.009f, 0.016f, metal(hilt));
        box(0.0f, 0.02f, 0.035f, 0.016f, metal(hilt), 0f);
        box(0.28f, 0.33f, 0.04f, 0.022f, metal(hilt), 0f);
        if (emissive.on()) slab(0.03f, 0.27f, 0f, 0.004f, 0.004f, g);
        int n = 11;
        float[] ss = new float[n], hw = new float[n], th = new float[n], cv = new float[n];
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            ss[i] = 0.33f + 0.6f * u;
            hw[i] = 0.05f * (1f - (float) Math.pow(u, 2.2)) + 0.003f;
            th[i] = 0.011f * (1f - 0.5f * u);
            cv[i] = -0.03f * u * u;
        }
        hw[n - 1] = 0f;
        th[n - 1] = 0f;
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.6f, 0.5f);
    }

    private void push(int blade, int hilt, int g) {
        // T-handle across the fist, short broad blade straight out of it
        slab(0.0f, 0.07f, 0f, 0.11f, 0.03f, hilt);
        slab(0.07f, 0.11f, 0f, 0.035f, 0.022f, metal(hilt));
        float[] ss = {0.11f, 0.2f, 0.36f, 0.5f, 0.6f};
        float[] hw = {0.05f, 0.07f, 0.055f, 0.03f, 0f};
        float[] th = {0.016f, 0.014f, 0.011f, 0.007f, 0f};
        float[] cv = new float[ss.length];
        extrude(ss, hw, th, cv, blade, ParticleSystem.mix(blade, 0xFFFFFF, 0.6f), 1f, false);
        if (emissive.on()) edgeLine(ss, hw, cv, g, 0.6f, 1f);
    }

    // ------------------------------------------------------------- hold & swing styles

    /** Classic, Two-handed, Heavy, Thrust, Flourish, Twin. */
    public String style() {
        if (holdMode.is("Classic")) return "Classic";
        String m = cur();
        net.m1227.weapons.WeaponLibrary.Entry im = imported(m);
        if (im != null) {
            String s = im.profile.style;
            if (s != null && !s.equals("Auto") && !s.isEmpty()) return s;
            if (!im.ready()) return "Classic";
            float l = im.fit.lengthWith(im.profile);
            return l >= 1.55f ? "Heavy" : l <= 0.95f ? "Thrust" : "Classic";
        }
        if (m.equals("Katana") || m.equals("Holy Claymore")) return "Two-handed";
        if (m.equals("Cleaver") || m.equals("Obsidian Edge") || m.equals("Rune Greatsword")) return "Heavy";
        if (m.equals("Void Rapier") || m.equals("Butterfly Knife")) return "Thrust";
        if (m.equals("Energy Saber") || m.equals("Storm Blade")) return "Flourish";
        if (m.equals("Twin Fang") || m.equals("Dragon Fang")) return "Twin";
        if (m.equals("Karambit")) return "Flourish";
        if (m.equals("Bayonet") || m.equals("Huntsman") || m.equals("Skeleton Knife") || m.equals("Push Dagger")) return "Thrust";
        return "Classic";
    }

    public boolean twoHanded() {
        return style().equals("Two-handed");
    }

    // ------------------------------------------------------------- motion engine

    private final net.m1227.weapons.WeaponMotion motion = new net.m1227.weapons.WeaponMotion();
    private final net.m1227.weapons.WeaponMotion.Input motionIn = new net.m1227.weapons.WeaponMotion.Input();
    private final net.m1227.weapons.WeaponMotion.Params motionP = new net.m1227.weapons.WeaponMotion.Params();

    public void holdTransform(float equip, float swingP) {
        holdTransform(equip, swingP, false, 1f);
    }

    /**
     * Replaces the vanilla first-person hold + swing transform for the custom swords, through the
     * layered motion engine (hold pose, draw, walking / sprint / jump / landing, camera inertia,
     * swings, combo, critical, blocking). Only the drawing moves: the real swing, hit timing and
     * blocking are Minecraft's.
     */
    public void holdTransform(float equip, float swingP, boolean blocking, float pt) {
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.entity.EntityPlayerSP p = mc.thePlayer;
        net.m1227.weapons.WeaponMotion.Input in = motionIn;
        in.time = System.nanoTime() / 1e9;
        in.swingProgress = swingP;
        in.equip = equip;
        in.blocking = blocking;
        if (p != null) {
            in.sprinting = p.isSprinting();
            in.onGround = p.onGround;
            in.inWater = p.isInWater() || p.isInLava() || p.isOnLadder() || p.ridingEntity != null;
            double mx = p.posX - p.prevPosX, mz = p.posZ - p.prevPosZ;
            in.speed = (float) Math.sqrt(mx * mx + mz * mz);
            in.motionY = (float) (p.posY - p.prevPosY);
            in.fallDistance = p.fallDistance;
            in.walkPhase = p.prevDistanceWalkedModified + (p.distanceWalkedModified - p.prevDistanceWalkedModified) * pt;
            in.yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;
            in.pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        }
        in.draw = drawT;
        in.drawRise = !drawAnim.is("Off");
        in.drawOvershoot = !drawAnim.is("Smooth") && !drawAnim.is("Quick");
        net.m1227.weapons.WeaponMotion.Params mp = motionP;
        mp.style = style();
        mp.swing = swingStyle.get();
        mp.swingSpeed = swingSpeed.f();
        mp.speed = animSpeed.f();
        mp.intensity = intensity.f();
        mp.sway = handSway.f();
        mp.move = moveMotion.f();
        mp.jump = jumpMotion.f();
        mp.alternate = altSwings.on();
        mp.combo = comboSwings.on();
        mp.critical = critSwing.on();
        mp.idle = idleMotion.on();
        mp.sprintPose = sprintPose.on();
        mp.smoothBlock = blockAnim.is("Smooth");
        net.m1227.module.AnimationsModule an = net.m1227.module.AnimationsModule.INSTANCE;
        mp.blockhit = an != null && an.isEnabled() && an.blockhit();
        int before = motion.swings();
        motion.update(in, mp);
        lastMotionUse = in.time;
        if (motion.swings() != before) {
            swingCount = motion.swings();
            // a new swing cancels a running inspect (it fades out instead of jumping)
            if (inspectStart >= 0L && inspectCancel < 0L) inspectCancel = System.currentTimeMillis();
        }
        for (float[] o : motion.ops) {
            if (o[0] == 0f) GlStateManager.translate(o[1], o[2], o[3]);
            else if (o[0] == 1f) GlStateManager.rotate(o[1], o[2], o[3], o[4]);
            else GlStateManager.scale(o[1], o[2], o[3]);
        }
    }

    /** Tilt of the current / last weapon swing's cut (degrees, sign = side), for slash effects. */
    public float slashTiltDeg() {
        return motion.slashTiltDeg();
    }

    /** True when the weapon motion engine has drawn a swing in the last second (it is in use). */
    public boolean motionInUse() {
        return isEnabled() && System.nanoTime() / 1e9 - lastMotionUse < 1.0;
    }

    private double lastMotionUse = -10;

    /** True when the last swing started was drawn as a critical one (falling while attacking). */
    public boolean lastSwingCritical() {
        return motion.lastSwingCritical();
    }

    /** Applies one of the motion presets. */
    private void preset(String name) {
        String sw = "Per model", draw = "Spin", block = "Smooth";
        double speed = 1.0, inten = 1.0, sway = 1.0, move = 1.0, jump = 1.0, anim = 1.0;
        boolean alt = true, combo = false, crit = true, idle = true;
        switch (name) {
            case "Classic PvP":
                sw = "Classic";
                draw = "Quick";
                block = "Instant";
                inten = 0.8;
                sway = 0.3;
                move = 0.5;
                jump = 0.5;
                alt = false;
                idle = false;
                break;
            case "Modern Combat":
                draw = "Rise";
                speed = 1.1;
                inten = 1.1;
                sway = 1.3;
                move = 1.2;
                jump = 1.2;
                combo = true;
                break;
            case "Cinematic":
                draw = "Flip";
                speed = 0.85;
                inten = 1.3;
                sway = 1.8;
                move = 1.5;
                jump = 1.5;
                anim = 0.9;
                combo = true;
                break;
            case "Minimal":
                sw = "Classic";
                draw = "Off";
                block = "Instant";
                inten = 0.5;
                sway = 0.0;
                move = 0.0;
                jump = 0.0;
                alt = false;
                crit = false;
                idle = false;
                break;
            default: // Smooth PvP
        }
        swingStyle.set(sw);
        drawAnim.set(draw);
        blockAnim.set(block);
        swingSpeed.setClamped(speed);
        intensity.setClamped(inten);
        handSway.setClamped(sway);
        moveMotion.setClamped(move);
        jumpMotion.setClamped(jump);
        animSpeed.setClamped(anim);
        altSwings.set(alt);
        comboSwings.set(combo);
        critSwing.set(crit);
        idleMotion.set(idle);
        net.m1227.core.Notifier.post(net.m1227.core.Notifier.Kind.SYSTEM, "Weapon motion", name, net.m1227.core.Notifier.ACCENT, 1500);
    }

    // ------------------------------------------------------------- hands

    /**
     * Draws your hand(s) wrapped around the grip, in the sword's own space, so they always stay
     * attached to the handle whatever the swing, rotation or size. Uses your skin's arm texture.
     */
    private void hands(Minecraft mc) {
        // blocking turns the sword across the screen: an arm held to the grip would swing into
        // view (a whole arm, or bent), so the hands are hidden while the block pose is on
        float bw = motionInUse() ? motion.blockWeight() : 0f;
        if (mc.thePlayer != null && mc.thePlayer.isBlocking() && bw < 0.05f) return; // Minecraft's own block pose
        if (bw > 0.3f) return;
        if (net.m1227.render.FlatColor.active()) return; // glow / outline copies are the weapon only
        if (!showHands.on() || mc.thePlayer == null || mc.thePlayer.isInvisible()) return;
        boolean slim = "slim".equals(mc.thePlayer.getSkinType());
        mc.getTextureManager().bindTexture(mc.thePlayer.getLocationSkin());
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
        GL11.glBegin(GL11.GL_QUADS);
        arm(cur().equals("Push Dagger") ? 0.035f : 0.17f, handAngle.f(), 40, 16, slim);
        if (twoHanded()) arm(0.05f, handAngle.f() + secondAngle.f(), 32, 48, slim);
        GL11.glEnd();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
    }

    private final float[][] hv = new float[8][3];

    /** One arm: fist on the grip at station s, forearm leaving at angleDeg from the handle's axis. */
    private void arm(float s, float angleDeg, int u, int v, boolean slim) {
        float a = s * len;
        float gx = OX + AX * a, gy = OY + AY * a;
        double th = Math.toRadians(225.0 + angleDeg);              // 225 deg = pointing down the handle
        float dx = (float) Math.cos(th), dy = (float) Math.sin(th); // forearm direction (item plane)
        float px = -dy, py = dx;                                    // across the arm
        float w = 0.075f * handSize.f(), d = 0.075f * handSize.f();
        float y0 = -w, y1 = 1.1f * handSize.f();
        float aw = slim ? 3f : 4f;
        // corners: i bit0 = across(-/+), bit1 = depth(-/+), bit2 = along(start/end)
        for (int i = 0; i < 8; i++) {
            float sx = (i & 1) == 0 ? -w : w, sz = (i & 2) == 0 ? -d : d, sy = (i & 4) == 0 ? y0 : y1;
            hv[i][0] = gx + px * sx + dx * sy;
            hv[i][1] = gy + py * sx + dy * sy;
            hv[i][2] = OZ + sz;
        }
        // Minecraft skin box layout for an arm (aw x 12 x 4) at (u, v): hand end is the "bottom" face
        float D = 4f, H = 12f;
        // long faces: front (u+D), back (u+D+aw+D), sides (u) and (u+D+aw)
        skinFace(hv[0], hv[1], hv[5], hv[4], u + D, v + D, aw, H, 0.85f);           // depth -
        skinFace(hv[3], hv[2], hv[6], hv[7], u + D + aw + D, v + D, aw, H, 0.85f);  // depth +
        skinFace(hv[2], hv[0], hv[4], hv[6], u, v + D, D, H, 0.7f);                 // across -
        skinFace(hv[1], hv[3], hv[7], hv[5], u + D + aw, v + D, D, H, 1.0f);        // across +
        skinFace(hv[0], hv[2], hv[3], hv[1], u + D + aw, v, aw, D, 0.9f);           // hand end
    }

    /** Quad a-b-c-d where a-b is the hand end (texture bottom) and c-d the far end (texture top). */
    private static void skinFace(float[] a, float[] b, float[] c, float[] d, float u, float v, float w, float h, float shade) {
        net.m1227.render.FlatColor.color(shade, shade, shade, 1f);
        float u0 = u / 64f, u1 = (u + w) / 64f, v0 = v / 64f, v1 = (v + h) / 64f;
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex3f(a[0], a[1], a[2]);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex3f(b[0], b[1], b[2]);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex3f(c[0], c[1], c[2]);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex3f(d[0], d[1], d[2]);
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
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
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
        GlStateManager.depthMask(!net.m1227.render.FlatColor.active());
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
        cyl(s0, s1, rad, rad * 0.62f, c);
        cyl(s0 - 0.004f, s1 + 0.004f, rad * 0.55f, rad * 0.4f, ParticleSystem.mix(c, 0xFFFFFF, 0.12f));
    }

    /**
     * Box from s0 to s1 with chamfered corners (an octagonal prism), so guards, grips and pommels
     * catch the light on their edges instead of looking like plain blocks. "flare" widens the far
     * end (guards).
     */
    private void box(float s0, float s1, float hw, float th, int c, float flare) {
        float ch = 0.32f;
        float[][] sec = {{1f, 1f - ch}, {1f - ch, 1f}, {-(1f - ch), 1f}, {-1f, 1f - ch}, {-1f, -(1f - ch)}, {-(1f - ch), -1f}, {1f - ch, -1f}, {1f, -(1f - ch)}};
        int n = sec.length;
        GL11.glBegin(GL11.GL_QUADS);
        for (int k = 0; k < n; k++) {
            float[] a = sec[k], b = sec[(k + 1) % n];
            pt(s0, a[0] * hw, a[1] * th, 0f, p);
            pt(s0, b[0] * hw, b[1] * th, 0f, q);
            pt(s1, b[0] * (hw + flare), b[1] * th, 0f, r);
            pt(s1, a[0] * (hw + flare), a[1] * th, 0f, s4);
            face(p, q, r, s4, k % 2 == 0 ? c : ParticleSystem.mix(c, 0xFFFFFF, 0.12f), 1f, false);
        }
        GL11.glEnd();
        // end caps as triangle fans around the centre
        for (int end = 0; end < 2; end++) {
            float s = end == 0 ? s0 : s1, w = end == 0 ? hw : hw + flare;
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            pt(s, 0f, 0f, 0f, p);
            float cr = ((c >> 16) & 255) / 255f * 0.8f, cg = ((c >> 8) & 255) / 255f * 0.8f, cb = (c & 255) / 255f * 0.8f;
            net.m1227.render.FlatColor.color(cr, cg, cb, 1f);
            GL11.glVertex3f(p[0], p[1], p[2]);
            for (int k = 0; k <= n; k++) {
                float[] a = sec[k % n];
                pt(s, a[0] * w, a[1] * th, 0f, q);
                GL11.glVertex3f(q[0], q[1], q[2]);
            }
            GL11.glEnd();
        }
    }

    /** Round disc (a tsuba or pommel cap) across the blade axis: radius rn across, rz in depth. */
    private void cyl(float s0, float s1, float rn, float rz, int c) {
        int n = 16;
        GL11.glBegin(GL11.GL_QUADS);
        for (int k = 0; k < n; k++) {
            double a0 = Math.PI * 2 * k / n, a1 = Math.PI * 2 * (k + 1) / n;
            float c0 = (float) Math.cos(a0), z0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), z1 = (float) Math.sin(a1);
            pt(s0, c0 * rn, z0 * rz, 0f, p);
            pt(s0, c1 * rn, z1 * rz, 0f, q);
            pt(s1, c1 * rn, z1 * rz, 0f, r);
            pt(s1, c0 * rn, z0 * rz, 0f, s4);
            face(p, q, r, s4, c, 1f, false);
        }
        GL11.glEnd();
        for (int end = 0; end < 2; end++) {
            float s = end == 0 ? s0 : s1;
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            pt(s, 0f, 0f, 0f, p);
            float k2 = end == 0 ? 0.7f : 0.95f;
            net.m1227.render.FlatColor.color(((c >> 16) & 255) / 255f * k2, ((c >> 8) & 255) / 255f * k2, (c & 255) / 255f * k2, 1f);
            GL11.glVertex3f(p[0], p[1], p[2]);
            for (int k = 0; k <= n; k++) {
                double a = Math.PI * 2 * k / n;
                pt(s, (float) Math.cos(a) * rn, (float) Math.sin(a) * rz, 0f, q);
                GL11.glVertex3f(q[0], q[1], q[2]);
            }
            GL11.glEnd();
        }
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
    // second light for the highlight, from the other side and towards the camera
    private static final float HX = -0.55f, HY = 0.35f, HZ = 0.76f;

    /** Emits one shaded quad (inside glBegin(GL_QUADS)). flat = unlit (emissive). */
    private static void face(float[] a, float[] b, float[] c, float[] d, int col, float alpha, boolean flat) {
        float shade = 1f, spec = 0f;
        if (!flat) {
            float ux = c[0] - a[0], uy = c[1] - a[1], uz = c[2] - a[2];
            float vx = d[0] - b[0], vy = d[1] - b[1], vz = d[2] - b[2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l > 1e-6f) {
                float dot = Math.abs((nx * LX + ny * LY + nz * LZ) / l); // two-sided
                shade = 0.38f + 0.62f * dot;
                float h = Math.abs((nx * HX + ny * HY + nz * HZ) / l);
                spec = h * h * h * h * h * h * 0.45f;
            }
        }
        float r = Math.min(1f, ((col >> 16) & 255) / 255f * shade + spec);
        float g = Math.min(1f, ((col >> 8) & 255) / 255f * shade + spec);
        float bl = Math.min(1f, (col & 255) / 255f * shade + spec);
        net.m1227.render.FlatColor.color(r, g, bl, alpha);
        GL11.glVertex3f(a[0], a[1], a[2]);
        GL11.glVertex3f(b[0], b[1], b[2]);
        GL11.glVertex3f(c[0], c[1], c[2]);
        GL11.glVertex3f(d[0], d[1], d[2]);
    }
}
