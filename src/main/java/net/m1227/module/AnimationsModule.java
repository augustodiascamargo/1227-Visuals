package net.m1227.module;

import java.lang.reflect.Field;
import net.m1227.Mod1227;
import net.m1227.core.Category;
import net.m1227.core.Module;
import net.m1227.core.Notifier;
import net.m1227.core.Setting;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.Project;

/**
 * Classic 1.7 first-person animations on 1.8.9.
 *
 * 1.8 freezes the swing while you block, eat/drink or draw a bow; 1.7 kept
 * playing it. When any 1.7 option is on, this module draws the first-person
 * hand itself (same steps as vanilla's ItemRenderer, with the 1.7 swing
 * transforms) and cancels the vanilla hand pass. Maps fall back to vanilla.
 *
 * Everything here is visual only: no packets, no change to reach, attack
 * timing or blocking. "Old swing" restarts the arm animation locally while
 * you hold attack during block/eat, it does not attack.
 */
public final class AnimationsModule extends Module {
    private final Setting.Bool blockhit, eating, bow, rod, swing;
    private final Setting.Num offX, offY, offZ, scale;

    private Field fItem, fEquip, fPrevEquip;
    private boolean reflectionFailed, warned;

    public AnimationsModule() {
        super("1.7 Animations", "Classic 1.7 blockhit, eating, bow, rod and swing.", Category.ANIMATIONS, false);
        add(new Setting.Action("Use 1.7 animations (all on)", new Runnable() {
            @Override
            public void run() {
                setAll(true);
                Notifier.post(Notifier.Kind.SYSTEM, "Animations", "1.7 set", Notifier.ACCENT, 1500);
            }
        }));
        add(new Setting.Action("Use 1.8 animations (vanilla)", new Runnable() {
            @Override
            public void run() {
                setAll(false);
                Notifier.post(Notifier.Kind.SYSTEM, "Animations", "1.8 vanilla", Notifier.INFO, 1500);
            }
        }));
        blockhit = add(new Setting.Bool("Old sword blocking (blockhit)", true));
        eating = add(new Setting.Bool("Old eating / drinking", true));
        bow = add(new Setting.Bool("Old bow", true));
        rod = add(new Setting.Bool("Old rod (approximation)", false));
        swing = add(new Setting.Bool("Old swing while using items", true));
        offX = add(new Setting.Num("Item offset X", 0, -0.5, 0.5, 0.01, ""));
        offY = add(new Setting.Num("Item offset Y", 0, -0.5, 0.5, 0.01, ""));
        offZ = add(new Setting.Num("Item offset Z", 0, -0.5, 0.5, 0.01, ""));
        scale = add(new Setting.Num("Item scale", 1.0, 0.5, 1.5, 0.05, "x"));
    }

    private void setAll(boolean on) {
        blockhit.set(on);
        eating.set(on);
        bow.set(on);
        swing.set(on);
        if (!on) {
            rod.set(false);
            offX.set(0.0);
            offY.set(0.0);
            offZ.set(0.0);
            scale.set(1.0);
        }
    }

    private boolean needsTakeover() {
        return blockhit.on() || eating.on() || bow.on() || rod.on() || offX.get() != 0 || offY.get() != 0
                || offZ.get() != 0 || scale.get() != 1.0;
    }

    private boolean reflect() {
        if (fItem != null) return true;
        if (reflectionFailed) return false;
        try {
            fItem = ReflectionHelper.findField(ItemRenderer.class, "itemToRender", "field_78453_b");
            fEquip = ReflectionHelper.findField(ItemRenderer.class, "equippedProgress", "field_78454_c");
            fPrevEquip = ReflectionHelper.findField(ItemRenderer.class, "prevEquippedProgress", "field_78451_d");
            return true;
        } catch (Throwable t) {
            reflectionFailed = true;
            Mod1227.LOGGER.warn("1.7 animations unavailable (could not access ItemRenderer): " + t);
            return false;
        }
    }

    // ------------------------------------------------------- old swing

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !swing.on()) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.currentScreen != null) return;
        if (p.isUsingItem() && mc.gameSettings.keyBindAttack.isKeyDown() && mc.objectMouseOver != null) {
            // restart the local arm animation (no packet is sent)
            if (!p.isSwingInProgress || p.swingProgressInt >= 3 || p.swingProgressInt < 0) {
                p.swingProgressInt = -1;
                p.isSwingInProgress = true;
            }
        }
    }

    // ------------------------------------------------ first-person hand

    @SubscribeEvent
    public void onHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!needsTakeover() || mc.thePlayer == null || mc.gameSettings.anaglyph || !reflect()) return;
        ItemStack item;
        try {
            item = (ItemStack) fItem.get(mc.getItemRenderer());
        } catch (Exception ex) {
            return;
        }
        if (item != null && item.getItem() instanceof ItemMap) return; // vanilla draws maps
        e.setCanceled(true);
        renderHand(mc, e.partialTicks, item);
    }

    private void renderHand(Minecraft mc, float pt, ItemStack item) {
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(handFov(mc, pt), (float) mc.displayWidth / (float) mc.displayHeight, 0.05F,
                mc.gameSettings.renderDistanceChunks * 16 * 2.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        try {
            Entity view = mc.getRenderViewEntity();
            boolean sleeping = view instanceof EntityLivingBase && ((EntityLivingBase) view).isPlayerSleeping();
            GlStateManager.pushMatrix();
            hurtCamera(mc, pt);
            if (mc.gameSettings.viewBobbing) bobbing(mc, pt);
            if (mc.gameSettings.thirdPersonView == 0 && !sleeping && !mc.gameSettings.hideGUI && !mc.playerController.isSpectator()) {
                mc.entityRenderer.enableLightmap();
                firstPerson(mc, pt, item);
                mc.entityRenderer.disableLightmap();
            }
            GlStateManager.popMatrix();
            if (mc.gameSettings.thirdPersonView == 0 && !sleeping) mc.getItemRenderer().renderOverlays(pt);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                Mod1227.LOGGER.warn("1.7 animations: hand render failed, falling back to vanilla", t);
            }
            reflectionFailed = true;
            fItem = null;
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
        }
    }

    private static float handFov(Minecraft mc, float pt) {
        float f = 70.0F;
        Entity v = mc.getRenderViewEntity();
        if (v instanceof EntityLivingBase && ((EntityLivingBase) v).getHealth() <= 0.0F) {
            float d = ((EntityLivingBase) v).deathTime + pt;
            f /= (1.0F - 500.0F / (d + 500.0F)) * 2.0F + 1.0F;
        }
        Block b = ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, v, pt);
        if (b.getMaterial() == Material.water) f = f * 60.0F / 70.0F;
        return f;
    }

    private static void hurtCamera(Minecraft mc, float pt) {
        if (!(mc.getRenderViewEntity() instanceof EntityLivingBase)) return;
        EntityLivingBase e = (EntityLivingBase) mc.getRenderViewEntity();
        float f = e.hurtTime - pt;
        if (e.getHealth() <= 0.0F) {
            float d = e.deathTime + pt;
            GlStateManager.rotate(40.0F - 8000.0F / (d + 200.0F), 0.0F, 0.0F, 1.0F);
        }
        if (f < 0.0F) return;
        f /= (float) e.maxHurtTime;
        f = MathHelper.sin(f * f * f * f * (float) Math.PI);
        float yaw = e.attackedAtYaw;
        GlStateManager.rotate(-yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-f * 14.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
    }

    private static void bobbing(Minecraft mc, float pt) {
        if (!(mc.getRenderViewEntity() instanceof net.minecraft.entity.player.EntityPlayer)) return;
        net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) mc.getRenderViewEntity();
        float f = p.distanceWalkedModified - p.prevDistanceWalkedModified;
        float f1 = -(p.distanceWalkedModified + f * pt);
        float f2 = p.prevCameraYaw + (p.cameraYaw - p.prevCameraYaw) * pt;
        float f3 = p.prevCameraPitch + (p.cameraPitch - p.prevCameraPitch) * pt;
        GlStateManager.translate(MathHelper.sin(f1 * (float) Math.PI) * f2 * 0.5F, -Math.abs(MathHelper.cos(f1 * (float) Math.PI) * f2), 0.0F);
        GlStateManager.rotate(MathHelper.sin(f1 * (float) Math.PI) * f2 * 3.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(Math.abs(MathHelper.cos(f1 * (float) Math.PI - 0.2F) * f2) * 5.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f3, 1.0F, 0.0F, 0.0F);
    }

    private void firstPerson(Minecraft mc, float pt, ItemStack item) throws IllegalAccessException {
        ItemRenderer ir = mc.getItemRenderer();
        float eq = fEquip.getFloat(ir), prev = fPrevEquip.getFloat(ir);
        float equip = 1.0F - (prev + (eq - prev) * pt);
        EntityPlayerSP p = mc.thePlayer;
        float swingP = p.getSwingProgress(pt);
        float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * pt;
        float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * pt;

        GlStateManager.pushMatrix();
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.popMatrix();

        int light = mc.theWorld.getCombinedLight(new BlockPos(p.posX, p.posY + p.getEyeHeight(), p.posZ), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (light & 65535), (float) (light >> 16));

        float armPitch = p.prevRenderArmPitch + (p.renderArmPitch - p.prevRenderArmPitch) * pt;
        float armYaw = p.prevRenderArmYaw + (p.renderArmYaw - p.prevRenderArmYaw) * pt;
        GlStateManager.rotate((p.rotationPitch - armPitch) * 0.1F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate((p.rotationYaw - armYaw) * 0.1F, 0.0F, 1.0F, 0.0F);

        GlStateManager.enableRescaleNormal();
        GlStateManager.pushMatrix();
        GlStateManager.translate(offX.f(), offY.f(), offZ.f());
        if (item != null) {
            if (p.getItemInUseCount() > 0) {
                EnumAction action = item.getItemUseAction();
                if (action == EnumAction.EAT || action == EnumAction.DRINK) {
                    drinking(p, pt, item);
                    transform(equip, eating.on() ? swingP : 0.0F);
                } else if (action == EnumAction.BLOCK) {
                    transform(equip, blockhit.on() ? swingP : 0.0F);
                    blockTransform();
                } else if (action == EnumAction.BOW) {
                    transform(equip, bow.on() ? swingP : 0.0F);
                    bowTransform(pt, p, item);
                } else {
                    transform(equip, 0.0F);
                }
            } else {
                itemUsed(swingP);
                transform(equip, swingP);
            }
            float s = scale.f();
            if (s != 1.0F) GlStateManager.scale(s, s, s);
            if (rod.on() && item.getItem() instanceof ItemFishingRod) {
                // 1.7 drew rods with the regular tool pose turned 180 degrees around Y
                GlStateManager.translate(0.0F, 0.25F, 0.125F);
                GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(25.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.scale(1.7F, 1.7F, 1.7F);
                GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                ir.renderItem(p, item, ItemCameraTransforms.TransformType.NONE);
            } else {
                ir.renderItem(p, item, ItemCameraTransforms.TransformType.FIRST_PERSON);
            }
        } else if (!p.isInvisible()) {
            arm(mc, p, equip, swingP);
        }
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
    }

    // vanilla 1.8.9 ItemRenderer transforms (same numbers), used with the 1.7 swing values above

    private static void transform(float equip, float swing) {
        GlStateManager.translate(0.56F, -0.52F, -0.71999997F);
        GlStateManager.translate(0.0F, equip * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f = MathHelper.sin(swing * swing * (float) Math.PI);
        float f1 = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        GlStateManager.rotate(f * -20.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(0.4F, 0.4F, 0.4F);
    }

    private static void itemUsed(float swing) {
        float f = -0.4F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2.0F);
        float f2 = -0.2F * MathHelper.sin(swing * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
    }

    private static void drinking(AbstractClientPlayer p, float pt, ItemStack item) {
        float f = (float) p.getItemInUseCount() - pt + 1.0F;
        float f1 = f / (float) item.getMaxItemUseDuration();
        float f2 = MathHelper.abs(MathHelper.cos(f / 4.0F * (float) Math.PI) * 0.1F);
        if (f1 >= 0.8F) f2 = 0.0F;
        GlStateManager.translate(0.0F, f2, 0.0F);
        float f3 = 1.0F - (float) Math.pow(f1, 27.0D);
        GlStateManager.translate(f3 * 0.6F, f3 * -0.5F, 0.0F);
        GlStateManager.rotate(f3 * 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * 10.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(f3 * 30.0F, 0.0F, 0.0F, 1.0F);
    }

    private static void blockTransform() {
        GlStateManager.translate(-0.5F, 0.2F, 0.0F);
        GlStateManager.rotate(30.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(60.0F, 0.0F, 1.0F, 0.0F);
    }

    private static void bowTransform(float pt, AbstractClientPlayer p, ItemStack item) {
        GlStateManager.rotate(-18.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(-12.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(-8.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translate(-0.9F, 0.2F, 0.0F);
        float f = (float) item.getMaxItemUseDuration() - ((float) p.getItemInUseCount() - pt + 1.0F);
        float f1 = f / 20.0F;
        f1 = (f1 * f1 + f1 * 2.0F) / 3.0F;
        if (f1 > 1.0F) f1 = 1.0F;
        if (f1 > 0.1F) {
            float f2 = MathHelper.sin((f - 0.1F) * 1.3F);
            float f4 = f2 * (f1 - 0.1F);
            GlStateManager.translate(0.0F, f4 * 0.01F, 0.0F);
        }
        GlStateManager.translate(0.0F, 0.0F, f1 * 0.1F);
        GlStateManager.scale(1.0F, 1.0F, 1.0F + f1 * 0.2F);
    }

    private static void arm(Minecraft mc, AbstractClientPlayer p, float equip, float swing) {
        float f = -0.3F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        float f1 = 0.4F * MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI * 2.0F);
        float f2 = -0.4F * MathHelper.sin(swing * (float) Math.PI);
        GlStateManager.translate(f, f1, f2);
        GlStateManager.translate(0.64000005F, -0.6F, -0.71999997F);
        GlStateManager.translate(0.0F, equip * -0.6F, 0.0F);
        GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
        float f3 = MathHelper.sin(swing * swing * (float) Math.PI);
        float f4 = MathHelper.sin(MathHelper.sqrt_float(swing) * (float) Math.PI);
        GlStateManager.rotate(f4 * 70.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(f3 * -20.0F, 0.0F, 0.0F, 1.0F);
        mc.getTextureManager().bindTexture(p.getLocationSkin());
        GlStateManager.translate(-1.0F, 3.6F, 3.5F);
        GlStateManager.rotate(120.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(200.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(-135.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(5.6F, 0.0F, 0.0F);
        Render<AbstractClientPlayer> render = mc.getRenderManager().getEntityRenderObject(p);
        GlStateManager.disableCull();
        ((RenderPlayer) render).renderRightArm(p);
        GlStateManager.enableCull();
    }
}
